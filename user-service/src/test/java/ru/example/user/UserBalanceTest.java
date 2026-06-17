package ru.example.user;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ru.example.user.repository.UserRepository;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
@Testcontainers
@AutoConfigureMockMvc
@Transactional
public class UserBalanceTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  private void createTestUser(String username) throws Exception {
    String registerJson =
        String.format(
            """
                            {
                              "username": "%s",
                              "password": "password",
                              "email": "%s@mail.com",
                              "role": "USER"
                            }
                            """,
            username, username);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
        .andExpect(status().isCreated());

    userRepository.findByUsername(username).orElseThrow();
  }

  @Test
  void shouldGetUserBalance() throws Exception {
    createTestUser("balance_user");

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/user/balance").header("X-User-Name", "balance_user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(1000.00));
  }

  @Test
  void shouldRechargeBalance() throws Exception {
    createTestUser("recharge_user");

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/recharge")
                .param("amount", "500.00")
                .header("X-User-Name", "recharge_user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value(1500.00));
  }

  @Test
  void shouldFailRechargeWithNegativeAmount() throws Exception {
    createTestUser("negative_recharge_user");

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/recharge")
                .param("amount", "-100.00")
                .header("X-User-Name", "negative_recharge_user"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldDeductBalance() throws Exception {
    createTestUser("deduct_user");

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/deduct")
                .param("username", "deduct_user")
                .param("amount", "200.00"))
        .andExpect(status().isOk());
  }

  @Test
  void shouldFailDeductMoreThanBalance() throws Exception {
    createTestUser("insufficient_balance_user");

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/deduct")
                .param("username", "insufficient_balance_user")
                .param("amount", "2000.00"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldFailBalanceOperationsForNonExistentUser() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/user/balance").header("X-User-Name", "ghost_user"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/user/recharge")
                .header("X-User-Name", "ghost_user")
                .param("amount", "100"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/deduct")
                .param("username", "ghost_user")
                .param("amount", "100"))
        .andExpect(status().isBadRequest());
  }
}
