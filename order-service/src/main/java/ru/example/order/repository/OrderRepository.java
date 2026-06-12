package ru.example.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.example.order.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {}
