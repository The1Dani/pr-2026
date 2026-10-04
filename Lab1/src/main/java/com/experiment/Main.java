package com.experiment;

import com.experiment.Tasks.Task1A;
import com.experiment.Tasks.Task2;

import java.nio.file.Paths;

public class Main {

    static ITask getTask1A() {
        var path = Paths.get("numbers.txt");

        return new Task1A(path);

    }

    static ITask getTask2() {
        var path = Paths.get(".");
        return new Task2(path);
    }

    static void main() {

        ITask task = getTask2();
        var results = task.Run();
        for (var r : results) {
            r.print();
        }

    }
}
