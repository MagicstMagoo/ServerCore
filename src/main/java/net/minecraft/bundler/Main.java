package net.minecraft.bundler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main(String[] argv) {
    (new Main()).run(argv);
  }
  
  private void run(String[] argv) {
    try {
      String defaultMainClassName = readResource("main-class", BufferedReader::readLine);
      String mainClassName = System.getProperty("bundlerMainClass", defaultMainClassName);
      String repoDir = System.getProperty("bundlerRepoDir", "");
      Path outputDir = Paths.get(repoDir, new String[0]);
      Files.createDirectories(outputDir, (FileAttribute<?>[])new FileAttribute[0]);
      List<URL> extractedUrls = new ArrayList<>();
      readAndExtractDir("versions", outputDir, extractedUrls);
      readAndExtractDir("libraries", outputDir, extractedUrls);
      if (mainClassName == null || mainClassName.isEmpty()) {
        System.out.println("Empty main class specified, exiting");
        System.exit(0);
      } 
      ClassLoader maybePlatformClassLoader = getClass().getClassLoader().getParent();
      URLClassLoader classLoader = new URLClassLoader(extractedUrls.<URL>toArray(new URL[0]), maybePlatformClassLoader);
      System.out.println("Starting " + mainClassName);
      Thread runThread = new Thread(() -> {
            try {
              Class<?> mainClass = Class.forName(mainClassName, true, classLoader);
              MethodHandle mainHandle = MethodHandles.lookup().findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class)).asFixedArity();
              mainHandle.invoke(argv);
            } catch (Throwable t) {
              Thrower.INSTANCE.sneakyThrow(t);
            } 
          }"ServerMain");
      runThread.setContextClassLoader(classLoader);
      runThread.start();
    } catch (Exception e) {
      e.printStackTrace(System.out);
      System.out.println("Failed to extract server libraries, exiting");
    } 
  }
  
  private <T> T readResource(String resource, ResourceParser<T> parser) throws Exception {
    String fullPath = "/META-INF/" + resource;
    InputStream is = getClass().getResourceAsStream(fullPath);
    try {
      if (is == null)
        throw new IllegalStateException("Resource " + fullPath + " not found"); 
      T t = parser.parse(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
      if (is != null)
        is.close(); 
      return t;
    } catch (Throwable throwable) {
      if (is != null)
        try {
          is.close();
        } catch (Throwable throwable1) {
          throwable.addSuppressed(throwable1);
        }  
      throw throwable;
    } 
  }
  
  private void readAndExtractDir(String subdir, Path outputDir, List<URL> extractedUrls) throws Exception {
    List<FileEntry> entries = readResource(subdir + ".list", reader -> reader.lines().map(FileEntry::parseLine).toList());
    Path subdirPath = outputDir.resolve(subdir);
    for (FileEntry entry : entries) {
      Path outputFile = subdirPath.resolve(entry.path);
      checkAndExtractJar(subdir, entry, outputFile);
      extractedUrls.add(outputFile.toUri().toURL());
    } 
  }
  
  private void checkAndExtractJar(String subdir, FileEntry entry, Path outputFile) throws Exception {
    if (!Files.exists(outputFile, new java.nio.file.LinkOption[0]) || !checkIntegrity(outputFile, entry.hash())) {
      System.out.printf("Unpacking %s (%s:%s) to %s%n", new Object[] { entry.path, subdir, entry.id, outputFile });
      extractJar(subdir, entry.path, outputFile);
    } 
  }
  
  private void extractJar(String subdir, String jarPath, Path outputFile) throws IOException {
    Files.createDirectories(outputFile.getParent(), (FileAttribute<?>[])new FileAttribute[0]);
    InputStream input = getClass().getResourceAsStream("/META-INF/" + subdir + "/" + jarPath);
    try {
      if (input == null)
        throw new IllegalStateException("Declared library " + jarPath + " not found"); 
      Files.copy(input, outputFile, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
      if (input != null)
        input.close(); 
    } catch (Throwable throwable) {
      if (input != null)
        try {
          input.close();
        } catch (Throwable throwable1) {
          throwable.addSuppressed(throwable1);
        }  
      throw throwable;
    } 
  }
  
  private static boolean checkIntegrity(Path file, String expectedHash) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    InputStream output = Files.newInputStream(file, new java.nio.file.OpenOption[0]);
    try {
      output.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
      String actualHash = byteToHex(digest.digest());
      if (actualHash.equalsIgnoreCase(expectedHash)) {
        boolean bool = true;
        if (output != null)
          output.close(); 
        return bool;
      } 
      System.out.printf("Expected file %s to have hash %s, but got %s%n", new Object[] { file, expectedHash, actualHash });
      if (output != null)
        output.close(); 
    } catch (Throwable throwable) {
      if (output != null)
        try {
          output.close();
        } catch (Throwable throwable1) {
          throwable.addSuppressed(throwable1);
        }  
      throw throwable;
    } 
    return false;
  }
  
  private static String byteToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      result.append(Character.forDigit(b >> 4 & 0xF, 16));
      result.append(Character.forDigit(b >> 0 & 0xF, 16));
    } 
    return result.toString();
  }
  
  @FunctionalInterface
  private static interface ResourceParser<T> {
    T parse(BufferedReader param1BufferedReader) throws Exception;
  }
  
  private static final class FileEntry extends Record {
    private final String hash;
    
    private final String id;
    
    private final String path;
    
    private FileEntry(String hash, String id, String path) {
      this.hash = hash;
      this.id = id;
      this.path = path;
    }
    
    public final String toString() {
      // Byte code:
      //   0: aload_0
      //   1: <illegal opcode> toString : (Lnet/minecraft/bundler/Main$FileEntry;)Ljava/lang/String;
      //   6: areturn
      // Line number table:
      //   Java source line number -> byte code offset
      //   #137	-> 0
      // Local variable table:
      //   start	length	slot	name	descriptor
      //   0	7	0	this	Lnet/minecraft/bundler/Main$FileEntry;
    }
    
    public final int hashCode() {
      // Byte code:
      //   0: aload_0
      //   1: <illegal opcode> hashCode : (Lnet/minecraft/bundler/Main$FileEntry;)I
      //   6: ireturn
      // Line number table:
      //   Java source line number -> byte code offset
      //   #137	-> 0
      // Local variable table:
      //   start	length	slot	name	descriptor
      //   0	7	0	this	Lnet/minecraft/bundler/Main$FileEntry;
    }
    
    public final boolean equals(Object o) {
      // Byte code:
      //   0: aload_0
      //   1: aload_1
      //   2: <illegal opcode> equals : (Lnet/minecraft/bundler/Main$FileEntry;Ljava/lang/Object;)Z
      //   7: ireturn
      // Line number table:
      //   Java source line number -> byte code offset
      //   #137	-> 0
      // Local variable table:
      //   start	length	slot	name	descriptor
      //   0	8	0	this	Lnet/minecraft/bundler/Main$FileEntry;
      //   0	8	1	o	Ljava/lang/Object;
    }
    
    public String hash() {
      return this.hash;
    }
    
    public String id() {
      return this.id;
    }
    
    public String path() {
      return this.path;
    }
    
    public static FileEntry parseLine(String line) {
      String[] fields = line.split("\t");
      if (fields.length != 3)
        throw new IllegalStateException("Malformed library entry: " + line); 
      return new FileEntry(fields[0], fields[1], fields[2]);
    }
  }
  
  private static class Thrower<T extends Throwable> {
    private static final Thrower<RuntimeException> INSTANCE = new Thrower();
    
    public void sneakyThrow(Throwable exception) throws T {
      throw (T)exception;
    }
  }
}
