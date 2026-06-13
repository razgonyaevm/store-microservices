package ru.example.order.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  public String placeOrder(OrderRequest orderRequest) {
    Order order = new Order();
    order.setOrderNumber(UUID.randomUUID().toString());

    List<OrderLineItems> orderLineItems =
        orderRequest.orderLineItemsList().stream().map(this::mapToDto).toList();

    order.setOrderLineItemsList(orderLineItems);

    // Сохраняем заказ. Товары уже были зарезервированы в корзине
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
