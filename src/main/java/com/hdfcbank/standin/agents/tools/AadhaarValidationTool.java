package com.hdfcbank.standin.agents.tools;

import com.hdfcbank.standin.agents.tools.dapr.DaprEnabledTool;
import com.hdfcbank.standin.agents.tools.dapr.DaprServiceClient;
import com.hdfcbank.standin.agents.tools.dapr.ServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Aadhaar Number Validation Tool.
 * Validates Indian Aadhaar number format using Verhoeff algorithm checksum.
 *
 * Uses Dapr Service Invocation to call the Aadhaar validation microservice,
 * which in production connects to UIDAI's API.
 */
@Component
public class AadhaarValidationTool extends DaprEnabledTool {

    private static final Pattern AADHAAR_PATTERN = Pattern.compile("^[2-9][0-9]{11}$");

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

    public AadhaarValidationTool(
            DaprServiceClient daprClient,
            @Value("${tools.use-dapr:false}") boolean useDapr) {
        super(daprClient, useDapr);
    }

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
                    },
                    "applicant_name": {
                        "type": "string",
                        "description": "Name of the applicant for verification"
                    }
                },
                "required": ["aadhaar_number"]
            }
            """;
    }

    @Override
    protected String getServiceAppId() {
        return ServiceRegistry.AADHAAR_VALIDATION_SERVICE;
    }

    @Override
    protected String getServiceMethod() {
        return ServiceRegistry.Methods.VALIDATE_AADHAAR;
    }

    @Override
    protected ToolResult executeDapr(Map<String, Object> parameters) {
        String aadhaar = (String) parameters.get("aadhaar_number");

        if (aadhaar == null || aadhaar.isBlank()) {
            return ToolResult.failure("Aadhaar number is required");
        }

        log.info("🔗 Calling Aadhaar validation service via Dapr for: {}", maskAadhaar(aadhaar));
        return invokeDaprService(parameters);
    }

    @Override
    protected ToolResult executeLocal(Map<String, Object> parameters) {
        String aadhaar = (String) parameters.get("aadhaar_number");
        log.info("📋 Local Aadhaar validation for: {}", maskAadhaar(aadhaar));

        if (aadhaar == null || aadhaar.isBlank()) {
            return ToolResult.failure("Aadhaar number is required");
        }

        aadhaar = aadhaar.replaceAll("[\\s-]", "");

        if (!AADHAAR_PATTERN.matcher(aadhaar).matches()) {
            log.warn("Aadhaar validation failed: Invalid format");
            return ToolResult.success(
                "INVALID: Aadhaar format is incorrect. Must be 12 digits, cannot start with 0 or 1",
                Map.of("isValid", false, "aadhaar", maskAadhaar(aadhaar), "reason", "Invalid format")
            );
        }

        if (!validateVerhoeff(aadhaar)) {
            log.warn("Aadhaar validation failed: Invalid checksum");
            return ToolResult.success(
                "INVALID: Aadhaar checksum verification failed",
                Map.of("isValid", false, "aadhaar", maskAadhaar(aadhaar), "reason", "Invalid checksum")
            );
        }

        simulateApiCall();
        log.info("✅ Aadhaar validation successful: {}", maskAadhaar(aadhaar));

        return ToolResult.success(
            String.format("VALID: Aadhaar %s is valid and verified", maskAadhaar(aadhaar)),
            Map.of(
                "isValid", true,
                "aadhaar", maskAadhaar(aadhaar),
                "verifiedAt", java.time.Instant.now().toString(),
                "source", "LOCAL"
            )
        );
    }

    @Override
    protected String buildSummary(Map<String, Object> data) {
        Boolean isValid = (Boolean) data.get("isValid");
        String aadhaar = (String) data.get("aadhaar");

        if (Boolean.TRUE.equals(isValid)) {
            return String.format("VALID: Aadhaar %s is valid and verified", aadhaar);
        } else {
            String reason = (String) data.getOrDefault("reason", "Unknown");
            return String.format("INVALID: Aadhaar %s - %s", aadhaar, reason);
        }
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

