package ru.example.order.dto;

import java.util.List;

public record OrderRequest(List<OrderLineItemsDto> orderLineItemsList) {}
