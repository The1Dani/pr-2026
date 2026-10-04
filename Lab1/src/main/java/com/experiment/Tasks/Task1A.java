package com.experiment.Tasks;

import com.experiment.Counter;
import com.experiment.CountsPrinter;
import com.experiment.ITask;
import com.experiment.Measure;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Task1A implements ITask {

    Path filePath;


    public Task1A(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");

    }

    @Override
    public List<CountsPrinter> Run() {

        List<Integer> threadCounts = List.of(1, 2, 4, 8, 12, 16, 32, 64);
        List<CountsPrinter> results = new ArrayList<>();
        var counter = new Counter();

        for (var tc : threadCounts) {
            try {
                results.add(new CountsPrinter(Measure.measure(() -> {
                    try {
                        return counter.countOne(filePath, tc);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }), "THREADS: %d\n".formatted(tc)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return results;
    }
}
