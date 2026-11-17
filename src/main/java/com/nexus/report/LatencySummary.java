package com.nexus.report;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.function.ToLongFunction;

@Getter
@AllArgsConstructor
public class LatencySummary {
    private final long minMs;
    private final double avgMs;
    private final long p95Ms;
    private final long maxMs;

    public static LatencySummary fromLatencies(List<Long> latencies) {
        if (latencies == null || latencies.isEmpty()) {
            return new LatencySummary(0L, 0.0, 0L, 0L);
        }
        List<Long> sorted = latencies.stream().sorted().toList();
        long min = sorted.getFirst();
        long max = sorted.getLast();
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
        int p95Index = Math.max(0, Math.min((int) Math.ceil(0.95 * sorted.size()) - 1, sorted.size() - 1));
        return new LatencySummary(min, Math.round(avg * 100.0) / 100.0, sorted.get(p95Index), max);
    }

    public static <T> LatencySummary from(List<T> results, ToLongFunction<T> latencyFn) {
        return fromLatencies(results.stream().mapToLong(latencyFn).boxed().toList());
    }
}
