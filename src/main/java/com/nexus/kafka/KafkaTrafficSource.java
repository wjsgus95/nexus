package com.nexus.kafka;

import com.nexus.config.target.KafkaReplayConfig;

import java.util.List;

/**
 * Consumes sampled HTTP request envelopes for Stage 3 (Kafka Traffic Replay).
 */
public interface KafkaTrafficSource {
    List<SampledRequest> consumeBatch(KafkaReplayConfig config);
}
