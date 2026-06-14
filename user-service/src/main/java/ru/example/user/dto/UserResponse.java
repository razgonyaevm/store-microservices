package ru.example.user.dto;

import java.math.BigDecimal;
import ru.example.user.model.Role;

public record UserResponse(Long id, String username, String email, Role role, BigDecimal balance) {}
