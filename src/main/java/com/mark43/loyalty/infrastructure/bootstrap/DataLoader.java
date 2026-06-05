package com.mark43.loyalty.infrastructure.bootstrap;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.infrastructure.repository.RewardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.mark43.loyalty.domain.entity.Tier.SILVER;

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final RewardRepository rewardRepository;

    public DataLoader(CustomerRepository customerRepository,
                      ProductRepository productRepository,
                      RewardRepository rewardRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.rewardRepository = rewardRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Bootstrapping comprehensive retail loyalty dataset...");

        if (customerRepository.count() == 0) {
            Address mainAddr = new Address(100, "Federal St", "Boston", "MA", "01010", "USA");
            Address northAddr = new Address(25, "Back Bay Way", "Boston", "MA", "15465","USA");
            Address southAddr = new Address(450, "Summer St", "Boston", "MA", "12321", "USA");

            customerRepository.saveAll(List.of(
                    new Customer(null, "Alice", "Smith", "alice@example.com", "555-0101", SILVER, mainAddr),
                    new Customer(null, "Bob", "Jones", "bob@example.com", "555-0102", SILVER, mainAddr),
                    new Customer(null, "Charlie", "Brown", "charlie@example.com", "555-0103", SILVER, northAddr),
                    new Customer(null, "Diana", "Prince", "diana@example.com", "555-0104", SILVER, northAddr),
                    new Customer(null, "Evan",  "Wright", "evan@example.com", "555-0105", SILVER, southAddr),
                    new Customer(null, "Fiona", "Gallagher", "fiona@example.com", "555-0106", SILVER, southAddr),
                    new Customer(null, "George", "Costanza", "george@example.com", "555-0107", SILVER, mainAddr),
                    new Customer(null, "Hannah", "Baker", "hannah@example.com", "555-0108", SILVER, northAddr),
                    new Customer(null, "Ian", "Malcolm", "ian@example.com", "555-0109", SILVER, southAddr),
                    new Customer(null, "Julia", "Roberts", "julia@example.com", "555-0110", SILVER, mainAddr),
                    new Customer(null, "Kevin",  "Bacon", "kevin@example.com", "555-0111", SILVER, northAddr)
            ));
            log.info("Successfully seeded 11 system users/customers.");
        }

        if (productRepository.count() == 0) {
            productRepository.saveAll(List.of(
                    new Product(null, "Premium Leather Jacket", "Brand new leather jacket",  new BigDecimal("250.00")),
                    new Product(null, "Designer Wool Coat", "Brand new designer Wool Coat",  new BigDecimal("180.00")),
                    new Product(null, "Ergonomic Running Shoes", "Brand new ergonomic Running Shoes",  new BigDecimal("120.00")),
                    new Product(null, "Waterproof Hiking Boots", "Brand new waterproof Hiking Boots",  new BigDecimal("150.00")),
                    new Product(null, "Mechanical Keyboard", "Brand new mechanical Keyboard",  new BigDecimal("95.00")),
                    new Product(null, "Wireless Earbuds", "Brand new wireless Earbuds",  new BigDecimal("85.00")),
                    new Product(null, "Full Grain Leather Belt", "Brand new full Grain Leather Belt",  new BigDecimal("45.00")),
                    new Product(null, "Polarized Sunglasses", "Brand new polarized Sunglasses",  new BigDecimal("65.00")),
                    new Product(null, "Stainless Travel Mug", "Brand new stainless Travel Mug",  new BigDecimal("25.00")),
                    new Product(null, "Organic Cotton Hoodie", "Brand new organic Cotton Hoodie",  new BigDecimal("55.00")),
                    new Product(null, "Slim Fit Denim Jeans", "Brand new slim Fit Denim Jeans",  new BigDecimal("70.00")),
                    new Product(null, "Minimalist Wallet", "Brand new minimalist Wallet",  new BigDecimal("35.00")),
                    new Product(null, "Gym Duffel Bag", "Brand new gym Duffel Bag",  new BigDecimal("40.00")),
                    new Product(null, "Desk LED Lamp", "Brand new desk LED Lamp",  new BigDecimal("30.00")),
                    new Product(null, "Hydro Flask Water Bottle", "Brand new hydro Flask Water Bottle",  new BigDecimal("32.00"))
            ));
            log.info("Successfully seeded 15 store products catalog.");
        }

        if (rewardRepository.count() == 0) {
            rewardRepository.saveAll(List.of(
                    new Reward(null, "Free Premium Espresso", "", new BigDecimal("15")),
                    new Reward(null, "Store Eco Tote Bag", "", new BigDecimal("30")),
                    new Reward(null, "Enamel Pin Collector Set", "", new BigDecimal("50")),
                    new Reward(null, "Signature Stainless Water Bottle", "", new BigDecimal("100")),
                    new Reward(null, "$15 Store Checkout Credit", "", new BigDecimal("150")),
                    new Reward(null, "Branded Graphic T-Shirt", "", new BigDecimal("200")),
                    new Reward(null, "$30 Store Checkout Credit", "", new BigDecimal("300")),
                    new Reward(null, "Luxury Scented Candle", "", new BigDecimal("400")),
                    new Reward(null, "Cozy Knit Throw Blanket", "", new BigDecimal("600")),
                    new Reward(null, "$100 Mega Gift Card VIP", "", new BigDecimal("1000"))
            ));
            log.info("Successfully seeded 10 reward redemptions catalog.");
        }

        log.info("Data layer bootstrap complete. Application engine data parameters locked down.");
    }
}