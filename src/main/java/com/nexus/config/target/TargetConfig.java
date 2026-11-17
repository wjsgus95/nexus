package com.nexus.config.target;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.List;

/**
 * Target configuration per profile defined in profile.yaml.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TargetConfig {
    private String name;
    private URI targetUrl;
    private URI stableUrl;
    private String healthPath = "/health";
    private DiffRulesConfig diffRules = new DiffRulesConfig(List.of());
    private KafkaReplayConfig kafka = new KafkaReplayConfig();

    public String getHealthPath() {
        return (healthPath == null || healthPath.isBlank()) ? "/health" : healthPath;
    }
}
