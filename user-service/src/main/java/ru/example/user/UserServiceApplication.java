package ru.example.user;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.example.user.model.Role;
import ru.example.user.model.User;
import ru.example.user.repository.UserRepository;

@SpringBootApplication
public class UserServiceApplication {

  // Внедряем имя и пароль админа из конфигов
  @Value("${app.admin.username}")
  private String adminUsername;

  @Value("${app.admin.password}")
  private String adminPassword;

  public static void main(String[] args) {
    SpringApplication.run(UserServiceApplication.class, args);
  }

  // Создаем суперпользователя на старте, если его нет
  @Bean
  public CommandLineRunner loadData(
      UserRepository userRepository, PasswordEncoder passwordEncoder) {
    return args -> {
      if (userRepository.findByUsername(adminUsername).isEmpty()) {
        User admin =
            User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .email("admin@store.com")
                .role(Role.ADMIN)
                .balance(BigDecimal.valueOf(1000))
                .build();
        userRepository.save(admin);
      }
    };
  }
}
