package com.nexus.eval.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.nexus.config.ConfigRepository;
import com.nexus.config.testcase.AssertionOp;
import com.nexus.config.testcase.AssertionSpec;
import com.nexus.config.testcase.ExpectSpec;
import com.nexus.http.HttpCallResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates HTTP status and essential JsonPath assertions (equals, notNull, notEmpty, gt, size).
 */
public class AssertionEvaluator {

    public List<AssertionResult> evaluate(HttpCallResult response, ExpectSpec expect) {
        List<AssertionResult> results = new ArrayList<>();
        if (response == null) {
            results.add(AssertionResult.fail("transport", "HTTP response", "null", "No response received"));
            return results;
        }
        if (!response.isTransportSuccess()) {
            results.add(AssertionResult.fail("transport", "HTTP response", "error",
                    response.getError() != null ? response.getError() : "Transport failure"));
            return results;
        }

        int expectedStatus = expect.getStatus() != null ? expect.getStatus() : 200;
        if (response.getStatusCode() == expectedStatus) {
            results.add(AssertionResult.pass("http.status", String.valueOf(expectedStatus), String.valueOf(response.getStatusCode())));
        } else {
            results.add(AssertionResult.fail("http.status", String.valueOf(expectedStatus), String.valueOf(response.getStatusCode()),
                    "Expected HTTP status " + expectedStatus + " but got " + response.getStatusCode()));
        }

        List<AssertionSpec> assertions = expect.getAssertions();
        if (assertions == null || assertions.isEmpty()) {
            return results;
        }

        DocumentContext doc;
        try {
            if (response.getBody() == null || response.getBody().isBlank()) {
                results.add(AssertionResult.fail("json.body", "non-empty JSON", "<empty>",
                        "Response body is empty, cannot evaluate JsonPath assertions"));
                return results;
            }
            doc = JsonPath.parse(response.getBody());
        } catch (Exception e) {
            results.add(AssertionResult.fail("json.body", "valid JSON", "<invalid>",
                    "Response body is not valid JSON: " + e.getMessage()));
            return results;
        }

        for (AssertionSpec spec : assertions) {
            results.add(evaluateSingle(doc, spec));
        }
        return results;
    }

    private AssertionResult evaluateSingle(DocumentContext doc, AssertionSpec spec) {
        String path = ConfigRepository.normalizeJsonPath(spec.getPath());
        AssertionOp op = spec.getOp();
        String expr = path + " " + op + (spec.getValue() != null && !spec.getValue().isNull() ? " " + spec.getValue() : "");

        Object actual;
        try {
            actual = doc.read(path);
        } catch (PathNotFoundException e) {
            return AssertionResult.fail(expr, stringifyExpected(spec.getValue()), "<missing>",
                    "Path '" + path + "' was not found in response");
        } catch (Exception e) {
            return AssertionResult.fail(expr, stringifyExpected(spec.getValue()), "<error>",
                    "Failed to evaluate JsonPath '" + path + "': " + e.getMessage());
        }

        if (op == null) {
            return AssertionResult.fail(expr, stringifyExpected(spec.getValue()), stringify(actual),
                    "Assertion operator must not be null");
        }

        return switch (op) {
            case notNull -> actual != null
                    ? AssertionResult.pass(expr, "notNull", stringify(actual))
                    : AssertionResult.fail(expr, "notNull", "null", "Expected non-null value at '" + path + "'");
            case notEmpty -> isNotEmpty(actual)
                    ? AssertionResult.pass(expr, "notEmpty", stringify(actual))
                    : AssertionResult.fail(expr, "notEmpty", stringify(actual), "Expected non-empty value at '" + path + "'");
            case equals -> {
                Object expectedVal = toJavaValue(spec.getValue());
                yield valuesEqual(actual, expectedVal)
                        ? AssertionResult.pass(expr, stringifyExpected(spec.getValue()), stringify(actual))
                        : AssertionResult.fail(expr, stringifyExpected(spec.getValue()), stringify(actual),
                                "Expected " + stringifyExpected(spec.getValue()) + " at '" + path + "' but got " + stringify(actual));
            }
            case gt -> checkGreaterThan(expr, path, actual, spec.getValue());
            case size -> checkSize(expr, path, actual, spec.getValue());
        };
    }

    private AssertionResult checkGreaterThan(String expr, String path, Object actual, JsonNode expectedNode) {
        BigDecimal actualNum = toBigDecimal(actual);
        BigDecimal expectedNum = expectedNode != null ? toBigDecimal(toJavaValue(expectedNode)) : null;
        if (actualNum == null || expectedNum == null) {
            return AssertionResult.fail(expr, stringifyExpected(expectedNode), stringify(actual),
                    "Numeric comparison 'gt' requires numeric actual and expected values");
        }
        return actualNum.compareTo(expectedNum) > 0
                ? AssertionResult.pass(expr, "gt " + expectedNum, actualNum.toPlainString())
                : AssertionResult.fail(expr, "gt " + expectedNum, actualNum.toPlainString(),
                        "Expected '" + path + "' (" + actualNum + ") to be gt " + expectedNum);
    }

    private AssertionResult checkSize(String expr, String path, Object actual, JsonNode expectedNode) {
        if (expectedNode == null || !expectedNode.isNumber()) {
            return AssertionResult.fail(expr, stringifyExpected(expectedNode), stringify(actual),
                    "'size' assertion requires a numeric expected size");
        }
        int expectedSize = expectedNode.asInt();
        Integer actualSize = switch (actual) {
            case Collection<?> col -> col.size();
            case Map<?, ?> map -> map.size();
            case String s -> s.length();
            case null, default -> null;
        };
        if (actualSize == null) {
            return AssertionResult.fail(expr, String.valueOf(expectedSize), stringify(actual),
                    "Value at '" + path + "' does not have a size");
        }
        return actualSize == expectedSize
                ? AssertionResult.pass(expr, String.valueOf(expectedSize), String.valueOf(actualSize))
                : AssertionResult.fail(expr, String.valueOf(expectedSize), String.valueOf(actualSize),
                        "Expected size " + expectedSize + " at '" + path + "' but got " + actualSize);
    }

    private static boolean isNotEmpty(Object actual) {
        return switch (actual) {
            case null -> false;
            case Collection<?> col -> !col.isEmpty();
            case Map<?, ?> map -> !map.isEmpty();
            case String s -> !s.isEmpty();
            default -> true;
        };
    }

    private static Object toJavaValue(JsonNode node) {
        return (node == null || node.isNull()) ? null : ConfigRepository.JSON_MAPPER.convertValue(node, Object.class);
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a == null || b == null) return a == b;
        BigDecimal numA = toBigDecimal(a);
        BigDecimal numB = toBigDecimal(b);
        return (numA != null && numB != null) ? numA.compareTo(numB) == 0 : Objects.equals(a, b);
    }

    private static BigDecimal toBigDecimal(Object val) {
        return switch (val) {
            case BigDecimal bd -> bd;
            case Number n -> new BigDecimal(n.toString());
            case null, default -> null;
        };
    }

    private static String stringify(Object val) {
        return val == null ? "null" : String.valueOf(val);
    }

    private static String stringifyExpected(JsonNode node) {
        return node == null ? "null" : node.toString();
    }
}
