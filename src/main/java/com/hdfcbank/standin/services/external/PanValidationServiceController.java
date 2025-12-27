package com.hdfcbank.standin.services.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mock PAN Validation Service.
 * 
 * In production, this would be a separate microservice that connects to
 * NSDL/Income Tax Department's PAN verification API.
 * 
 * Dapr App ID: pan-validation-service
 * 
 * This mock service can be:
 * 1. Run as a separate Dapr app for testing
 * 2. Replaced with a real implementation connecting to NSDL
 * 3. Deployed independently and scaled separately
 */
@RestController
@RequestMapping("/mock/pan")
public class PanValidationServiceController {
    
    private static final Logger log = LoggerFactory.getLogger(PanValidationServiceController.class);
    
    // PAN format: 5 letters + 4 digits + 1 letter
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    
    private static final Map<Character, String> HOLDER_TYPES = Map.of(
        'P', "Individual",
        'C', "Company",
        'H', "Hindu Undivided Family",
        'A', "Association of Persons",
        'B', "Body of Individuals",
        'G', "Government",
        'J', "Artificial Juridical Person",
        'L', "Local Authority",
        'F', "Firm/Partnership",
        'T', "Trust"
    );
    
    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "pan-validation-service",
            "timestamp", Instant.now().toString()
        ));
    }
    
    /**
     * Validate PAN number.
     * 
     * POST /validate
     * Body: { "pan_number": "ABCPM1234K", "applicant_name": "Rahul Sharma" }
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validatePan(@RequestBody Map<String, Object> request) {
        String panNumber = (String) request.get("pan_number");
        String applicantName = (String) request.getOrDefault("applicant_name", "Unknown");
        
        log.info("📋 PAN Validation Request: {} for {}", maskPan(panNumber), applicantName);
        
        if (panNumber == null || panNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "PAN number is required"
            ));
        }
        
        panNumber = panNumber.toUpperCase().trim();
        
        // Validate format
        if (!PAN_PATTERN.matcher(panNumber).matches()) {
            log.warn("❌ PAN validation failed: Invalid format for {}", maskPan(panNumber));
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                    "isValid", false,
                    "pan", maskPan(panNumber),
                    "reason", "Invalid PAN format. Expected: 5 letters + 4 digits + 1 letter",
                    "verifiedAt", Instant.now().toString()
                )
            ));
        }
        
        // Extract holder type
        char holderTypeChar = panNumber.charAt(3);
        String holderType = HOLDER_TYPES.getOrDefault(holderTypeChar, "Unknown");
        
        // Simulate NSDL API call delay
        simulateApiLatency();
        
        // In production: Call NSDL API here
        // Response would include: name match, status (active/inactive), etc.
        
        log.info("✅ PAN validated successfully: {} ({})", maskPan(panNumber), holderType);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "isValid", true,
                "pan", maskPan(panNumber),
                "holderType", holderType,
                "holderTypeCode", String.valueOf(holderTypeChar),
                "status", "ACTIVE",
                "nameOnPan", applicantName.toUpperCase(),
                "nameMatchScore", 95, // Simulated name match
                "verifiedAt", Instant.now().toString(),
                "source", "NSDL"
            )
        ));
    }
    
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "INVALID";
        return pan.substring(0, 2) + "XXXX" + pan.substring(6, 8) + "XX";
    }
    
    private void simulateApiLatency() {
        try {
            Thread.sleep(100 + (int)(Math.random() * 200)); // 100-300ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

