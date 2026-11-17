package com.nexus.runner.doctor;

import java.io.PrintWriter;
import java.nio.file.Path;

/**
 * Single pre-flight diagnostic check that logs its result and returns whether it passed.
 */
public interface Doctor {
    boolean check(Path profileFile, PrintWriter out);

    static boolean logResult(PrintWriter out, String slice, String target, boolean passed, String detail) {
        out.printf("  [%s] %-14s %-18s %s%n",
                passed ? "PASS" : "FAIL",
                slice,
                target,
                detail);
        return passed;
    }
}
