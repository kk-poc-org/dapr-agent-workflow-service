package com.hdfcbank.standin.agents.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * PAN Card Validation Tool.
 * Validates Indian PAN (Permanent Account Number) format and structure.
 * 
 * In production, this would call the Income Tax Department's PAN verification API.
 */
@Component
public class PanValidationTool implements AgentTool {
    
    private static final Logger log = LoggerFactory.getLogger(PanValidationTool.class);
    
    // PAN format: 5 letters + 4 digits + 1 letter (e.g., ABCDE1234F)
    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    
    // 4th character indicates holder type
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
    
    @Override
    public String getName() {
        return "validate_pan";
    }
    
    @Override
    public String getDescription() {
        return "Validates an Indian PAN (Permanent Account Number). " +
               "Checks format, structure, and returns holder type. " +
               "Use this tool when you need to verify if a PAN number is valid.";
    }
    
    @Override
    public String getParameterSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "pan_number": {
                        "type": "string",
                        "description": "The PAN number to validate (10 characters)"
                    }
                },
                "required": ["pan_number"]
            }
            """;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        String panNumber = (String) parameters.get("pan_number");
        log.info("🔧 Tool [validate_pan] executing for PAN: {}", maskPan(panNumber));
        
        if (panNumber == null || panNumber.isBlank()) {
            return ToolResult.failure("PAN number is required");
        }
        
        // Normalize to uppercase
        panNumber = panNumber.toUpperCase().trim();
        
        // Validate format
        if (!PAN_PATTERN.matcher(panNumber).matches()) {
            log.warn("PAN validation failed: Invalid format");
            return ToolResult.success(
                "INVALID: PAN format is incorrect. Expected format: 5 letters + 4 digits + 1 letter",
                Map.of(
                    "isValid", false,
                    "pan", maskPan(panNumber),
                    "reason", "Invalid format"
                )
            );
        }
        
        // Extract holder type from 4th character
        char holderTypeChar = panNumber.charAt(3);
        String holderType = HOLDER_TYPES.getOrDefault(holderTypeChar, "Unknown");
        
        // Simulate API call delay (in production, this would be a real API call)
        simulateApiCall();
        
        log.info("PAN validation successful: {} ({})", maskPan(panNumber), holderType);
        
        return ToolResult.success(
            String.format("VALID: PAN %s is valid. Holder Type: %s", maskPan(panNumber), holderType),
            Map.of(
                "isValid", true,
                "pan", maskPan(panNumber),
                "holderType", holderType,
                "holderTypeCode", String.valueOf(holderTypeChar),
                "verifiedAt", java.time.Instant.now().toString()
            )
        );
    }
    
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "INVALID";
        return pan.substring(0, 2) + "XXXX" + pan.substring(6, 8) + "XX";
    }
    
    private void simulateApiCall() {
        try {
            Thread.sleep(100); // Simulate network latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

