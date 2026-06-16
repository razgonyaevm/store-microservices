package ru.example.user;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.user.client.CartClient;
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
@Transactional
public class UserServiceApplicationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @MockBean private CartClient cartClient;

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

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
        .andExpect(status().isCreated());

    Assertions.assertTrue(userRepository.findByUsername("test_buyer").isPresent());
    Assertions.assertNotEquals(
        "my_password", userRepository.findByUsername("test_buyer").get().getPassword());

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
  }

  @Test
  void shouldRegisterWithFallbackToUserForInvalidRole() throws Exception {
    String json =
        """
                {
                  "username": "invalid_role_user",
                  "password": "password",
                  "role": "SUPERMAN"
                }
                """;

    // Роль SUPERMAN не существует. Ожидаем успешную регистрацию с откатом роли к USER
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());

    User user = userRepository.findByUsername("invalid_role_user").orElseThrow();
    Assertions.assertEquals(Role.USER, user.getRole());
  }

  @Test
  void shouldFailLoginWithWrongPasswordForExistingUser() throws Exception {
    String registerJson =
        """
                {
                  "username": "wrong_pwd_user",
                  "password": "correct_password",
                  "role": "USER"
                }
                """;
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
        .andExpect(status().isCreated());

    String loginJson =
        """
                {
                  "username": "wrong_pwd_user",
                  "password": "wrong_password"
                }
                """;
    // Вводим неверный пароль для существующего юзера - ожидаем 400
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
        .andExpect(status().isUnauthorized())
        .andExpect(
            result ->
                Assertions.assertEquals(
                    "Invalid username or password", result.getResponse().getErrorMessage()));
  }

  @Test
  void shouldFailToModifyOrDeleteRootAdmin() throws Exception {
    // Ищем созданного CommandLineRunner-ом системного админа
    User admin = userRepository.findByUsername("admin").orElseThrow();

    // Попытка изменить роль админу - ожидаем 400
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/" + admin.getId() + "/role")
                .header("X-User-Name", "admin")
                .param("role", "USER"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getErrorMessage()
                        .contains("Modifying the root ADMIN account is forbidden")));

    // Попытка удалить админа - ожидаем 400
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/api/user/" + admin.getId())
                .header("X-User-Name", "admin"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result.getResponse().getErrorMessage().contains("Deleting the root ADMIN")));
  }

  @Test
  void shouldChangeUserRoleAndFailIfPromotingToAdmin() throws Exception {
    User user =
        User.builder()
            .username("role_change_user")
            .password("password")
            .role(Role.USER)
            .balance(BigDecimal.TEN)
            .build();
    userRepository.save(user);

    // Успешное изменение роли на OWNER
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/" + user.getId() + "/role")
                .header("X-User-Name", "admin")
                .param("role", "OWNER"))
        .andExpect(status().isOk());

    // Изменение роли на несуществующее
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/" + user.getId() + "/role")
                .header("X-User-Name", "admin")
                .param("role", "SUPERMAN"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result.getResponse().getErrorMessage().contains("Invalid role name")));

    // Попытка повысить до ADMIN - ожидаем 400
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/" + user.getId() + "/role")
                .header("X-User-Name", "admin")
                .param("role", "ADMIN"))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getErrorMessage()
                        .contains("Promotion to ADMIN role is forbidden")));
  }

  @Test
  void shouldFailUserNotFoundLambdas() throws Exception {
    // Тестируем лямбды генерации исключений (orElseThrow) на несуществующих ID/именах

    // getBalance
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/user/balance").header("X-User-Name", "ghost_user"))
        .andExpect(status().isNotFound());

    // rechargeBalance
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/recharge")
                .header("X-User-Name", "ghost_user")
                .param("amount", "100"))
        .andExpect(status().isNotFound());

    // deductBalance
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/deduct")
                .param("username", "ghost_user")
                .param("amount", "100"))
        .andExpect(status().isNotFound());

    // getCurrentUser (/me)
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/user/me").header("X-User-Name", "ghost_user"))
        .andExpect(status().isNotFound());

    // changeRole (несуществующий ID)
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/9999/role")
                .header("X-User-Name", "admin")
                .param("role", "OWNER"))
        .andExpect(status().isNotFound());

    // deleteUser (несуществующий ID)
    mockMvc
        .perform(MockMvcRequestBuilders.delete("/api/user/9999").header("X-User-Name", "admin"))
        .andExpect(status().isNotFound());
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
  void shouldFailRegistrationIfUserAlreadyExists() throws Exception {
    String json =
        """
                {
                  "username": "duplicate_user",
                  "password": "password",
                  "email": "dup@mail.com",
                  "role": "USER"
                }
                """;

    // Регистрируем в первый раз - 201
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());

    // Попытка зарегистрировать дубликат - 400 Bad Request
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertEquals(
                    "Username already exists", result.getResponse().getErrorMessage()));
  }

  @Test
  void shouldFailRegistrationIfAdminRoleIsRequested() throws Exception {
    String json =
        """
                {
                  "username": "fake_admin",
                  "password": "password",
                  "email": "fake@mail.com",
                  "role": "ADMIN"
                }
                """;

    // Попытка зарегистрировать админа извне - 400 Bad Request
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertEquals(
                    "Registration with ADMIN role is forbidden!",
                    result.getResponse().getErrorMessage()));
  }

  @Test
  void shouldFailLoginWithWrongCredentials() throws Exception {
    String json =
        """
                {
                  "username": "non_existent_user",
                  "password": "wrong_password"
                }
                """;

    // Попытка входа с неверными учетными данными - 400 Bad Request
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andDo(print())
        .andExpect(status().isUnauthorized())
        .andExpect(
            result ->
                Assertions.assertEquals(
                    "Invalid username or password", result.getResponse().getErrorMessage()));
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
