package ru.example.user.service;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.example.user.dto.AuthRequest;
import ru.example.user.dto.RegisterRequest;
import ru.example.user.dto.UserResponse;
import ru.example.user.model.Role;
import ru.example.user.model.User;
import ru.example.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public String register(RegisterRequest registerRequest) {
    if (userRepository.findByUsername(registerRequest.username()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
    }

    // По умолчанию выдается роль USER
    Role userRole = Role.USER;
    if (registerRequest.role() != null) {
      try {
        userRole = Role.valueOf(registerRequest.role().toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }

    User user =
        User.builder()
            .username(registerRequest.username())
            // Хэшируем пароль перед записью в бд
            .password(passwordEncoder.encode(registerRequest.password()))
            .email(registerRequest.email())
            .role(userRole)
            .balance(
                BigDecimal.valueOf(
                    1000)) // Дарим пользователю 1000 приветственных баксов для тестов
            .build();

    userRepository.save(user);
    return "User registered successfully with role " + userRole.name();
  }

  public BigDecimal getBalance(String username) {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    return user.getBalance();
  }

  @Transactional
  public BigDecimal rechargeBalance(String username, BigDecimal amount) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be greater than zero");
    }
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

    user.setBalance(user.getBalance().add(amount));
    userRepository.save(user);
    return user.getBalance();
  }

  @Transactional
  public void deductBalance(String username, BigDecimal amount) {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

    if (user.getBalance().compareTo(amount) < 0) {
      throw new IllegalArgumentException(
          "Insufficient funds! You have $" + user.getBalance() + " but order costs $" + amount);
    }

    user.setBalance(user.getBalance().subtract(amount));
    userRepository.save(user);
  }

  // Получение всех зарегистрированных пользователей
  @Transactional(readOnly = true)
  public List<UserResponse> getAllUsers() {
    return userRepository.findAllByOrderByIdAsc().stream()
        .map(
            user ->
                new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole(),
                    user.getBalance()))
        .toList();
  }

  // Принудительное изменение роли пользователя администратором
  @Transactional
  public void changeRole(Long id, String roleStr) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

    try {
      Role newRole = Role.valueOf(roleStr.toUpperCase());
      user.setRole(newRole);
      userRepository.save(user);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid role name: " + roleStr);
    }
  }

  // Удаление пользователя из бд
  @Transactional
  public void deleteUser(Long id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

    // Предотвращаем самоудаление
    userRepository.delete(user);
  }

  // Получение актуального профиля пользователя из бд
  @Transactional(readOnly = true)
  public UserResponse getCurrentUser(String username) {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    return new UserResponse(
        user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getBalance());
  }

  public String login(AuthRequest authRequest) {
    User user =
        userRepository
            .findByUsername(authRequest.username())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid username or password"));

    // Проверяем, совпадает ли введенный пароль с хэшем в бд
    if (passwordEncoder.matches(authRequest.password(), user.getPassword())) {
      return jwtService.generateToken(user.getUsername(), user.getRole().name());
    } else {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
  }
}
