package com.hdfcbank.standin.agents.tools;

import com.hdfcbank.standin.agents.tools.dapr.DaprEnabledTool;
import com.hdfcbank.standin.agents.tools.dapr.DaprServiceClient;
import com.hdfcbank.standin.agents.tools.dapr.ServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Income Verification Tool.
 * Verifies income through ITR data and employer records.
 *
 * Uses Dapr Service Invocation to call the income verification microservice,
 * which in production connects to Income Tax Department APIs.
 */
@Component
public class IncomeVerificationTool extends DaprEnabledTool {

    public IncomeVerificationTool(
            DaprServiceClient daprClient,
            @Value("${tools.use-dapr:false}") boolean useDapr) {
        super(daprClient, useDapr);
    }

    @Override
    public String getName() {
        return "verify_income";
    }

    @Override
    public String getDescription() {
        return "Verifies declared income against ITR (Income Tax Returns) data and employer records. " +
               "Returns verified income, variance from declared, and ITR filing history. " +
               "Use this when you need to verify the income declared by an applicant.";
    }

    @Override
    public String getParameterSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "pan_number": {"type": "string", "description": "Applicant's PAN"},
                    "declared_income": {"type": "number", "description": "Declared annual income"},
                    "employer_name": {"type": "string", "description": "Employer name"},
                    "employment_type": {"type": "string", "description": "SALARIED/SELF_EMPLOYED/BUSINESS"}
                },
                "required": ["pan_number", "declared_income"]
            }
            """;
    }

    @Override
    protected String getServiceAppId() {
        return ServiceRegistry.INCOME_VERIFICATION_SERVICE;
    }

    @Override
    protected String getServiceMethod() {
        return ServiceRegistry.Methods.VERIFY_INCOME;
    }

    @Override
    protected ToolResult executeDapr(Map<String, Object> parameters) {
        String pan = (String) parameters.get("pan_number");

        if (pan == null || pan.isBlank()) {
            return ToolResult.failure("PAN number is required for income verification");
        }

        log.info("🔗 Calling income verification service via Dapr");
        return invokeDaprService(parameters);
    }

    @Override
    protected ToolResult executeLocal(Map<String, Object> parameters) {
        String pan = (String) parameters.get("pan_number");
        Object incomeObj = parameters.get("declared_income");
        double declaredIncome = 0;
        if (incomeObj instanceof Number) {
            declaredIncome = ((Number) incomeObj).doubleValue();
        } else if (incomeObj instanceof String) {
            try {
                declaredIncome = Double.parseDouble((String) incomeObj);
            } catch (NumberFormatException e) {
                declaredIncome = 0;
            }
        }
        String employer = (String) parameters.getOrDefault("employer_name", "Unknown");
        String empType = (String) parameters.getOrDefault("employment_type", "SALARIED");

        log.info("📋 Local income verification for PAN: {}", maskPan(pan));

        simulateApiCall();

        int panHash = Math.abs(pan.hashCode());
        double maxVariance = "SALARIED".equals(empType) ? 0.05 : 0.10;
        double variance = ((panHash % 100) / 100.0) * maxVariance;
        double verifiedIncome = declaredIncome * (1 + variance);
        double variancePercent = variance * 100;

        int itrYearsFiled = "SALARIED".equals(empType) ? 4 + (panHash % 3) : 2 + (panHash % 4);
        boolean itrConsistent = variancePercent < 8;
        boolean employerVerified = "SALARIED".equals(empType) && employer != null &&
                                   !employer.equalsIgnoreCase("Unknown");

        String summary = String.format(
            "Verified Income: ₹%.0f (Variance: %.1f%%). ITR Filed: %d years. " +
            "Consistency: %s. Employer Verified: %s",
            verifiedIncome, variancePercent, itrYearsFiled,
            itrConsistent ? "CONSISTENT" : "INCONSISTENT",
            employerVerified ? "YES" : "NO"
        );

        boolean isVerified = variancePercent < 15;
        log.info("✅ Income verification: Declared={}, Verified={}, Variance={}%",
                declaredIncome, verifiedIncome, variancePercent);

        return ToolResult.success(summary, Map.of(
            "declaredIncome", declaredIncome,
            "verifiedIncome", verifiedIncome,
            "variancePercent", variancePercent,
            "isVerified", isVerified,
            "itrYearsFiled", itrYearsFiled,
            "itrConsistent", itrConsistent,
            "employerVerified", employerVerified,
            "verifiedAt", java.time.Instant.now().toString(),
            "source", "LOCAL"
        ));
    }

    @Override
    protected String buildSummary(Map<String, Object> data) {
        double verifiedIncome = ((Number) data.get("itrIncome")).doubleValue();
        double variancePercent = ((Number) data.get("variancePercent")).doubleValue();
        String status = (String) data.get("verificationStatus");

        return String.format("Income Verification: %s. ITR Income: ₹%.0f. Variance: %.1f%%",
                status, verifiedIncome, variancePercent);
    }

    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "INVALID";
        return pan.substring(0, 2) + "XXXX" + pan.substring(6, 8) + "XX";
    }

    private void simulateApiCall() {
        try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

