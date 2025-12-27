package com.hdfcbank.standin.agents.tools;

import com.hdfcbank.standin.agents.tools.dapr.DaprEnabledTool;
import com.hdfcbank.standin.agents.tools.dapr.DaprServiceClient;
import com.hdfcbank.standin.agents.tools.dapr.ServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Credit Bureau Tool.
 * Fetches credit score and history from credit bureaus (CIBIL, Experian, etc.)
 *
 * Uses Dapr Service Invocation to call the credit bureau microservice,
 * which in production connects to CIBIL/Experian APIs.
 */
@Component
public class CreditBureauTool extends DaprEnabledTool {

    public CreditBureauTool(
            DaprServiceClient daprClient,
            @Value("${tools.use-dapr:false}") boolean useDapr) {
        super(daprClient, useDapr);
    }

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
                        "description": "Credit score declared by applicant (optional)"
                    }
                },
                "required": ["pan_number"]
            }
            """;
    }

    @Override
    protected String getServiceAppId() {
        return ServiceRegistry.CREDIT_BUREAU_SERVICE;
    }

    @Override
    protected String getServiceMethod() {
        return ServiceRegistry.Methods.FETCH_REPORT;
    }

    @Override
    protected ToolResult executeDapr(Map<String, Object> parameters) {
        String pan = (String) parameters.get("pan_number");

        if (pan == null || pan.isBlank()) {
            return ToolResult.failure("PAN number is required to fetch credit report");
        }

        log.info("🔗 Calling credit bureau service via Dapr");
        return invokeDaprService(parameters);
    }

    @Override
    protected ToolResult executeLocal(Map<String, Object> parameters) {
        String pan = (String) parameters.get("pan_number");
        String name = (String) parameters.getOrDefault("applicant_name", "Unknown");
        Object declaredScoreObj = parameters.get("declared_score");
        int declaredScore = 0;
        if (declaredScoreObj instanceof Number) {
            declaredScore = ((Number) declaredScoreObj).intValue();
        } else if (declaredScoreObj instanceof String) {
            try {
                declaredScore = Integer.parseInt((String) declaredScoreObj);
            } catch (NumberFormatException e) {
                declaredScore = 0;
            }
        }

        log.info("📋 Local credit report fetch for: {} (declared: {})", name, declaredScore);

        if (pan == null || pan.isBlank()) {
            return ToolResult.failure("PAN number is required to fetch credit report");
        }

        simulateApiCall();

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

        log.info("✅ Credit report fetched: Score={}, Rating={}", creditScore, rating);

        return ToolResult.success(summary, Map.of(
            "creditScore", creditScore,
            "rating", rating,
            "activeLoans", activeLoans,
            "totalOutstanding", totalOutstanding,
            "missedPayments12Months", missedPayments,
            "creditHistoryYears", creditAge,
            "bureau", "CIBIL",
            "fetchedAt", java.time.Instant.now().toString(),
            "source", "LOCAL"
        ));
    }

    @Override
    protected String buildSummary(Map<String, Object> data) {
        int creditScore = ((Number) data.get("creditScore")).intValue();
        String rating = (String) data.get("rating");
        int activeLoans = ((Number) data.get("activeLoans")).intValue();
        double totalOutstanding = ((Number) data.get("totalOutstanding")).doubleValue();
        int missedPayments = ((Number) data.get("missedPayments12Months")).intValue();
        int creditAge = ((Number) data.get("creditHistoryYears")).intValue();

        return String.format(
            "Credit Score: %d (%s). Active Loans: %d. Outstanding: ₹%.0f. " +
            "Missed Payments (12m): %d. Credit History: %d years.",
            creditScore, rating, activeLoans, totalOutstanding, missedPayments, creditAge
        );
    }

    private int generateCreditScore(String pan, int declaredScore) {
        if (declaredScore > 0) {
            int hash = Math.abs(pan.hashCode());
            int variance = (hash % 41) - 20;
            int score = declaredScore + variance;
            return Math.max(300, Math.min(900, score));
        }
        int hash = Math.abs(pan.hashCode());
        return 550 + (hash % 300);
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

