package com.experiment;

import com.experiment.Tasks.Task1A;
import com.experiment.Tasks.Task2;

import java.nio.file.Paths;
import java.util.List;

public class Main {

    static ITask getTask1A(String path) {
        var _path = Paths.get(path); // "numbers.txt"

        return new Task1A(_path);

    }

    static ITask getTask2(String path, List<Integer> threadCounts) {
        var _path = Paths.get(path); // "."
        return new Task2(_path, threadCounts);
    }

    static void main(String[] args) {

        String path = args.length > 0 ? args[0] : ".";
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1;

        ITask task = getTask2(path, List.of(threads));
        var results = task.Run();
        for (var r : results) {
            r.print();
        }

    }
}
