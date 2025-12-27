package com.hdfcbank.standin.agents.tools;

import com.hdfcbank.standin.agents.tools.dapr.DaprEnabledTool;
import com.hdfcbank.standin.agents.tools.dapr.DaprServiceClient;
import com.hdfcbank.standin.agents.tools.dapr.ServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fraud Detection Tool.
 * Checks for fraud indicators and suspicious patterns.
 *
 * Uses Dapr Service Invocation to call the fraud detection microservice,
 * which in production connects to fraud detection and AML systems.
 */
@Component
public class FraudDetectionTool extends DaprEnabledTool {

    public FraudDetectionTool(
            DaprServiceClient daprClient,
            @Value("${tools.use-dapr:false}") boolean useDapr) {
        super(daprClient, useDapr);
    }

    @Override
    public String getName() {
        return "check_fraud_indicators";
    }

    @Override
    public String getDescription() {
        return "Checks for fraud indicators and suspicious patterns in the loan application. " +
               "Analyzes income vs loan amount ratio, document authenticity signals, and known fraud patterns. " +
               "Use this when you want to assess fraud risk for an application.";
    }

    @Override
    public String getParameterSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "pan_number": {"type": "string", "description": "Applicant's PAN"},
                    "aadhaar_number": {"type": "string", "description": "Applicant's Aadhaar"},
                    "annual_income": {"type": "number", "description": "Declared annual income"},
                    "loan_amount": {"type": "number", "description": "Requested loan amount"},
                    "employer_name": {"type": "string", "description": "Employer name"}
                },
                "required": ["pan_number", "annual_income", "loan_amount"]
            }
            """;
    }

    @Override
    protected String getServiceAppId() {
        return ServiceRegistry.FRAUD_DETECTION_SERVICE;
    }

    @Override
    protected String getServiceMethod() {
        return ServiceRegistry.Methods.CHECK_FRAUD;
    }

    @Override
    protected ToolResult executeDapr(Map<String, Object> parameters) {
        log.info("🔗 Calling fraud detection service via Dapr");
        return invokeDaprService(parameters);
    }

    @Override
    protected ToolResult executeLocal(Map<String, Object> parameters) {
        String pan = (String) parameters.get("pan_number");
        double income = parseDouble(parameters.get("annual_income"));
        double loanAmount = parseDouble(parameters.get("loan_amount"));
        String employer = (String) parameters.getOrDefault("employer_name", "");

        log.info("📋 Local fraud check executing...");

        List<String> alerts = new ArrayList<>();
        int riskScore = 0;

        double incomeToLoanRatio = income > 0 ? loanAmount / income : 999;
        if (incomeToLoanRatio > 10) {
            alerts.add("HIGH RISK: Loan amount is " + String.format("%.1f", incomeToLoanRatio) +
                      "x annual income");
            riskScore += 40;
        } else if (incomeToLoanRatio > 6) {
            alerts.add("MEDIUM RISK: High loan-to-income ratio (" +
                      String.format("%.1f", incomeToLoanRatio) + "x)");
            riskScore += 20;
        }

        if (pan != null && pan.startsWith("AAAAA")) {
            alerts.add("ALERT: PAN pattern matches known synthetic identity pattern");
            riskScore += 50;
        }

        if (employer != null && (employer.toLowerCase().contains("xyz") ||
            employer.toLowerCase().contains("abc enterprises"))) {
            alerts.add("ALERT: Employer name matches shell company patterns");
            riskScore += 30;
        }

        simulateApiCall();

        String riskLevel = riskScore >= 50 ? "HIGH" : riskScore >= 20 ? "MEDIUM" : "LOW";
        String summary = alerts.isEmpty()
            ? "No fraud indicators detected. Risk Level: LOW"
            : String.format("Risk Level: %s. Alerts: %s", riskLevel, String.join("; ", alerts));

        log.info("✅ Fraud check complete: Risk={}, Alerts={}", riskLevel, alerts.size());

        return ToolResult.success(summary, Map.of(
            "riskLevel", riskLevel,
            "riskScore", riskScore,
            "alerts", alerts,
            "incomeToLoanRatio", incomeToLoanRatio,
            "checkedAt", java.time.Instant.now().toString(),
            "source", "LOCAL"
        ));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String buildSummary(Map<String, Object> data) {
        String riskLevel = (String) data.get("riskLevel");
        int riskScore = ((Number) data.get("riskScore")).intValue();
        List<String> flags = (List<String>) data.getOrDefault("fraudFlags", List.of());

        if (flags.isEmpty()) {
            return String.format("No fraud indicators detected. Risk Level: %s (Score: %d)",
                    riskLevel, riskScore);
        }
        return String.format("Risk Level: %s (Score: %d). Flags: %s",
                riskLevel, riskScore, String.join("; ", flags));
    }

    private void simulateApiCall() {
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private double parseDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try { return Double.parseDouble((String) obj); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}

