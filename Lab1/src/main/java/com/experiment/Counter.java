package com.experiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Counter {

    private final List<Counts> results = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();


    // The bytes are exact bytes to start and end should be provided from a middle of the line
    private void countChunk(RandomAccessFile raf, Counts counts, long fromByte, long toByte) throws IOException {
        raf.seek(fromByte);

        var reader = new BufferedReader(
                new InputStreamReader(
                        Channels.newInputStream(raf.getChannel()),
                        StandardCharsets.US_ASCII
                ),
                1 << 16 // 64KB
        );

        long pos = fromByte;
        String line;
        while (pos < toByte && (line = reader.readLine()) != null) {
            pos += line.length() + 1; // track the position
            int num = Integer.parseInt(line);
            if (num == 0) {
                counts.zero++;
            } else if (num < 0) {
                counts.neg++;
            } else {
                counts.pos++;
            }
        }
    }

    private void _makeThreads(Path filePath, int numThreads) {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            var chunker = new Chunker(raf, numThreads);

            for (int i = 0; i < numThreads; i++) {
                var counts = new Counts();
                results.add(counts);
                var fromByte = chunker.getFirstByteToRead();
                var toByte = chunker.getLastByteToRead();

                var thread = new Thread(() -> {
                    try (RandomAccessFile own = new RandomAccessFile(filePath.toFile(), "r")) {
                        countChunk(own, counts, fromByte, toByte);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                threads.add(thread);

                chunker.nextChunk();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void appendCount(Path filePath, int threadCount) {
        _makeThreads(filePath, threadCount);
    }

    public Counts countOne(Path filePath, int threadCount) throws IOException {
        _makeThreads(filePath, threadCount);
        return countAll();
    }

    public Counts countAll() throws IOException {
        // Start the threads
        for (var thread : threads) {
            thread.start();
        }

        // Join the threads
        try {
            for (var thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        var finalCounts = Counter.sumCounts(results);

        // Cleanup the lists
        results.clear();
        threads.clear();

        return finalCounts;

    }

    public static Counts sumCounts(List<Counts> results) {
        Counts finalCounts = new Counts();
        for (var counts : results) {
            finalCounts.zero += counts.zero;
            finalCounts.pos += counts.pos;
            finalCounts.neg += counts.neg;
        }
        return finalCounts;
    }

}
