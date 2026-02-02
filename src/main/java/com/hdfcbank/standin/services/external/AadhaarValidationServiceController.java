package com.hdfcbank.standin.services.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Mock Aadhaar Validation Service.
 * 
 * In production, this would connect to UIDAI's Aadhaar verification API.
 * 
 * Dapr App ID: aadhaar-validation-service
 */
@RestController
@RequestMapping("/mock/aadhaar")
public class AadhaarValidationServiceController {
    
    private static final Logger log = LoggerFactory.getLogger(AadhaarValidationServiceController.class);
    
    // Verhoeff multiplication table
    private static final int[][] VERHOEFF_D = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
        {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
        {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
        {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
        {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
        {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
        {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
        {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
        {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };
    
    // Verhoeff permutation table
    private static final int[][] VERHOEFF_P = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
        {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
        {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
        {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
        {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
        {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
        {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "aadhaar-validation-service",
            "timestamp", Instant.now().toString()
        ));
    }
    
    /**
     * Validate Aadhaar number using Verhoeff algorithm.
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateAadhaar(@RequestBody Map<String, Object> request) {
        String aadhaarNumber = (String) request.get("aadhaar_number");
        String applicantName = (String) request.getOrDefault("applicant_name", "Unknown");
        
        log.info("📋 Aadhaar Validation Request for: {}", applicantName);
        
        if (aadhaarNumber == null || aadhaarNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Aadhaar number is required"
            ));
        }
        
        // Remove spaces and validate length
        String cleanAadhaar = aadhaarNumber.replaceAll("\\s+", "");
        
        if (cleanAadhaar.length() != 12 || !cleanAadhaar.matches("\\d{12}")) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "isValid", false,
                    "aadhaar", maskAadhaar(cleanAadhaar),
                    "reason", "Invalid format. Aadhaar must be 12 digits",
                    "verifiedAt", Instant.now().toString()
                )
            ));
        }
        
        // Validate using Verhoeff algorithm
        boolean isValidChecksum = validateVerhoeff(cleanAadhaar);
        
        if (!isValidChecksum) {
            log.warn("❌ Aadhaar checksum validation failed");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "isValid", false,
                    "aadhaar", maskAadhaar(cleanAadhaar),
                    "reason", "Invalid checksum (Verhoeff algorithm failed)",
                    "verifiedAt", Instant.now().toString()
                )
            ));
        }
        
        simulateApiLatency();
        
        log.info("✅ Aadhaar validated successfully: {}", maskAadhaar(cleanAadhaar));
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "isValid", true,
                "aadhaar", maskAadhaar(cleanAadhaar),
                "status", "ACTIVE",
                "state", "Maharashtra", // Simulated
                "gender", "M",
                "ageRange", "25-35",
                "verifiedAt", Instant.now().toString(),
                "source", "UIDAI"
            )
        ));
    }
    
    private boolean validateVerhoeff(String num) {
        int c = 0;
        int[] myArray = stringToReversedIntArray(num);
        for (int i = 0; i < myArray.length; i++) {
            c = VERHOEFF_D[c][VERHOEFF_P[(i % 8)][myArray[i]]];
        }
        return c == 0;
    }
    
    private int[] stringToReversedIntArray(String num) {
        int[] myArray = new int[num.length()];
        for (int i = 0; i < num.length(); i++) {
            myArray[i] = Character.getNumericValue(num.charAt(num.length() - i - 1));
        }
        return myArray;
    }
    
    private String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 12) return "INVALID";
        return "XXXX-XXXX-" + aadhaar.substring(8);
    }
    
    private void simulateApiLatency() {
        try { Thread.sleep(150 + (int)(Math.random() * 250)); } 
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

