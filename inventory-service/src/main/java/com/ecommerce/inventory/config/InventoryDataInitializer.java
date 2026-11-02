package com.ecommerce.inventory.config;

import com.ecommerce.inventory.entity.InventoryItem;
import com.ecommerce.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class InventoryDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryDataInitializer.class);

    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) {
        if (inventoryRepository.count() > 0) {
            log.info("Inventory table already initialized with {} items.", inventoryRepository.count());
            return;
        }

        log.info("Seeding initial stock levels into ecommerce_inventory...");

        List<InventoryItem> items = List.of(
                InventoryItem.builder().productId(1L).sku("TECH-KB-01").availableQuantity(45).reservedQuantity(0).build(),
                InventoryItem.builder().productId(2L).sku("AUD-HP-02").availableQuantity(30).reservedQuantity(0).build(),
                InventoryItem.builder().productId(3L).sku("DISP-MON-03").availableQuantity(15).reservedQuantity(0).build(),
                InventoryItem.builder().productId(4L).sku("WEAR-SW-04").availableQuantity(50).reservedQuantity(0).build(),
                InventoryItem.builder().productId(5L).sku("TECH-MS-05").availableQuantity(60).reservedQuantity(0).build(),
                InventoryItem.builder().productId(6L).sku("AUD-SPK-06").availableQuantity(40).reservedQuantity(0).build(),
                InventoryItem.builder().productId(7L).sku("TECH-CAM-07").availableQuantity(25).reservedQuantity(0).build(),
                InventoryItem.builder().productId(8L).sku("ACC-CHG-08").availableQuantity(80).reservedQuantity(0).build()
        );

        inventoryRepository.saveAll(items);
        log.info("Inventory initialized successfully for {} products.", items.size());
    }
}
