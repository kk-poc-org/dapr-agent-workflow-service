package com.hdfcbank.standin.agents.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fraud Detection Tool.
 * Checks for fraud indicators and suspicious patterns.
 * 
 * In production, this would integrate with fraud detection systems.
 */
@Component
public class FraudDetectionTool implements AgentTool {
    
    private static final Logger log = LoggerFactory.getLogger(FraudDetectionTool.class);
    
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
    public ToolResult execute(Map<String, Object> parameters) {
        String pan = (String) parameters.get("pan_number");
        double income = ((Number) parameters.getOrDefault("annual_income", 0)).doubleValue();
        double loanAmount = ((Number) parameters.getOrDefault("loan_amount", 0)).doubleValue();
        String employer = (String) parameters.getOrDefault("employer_name", "");
        
        log.info("🔧 Tool [check_fraud_indicators] executing...");
        
        List<String> alerts = new ArrayList<>();
        int riskScore = 0;
        
        // Check income to loan ratio
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
        
        // Check for known fraud patterns (simulated)
        if (pan != null && pan.startsWith("AAAAA")) {
            alerts.add("ALERT: PAN pattern matches known synthetic identity pattern");
            riskScore += 50;
        }
        
        // Check employer (simulated shell company detection)
        if (employer != null && (employer.toLowerCase().contains("xyz") || 
            employer.toLowerCase().contains("abc enterprises"))) {
            alerts.add("ALERT: Employer name matches shell company patterns");
            riskScore += 30;
        }
        
        // Simulate check delay
        simulateApiCall();
        
        String riskLevel = riskScore >= 50 ? "HIGH" : riskScore >= 20 ? "MEDIUM" : "LOW";
        String summary = alerts.isEmpty() 
            ? "No fraud indicators detected. Risk Level: LOW" 
            : String.format("Risk Level: %s. Alerts: %s", riskLevel, String.join("; ", alerts));
        
        log.info("Fraud check complete: Risk={}, Alerts={}", riskLevel, alerts.size());
        
        return ToolResult.success(summary, Map.of(
            "riskLevel", riskLevel,
            "riskScore", riskScore,
            "alerts", alerts,
            "incomeToLoanRatio", incomeToLoanRatio,
            "checkedAt", java.time.Instant.now().toString()
        ));
    }
    
    private void simulateApiCall() {
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

