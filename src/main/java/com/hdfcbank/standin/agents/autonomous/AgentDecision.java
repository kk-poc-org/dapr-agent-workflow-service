package com.hdfcbank.standin.agents.autonomous;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * The final decision made by the autonomous agent.
 * Contains the decision, confidence, reasoning, and full audit trail.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AgentDecision(
    String applicationId,
    String decision,           // APPROVED, REJECTED, MANUAL_REVIEW
    int confidenceScore,       // 0-100
    String reasoning,          // LLM's reasoning for the decision
    String reasoningTrace,     // Full conversation/reasoning trace
    List<AgentSession.ToolExecutionRecord> toolExecutions,
    Instant decidedAt
) implements Serializable {
    
    public AgentDecision(String applicationId, String decision, int confidenceScore,
                         String reasoning, String reasoningTrace,
                         List<AgentSession.ToolExecutionRecord> toolExecutions) {
        this(applicationId, decision, confidenceScore, reasoning, reasoningTrace, 
             toolExecutions, Instant.now());
    }
    
    public static AgentDecision approved(String applicationId, int confidence, 
                                          String reasoning, String trace,
                                          List<AgentSession.ToolExecutionRecord> tools) {
        return new AgentDecision(applicationId, "APPROVED", confidence, reasoning, trace, tools);
    }
    
    public static AgentDecision rejected(String applicationId, String reasoning, 
                                          String trace, List<AgentSession.ToolExecutionRecord> tools) {
        return new AgentDecision(applicationId, "REJECTED", 90, reasoning, trace, tools);
    }
    
    public static AgentDecision manualReview(String applicationId, String reasoning, String trace) {
        return new AgentDecision(applicationId, "MANUAL_REVIEW", 50, reasoning, trace, List.of());
    }
    
    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(decision);
    }
    
    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(decision);
    }
    
    public boolean needsManualReview() {
        return "MANUAL_REVIEW".equalsIgnoreCase(decision);
    }
}

