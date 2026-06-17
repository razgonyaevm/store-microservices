package ru.example.gateway;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.example.gateway.dto.UserResponse;

public class ApiGatewayDtoTest {

  @Test
  void testUserResponseRecord() {
    UserResponse response =
        new UserResponse(1L, "admin", "admin@mail.com", "ADMIN", BigDecimal.TEN);
    Assertions.assertEquals(1L, response.id());
    Assertions.assertEquals("admin", response.username());
    Assertions.assertEquals("admin@mail.com", response.email());
    Assertions.assertEquals("ADMIN", response.role());
    Assertions.assertEquals(BigDecimal.TEN, response.balance());
    Assertions.assertNotNull(response.toString());
    Assertions.assertNotNull(response.hashCode());
    Assertions.assertEquals(response, response);
  }
}
