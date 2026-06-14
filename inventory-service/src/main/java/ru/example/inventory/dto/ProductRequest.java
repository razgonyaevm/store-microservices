package ru.example.inventory.dto;

import java.math.BigDecimal;

public record ProductRequest(
    String skuCode, String name, BigDecimal price, Integer quantity, String emoji) {}
