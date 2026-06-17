package ru.example.inventory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
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
import ru.example.inventory.dto.ProductRequest;
import ru.example.inventory.model.Inventory;
import ru.example.inventory.repository.InventoryRepository;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "eureka.client.enabled=false")
@Testcontainers
@AutoConfigureMockMvc
public class InventoryProductManagementTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:15-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private InventoryRepository inventoryRepository;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldGetAllProducts() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/inventory/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", Matchers.greaterThanOrEqualTo(2)))
        .andExpect(jsonPath("$[0].skuCode").value("iphone_15"));
  }

  @Test
  void shouldCreateProduct() throws Exception {
    ProductRequest request =
        new ProductRequest("ipad_pro", "iPad Pro", BigDecimal.valueOf(1000), 5, "💻");

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/inventory/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skuCode").value("ipad_pro"))
        .andExpect(jsonPath("$.name").value("iPad Pro"))
        .andExpect(jsonPath("$.quantity").value(5));

    Assertions.assertTrue(inventoryRepository.findBySkuCode("ipad_pro").isPresent());
  }

  @Test
  void shouldUpdateProduct() throws Exception {
    Inventory saved =
        inventoryRepository.save(
            Inventory.builder()
                .skuCode("macbook_pro")
                .name("MacBook Pro")
                .price(BigDecimal.valueOf(2000))
                .quantity(3)
                .emoji("💻")
                .build());

    ProductRequest updateRequest =
        new ProductRequest("macbook_pro", "MacBook Pro", BigDecimal.valueOf(2200), 5, "🔥");

    mockMvc
        .perform(
            MockMvcRequestBuilders.put("/api/inventory/products/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("MacBook Pro"))
        .andExpect(jsonPath("$.price").value(2200))
        .andExpect(jsonPath("$.quantity").value(5));
  }

  @Test
  void shouldDeleteProduct() throws Exception {
    Inventory saved =
        inventoryRepository.save(
            Inventory.builder()
                .skuCode("delete_me")
                .name("Delete Me")
                .price(BigDecimal.valueOf(100))
                .quantity(1)
                .emoji("🗑️")
                .build());

    mockMvc
        .perform(MockMvcRequestBuilders.delete("/api/inventory/products/" + saved.getId()))
        .andExpect(status().isOk());

    Assertions.assertFalse(inventoryRepository.findById(saved.getId()).isPresent());
  }
}
