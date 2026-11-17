package com.nexus.runner.doctor;

import com.nexus.config.ConfigRepository;
import com.nexus.config.target.TargetConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.TopicDescription;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Checks Kafka topic reachability via {@link AdminClient} for the target profile.
 */
@RequiredArgsConstructor
public class KafkaDoctor implements Doctor {
    private final String bootstrapServers;

    @Override
    public boolean check(Path profileFile, PrintWriter out) {
        TargetConfig target;
        try {
            target = ConfigRepository.loadTarget(profileFile);
        } catch (Exception ignored) {
            return true;
        }
        String topic = target.getKafka() != null ? target.getKafka().getTopic() : null;
        return checkTopic(target.getName(), topic, out);
    }

    private boolean checkTopic(String targetKey, String topic, PrintWriter out) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return Doctor.logResult(out, "kafka", targetKey, false, "KAFKA_BOOTSTRAP_SERVERS is not configured");
        }
        if (topic == null || topic.isBlank()) {
            return Doctor.logResult(out, "kafka", targetKey, false, "kafka.topic is not configured on target");
        }
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");
        try (AdminClient admin = AdminClient.create(props)) {
            Map<String, TopicDescription> desc = admin.describeTopics(List.of(topic))
                    .allTopicNames()
                    .get(5, TimeUnit.SECONDS);
            TopicDescription td = desc.get(topic);
            int partitions = td != null ? td.partitions().size() : 0;
            return Doctor.logResult(out, "kafka", targetKey, true,
                    "Topic '" + topic + "' reachable (" + partitions + " partition(s))");
        } catch (Exception e) {
            return Doctor.logResult(out, "kafka", targetKey, false,
                    "Kafka check failed for topic '" + topic + "': " + e.getMessage());
        }
    }
}
