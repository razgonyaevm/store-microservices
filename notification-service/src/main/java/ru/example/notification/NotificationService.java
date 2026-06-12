package ru.example.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import ru.example.notification.event.OrderPlacedEvent;

@Service
@Slf4j
public class NotificationService {

  // Слушаем топик "notificationTopic"
  @KafkaListener(topics = "notificationTopic", groupId = "notification-group")
  public void handleNotification(OrderPlacedEvent orderPlacedEvent) {
    // Имитируем отправку уведомления клиенту
    log.info("Received Notification for Order - {}", orderPlacedEvent.getOrderNumber());
    log.info("Sending email notification...");
  }
}
