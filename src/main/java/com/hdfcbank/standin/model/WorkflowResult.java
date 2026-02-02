package com.hdfcbank.standin.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Final result of the Home Loan Workflow.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record WorkflowResult(
    String applicationId,
    String finalDecision,  // APPROVED, REJECTED, MANUAL_REVIEW
    double approvedAmount,
    double interestRate,
    double monthlyEmi,
    int overallConfidence,
    String summary,
    List<AgentResult> agentResults,
    Instant processedAt
) implements Serializable {
    
    public static WorkflowResult approved(String applicationId, double amount, double rate, 
                                          double emi, int confidence, String summary,
                                          List<AgentResult> results) {
        return new WorkflowResult(
            applicationId, "APPROVED", amount, rate, emi, confidence, summary, results, Instant.now()
        );
    }
    
    public static WorkflowResult rejected(String applicationId, String reason, List<AgentResult> results) {
        return new WorkflowResult(
            applicationId, "REJECTED", 0, 0, 0, 0, reason, results, Instant.now()
        );
    }
    
    public static WorkflowResult manualReview(String applicationId, String reason, 
                                               int confidence, List<AgentResult> results) {
        return new WorkflowResult(
            applicationId, "MANUAL_REVIEW", 0, 0, 0, confidence, reason, results, Instant.now()
        );
    }
}

