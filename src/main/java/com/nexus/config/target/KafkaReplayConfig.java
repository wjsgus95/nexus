package com.nexus.config.target;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaReplayConfig {
    private String topic;
    private int maxMessages = 50;
    private int timeoutSeconds = 15;
    private String offsetReset = "latest";
    private boolean autoNoiseDetection;

    public int getMaxMessages() {
        return maxMessages <= 0 ? 50 : maxMessages;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds <= 0 ? 15 : timeoutSeconds;
    }

    public String getOffsetReset() {
        return (offsetReset == null || offsetReset.isBlank()) ? "latest" : offsetReset;
    }
}
