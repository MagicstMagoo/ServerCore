package net.minecraft.bundler;

import java.io.BufferedReader;

@FunctionalInterface
interface ResourceParser<T> {
  T parse(BufferedReader paramBufferedReader) throws Exception;
}
