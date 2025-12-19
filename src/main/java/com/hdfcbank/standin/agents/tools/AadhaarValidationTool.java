package com.hdfcbank.standin.agents.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Aadhaar Number Validation Tool.
 * Validates Indian Aadhaar number format using Verhoeff algorithm checksum.
 * 
 * In production, this would call UIDAI's Aadhaar verification API.
 */
@Component
public class AadhaarValidationTool implements AgentTool {
    
    private static final Logger log = LoggerFactory.getLogger(AadhaarValidationTool.class);
    
    // Aadhaar: 12 digits, cannot start with 0 or 1
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("^[2-9][0-9]{11}$");
    
    // Verhoeff algorithm tables for checksum validation
    private static final int[][] MULTIPLICATION_TABLE = {
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
    
    private static final int[][] PERMUTATION_TABLE = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
        {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
        {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
        {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
        {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
        {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
        {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
        {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };
    
    @Override
    public String getName() {
        return "validate_aadhaar";
    }
    
    @Override
    public String getDescription() {
        return "Validates an Indian Aadhaar number using Verhoeff checksum algorithm. " +
               "Checks format and mathematical validity. " +
               "Use this tool when you need to verify if an Aadhaar number is valid.";
    }
    
    @Override
    public String getParameterSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "aadhaar_number": {
                        "type": "string",
                        "description": "The 12-digit Aadhaar number to validate"
                    }
                },
                "required": ["aadhaar_number"]
            }
            """;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        String aadhaar = (String) parameters.get("aadhaar_number");
        log.info("🔧 Tool [validate_aadhaar] executing for Aadhaar: {}", maskAadhaar(aadhaar));
        
        if (aadhaar == null || aadhaar.isBlank()) {
            return ToolResult.failure("Aadhaar number is required");
        }
        
        // Remove any spaces or dashes
        aadhaar = aadhaar.replaceAll("[\\s-]", "");
        
        // Validate format
        if (!AADHAAR_PATTERN.matcher(aadhaar).matches()) {
            log.warn("Aadhaar validation failed: Invalid format");
            return ToolResult.success(
                "INVALID: Aadhaar format is incorrect. Must be 12 digits, cannot start with 0 or 1",
                Map.of("isValid", false, "aadhaar", maskAadhaar(aadhaar), "reason", "Invalid format")
            );
        }
        
        // Validate Verhoeff checksum
        if (!validateVerhoeff(aadhaar)) {
            log.warn("Aadhaar validation failed: Invalid checksum");
            return ToolResult.success(
                "INVALID: Aadhaar checksum verification failed",
                Map.of("isValid", false, "aadhaar", maskAadhaar(aadhaar), "reason", "Invalid checksum")
            );
        }
        
        simulateApiCall();
        log.info("Aadhaar validation successful: {}", maskAadhaar(aadhaar));
        
        return ToolResult.success(
            String.format("VALID: Aadhaar %s is valid and verified", maskAadhaar(aadhaar)),
            Map.of(
                "isValid", true,
                "aadhaar", maskAadhaar(aadhaar),
                "verifiedAt", java.time.Instant.now().toString()
            )
        );
    }
    
    private boolean validateVerhoeff(String num) {
        int c = 0;
        int len = num.length();
        for (int i = 0; i < len; i++) {
            int digit = Character.getNumericValue(num.charAt(len - i - 1));
            c = MULTIPLICATION_TABLE[c][PERMUTATION_TABLE[i % 8][digit]];
        }
        return c == 0;
    }
    
    private String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 12) return "INVALID";
        return "XXXX-XXXX-" + aadhaar.substring(8);
    }
    
    private void simulateApiCall() {
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

