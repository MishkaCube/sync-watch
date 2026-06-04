package com.syncwatch.controller;

import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class PartialFileResource extends FileSystemResource {

    private final long offset;
    private final long length;

    public PartialFileResource(Path path, long offset, long length) {
        super(path);
        this.offset = offset;
        this.length = length;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream is = Files.newInputStream(getFile().toPath());
        is.skipNBytes(offset);
        return new BoundedInputStream(is, length);
    }

    // Wraps InputStream to limit how many bytes are read
    private static class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = delegate.read();
            if (b != -1) remaining--;
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int n = delegate.read(buf, off, toRead);
            if (n != -1) remaining -= n;
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
