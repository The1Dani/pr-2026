package com.experiment.Tasks;

import com.experiment.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Task2 implements ITask {

    private final ArrayList<Path> files = new ArrayList<>();
    private final List<Integer> threadCounts =  new ArrayList<>();
    public Task2(Path filesFolder, List<Integer> threadCounts) {
        var filename = "numbers.%d.txt";
        this.threadCounts.addAll(threadCounts);
        for (var i = 1; i <= 8; i++) {
            files.add(
                    filesFolder
                            .resolve(Path.of(filename.formatted(i)))
                            .normalize()
            );
        }
    }

    @Override
    public List<CountsPrinter> Run() {

//        List<Integer> threadCounts = List.of(1, 2, 4);
        List<CountsPrinter> results = new ArrayList<>();

        for (var threadCount : threadCounts) {
            var counter = new Counter();
            var _files = new ArrayList<>(files);
            results.add(new CountsPrinter(Measure.measure(() -> {
                        List<Counts> _results = new ArrayList<>();

                        while (!_files.isEmpty()) {
                            for (int i = 0; i < threadCount; i++) {
                                var file = _files.removeLast();
                                counter.appendCount(file, 1);
                            }
                            try {
                                _results.add(counter.countAll());

                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        return Counter.sumCounts(_results);
                    }), "THREADS: %d\n".formatted(threadCount))
            );
        }

        return results;
    }
}
