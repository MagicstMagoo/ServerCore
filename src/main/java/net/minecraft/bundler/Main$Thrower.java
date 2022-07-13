package net.minecraft.bundler;

class Thrower<T extends Throwable> {
  private static final Thrower<RuntimeException> INSTANCE = new Thrower();
  
  public void sneakyThrow(Throwable exception) throws T {
    throw (T)exception;
  }
}

