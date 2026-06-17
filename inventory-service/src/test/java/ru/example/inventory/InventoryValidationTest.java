package ru.example.inventory;

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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
@Testcontainers
@AutoConfigureMockMvc
public class InventoryValidationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldFailProductCannotBeWithNegativeQuantity() throws Exception {
    String negativeQtyProductJson =
        """
                        {
                          "skuCode": "neg_qty_sku",
                          "name": "Negative Qty",
                          "price": 100,
                          "quantity": -5,
                          "emoji": "📱"
                        }
                        """;

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(negativeQtyProductJson))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getContentAsString()
                        .contains("Quantity cannot be negative")));

    String negativePriceUpdateJson =
        """
                        {
                          "skuCode": "iphone_15",
                          "name": "iPhone 15 Pro",
                          "price": -100,
                          "quantity": 10,
                          "emoji": "📱"
                        }
                        """;
    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(negativePriceUpdateJson))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result
                        .getResponse()
                        .getContentAsString()
                        .contains("Price must be greater than zero")));
  }
}
