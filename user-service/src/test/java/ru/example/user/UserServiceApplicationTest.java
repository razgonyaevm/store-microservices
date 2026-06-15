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
import ru.example.user.dto.AuthRequest;
import ru.example.user.dto.AuthResponse;
import ru.example.user.dto.RegisterRequest;
import ru.example.user.model.Role;
import ru.example.user.model.User;
import ru.example.user.repository.UserRepository;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
@Testcontainers
@AutoConfigureMockMvc
public class UserServiceApplicationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Test
  void shouldPerformFullUserFlowAndEnforceSecurity() throws Exception {
    // Тестирование регистрации и логина
    String registerJson =
        """
                {
                  "username": "test_buyer",
                  "password": "my_password",
                  "email": "buyer@mail.com",
                  "role": "USER"
                }
                """;

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
        .andExpect(status().isCreated());

    User user = userRepository.findByUsername("test_buyer").orElseThrow();
    Assertions.assertEquals(Role.USER, user.getRole());

    String loginJson =
        """
                {
                  "username": "test_buyer",
                  "password": "my_password"
                }
                """;

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists());

    // Тестирование защиты регистрации
    // Попытка зарегистрировать дубликат (ожидаем 400 Bad Request)
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
        .andExpect(status().isBadRequest());

    // Попытка зарегистрироваться под ролью ADMIN напрямую (бэк блокирует - ожидает 400)
    String fakeAdminJson =
        """
                {
                  "username": "fake_admin",
                  "password": "password",
                  "role": "ADMIN"
                }
                """;
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fakeAdminJson))
        .andExpect(status().isBadRequest());

    // Тестирование баланса
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/user/balance").header("X-User-Name", "test_buyer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(1000.00));

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/recharge")
                .param("amount", "500.00")
                .header("X-User-Name", "test_buyer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(1500.00));

    // Попытка пополнить баланс на отрицательную сумму (заблокировано)
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/recharge")
                .param("amount", "-100.00")
                .header("X-User-Name", "test_buyer"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/deduct")
                .param("username", "test_buyer")
                .param("amount", "200.00"))
        .andExpect(status().isOk());

    // Попытка списать больше, чем есть на балансе (заблокировано)
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/deduct")
                .param("username", "test_buyer")
                .param("amount", "2000.00"))
        .andExpect(status().isBadRequest());

    // Тестируем получение профиля пользователя (GET /api/user/me)
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/user/me").header("X-User-Name", "test_buyer"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("test_buyer"))
        .andExpect(jsonPath("$.role").value("USER"));

    // Тестирование защиты суперюзера
    // Автоматически созданный admin должен существовать
    User rootAdmin = userRepository.findByUsername("admin").orElseThrow();

    // Попытка удалить аккаунт системного admin (запрещено - ожидаем 400)
    mockMvc
        .perform(MockMvcRequestBuilders.delete("/api/user/" + rootAdmin.getId()))
        .andExpect(status().isBadRequest());

    // Попытка изменить роль системного admin (запрещено - ожидаем 400)
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/" + rootAdmin.getId() + "/role")
                .param("role", "USER"))
        .andExpect(status().isBadRequest());

    // Административные методы управления пользователями
    // Получаем всех пользователей
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/user/all").header("X-User-Name", "admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2)) // admin + test_buyer
        .andExpect(jsonPath("$[0].username").value("admin"));

    // Смена роли пользователя админом
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/" + user.getId() + "/role")
                .param("role", "OWNER"))
        .andExpect(status().isOk());

    Assertions.assertEquals(Role.OWNER, userRepository.findById(user.getId()).get().getRole());

    // Удаление пользователя админом
    mockMvc
        .perform(MockMvcRequestBuilders.delete("/api/user/" + user.getId()))
        .andExpect(status().isOk());

    Assertions.assertFalse(userRepository.findById(user.getId()).isPresent());
  }

  @Test
  void testUserDtos() {
    RegisterRequest r1 = new RegisterRequest("u", "p", "e", "r");
    Assertions.assertEquals("u", r1.username());
    Assertions.assertEquals("p", r1.password());
    Assertions.assertEquals("e", r1.email());
    Assertions.assertEquals("r", r1.role());
    Assertions.assertNotNull(r1.toString());
    Assertions.assertNotNull(r1.hashCode());
    Assertions.assertEquals(r1, r1);

    AuthRequest a1 = new AuthRequest("u", "p");
    Assertions.assertEquals("u", a1.username());
    Assertions.assertEquals("p", a1.password());
    Assertions.assertNotNull(a1.toString());

    AuthResponse res = new AuthResponse("t");
    Assertions.assertEquals("t", res.token());
    Assertions.assertNotNull(res.toString());
  }

  @Test
  void testMainMethod() {
    // Динамически переопределяем свойства подключения к БД, указывая на наш тест-контейнер в Docker
    System.setProperty("spring.datasource.url", postgresContainer.getJdbcUrl());
    System.setProperty("spring.datasource.username", postgresContainer.getUsername());
    System.setProperty("spring.datasource.password", postgresContainer.getPassword());
    System.setProperty("eureka.client.enabled", "false");

    UserServiceApplication.main(new String[] {});
  }
}
