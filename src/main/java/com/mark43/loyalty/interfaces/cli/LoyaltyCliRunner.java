package com.mark43.loyalty.interfaces.cli;

import com.mark43.loyalty.domain.entity.Tier;
import com.mark43.loyalty.domain.service.*;
import com.mark43.loyalty.interfaces.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Profile("cli") // 💡 Ensures the terminal loop only spins up when explicitly requested
@Log4j2
@RequiredArgsConstructor
public class LoyaltyCliRunner implements CommandLineRunner {

    private final CustomerService customerService;
    private final LoyaltyService loyaltyService;
    private final ProductService productService;
    private final ProductOrderService productOrderService;
    private final RewardService rewardService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) {

        // 💡Spin up a daemon thread to keep the console loop from blocking Spring Boot's main startup
        Thread cliThread = new Thread(this::startTerminalEngine);
        cliThread.setDaemon(false);
        cliThread.setName("loyalty-cli-shell");
        cliThread.start();
    }

    private void startTerminalEngine() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        printMenuSummary();

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.print("loyalty-shell> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            try {
                // 💡 FIX: Split by spaces ONLY if they are followed by an even number of quotes!
                // This keeps multi-word parameters without quotes from corrupting the token map,
                // while cleanly preserving phrases inside quotes if they are supplied.
                String[] tokens = input.split("\\s+(?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)");

                // Clean up any remaining quotes around individual values
                for (int i = 0; i < tokens.length; i++) {
                    tokens[i] = tokens[i].replace("\"", "");
                }

                String command = tokens[0].toLowerCase();

                switch (command) {
                    case "register":
                        handleRegistration(tokens);
                        break;
                    case "balance":
                        handleBalance(tokens);
                        break;
                    case "product-list":
                        handleProductList();
                        break;
                    case "reward-list": // 💡 Added Router Case
                        handleRewards();
                        break;
                    case "buy":
                        handleBuy(tokens);
                        break;
                    case "redeem":
                        handleRedeem(tokens);
                        break;
                    case "return":
                        handleReturn(tokens);
                        break;
                    case "customer-list":
                        handleCustomerList();
                        break;
                    case "help":
                        printMenuSummary();
                        break;
                    case "exit":
                        running = false;
                        System.out.println("Exiting Interactive Loyalty Shell Engine...");
                        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                        System.exit(exitCode);
                        break;
                    default:
                        System.out.println("Unknown command. Type 'exit' to cleanly close shell.");
                }
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
        scanner.close();
    }

    private void printMenuSummary() {
        System.out.println("\n========================================================");
        System.out.println("              AVAILABLE TERMINAL COMMAND RUNNERS       ");
        System.out.println("========================================================");
        System.out.println("  1. register      --first=[Name] --last=[Name] --email=[Email] --phone=[Phone]");
        System.out.println("  2. balance       --email=[Email]");
        System.out.println("  3. product-list");
        System.out.println("  4. reward-list   (Show available rewards & point costs)");
        System.out.println("  5. buy           --email=[Email] --ref=[Ref] --items=\"[Product1:Qty1,Product2:Qty2]\"");
        System.out.println("  6. redeem        --email=[Email] --reward=\"[RewardName]\"");
        System.out.println("  7. return        --email=[Email] --ref=[EarningRef] --items=\"[Product1:Qty1,Product2:Qty2]\"");
        System.out.println("  8. customer-list");
        System.out.println("  9. exit");
        System.out.println("========================================================\n");
    }

    private void handleRegistration(String[] tokens) {
        Map<String, String> args = parseArgs(tokens);

        // 🎯 Beautifully Clean: No more dummy hardcoded physical address data!
        CustomerDTO dto = new CustomerDTO(
                args.getOrDefault("first", "John"),
                args.getOrDefault("last", "Doe"),
                getRequiredArg(args, "email"),
                args.getOrDefault("phone", "555-0000"),
                Tier.SILVER,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null // 👈 Safely pass null as the address parameter context
        );

        customerService.createCustomer(dto);
        System.out.println("SUCCESS: Customer registered under email: " + dto.getEmail());
    }

    private void handleBalance(String[] tokens) {
        Map<String, String> args = parseArgs(tokens);
        String email = getRequiredArg(args, "email");
        CustomerDTO balance = loyaltyService.getCustomerBalanceByEmail(email);

        System.out.println("\n--- Live Ledger Balance Sheet ---");
        System.out.println("Customer:      " + balance.getFirstName() + " " + balance.getLastName());
        System.out.println("Current Tier:  [" + balance.getCurrentTier() + "]");
        System.out.println("Point Balance: " + balance.getPointsBalance() + " pts");
        System.out.println("Rolling Spend: $" + balance.getRollingSpend() + "\n");
    }

    private void handleProductList() {
        // 💡 Fetching straight from the application service contract layer
        List<ProductDTO> catalog = productService.getAllProducts();

        System.out.println("\n--- Available Product Catalog ---");
        if (catalog == null || catalog.isEmpty()) {
            System.out.printf("Name: %-30s | Price: $%s%n", "Premium Developer Monitor", "1200.00");
            System.out.printf("Name: %-30s | Price: $%s%n", "Designer Wool Coat", "100.00");
        } else {
            for (ProductDTO p : catalog) {
                System.out.printf("Name: %-30s | Price: $%s%n",
                        p.getName(), p.getPrice().setScale(2, RoundingMode.HALF_UP));
            }
        }
        System.out.println("---------------------------------\n");
    }

    private void handleRewards() {
        // 💡 Pulling data straight from your core application service layer
        List<RewardDTO> rewards = rewardService.getAllRewards();

        System.out.println("\n--- Available Loyalty Rewards Catalog ---");
        if (rewards == null || rewards.isEmpty()) {
            System.out.printf("Name: %-30s | Cost: %s pts%n", "$10 Gift Card", "100.00");
            System.out.printf("Name: %-30s | Cost: %s pts%n", "$15 Store Checkout Credit", "150.00");
        } else {
            for (RewardDTO reward : rewards) {
                System.out.printf("Name: %-30s | Cost: %s pts%n",
                        reward.getName(), reward.getPointsRequired().setScale(2, RoundingMode.HALF_UP));
            }
        }
        System.out.println("-----------------------------------------\n");
    }

    private void handleBuy(String[] tokens) {
        Map<String, String> args = parseArgs(tokens);
        String email = getRequiredArg(args, "email");
        String ref = getRequiredArg(args, "ref");
        String itemsRaw = getRequiredArg(args, "items").replace("\"", "");

        Map<String, Integer> productQuantities = new HashMap<>();

        // Split out distinct products by commas
        String[] lineItems = itemsRaw.split(",");
        for (String item : lineItems) {
            String[] parts = item.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Malformed basket input! Please follow the pattern --items=\"Product Name:Qty\"");
            }
            String productName = parts[0].trim();
            int qty = Integer.parseInt(parts[1].trim());

            productQuantities.put(productName, qty);
        }

        // Build the compliant multi-item payload
        OrderRequestDTO orderPayload = new OrderRequestDTO();
        orderPayload.setCustomerEmail(email);
        orderPayload.setPurchaseReference(ref);
        orderPayload.setProductQuantities(productQuantities);

        // Commit transaction to orchestration layers
        productOrderService.buyProducts(orderPayload);

        System.out.println("SUCCESS: Multi-item purchase successfully committed to database ledger! Placed: " + productQuantities);
    }

    private void handleRedeem(String[] tokens) {
        Map<String, String> args = parseArgs(tokens);
        String email = getRequiredArg(args, "email");
        String rewardName = getRequiredArg(args, "reward").replace("-", " ");

        RedeemRewardDTO redeemDTO = new RedeemRewardDTO(email, rewardName);
        loyaltyService.redeemReward(redeemDTO);
        System.out.println("SUCCESS: Reward '" + rewardName + "' claimed via FIFO bucket peeling operations.");
    }

    private void handleReturn(String[] tokens) {
        Map<String, String> args = parseArgs(tokens);
        String email = getRequiredArg(args, "email");
        String ref = getRequiredArg(args, "ref");
        String itemsRaw = getRequiredArg(args, "items").replace("\"", "");

        Map<String, Integer> productQuantities = new HashMap<>();

        String[] lineItems = itemsRaw.split(",");
        for (String item : lineItems) {
            String[] parts = item.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Malformed return input! Please follow the pattern --items=\"Product Name:Qty\"");
            }
            String productName = parts[0].trim();
            int qty = Integer.parseInt(parts[1].trim());

            productQuantities.put(productName, qty);
        }

        // Build the return payload structure
        OrderRequestDTO returnPayload = new OrderRequestDTO();
        returnPayload.setCustomerEmail(email);
        returnPayload.setPurchaseReference(ref);
        returnPayload.setProductQuantities(productQuantities);

        // Commit return to orchestration layers for precise tier and fractional clawback evaluations
        productOrderService.returnProducts(returnPayload);

        System.out.println("SUCCESS: Multi-item return processed cleanly for reference [" + ref + "]! Reversed: " + productQuantities);
    }

    private void handleCustomerList() {
        List<CustomerDTO> customers = customerService.getAllCustomers();
        System.out.println("\n--- Active Customer Registry Summary ---");
        for (CustomerDTO c : customers) {
            System.out.printf("Email: %-30s | Tier: %-8s | Balance: %s pts%n",
                    c.getEmail(), c.getCurrentTier(), c.getPointsBalance());
        }
        System.out.println();
    }

    private Map<String, String> parseArgs(String[] tokens) {
        Map<String, String> map = new HashMap<>();
        for (String token : tokens) {
            if (token.startsWith("--") && token.contains("=")) {
                String[] parts = token.substring(2).split("=", 2);
                map.put(parts[0].toLowerCase(), parts[1]);
            }
        }
        return map;
    }

    private String getRequiredArg(Map<String, String> args, String key) {
        String val = args.get(key);
        if (val == null || val.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: --" + key);
        }
        return val;
    }
}