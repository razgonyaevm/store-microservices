package ru.example.cart.dto;

import java.util.List;

public record OrderRequest(List<OrderLineItemsDto> orderLineItemsList) {}
