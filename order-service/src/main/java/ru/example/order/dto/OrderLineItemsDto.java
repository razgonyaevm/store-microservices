package ru.example.order.dto;

import java.math.BigDecimal;

public record OrderLineItemsDto(String skuCode, BigDecimal price, Integer quantity) {}
