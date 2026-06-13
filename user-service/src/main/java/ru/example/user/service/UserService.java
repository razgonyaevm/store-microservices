package ru.example.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.example.user.dto.AuthRequest;
import ru.example.user.dto.RegisterRequest;
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
            .build();

    userRepository.save(user);
    return "User registered successfully";
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
