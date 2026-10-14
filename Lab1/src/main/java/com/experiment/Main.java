package com.experiment;

import com.experiment.Tasks.Task1A;
import com.experiment.Tasks.Task1B;
import com.experiment.Tasks.Task1C;
import com.experiment.Tasks.Task2;

import java.nio.file.Paths;
import java.util.List;

public class Main {

    static ITask getTask1A(String path) {
        var _path = Paths.get(path); // "numbers.txt"

        return new Task1A(_path);

    }
    static ITask getTask1B(String path, List<Integer> threadCounts) {
        var _path = Paths.get(path); // "."
        return new Task1B(_path);
    }

    static ITask getTask1C(String path, List<Integer> threadCounts) {
        var _path = Paths.get(path); // "numbers.txt"
        return new Task1C(_path, threadCounts);
    }
    static ITask getTask2(String path, List<Integer> threadCounts) {
        var _path = Paths.get(path); // "."
        return new Task2(_path, threadCounts);
    }


    static void main(String[] args) {

        String path = args.length > 0 ? args[0] : "D:/University/5th-Semester/Multithreading/pr-2026/Lab1/numbers.txt";
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 1;

        // Without a thread argument Task3 runs the full 1..64 worker sweep
        List<Integer> workerCounts = args.length > 1 ? List.of(threads) : List.of(1, 2, 4, 8, 16, 32, 64);

        ITask task = getTask1C(path, workerCounts);
        var results = task.Run();
        for (var r : results) {
            r.print();
        }

    }
}
