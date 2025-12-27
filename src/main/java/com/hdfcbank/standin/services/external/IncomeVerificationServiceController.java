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
 * Mock Income Verification Service.
 * 
 * In production, this would connect to Income Tax Department's ITR verification API.
 * 
 * Dapr App ID: income-verification-service
 */
@RestController
@RequestMapping("/mock/income")
public class IncomeVerificationServiceController {
    
    private static final Logger log = LoggerFactory.getLogger(IncomeVerificationServiceController.class);
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "income-verification-service",
            "timestamp", Instant.now().toString()
        ));
    }
    
    /**
     * Verify income against ITR records.
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyIncome(@RequestBody Map<String, Object> request) {
        String panNumber = (String) request.get("pan_number");
        Object declaredIncomeObj = request.get("declared_income");
        String employmentType = (String) request.getOrDefault("employment_type", "SALARIED");
        String employerName = (String) request.getOrDefault("employer_name", "Unknown");
        
        double declaredIncome = declaredIncomeObj != null ? ((Number) declaredIncomeObj).doubleValue() : 0;
        
        log.info("💰 Income Verification Request: PAN={}, Declared=₹{}", 
                maskPan(panNumber), declaredIncome);
        
        if (panNumber == null || panNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "PAN number is required"
            ));
        }
        
        simulateApiLatency();
        
        Random random = new Random(panNumber.hashCode());
        
        // Generate ITR data
        double itrIncome = generateItrIncome(declaredIncome, random);
        double variance = declaredIncome > 0 ? 
            Math.abs(itrIncome - declaredIncome) / declaredIncome * 100 : 0;
        boolean isConsistent = variance <= 15; // Within 15% is acceptable
        
        String verificationStatus = isConsistent ? "VERIFIED" : "DISCREPANCY_FOUND";
        
        log.info("✅ Income verification: ITR=₹{}, Variance={}%, Status={}",
                itrIncome, String.format("%.1f", variance), verificationStatus);

        Map<String, Object> data = new HashMap<>();
        data.put("verificationStatus", verificationStatus);
        data.put("declaredIncome", declaredIncome);
        data.put("itrIncome", itrIncome);
        data.put("variancePercent", Math.round(variance * 10) / 10.0);
        data.put("isConsistent", isConsistent);
        data.put("itrYears", List.of(
            Map.of("year", "2023-24", "income", itrIncome),
            Map.of("year", "2022-23", "income", itrIncome * 0.9),
            Map.of("year", "2021-22", "income", itrIncome * 0.8)
        ));
        data.put("employmentType", employmentType);
        data.put("employerName", employerName);
        data.put("form16Available", "SALARIED".equals(employmentType));
        data.put("taxPaid", itrIncome * 0.2);
        data.put("verifiedAt", Instant.now().toString());
        data.put("source", "ITD");

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
    
    /**
     * Get ITR summary.
     */
    @PostMapping("/itr-summary")
    public ResponseEntity<Map<String, Object>> getItrSummary(@RequestBody Map<String, Object> request) {
        String panNumber = (String) request.get("pan_number");
        
        if (panNumber == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "PAN required"));
        }
        
        Random random = new Random(panNumber.hashCode());
        double baseIncome = 500000 + random.nextInt(2000000);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "itrFiled", true,
                "lastFiledYear", "2023-24",
                "totalIncome", baseIncome,
                "taxableIncome", baseIncome * 0.85,
                "taxPaid", baseIncome * 0.2,
                "refundClaimed", random.nextBoolean() ? baseIncome * 0.02 : 0,
                "source", "ITD"
            )
        ));
    }
    
    private double generateItrIncome(double declaredIncome, Random random) {
        if (declaredIncome > 0) {
            // Generate ITR income within ±10% of declared
            double variance = (random.nextDouble() * 0.2) - 0.1; // -10% to +10%
            return declaredIncome * (1 + variance);
        }
        return 500000 + random.nextInt(2000000);
    }
    
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "INVALID";
        return pan.substring(0, 2) + "XXXX" + pan.substring(6, 8) + "XX";
    }
    
    private void simulateApiLatency() {
        try { Thread.sleep(150 + (int)(Math.random() * 200)); } 
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

