package ru.example.inventory;

import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.example.inventory.model.Inventory;
import ru.example.inventory.repository.InventoryRepository;

@SpringBootApplication
public class InventoryServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(InventoryServiceApplication.class, args);
  }

  @Bean
  public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
    return args -> {
      if (inventoryRepository.count() == 0) {
        Inventory item1 =
            Inventory.builder()
                .skuCode("iphone_15")
                .name("iPhone 15")
                .price(BigDecimal.valueOf(1200))
                .emoji("📱")
                .quantity(10)
                .build();

        Inventory item2 =
            Inventory.builder()
                .skuCode("pixel_8")
                .name("Google Pixel 8")
                .price(BigDecimal.valueOf(800))
                .emoji("🤖")
                .quantity(0) // Нет в наличии
                .build();

        inventoryRepository.save(item1);
        inventoryRepository.save(item2);
      }
    };
  }
}
