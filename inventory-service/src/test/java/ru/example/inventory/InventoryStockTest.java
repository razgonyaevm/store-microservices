package ru.example.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
import ru.example.inventory.dto.InventoryReduceRequest;
import ru.example.inventory.repository.InventoryRepository;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
@Testcontainers
@AutoConfigureMockMvc
public class InventoryStockTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private InventoryRepository inventoryRepository;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldCheckInStock() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/inventory")
                .param("skuCode", "iphone_15")
                .param("skuCode", "pixel_8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].skuCode").value("iphone_15"))
        .andExpect(jsonPath("$[0].isInStock").value(true))
        .andExpect(jsonPath("$[1].skuCode").value("pixel_8"))
        .andExpect(jsonPath("$[1].isInStock").value(false));

    Assertions.assertTrue(postgresContainer.isRunning());
  }

  @Test
  void shouldReduceAndIncreaseStock() throws Exception {
    List<InventoryReduceRequest> reduceRequests =
        List.of(new InventoryReduceRequest("iphone_15", 2));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/reduce")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reduceRequests)))
        .andExpect(status().isOk());

    Assertions.assertEquals(8, inventoryRepository.findBySkuCode("iphone_15").get().getQuantity());

    List<InventoryReduceRequest> increaseRequests =
        List.of(new InventoryReduceRequest("iphone_15", 2));

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/increase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(increaseRequests)))
        .andExpect(status().isOk());

    Assertions.assertEquals(10, inventoryRepository.findBySkuCode("iphone_15").get().getQuantity());
  }

  @Test
  void shouldFailReductionIfInsufficientQuantity() throws Exception {
    String reduceJson =
        """
                        [
                          {
                            "skuCode": "iphone_15",
                            "quantity": 100
                          }
                        ]
                        """;

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/reduce")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reduceJson))
        .andExpect(status().isBadRequest())
        .andExpect(
            result ->
                Assertions.assertTrue(
                    result.getResponse().getContentAsString().contains("Not enough stock")));
  }
}
