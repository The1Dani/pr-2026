package com.experiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

// Producer-consumer: one reader thread fills a bounded queue with batches of lines,
// N worker threads take batches from it and count them
public class QueueCounter {

    public static final int BATCH_SIZE = 10_000;
    public static final int QUEUE_CAPACITY = 100;

    // Marker that means "the queue is closed". Compared by reference, never counted.
    private static final List<String> POISON = new ArrayList<>(0);

    private final Path filePath;
    private final int workerCount;

    public QueueCounter(Path filePath, int workerCount) {
        this.filePath = filePath;
        this.workerCount = workerCount;
    }

    public Counts count() {
        BlockingQueue<List<String>> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        var reader = new Thread(() -> readBatches(queue), "reader");

        List<Counts> results = new ArrayList<>();
        List<Thread> workers = new ArrayList<>();
        for (int w = 0; w < workerCount; w++) {
            var counts = new Counts();
            results.add(counts);
            workers.add(new Thread(() -> countBatches(queue, counts), "worker-" + w));
        }

        reader.start();
        workers.forEach(Thread::start);
        try {
            reader.join();
            for (var worker : workers) {
                worker.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return Counter.sumCounts(results);
    }

    // Reader (producer): put() blocks while the queue holds QUEUE_CAPACITY batches
    private void readBatches(BlockingQueue<List<String>> queue) {
        try (BufferedReader in = Files.newBufferedReader(filePath, StandardCharsets.US_ASCII)) {
            List<String> batch = new ArrayList<>(BATCH_SIZE);
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                batch.add(line);
                if (batch.size() == BATCH_SIZE) {
                    queue.put(batch);
                    batch = new ArrayList<>(BATCH_SIZE);
                }
            }
            if (!batch.isEmpty()) {
                queue.put(batch);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            closeQueue(queue);
        }
    }

    // Worker (consumer): take() blocks while the queue is empty
    private static void countBatches(BlockingQueue<List<String>> queue, Counts counts) {
        try {
            while (true) {
                List<String> batch = queue.take();
                if (batch == POISON) {
                    queue.put(POISON); // pass it on so the other workers stop too
                    return;
                }
                for (String s : batch) {
                    int num = Integer.parseInt(s);
                    if (num == 0) {
                        counts.zero++;
                    } else if (num < 0) {
                        counts.neg++;
                    } else {
                        counts.pos++;
                    }
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // "Closing" = putting POISON behind the last real batch; FIFO order means
    // workers only see it once every batch before it has been taken
    private static void closeQueue(BlockingQueue<List<String>> queue) {
        try {
            queue.put(POISON);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
