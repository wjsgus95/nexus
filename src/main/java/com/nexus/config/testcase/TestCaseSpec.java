package com.nexus.config.testcase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexus.http.HttpRequestSpec;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single HTTP test case specification for smoke or regression stages.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCaseSpec {
    private String id;
    private String description;
    private HttpRequestSpec request;
    private ExpectSpec expect = ExpectSpec.defaultStatusOk();
}
