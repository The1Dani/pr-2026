package com.experiment;

public record MeasureResultRecord<T>(
        long elapsedNs,
        long allocated,
        long peakHeap,
        T result
) {
    private static final double MIB = 1024.0 * 1024.0;

    public double getElapsedMs() {
        return elapsedNs / 1e6;
    }

    public double getAllocatedMIB() {
        return allocated / MIB;
    }

    public double getPeakHeapMIB() {
        return peakHeap / MIB;
    }
}
