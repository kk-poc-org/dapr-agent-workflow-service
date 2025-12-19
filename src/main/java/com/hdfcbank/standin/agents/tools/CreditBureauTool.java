package com.hdfcbank.standin.agents.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Credit Bureau Tool.
 * Fetches credit score and history from credit bureaus (CIBIL, Experian, etc.)
 * 
 * In production, this would call actual credit bureau APIs.
 */
@Component
public class CreditBureauTool implements AgentTool {
    
    private static final Logger log = LoggerFactory.getLogger(CreditBureauTool.class);
    
    @Override
    public String getName() {
        return "fetch_credit_report";
    }
    
    @Override
    public String getDescription() {
        return "Fetches credit score and credit history from credit bureaus (CIBIL/Experian). " +
               "Returns credit score, rating, payment history, and existing loans. " +
               "Use this when you need to assess the creditworthiness of an applicant.";
    }
    
    @Override
    public String getParameterSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "pan_number": {
                        "type": "string",
                        "description": "PAN number of the applicant"
                    },
                    "applicant_name": {
                        "type": "string",
                        "description": "Name of the applicant"
                    },
                    "declared_score": {
                        "type": "integer",
                        "description": "Credit score declared by applicant (optional, used as baseline)"
                    }
                },
                "required": ["pan_number"]
            }
            """;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        String pan = (String) parameters.get("pan_number");
        String name = (String) parameters.getOrDefault("applicant_name", "Unknown");
        Object declaredScoreObj = parameters.get("declared_score");
        int declaredScore = declaredScoreObj != null ? ((Number) declaredScoreObj).intValue() : 0;

        log.info("🔧 Tool [fetch_credit_report] executing for: {} (declared: {})", name, declaredScore);

        if (pan == null || pan.isBlank()) {
            return ToolResult.failure("PAN number is required to fetch credit report");
        }

        simulateApiCall();

        // Use declared score as baseline with small variance (simulating bureau verification)
        int creditScore = generateCreditScore(pan, declaredScore);
        String rating = getCreditRating(creditScore);
        int activeLoans = new Random(pan.hashCode()).nextInt(5);
        double totalOutstanding = activeLoans * (100000 + new Random(pan.hashCode() + 1).nextInt(500000));
        int missedPayments = creditScore < 650 ? new Random(pan.hashCode() + 2).nextInt(5) : 0;
        int creditAge = 1 + new Random(pan.hashCode() + 3).nextInt(15);

        String summary = String.format(
            "Credit Score: %d (%s). Active Loans: %d. Outstanding: ₹%.0f. " +
            "Missed Payments (12m): %d. Credit History: %d years.",
            creditScore, rating, activeLoans, totalOutstanding, missedPayments, creditAge
        );

        log.info("Credit report fetched: Score={}, Rating={}", creditScore, rating);

        return ToolResult.success(summary, Map.of(
            "creditScore", creditScore,
            "rating", rating,
            "activeLoans", activeLoans,
            "totalOutstanding", totalOutstanding,
            "missedPayments12Months", missedPayments,
            "creditHistoryYears", creditAge,
            "bureau", "CIBIL",
            "fetchedAt", java.time.Instant.now().toString()
        ));
    }

    private int generateCreditScore(String pan, int declaredScore) {
        // If declared score provided, use it with small variance (±20 points)
        if (declaredScore > 0) {
            int hash = Math.abs(pan.hashCode());
            int variance = (hash % 41) - 20; // -20 to +20
            int score = declaredScore + variance;
            return Math.max(300, Math.min(900, score)); // Clamp to valid range
        }
        // Fallback: generate based on PAN hash
        int hash = Math.abs(pan.hashCode());
        return 550 + (hash % 300); // Score between 550-850
    }
    
    private String getCreditRating(int score) {
        if (score >= 750) return "EXCELLENT";
        if (score >= 700) return "GOOD";
        if (score >= 650) return "FAIR";
        if (score >= 550) return "POOR";
        return "VERY_POOR";
    }
    
    private void simulateApiCall() {
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

