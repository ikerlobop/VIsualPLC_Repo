package springboot_kafka_tutorial.kafka;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.security.SecurityProperties.User;
import org.springframework.kafka.annotation.KafkaListener;


public class JsonKafkaConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonKafkaConsumer.class);

    @KafkaListener(topics = "javaguides_json", groupId = "myGroup")
    public void consume(User user) {
        LOGGER.info(String.format("#### -> Json mesage received -> %s", user.toString()));
        System.out.println("Json message received: " + user.toString());
    }
}
