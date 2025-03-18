// https://www.youtube.com/watch?v=inrQUHLPFd4

package springboot_kafka_tutorial;

import org.springframework.boot.SpringApplication; 
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KafkaApplication {
    
    public static void main(String[] args) {

        // Iniciar la aplicación Spring Boot
        SpringApplication.run(KafkaApplication.class, args);
    }
}