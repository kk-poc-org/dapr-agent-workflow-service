package com.hdfcbank.standin.services.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mock Fraud Detection Service.
 * 
 * In production, this would connect to fraud detection systems,
 * AML databases, and risk scoring engines.
 * 
 * Dapr App ID: fraud-detection-service
 */
@RestController
@RequestMapping("/mock/fraud")
public class FraudDetectionServiceController {
    
    private static final Logger log = LoggerFactory.getLogger(FraudDetectionServiceController.class);
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "fraud-detection-service",
            "timestamp", Instant.now().toString()
        ));
    }
    
    /**
     * Check for fraud indicators.
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> checkFraud(@RequestBody Map<String, Object> request) {
        String panNumber = (String) request.get("pan_number");
        String aadhaarNumber = (String) request.get("aadhaar_number");
        String applicantName = (String) request.getOrDefault("applicant_name", "Unknown");
        Object incomeObj = request.get("annual_income");
        Object loanAmountObj = request.get("loan_amount");
        
        double annualIncome = incomeObj != null ? ((Number) incomeObj).doubleValue() : 0;
        double loanAmount = loanAmountObj != null ? ((Number) loanAmountObj).doubleValue() : 0;
        
        log.info("🔍 Fraud Check Request for: {}", applicantName);
        
        simulateApiLatency();
        
        Random random = new Random((panNumber + aadhaarNumber).hashCode());
        
        // Calculate risk score (0-100, lower is better)
        int riskScore = calculateRiskScore(annualIncome, loanAmount, random);
        String riskLevel = getRiskLevel(riskScore);
        List<String> flags = generateFraudFlags(riskScore, annualIncome, loanAmount, random);
        
        boolean isFraudulent = riskScore > 70;
        boolean requiresManualReview = riskScore > 50 && riskScore <= 70;
        
        log.info("✅ Fraud check complete: Risk={}, Level={}, Flags={}",
                riskScore, riskLevel, flags.size());

        Map<String, Object> data = new HashMap<>();
        data.put("riskScore", riskScore);
        data.put("riskLevel", riskLevel);
        data.put("isFraudulent", isFraudulent);
        data.put("requiresManualReview", requiresManualReview);
        data.put("fraudFlags", flags);
        data.put("amlCheck", "CLEAR");
        data.put("sanctionListCheck", "CLEAR");
        data.put("pepCheck", "NOT_PEP");
        data.put("duplicateApplicationCheck", random.nextInt(100) > 95 ? "DUPLICATE_FOUND" : "CLEAR");
        data.put("velocityCheck", "NORMAL");
        data.put("deviceFingerprint", "TRUSTED");
        data.put("checkedAt", Instant.now().toString());
        data.put("checkId", "FRD-" + System.currentTimeMillis());

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
    
    /**
     * Get risk score only.
     */
    @PostMapping("/risk-score")
    public ResponseEntity<Map<String, Object>> getRiskScore(@RequestBody Map<String, Object> request) {
        String panNumber = (String) request.getOrDefault("pan_number", "");
        String aadhaarNumber = (String) request.getOrDefault("aadhaar_number", "");
        
        Random random = new Random((panNumber + aadhaarNumber).hashCode());
        int riskScore = 10 + random.nextInt(50); // Generally low risk for quick check
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "riskScore", riskScore,
                "riskLevel", getRiskLevel(riskScore),
                "checkedAt", Instant.now().toString()
            )
        ));
    }
    
    private int calculateRiskScore(double income, double loanAmount, Random random) {
        int baseScore = 10 + random.nextInt(30); // Base: 10-40
        
        // High loan-to-income ratio increases risk
        if (income > 0 && loanAmount / income > 5) {
            baseScore += 20;
        }
        
        // Very high loan amounts increase risk
        if (loanAmount > 10000000) { // > 1 Crore
            baseScore += 15;
        }
        
        return Math.min(100, baseScore);
    }
    
    private String getRiskLevel(int score) {
        if (score <= 25) return "LOW";
        if (score <= 50) return "MEDIUM";
        if (score <= 70) return "HIGH";
        return "CRITICAL";
    }
    
    private List<String> generateFraudFlags(int riskScore, double income, double loanAmount, Random random) {
        List<String> flags = new ArrayList<>();
        
        if (riskScore > 50) {
            if (income > 0 && loanAmount / income > 5) {
                flags.add("HIGH_LOAN_TO_INCOME_RATIO");
            }
            if (random.nextInt(100) > 80) {
                flags.add("MULTIPLE_APPLICATIONS_DETECTED");
            }
        }
        if (riskScore > 70) {
            flags.add("SUSPICIOUS_PATTERN_DETECTED");
        }
        
        return flags;
    }
    
    private void simulateApiLatency() {
        try { Thread.sleep(100 + (int)(Math.random() * 150)); } 
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

