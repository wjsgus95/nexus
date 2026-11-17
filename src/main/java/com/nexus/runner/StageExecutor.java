package com.nexus.runner;

import com.nexus.auth.AuthHeaders;
import com.nexus.config.target.TargetConfig;
import com.nexus.eval.diff.StableEvaluation;
import com.nexus.eval.diff.StableSpec;
import com.nexus.http.HttpCallResult;
import com.nexus.http.HttpRequestSpec;
import com.nexus.report.StageResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Bounded virtual-thread stage execution helper with uniform retries and concurrent dual-target calls.
 */
final class StageExecutor {
    private StageExecutor() {}

    static StableEvaluation executeAgainstStable(
            HttpRequestSpec request,
            TargetConfig target,
            AuthHeaders auth,
            Duration timeout,
            boolean autoNoiseDetection
    ) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<StableSpec> stableFuture = executor.submit(
                    () -> StableSpec.capture(request, target.getStableUrl(), auth, timeout, autoNoiseDetection));
            Future<HttpCallResult> targetFuture = executor.submit(
                    () -> request.execute(target.getTargetUrl(), auth, timeout));
            return stableFuture.get().evaluate(targetFuture.get(), target.getDiffRules());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dual-target request interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Dual-target request failed", e.getCause());
        }
    }

    static <I, R extends StageResult> List<R> runBoundedWithRetries(
            List<I> items,
            int concurrencyLimit,
            int retries,
            Function<I, R> task
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Semaphore semaphore = new Semaphore(Math.max(1, concurrencyLimit));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = new ArrayList<>(items.size());
            for (I item : items) {
                futures.add(executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        return executeWithRetries(retries, () -> task.apply(item));
                    } finally {
                        semaphore.release();
                    }
                }));
            }
            List<R> results = new ArrayList<>(items.size());
            for (Future<R> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Task execution failed", cause);
        }
    }

    private static <R extends StageResult> R executeWithRetries(int retries, Supplier<R> attempt) {
        int attempts = Math.max(0, retries) + 1;
        R result = null;
        for (int i = 0; i < attempts; i++) {
            result = attempt.get();
            if (result.isPassed()) {
                return result;
            }
        }
        return result;
    }
}
