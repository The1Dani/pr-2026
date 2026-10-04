package com.experiment;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;
import java.util.function.Supplier;

public class Measure {

    public static <T> MeasureResultRecord<T> measure(Supplier<T> func) {
        List<MemoryPoolMXBean> heapPools = ManagementFactory.getMemoryPoolMXBeans()
                .stream().filter(p -> p.getType() == MemoryType.HEAP).toList();

        com.sun.management.ThreadMXBean threadBean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!threadBean.isThreadAllocatedMemoryEnabled()) {
            threadBean.setThreadAllocatedMemoryEnabled(true);
        }

        // Start from a clean-ish heap and reset the peak counters
        System.gc();
        heapPools.forEach(MemoryPoolMXBean::resetPeakUsage);

        long allocBefore = threadBean.getTotalThreadAllocatedBytes();  // all threads, incl. terminated
        long start = System.nanoTime();

        T result = func.get();  // must wait for all its threads to finish before returning

        long elapsedNs = System.nanoTime() - start;
        long allocated = threadBean.getTotalThreadAllocatedBytes() - allocBefore;
        long peakHeap = heapPools.stream()
                .mapToLong(p -> p.getPeakUsage().getUsed()).sum();
        //        System.out.printf("%s: %.3f ms, allocated %.2f MiB, peak heap %.2f MiB%n",
        //                name, elapsedNs / 1e6, allocated / MIB, peakHeap / MIB);
        return new MeasureResultRecord<T>(
                elapsedNs,
                allocated,
                peakHeap,
                result
        );
    }
}
