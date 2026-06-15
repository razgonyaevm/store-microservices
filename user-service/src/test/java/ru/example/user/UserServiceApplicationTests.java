package ru.example.user;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.user.repository.UserRepository;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
@Testcontainers
@AutoConfigureMockMvc
public class UserServiceApplicationTests {

  // Запускаем postgres во временном контейнере перед тестами
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Test
  void shouldRegisterAndLoginUser() throws Exception {
    String registerJson =
        """
                {
                  "username": "test_buyer",
                  "password": "my_password",
                  "email": "buyer@mail.com",
                  "role": "USER"
                }
                """;

    // Тестируем регистрацию POST /api/user/register
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
        .andExpect(status().isCreated());

    // Проверяем, что пользователь физически сохранился в БД
    Assertions.assertTrue(userRepository.findByUsername("test_buyer").isPresent());

    // Убеждаемся, что пароль в бд закэширован, а не захардкожен
    Assertions.assertNotEquals(
        "my_password", userRepository.findByUsername("test_buyer").get().getPassword());

    String loginJson =
        """
                {
                  "username": "test_buyer",
                  "password": "my_password"
                }
                """;

    // Тестируем авторизацию
    // ожидаем 200 и что бэк вернет json с токеном
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists());
  }
}
