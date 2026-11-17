package com.nexus.eval.assertion;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AssertionResult {
    private final boolean passed;
    private final String expression;
    private final String expected;
    private final String actual;
    private final String message;

    public static AssertionResult pass(String expression, String expected, String actual) {
        return new AssertionResult(true, expression, expected, actual, "OK");
    }

    public static AssertionResult fail(String expression, String expected, String actual, String message) {
        return new AssertionResult(false, expression, expected, actual, message);
    }
}
