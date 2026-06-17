package ru.example.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.compatibility-verifier.enabled=false"
    })
public class ApiGatewayApplicationTest {

  @Test
  void testMainMethod() {
    ApiGatewayApplication.main(new String[] {});
  }
}
