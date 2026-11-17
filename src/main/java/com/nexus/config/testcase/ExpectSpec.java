package com.nexus.config.testcase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpectSpec {
    private Integer status = 200;
    private List<AssertionSpec> assertions = List.of();

    public static ExpectSpec defaultStatusOk() {
        return new ExpectSpec(200, List.of());
    }
}
