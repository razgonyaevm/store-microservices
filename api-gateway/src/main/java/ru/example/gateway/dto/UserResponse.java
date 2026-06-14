package ru.example.gateway.dto;

import java.math.BigDecimal;

public record UserResponse(
    Long id, String username, String email, String role, BigDecimal balance) {}
