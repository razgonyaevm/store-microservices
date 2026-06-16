package ru.example.order.model;

import jakarta.persistence.*;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "t_orders")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String orderNumber;

  @OneToMany(cascade = CascadeType.ALL)
  @JoinColumn(name = "order_id")
  private List<OrderLineItems> orderLineItemsList;
}
