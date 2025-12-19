package com.hdfcbank.standin.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.io.Serializable;
import java.util.List;

/**
 * Result from an AI Agent activity.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record AgentResult(
    String agentName,
    boolean success,
    int confidenceScore,  // 0-100
    String recommendation, // APPROVE, REJECT, MANUAL_REVIEW
    String reasoning,
    List<String> issues,
    Object extractedData
) implements Serializable {
    
    public static AgentResult success(String agentName, int confidence, String recommendation, String reasoning) {
        return new AgentResult(agentName, true, confidence, recommendation, reasoning, List.of(), null);
    }
    
    public static AgentResult failure(String agentName, String reasoning, List<String> issues) {
        return new AgentResult(agentName, false, 0, "REJECT", reasoning, issues, null);
    }
    
    public static AgentResult withData(String agentName, int confidence, String recommendation, 
                                        String reasoning, Object data) {
        return new AgentResult(agentName, true, confidence, recommendation, reasoning, List.of(), data);
    }
}

