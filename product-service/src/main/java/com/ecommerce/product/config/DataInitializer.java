package com.ecommerce.product.config;

import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.repository.CategoryRepository;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            log.info("Products already seeded. Total count={}", productRepository.count());
            return;
        }

        log.info("Seeding initial catalog categories and products into MySQL...");

        Category electronics = categoryRepository.save(Category.builder()
                .name("Electronics")
                .description("Cutting-edge gadgets, peripherals, and smart gear")
                .build());

        Category audio = categoryRepository.save(Category.builder()
                .name("Audio")
                .description("Studio headphones, speakers, and wireless earbuds")
                .build());

        Category wearables = categoryRepository.save(Category.builder()
                .name("Wearables")
                .description("Smartwatches, fitness bands, and wearable tech")
                .build());

        Category accessories = categoryRepository.save(Category.builder()
                .name("Accessories")
                .description("Keyboards, mice, ergonomic stands, and cables")
                .build());

        List<Product> seedProducts = List.of(
                Product.builder()
                        .name("Apex Pro Mechanical Keyboard RGB")
                        .description("Customizable OmniPoint adjustable mechanical switches, aircraft-grade aluminum alloy frame, and magnetic wrist rest.")
                        .price(new BigDecimal("189.99"))
                        .sku("TECH-KB-01")
                        .imageUrl("https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80")
                        .category(accessories)
                        .stockQuantity(45)
                        .active(true)
                        .build(),

                Product.builder()
                        .name("SonicWave ANC Wireless Headphones")
                        .description("Industry-leading active noise cancellation, high-fidelity 40mm drivers, and up to 45 hours of ultra-long battery life.")
                        .price(new BigDecimal("249.99"))
                        .sku("AUD-HP-02")
                        .imageUrl("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80")
                        .category(audio)
                        .stockQuantity(30)
                        .active(true)
                        .build(),

                Product.builder()
                        .name("UltraView 4K Curved Gaming Monitor 34\"")
                        .description("Immersive 1500R curvature, 165Hz refresh rate, 1ms response time, and HDR400 for stunning visual clarity.")
                        .price(new BigDecimal("499.99"))
                        .sku("DISP-MON-03")
                        .imageUrl("https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=800&q=80")
                        .category(electronics)
                        .stockQuantity(15)
                        .active(true)
                        .build(),

                Product.builder()
                        .name("Chronos Elite Smart Fitness Watch")
                        .description("Always-On AMOLED display, dual-band GPS, advanced SpO2 heart health monitoring, and 14-day battery life.")
                        .price(new BigDecimal("179.99"))
                        .sku("WEAR-SW-04")
                        .imageUrl("https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80")
                        .category(wearables)
                        .stockQuantity(50)
                        .active(true)
                        .build(),

                Product.builder()
                        .name("Precision Pro Wireless Ergonomic Mouse")
                        .description("Advanced 26,000 DPI optical sensor, ultra-fast 2.4GHz wireless plus Bluetooth multi-device switching.")
                        .price(new BigDecimal("79.99"))
                        .sku("TECH-MS-05")
                        .imageUrl("https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?auto=format&fit=crop&w=800&q=80")
                        .category(accessories)
                        .stockQuantity(60)
                        .active(true)
                        .build(),

                Product.builder()
                        .name("SoundPulse Portable Waterproof Speaker")
                        .description("360-degree room-filling surround sound, IPX7 waterproof rating, vibrant RGB beat sync, and 24h battery.")
                        .price(new BigDecimal("89.99"))
                        .sku("AUD-SPK-06")
                        .imageUrl("https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=800&q=80")
                        .category(audio)
                        .stockQuantity(40)
                        .active(true)
                        .build(),

                Product.builder()
                        .name("VisionStream 4K Ultra HD Webcam")
                        .description("Crystal-clear 4K@30fps resolution, dual noise-reducing stereo microphones, and AI auto-framing technology.")
                        .price(new BigDecimal("119.99"))
                        .sku("TECH-CAM-07")
                        .imageUrl("https://images.unsplash.com/photo-1588508065123-287b28e013da?auto=format&fit=crop&w=800&q=80")
                        .category(electronics)
                        .stockQuantity(25)
                        .active(true)
                        .build(),

                Product.builder()
                        .name("ChargeCore MagFast 3-in-1 Charging Station")
                        .description("Simultaneous 15W fast wireless charging for your smartphone, smartwatch, and wireless earbuds.")
                        .price(new BigDecimal("59.99"))
                        .sku("ACC-CHG-08")
                        .imageUrl("https://images.unsplash.com/photo-1583863788434-e58a36330cf0?auto=format&fit=crop&w=800&q=80")
                        .category(accessories)
                        .stockQuantity(80)
                        .active(true)
                        .build()
        );

        productRepository.saveAll(seedProducts);
        log.info("Seeding completed successfully! {} products added.", seedProducts.size());
    }
}
