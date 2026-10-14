package com.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class ArrayCounter {

    private final int[] numbers;
    private final int numThreads;

    public ArrayCounter(int[] numbers, int numThreads) {
        this.numbers = numbers;
        this.numThreads = numThreads;
    }

    // Version 1: all threads increment the same Counts object, no synchronisation (race condition)
    public Counts countSharedUnsync() {
        Counts shared = new Counts();
        runThreads(i -> {
            int num = numbers[i];
            if (num == 0) {
                shared.zero++;
            } else if (num < 0) {
                shared.neg++;
            } else {
                shared.pos++;
            }
        });
        return shared;
    }

    // Version 2: same shared Counts object, but each update happens while holding a lock
    public Counts countSharedLocked() {
        Counts shared = new Counts();
        Object lock = new Object();
        runThreads(i -> {
            int num = numbers[i];
            synchronized (lock) {
                if (num == 0) {
                    shared.zero++;
                } else if (num < 0) {
                    shared.neg++;
                } else {
                    shared.pos++;
                }
            }
        });
        return shared;
    }

    // Version 3: each thread has its own Counts, summed after all threads finish
    public Counts countPerThread() {
        List<Counts> results = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            var counts = new Counts();
            results.add(counts);
            int from = chunkStart(t);
            int to = chunkStart(t + 1);

            threads.add(new Thread(() -> {
                for (int i = from; i < to; i++) {
                    int num = numbers[i];
                    if (num == 0) {
                        counts.zero++;
                    } else if (num < 0) {
                        counts.neg++;
                    } else {
                        counts.pos++;
                    }
                }
            }));
        }

        startAndJoin(threads);
        return Counter.sumCounts(results);
    }

    // Splits the array into numThreads equal slices and calls body(i) for every index of each slice
    private void runThreads(IntConsumer body) {
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            int from = chunkStart(t);
            int to = chunkStart(t + 1);
            threads.add(new Thread(() -> {
                for (int i = from; i < to; i++) {
                    body.accept(i);
                }
            }));
        }
        startAndJoin(threads);
    }

    private int chunkStart(int threadIndex) {
        return (int) ((long) threadIndex * numbers.length / numThreads);
    }

    private static void startAndJoin(List<Thread> threads) {
        for (var thread : threads) {
            thread.start();
        }
        try {
            for (var thread : threads) {
                thread.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
