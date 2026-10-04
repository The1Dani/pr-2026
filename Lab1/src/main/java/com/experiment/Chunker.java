package com.experiment;

import java.io.IOException;
import java.io.RandomAccessFile;

public class Chunker {

    private final RandomAccessFile raf;
    private int chunkAt = 0;
    private long byteAt = 0;
    private final long chunkLength;

    public Chunker(RandomAccessFile raf, int chunkCount) throws IOException {
        this.raf = raf;
        this.chunkLength = (raf.length() / (long) chunkCount) + 1;
    }

    public long getFirstByteToRead() throws IOException {
        long fromByte = byteAt;
        if (fromByte == 0) return 0;

        // Read first char
        raf.seek(fromByte);
        char ch = (char) raf.read();
        if (ch == '\n') return fromByte + 1;

        // Make sure to read until we find new line of eof
        for (; ch != '\n'; fromByte++) {
            raf.seek(fromByte);
            ch = (char) raf.read();
            if (ch == (char) -1) return fromByte;
        }
        return fromByte; // Start from the next byte which is provided by the for loop
    }

    public long getLastByteToRead() throws IOException {
        long toByte = toBytes();
        if (toByte >= raf.length()) return raf.length();

        raf.seek(toByte);
        int ch;
        while ((ch = raf.read()) != -1) {
            if (ch == '\n') return raf.getFilePointer(); // byte right after the '\n'
        }
        return raf.length();
    }

    public void nextChunk() {
        byteAt += chunkLength;
        chunkAt = chunkAt + 1;
    }

    private long toBytes() {
        return byteAt + chunkLength;
    }


}
