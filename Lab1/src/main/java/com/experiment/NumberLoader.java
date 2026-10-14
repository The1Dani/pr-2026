package com.experiment;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class NumberLoader {

    // Reads one integer per line into an int array (grows like an ArrayList, then trims)
    public static int[] load(Path filePath) throws IOException {
        int[] numbers = new int[1 << 20];
        int size = 0;

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.US_ASCII)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                if (size == numbers.length) {
                    numbers = Arrays.copyOf(numbers, numbers.length * 2);
                }
                numbers[size++] = Integer.parseInt(line);
            }
        }

        return Arrays.copyOf(numbers, size);
    }
}
