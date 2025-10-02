package com.yk.aiagent.openaibatch;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class MultipartBodyPublisher {
  private final String boundary;
  private final List<byte[]> parts = new ArrayList<>();

  private MultipartBodyPublisher(String boundary) {
    this.boundary = boundary;
  }

  public static Builder newBuilder() {
    String b = "----JavaMultipart_" + UUID.randomUUID();
    return new Builder(new MultipartBodyPublisher(b));
  }

  public String getBoundary() { return boundary; }

  public HttpRequest.BodyPublisher buildPublisher() {
    byte[] delimiter = ("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);
    byte[] close     = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

    List<byte[]> all = new ArrayList<>();
    for (byte[] p : parts) {
      all.add(delimiter);
      all.add(p);
    }
    all.add(close);

    long length = all.stream().mapToLong(a -> a.length).sum();
    return HttpRequest.BodyPublishers.ofByteArrays(all);
  }

  static final class Builder {
    private final MultipartBodyPublisher mp;

    Builder(MultipartBodyPublisher mp) { this.mp = mp; }

    public Builder addText(String name, String value) {
      String part = "Content-Disposition: form-data; name=\"" + name + "\"\r\n" +
          "Content-Type: text/plain; charset=UTF-8\r\n\r\n" +
          value + "\r\n";
      mp.parts.add(part.getBytes(StandardCharsets.UTF_8));
      return this;
    }

    public Builder addFile(String name, Path path, String contentType) throws IOException {
      String filename = path.getFileName().toString();
      String header = "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n" +
          "Content-Type: " + contentType + "\r\n\r\n";
      byte[] head = header.getBytes(StandardCharsets.UTF_8);
      byte[] data = Files.readAllBytes(path);
      byte[] tail = "\r\n".getBytes(StandardCharsets.UTF_8);

      mp.parts.add(concat(head, data, tail));
      return this;
    }

    public MultipartBodyPublisher build() {
      return mp;
    }

    private static byte[] concat(byte[]... arrays) {
      int len = Stream.of(arrays).mapToInt(a -> a.length).sum();
      byte[] out = new byte[len];
      int pos = 0;
      for (byte[] a : arrays) {
        System.arraycopy(a, 0, out, pos, a.length);
        pos += a.length;
      }
      return out;
    }
  }
}
