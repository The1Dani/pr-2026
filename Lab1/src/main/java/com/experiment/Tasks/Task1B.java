package com.experiment.Tasks;

import com.experiment.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Task1B implements ITask {

    private final int[] numbers;
    private final int threadCount;

    public Task1B(Path filePath) {
        Objects.requireNonNull(filePath, "filePath must not be null");
        try {
            // Loading happens here, outside of any measurement
            this.numbers = NumberLoader.load(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.threadCount = Runtime.getRuntime().availableProcessors();
    }

    @Override
    public List<CountsPrinter> Run() {
        List<CountsPrinter> results = new ArrayList<>();
        var counter = new ArrayCounter(numbers, threadCount);
        var header = "THREADS: %d, NUMBERS: %d\n".formatted(threadCount, numbers.length);

        for (int run = 1; run <= 3; run++) {
            results.add(new CountsPrinter(
                    Measure.measure(counter::countSharedUnsync),
                    "V1 shared, no sync (run %d/3) | %s".formatted(run, header)));
        }

        results.add(new CountsPrinter(
                Measure.measure(counter::countSharedLocked),
                "V2 shared, locked | " + header));

        results.add(new CountsPrinter(
                Measure.measure(counter::countPerThread),
                "V3 per-thread counters | " + header));

        return results;
    }
}
