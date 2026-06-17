package ru.example.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(
    classes = NotificationServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.compatibility-verifier.enabled=false",
      "spring.kafka.consumer.auto-offset-reset=earliest"
    })
@EmbeddedKafka(
    topics = "notificationTopik",
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
public class NotificationApplicationTest {

  @Test
  void testMainMethod() {
    NotificationServiceApplication.main(new String[] {});
  }
}
