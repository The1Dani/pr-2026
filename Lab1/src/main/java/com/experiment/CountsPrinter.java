package com.experiment;

public class CountsPrinter {

    private final String prefix;
    private final MeasureResultRecord<Counts> result;
    private final Counts counts;

    public CountsPrinter(MeasureResultRecord<Counts> result, String prefix) {
        this.prefix = prefix;
        this.result = result;
        this.counts = result.result();
    }

    private void _print(String prefix) {
        IO.print("%sElapsedMS: %.3fms (%.3fs)\nAllocated %.2f MiB\nPeak Heap %.2f MiB\nPOS: %d\nNEG: %d\nZERO: %d\nTOTAL: %d\n\n"
                .formatted(prefix,
                        result.getElapsedMs(),
                        result.getElapsedMs() / 1000,
                        result.getAllocatedMIB(),
                        result.getPeakHeapMIB(),
                        counts.pos,
                        counts.neg,
                        counts.zero,
                        counts.getTotal()
                ));
    }

    public void print() {
        _print(prefix);
    }

}
