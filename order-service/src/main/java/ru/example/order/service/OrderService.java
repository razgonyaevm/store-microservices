package ru.example.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.example.order.client.InventoryClient;
import ru.example.order.client.UserClient;
import ru.example.order.dto.OrderLineItemsDto;
import ru.example.order.dto.OrderRequest;
import ru.example.order.event.OrderPlacedEvent;
import ru.example.order.model.Order;
import ru.example.order.model.OrderLineItems;
import ru.example.order.repository.OrderRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

  private final OrderRepository orderRepository;
  // KafkaTemplate для отправки событий
  private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
  private final InventoryClient inventoryClient;
  private final UserClient userClient;

  public String placeOrder(OrderRequest orderRequest, String username) {
    Order order = new Order();
    order.setOrderNumber(UUID.randomUUID().toString());

    List<OrderLineItems> orderLineItems =
        orderRequest.orderLineItemsList().stream().map(this::mapToDto).toList();

    order.setOrderLineItemsList(orderLineItems);

    // Вычисляем полную стоимость заказа
    BigDecimal totalOrderCost =
        order.getOrderLineItemsList().stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Списываем деньги с баланса пользователя
    // если денег не хватает, user-service выбросит ошибку 400
    // прерывает выполнение метода и откатывает транзакцию
    userClient.deductBalance(username, totalOrderCost);

    // Сохраняем заказ в бд
    orderRepository.save(order);

    // Отправка события в Kafka
    kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));

    return "Order Placed Successfully";
  }

  private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
    return OrderLineItems.builder()
        .price(orderLineItemsDto.price())
        .quantity(orderLineItemsDto.quantity())
        .skuCode(orderLineItemsDto.skuCode())
        .build();
  }
}
