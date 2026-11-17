package com.nexus.eval.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.config.ConfigRepository;
import com.nexus.config.target.DiffRulesConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * JSON diff engine supporting manual JsonPath ignoredPaths and double-Stable auto-noise removal.
 */
public class JsonDiffEngine {

    public static DiffResult diff(String stableBody, String targetBody, DiffRulesConfig rules) {
        return diff(stableBody, targetBody, rules, Set.of());
    }

    public static DiffResult diff(
            String stableBody,
            String targetBody,
            DiffRulesConfig rules,
            Set<String> autoIgnoredNoisePaths
    ) {
        Set<String> allIgnored = new LinkedHashSet<>();
        if (rules != null) {
            addNormalizedPaths(allIgnored, rules.getIgnoredPaths());
        }
        addNormalizedPaths(allIgnored, autoIgnoredNoisePaths);

        List<FieldDiff> diffs = new ArrayList<>();
        JsonNode stableNode = parse(stableBody, "stable", diffs);
        JsonNode targetNode = parse(targetBody, "target", diffs);
        Set<String> safeAutoNoise = autoIgnoredNoisePaths == null ? Set.of() : Set.copyOf(autoIgnoredNoisePaths);

        if (!diffs.isEmpty()) {
            return new DiffResult(false, List.copyOf(diffs), safeAutoNoise);
        }

        compareNodes("$", stableNode, targetNode, allIgnored, diffs);
        return new DiffResult(diffs.isEmpty(), List.copyOf(diffs), safeAutoNoise);
    }

    /**
     * Compares two Stable responses (Twitter Diffy pattern) and returns the JSON paths
     * whose values differ between the two calls so they are automatically ignored.
     */
    public static Set<String> detectAutoNoisePaths(String stableBody1, String stableBody2) {
        if (stableBody1 == null || stableBody2 == null) {
            return Set.of();
        }
        List<FieldDiff> parseErrors = new ArrayList<>();
        JsonNode b1 = parse(stableBody1, "stable", parseErrors);
        JsonNode b2 = parse(stableBody2, "target", parseErrors);
        if (!parseErrors.isEmpty() || b1 == null || b2 == null) {
            return Set.of();
        }
        List<FieldDiff> noiseDiffs = new ArrayList<>();
        compareNodes("$", b1, b2, Set.of(), noiseDiffs);
        Set<String> noisyPaths = new LinkedHashSet<>();
        for (FieldDiff d : noiseDiffs) {
            noisyPaths.add(d.getPath());
        }
        return noisyPaths;
    }

    private static void addNormalizedPaths(Set<String> dest, Collection<String> paths) {
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            if (path != null && !path.isBlank()) {
                dest.add(ConfigRepository.normalizeJsonPath(path));
            }
        }
    }

    private static JsonNode parse(String rawBody, String side, List<FieldDiff> diffs) {
        if (rawBody == null || rawBody.isBlank()) {
            diffs.add(new FieldDiff("$",
                    side.equals("stable") ? "<empty>" : null,
                    side.equals("target") ? "<empty>" : null));
            return null;
        }
        try {
            return ConfigRepository.JSON_MAPPER.readTree(rawBody);
        } catch (Exception e) {
            diffs.add(new FieldDiff("$",
                    side.equals("stable") ? "Invalid JSON: " + e.getMessage() : null,
                    side.equals("target") ? "Invalid JSON: " + e.getMessage() : null));
            return null;
        }
    }

    private static void compareNodes(
            String path,
            JsonNode stable,
            JsonNode target,
            Set<String> ignoredPaths,
            List<FieldDiff> diffs
    ) {
        if (ignoredPaths.contains(path) || (stable == null && target == null)) {
            return;
        }
        if (stable == null || target == null) {
            diffs.add(new FieldDiff(path, stringify(stable), stringify(target)));
            return;
        }
        if (stable.getNodeType() != target.getNodeType()) {
            if (stable.isNumber() && target.isNumber()) {
                if (!numbersEqual(stable, target)) {
                    diffs.add(new FieldDiff(path, stable.asText(), target.asText()));
                }
                return;
            }
            diffs.add(new FieldDiff(path, stringify(stable), stringify(target)));
            return;
        }

        if (stable.isObject()) {
            Set<String> allKeys = new TreeSet<>();
            stable.fieldNames().forEachRemaining(allKeys::add);
            target.fieldNames().forEachRemaining(allKeys::add);
            for (String key : allKeys) {
                compareNodes(path + "." + key, stable.get(key), target.get(key), ignoredPaths, diffs);
            }
            return;
        }

        if (stable.isArray()) {
            int max = Math.max(stable.size(), target.size());
            for (int i = 0; i < max; i++) {
                JsonNode stableElem = i < stable.size() ? stable.get(i) : null;
                JsonNode targetElem = i < target.size() ? target.get(i) : null;
                compareNodes(path + "[" + i + "]", stableElem, targetElem, ignoredPaths, diffs);
            }
            return;
        }

        if (stable.isNumber()) {
            if (!numbersEqual(stable, target)) {
                diffs.add(new FieldDiff(path, stable.asText(), target.asText()));
            }
            return;
        }

        if (!stable.equals(target)) {
            diffs.add(new FieldDiff(path, stringify(stable), stringify(target)));
        }
    }

    private static boolean numbersEqual(JsonNode a, JsonNode b) {
        try {
            return new BigDecimal(a.asText()).compareTo(new BigDecimal(b.asText())) == 0;
        } catch (NumberFormatException e) {
            return a.equals(b);
        }
    }

    private static String stringify(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isNull()) {
            return "null";
        }
        return node.isTextual() ? node.asText() : node.toString();
    }
}
