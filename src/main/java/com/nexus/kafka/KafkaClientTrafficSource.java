package com.nexus.kafka;

import com.nexus.config.ConfigRepository;
import com.nexus.config.ConfigValidationException;
import com.nexus.config.target.KafkaReplayConfig;
import com.nexus.http.HttpRequestSpec;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Kafka-backed {@link KafkaTrafficSource} reading sampled HTTP request envelopes from a topic.
 */
@RequiredArgsConstructor
public class KafkaClientTrafficSource implements KafkaTrafficSource {
    private final String bootstrapServers;

    @Override
    public List<SampledRequest> consumeBatch(KafkaReplayConfig config) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            throw new ConfigValidationException("KAFKA_BOOTSTRAP_SERVERS is required for live Kafka replay");
        }
        if (config == null || config.getTopic() == null || config.getTopic().isBlank()) {
            throw new ConfigValidationException("Target kafka.topic must be configured for diff stage");
        }

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "nexus-diff-" + UUID.randomUUID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.getOffsetReset().toLowerCase());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(Math.min(config.getMaxMessages(), 500)));

        List<SampledRequest> batch = new ArrayList<>();
        Instant deadline = Instant.now().plusSeconds(config.getTimeoutSeconds());
        int index = 0;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(config.getTopic()));
            while (batch.size() < config.getMaxMessages() && Instant.now().isBefore(deadline)) {
                long remainingMs = Math.max(50L, Duration.between(Instant.now(), deadline).toMillis());
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(Math.min(250L, remainingMs)));
                for (ConsumerRecord<String, String> record : records) {
                    if (batch.size() >= config.getMaxMessages()) {
                        break;
                    }
                    String payload = record.value();
                    if (payload == null || payload.isBlank()) {
                        continue;
                    }
                    HttpRequestSpec req = parseEnvelope(payload, index);
                    String id = (record.key() != null && !record.key().isBlank())
                            ? record.key()
                            : "kafka-" + record.partition() + "-" + record.offset();
                    batch.add(new SampledRequest(id, req));
                    index++;
                }
            }
        }
        return batch;
    }

    static HttpRequestSpec parseEnvelope(String json, int index) {
        try {
            return ConfigRepository.JSON_MAPPER.readValue(json, HttpRequestSpec.class);
        } catch (Exception e) {
            throw new ConfigValidationException("Failed to parse Kafka message #" + index + " as HttpRequestSpec: " + e.getMessage(), e);
        }
    }
}
