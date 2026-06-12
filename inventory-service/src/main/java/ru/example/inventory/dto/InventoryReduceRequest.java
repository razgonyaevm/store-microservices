package ru.example.inventory.dto;

public record InventoryReduceRequest(String skuCode, Integer quantity) {}
