package com.hdfcbank.standin.services.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mock Credit Bureau Service.
 * 
 * In production, this would connect to CIBIL, Experian, or Equifax APIs.
 * 
 * Dapr App ID: credit-bureau-service
 */
@RestController
@RequestMapping("/mock/credit")
public class CreditBureauServiceController {
    
    private static final Logger log = LoggerFactory.getLogger(CreditBureauServiceController.class);
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "credit-bureau-service",
            "timestamp", Instant.now().toString()
        ));
    }
    
    /**
     * Fetch credit report from bureau.
     */
    @PostMapping("/fetch-report")
    public ResponseEntity<Map<String, Object>> fetchCreditReport(@RequestBody Map<String, Object> request) {
        String panNumber = (String) request.get("pan_number");
        String applicantName = (String) request.getOrDefault("applicant_name", "Unknown");
        Object declaredScoreObj = request.get("declared_score");
        int declaredScore = declaredScoreObj != null ? ((Number) declaredScoreObj).intValue() : 0;
        
        log.info("📊 Credit Report Request for: {} (declared score: {})", applicantName, declaredScore);
        
        if (panNumber == null || panNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "PAN number is required"
            ));
        }
        
        simulateApiLatency();
        
        // Generate credit data based on PAN hash (for consistent results)
        Random random = new Random(panNumber.hashCode());
        
        int creditScore = generateCreditScore(declaredScore, random);
        String rating = getCreditRating(creditScore);
        int activeLoans = random.nextInt(5);
        double totalOutstanding = activeLoans * (100000 + random.nextInt(500000));
        int missedPayments = creditScore < 650 ? random.nextInt(5) : 0;
        int creditAge = 1 + random.nextInt(15);
        double creditUtilization = 20 + random.nextInt(60);
        
        log.info("✅ Credit report generated: Score={}, Rating={}", creditScore, rating);

        Map<String, Object> data = new HashMap<>();
        data.put("creditScore", creditScore);
        data.put("rating", rating);
        data.put("activeLoans", activeLoans);
        data.put("totalOutstanding", totalOutstanding);
        data.put("missedPayments12Months", missedPayments);
        data.put("creditHistoryYears", creditAge);
        data.put("creditUtilization", creditUtilization);
        data.put("enquiriesLast6Months", random.nextInt(5));
        data.put("oldestAccount", (2024 - creditAge) + "-01-15");
        data.put("paymentHistory", generatePaymentHistory(creditScore, random));
        data.put("bureau", "CIBIL");
        data.put("reportId", "CBR-" + System.currentTimeMillis());
        data.put("fetchedAt", Instant.now().toString());

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
    
    /**
     * Get just the credit score (lighter API).
     */
    @PostMapping("/score")
    public ResponseEntity<Map<String, Object>> getCreditScore(@RequestBody Map<String, Object> request) {
        String panNumber = (String) request.get("pan_number");
        Object declaredScoreObj = request.get("declared_score");
        int declaredScore = declaredScoreObj != null ? ((Number) declaredScoreObj).intValue() : 0;
        
        if (panNumber == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "PAN required"));
        }
        
        Random random = new Random(panNumber.hashCode());
        int creditScore = generateCreditScore(declaredScore, random);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "creditScore", creditScore,
                "rating", getCreditRating(creditScore),
                "bureau", "CIBIL",
                "fetchedAt", Instant.now().toString()
            )
        ));
    }
    
    private int generateCreditScore(int declaredScore, Random random) {
        if (declaredScore > 0) {
            int variance = random.nextInt(41) - 20; // -20 to +20
            return Math.max(300, Math.min(900, declaredScore + variance));
        }
        return 550 + random.nextInt(300); // 550-850
    }
    
    private String getCreditRating(int score) {
        if (score >= 750) return "EXCELLENT";
        if (score >= 700) return "GOOD";
        if (score >= 650) return "FAIR";
        if (score >= 550) return "POOR";
        return "VERY_POOR";
    }
    
    private List<String> generatePaymentHistory(int score, Random random) {
        String[] statuses = score >= 700 ? 
            new String[]{"ON_TIME", "ON_TIME", "ON_TIME", "ON_TIME", "ON_TIME", "ON_TIME"} :
            new String[]{"ON_TIME", "ON_TIME", "LATE_30", "ON_TIME", "ON_TIME", "LATE_60"};
        return List.of(statuses);
    }
    
    private void simulateApiLatency() {
        try { Thread.sleep(200 + (int)(Math.random() * 300)); } 
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

