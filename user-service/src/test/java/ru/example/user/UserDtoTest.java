package ru.example.user;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.example.user.dto.AuthRequest;
import ru.example.user.dto.AuthResponse;
import ru.example.user.dto.RegisterRequest;

public class UserDtoTest {

  @Test
  void testRegisterRequestRecord() {
    RegisterRequest r1 = new RegisterRequest("u", "p", "e", "r");
    Assertions.assertEquals("u", r1.username());
    Assertions.assertEquals("p", r1.password());
    Assertions.assertEquals("e", r1.email());
    Assertions.assertEquals("r", r1.role());
    Assertions.assertNotNull(r1.toString());
    Assertions.assertNotNull(r1.hashCode());
    Assertions.assertEquals(r1, r1);
  }

  @Test
  void testAuthRequestRecord() {
    AuthRequest a1 = new AuthRequest("u", "p");
    Assertions.assertEquals("u", a1.username());
    Assertions.assertEquals("p", a1.password());
    Assertions.assertNotNull(a1.toString());
  }

  @Test
  void testAuthResponseRecord() {
    AuthResponse res = new AuthResponse("t");
    Assertions.assertEquals("t", res.token());
    Assertions.assertNotNull(res.toString());
  }
}
