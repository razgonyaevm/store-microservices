package ru.example.notification;

import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import ru.example.notification.event.OrderPlacedEvent;

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
public class NotificationKafkaTest {

  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired private ObjectMapper objectMapper;

  @SpyBean private NotificationService notificationService;

  @Test
  void shouldConsumeOrderPlacedEvent() throws Exception {
    OrderPlacedEvent event = new OrderPlacedEvent("order_12345");

    String jsonEvent = objectMapper.writeValueAsString(event);

    kafkaTemplate.send("notificationTopic", jsonEvent);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Mockito.verify(notificationService, Mockito.times(1))
                  .handleNotification(
                      Mockito.argThat(argument -> argument.getOrderNumber().equals("order_12345")));
            });
  }
}
