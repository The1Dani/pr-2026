package com.experiment.Tasks;

import com.experiment.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Task1C implements ITask {

    private final Path filePath;
    private final List<Integer> workerCounts;

    public Task1C(Path filePath, List<Integer> workerCounts) {
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
        this.workerCounts = List.copyOf(workerCounts);
    }

    @Override
    public List<CountsPrinter> Run() {
        List<CountsPrinter> results = new ArrayList<>();
        for (var workers : workerCounts) {
            var counter = new QueueCounter(filePath, workers);
            results.add(new CountsPrinter(
                    Measure.measure(counter::count),
                    "WORKERS: %d (batch %d, queue %d)\n".formatted(
                            workers, QueueCounter.BATCH_SIZE, QueueCounter.QUEUE_CAPACITY)));
        }
        return results;
    }
}
