package com.hdfcbank.standin.agents.tools;

import com.hdfcbank.standin.agents.tools.dapr.DaprEnabledTool;
import com.hdfcbank.standin.agents.tools.dapr.DaprServiceClient;
import com.hdfcbank.standin.agents.tools.dapr.ServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * PAN Card Validation Tool.
 * Validates Indian PAN (Permanent Account Number) format and structure.
 *
 * Uses Dapr Service Invocation to call the PAN validation microservice,
 * which in production connects to NSDL/Income Tax Department's API.
 */
@Component
public class PanValidationTool extends DaprEnabledTool {

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

    public PanValidationTool(
            DaprServiceClient daprClient,
            @Value("${tools.use-dapr:false}") boolean useDapr) {
        super(daprClient, useDapr);
    }

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
                    },
                    "applicant_name": {
                        "type": "string",
                        "description": "Name of the applicant for name matching"
                    }
                },
                "required": ["pan_number"]
            }
            """;
    }

    @Override
    protected String getServiceAppId() {
        return ServiceRegistry.PAN_VALIDATION_SERVICE;
    }

    @Override
    protected String getServiceMethod() {
        return ServiceRegistry.Methods.VALIDATE_PAN;
    }

    @Override
    protected ToolResult executeDapr(Map<String, Object> parameters) {
        String panNumber = (String) parameters.get("pan_number");

        if (panNumber == null || panNumber.isBlank()) {
            return ToolResult.failure("PAN number is required");
        }

        log.info("🔗 Calling PAN validation service via Dapr for: {}", maskPan(panNumber));
        return invokeDaprService(parameters);
    }

    @Override
    protected ToolResult executeLocal(Map<String, Object> parameters) {
        String panNumber = (String) parameters.get("pan_number");
        log.info("📋 Local PAN validation for: {}", maskPan(panNumber));

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

        // Simulate API call delay
        simulateApiCall();

        log.info("✅ PAN validation successful: {} ({})", maskPan(panNumber), holderType);

        return ToolResult.success(
            String.format("VALID: PAN %s is valid. Holder Type: %s", maskPan(panNumber), holderType),
            Map.of(
                "isValid", true,
                "pan", maskPan(panNumber),
                "holderType", holderType,
                "holderTypeCode", String.valueOf(holderTypeChar),
                "verifiedAt", java.time.Instant.now().toString(),
                "source", "LOCAL"
            )
        );
    }

    @Override
    protected String buildSummary(Map<String, Object> data) {
        Boolean isValid = (Boolean) data.get("isValid");
        String pan = (String) data.get("pan");

        if (Boolean.TRUE.equals(isValid)) {
            String holderType = (String) data.get("holderType");
            return String.format("VALID: PAN %s is valid. Holder Type: %s", pan, holderType);
        } else {
            String reason = (String) data.getOrDefault("reason", "Unknown");
            return String.format("INVALID: PAN %s - %s", pan, reason);
        }
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "INVALID";
        return pan.substring(0, 2) + "XXXX" + pan.substring(6, 8) + "XX";
    }

    private void simulateApiCall() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

