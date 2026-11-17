package com.nexus.eval.diff;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
@AllArgsConstructor
public class DiffResult {
    private final boolean identical;
    private final List<FieldDiff> diffs;
    private final Set<String> autoIgnoredNoisePaths;
}
