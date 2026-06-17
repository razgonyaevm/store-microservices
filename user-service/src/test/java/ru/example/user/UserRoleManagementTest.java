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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
public class UserRoleManagementTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Test
  void shouldChangeUserRole() throws Exception {
    User user =
        User.builder()
            .username("role_change_user")
            .password("password")
            .role(Role.USER)
            .balance(BigDecimal.TEN)
            .build();
    userRepository.save(user);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/" + user.getId() + "/role")
                .header("X-User-Name", "admin")
                .param("role", "OWNER"))
        .andExpect(status().isOk());

    Assertions.assertEquals(Role.OWNER, userRepository.findById(user.getId()).get().getRole());
  }

  @Test
  void shouldFailPromotionToAdmin() throws Exception {
    User user =
        User.builder()
            .username("no_admin_user")
            .password("password")
            .role(Role.USER)
            .balance(BigDecimal.TEN)
            .build();
    userRepository.save(user);

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
                        .getContentAsString()
                        .contains("Promotion to ADMIN role is forbidden")));
  }

  @Test
  void shouldFailInvalidRoleName() throws Exception {
    User user =
        User.builder()
            .username("invalid_role_user")
            .password("password")
            .role(Role.USER)
            .balance(BigDecimal.TEN)
            .build();
    userRepository.save(user);

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/user/" + user.getId() + "/role")
                .header("X-User-Name", "admin")
                .param("role", "SUPERMAN"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result.getResponse().getContentAsString().contains("Invalid role name")));
  }

  @Test
  void shouldFailModifyingRootAdmin() throws Exception {
    User admin = userRepository.findByUsername("admin").orElseThrow();

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
                        .getContentAsString()
                        .contains("Modifying the root ADMIN account is forbidden")));
  }

  @Test
  void shouldGetAllUsers() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/user/all").header("X-User-Name", "admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].username").value("admin"));
  }

  @Test
  void shouldGetCurrentUser() throws Exception {
    User user =
        User.builder()
            .username("current_user")
            .password("password")
            .role(Role.USER)
            .balance(BigDecimal.TEN)
            .build();
    userRepository.save(user);

    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/user/me").header("X-User-Name", "current_user"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("current_user"))
        .andExpect(jsonPath("$.role").value("USER"));
  }

  @Test
  void shouldDeleteUser() throws Exception {
    User user =
        User.builder()
            .username("delete_user")
            .password("password")
            .role(Role.USER)
            .balance(BigDecimal.TEN)
            .build();
    userRepository.save(user);

    mockMvc
        .perform(MockMvcRequestBuilders.delete("/api/user/" + user.getId()))
        .andExpect(status().isOk());

    Assertions.assertFalse(userRepository.findById(user.getId()).isPresent());
  }

  @Test
  void shouldFailDeletingRootAdmin() throws Exception {
    User admin = userRepository.findByUsername("admin").orElseThrow();

    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/api/user/" + admin.getId())
                .header("X-User-Name", "admin"))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result.getResponse().getContentAsString().contains("Deleting the root ADMIN")));
  }
}
