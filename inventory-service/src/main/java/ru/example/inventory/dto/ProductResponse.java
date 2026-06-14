package ru.example.inventory.dto;

import java.math.BigDecimal;

public record ProductResponse(
    Long id,
    String skuCode,
    String name,
    BigDecimal price,
    Integer quantity,
    String emoji,
    boolean isInStock) {}
