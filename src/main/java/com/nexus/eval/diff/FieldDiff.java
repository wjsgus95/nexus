package com.nexus.eval.diff;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FieldDiff {
    private final String path;
    private final String stableValue;
    private final String targetValue;
}
