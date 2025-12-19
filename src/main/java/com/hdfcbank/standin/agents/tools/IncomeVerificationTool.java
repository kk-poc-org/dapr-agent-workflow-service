package com.hdfcbank.standin.agents.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Income Verification Tool.
 * Verifies income through ITR data and employer records.
 * 
 * In production, this would integrate with Income Tax Department and employer databases.
 */
@Component
public class IncomeVerificationTool implements AgentTool {
    
    private static final Logger log = LoggerFactory.getLogger(IncomeVerificationTool.class);
    
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
    public ToolResult execute(Map<String, Object> parameters) {
        String pan = (String) parameters.get("pan_number");
        double declaredIncome = ((Number) parameters.get("declared_income")).doubleValue();
        String employer = (String) parameters.getOrDefault("employer_name", "Unknown");
        String empType = (String) parameters.getOrDefault("employment_type", "SALARIED");

        log.info("🔧 Tool [verify_income] executing for PAN: {}", maskPan(pan));

        simulateApiCall();

        // Simulate ITR verification (in production, this would call IT Dept API)
        // Use a deterministic but reasonable variance based on PAN
        int panHash = Math.abs(pan.hashCode());

        // For salaried employees with known employers, variance is typically small (0-5%)
        // For self-employed, variance can be higher
        double maxVariance = "SALARIED".equals(empType) ? 0.05 : 0.10;
        double variance = ((panHash % 100) / 100.0) * maxVariance; // 0% to maxVariance
        double verifiedIncome = declaredIncome * (1 + variance);
        double variancePercent = variance * 100;

        // ITR filing history - salaried with good employers typically have consistent filing
        int itrYearsFiled = "SALARIED".equals(empType) ? 4 + (panHash % 3) : 2 + (panHash % 4);
        boolean itrConsistent = variancePercent < 8; // Consistent if variance < 8%

        // Employer verification for salaried
        boolean employerVerified = "SALARIED".equals(empType) && employer != null &&
                                   !employer.equalsIgnoreCase("Unknown");

        String summary = String.format(
            "Verified Income: ₹%.0f (Variance: %.1f%%). ITR Filed: %d years. " +
            "Consistency: %s. Employer Verified: %s",
            verifiedIncome, variancePercent, itrYearsFiled,
            itrConsistent ? "CONSISTENT" : "INCONSISTENT",
            employerVerified ? "YES" : "NO"
        );

        boolean isVerified = variancePercent < 15; // Verified if variance < 15%
        log.info("Income verification: Declared={}, Verified={}, Variance={}%, Match={}",
                declaredIncome, verifiedIncome, variancePercent, isVerified);

        return ToolResult.success(summary, Map.of(
            "declaredIncome", declaredIncome,
            "verifiedIncome", verifiedIncome,
            "variancePercent", variancePercent,
            "isVerified", isVerified,
            "itrYearsFiled", itrYearsFiled,
            "itrConsistent", itrConsistent,
            "employerVerified", employerVerified,
            "verifiedAt", java.time.Instant.now().toString()
        ));
    }
    
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "INVALID";
        return pan.substring(0, 2) + "XXXX" + pan.substring(6, 8) + "XX";
    }
    
    private void simulateApiCall() {
        try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

