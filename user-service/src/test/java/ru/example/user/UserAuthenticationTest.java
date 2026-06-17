package ru.example.user;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.example.user.model.Role;
import ru.example.user.model.User;
import ru.example.user.repository.UserRepository;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
@Testcontainers
@AutoConfigureMockMvc
@Transactional
public class UserAuthenticationTest {

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
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getContentAsString()
                        .contains("Invalid username or password")));
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
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getContentAsString()
                        .contains("Invalid username or password")));
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

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getContentAsString()
                        .contains("Registration with ADMIN role is forbidden!")));
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

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result.getResponse().getContentAsString().contains("Username already exists")));
  }
}
