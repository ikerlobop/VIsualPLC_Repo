package springboot_kafka_tutorial.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    public NewTopic javaguidesTopic() {

        return TopicBuilder.name("javaguides").build();
    }

}
