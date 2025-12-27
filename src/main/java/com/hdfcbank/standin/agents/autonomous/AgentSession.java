package com.hdfcbank.standin.agents.autonomous;

import com.hdfcbank.standin.agents.tools.AgentTool;
import com.hdfcbank.standin.model.LoanApplication;

import java.time.Instant;
import java.util.*;

/**
 * Maintains the state of an autonomous agent session.
 * Tracks conversation history, tool executions, and reasoning trace.
 */
public class AgentSession {
    
    private final String applicationId;
    private final LoanApplication application;
    private final List<Map<String, String>> conversationHistory;
    private final List<ToolExecutionRecord> toolExecutions;
    private final Instant startTime;
    
    public AgentSession(LoanApplication application) {
        this.applicationId = application.applicationId();
        this.application = application;
        this.conversationHistory = new ArrayList<>();
        this.toolExecutions = new ArrayList<>();
        this.startTime = Instant.now();
    }
    
    public void addMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        conversationHistory.add(message);
    }
    
    public void addToolExecution(String toolName, Map<String, Object> params, AgentTool.ToolResult result) {
        toolExecutions.add(new ToolExecutionRecord(
            toolName,
            params,
            result.success(),
            result.success() ? result.output() : result.errorMessage(),
            result.data(),
            Instant.now()
        ));
    }
    
    public String getApplicationId() {
        return applicationId;
    }
    
    public LoanApplication getApplication() {
        return application;
    }
    
    public List<Map<String, String>> getConversationHistory() {
        return Collections.unmodifiableList(conversationHistory);
    }
    
    public List<ToolExecutionRecord> getToolExecutions() {
        return Collections.unmodifiableList(toolExecutions);
    }
    
    public String getReasoningTrace() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Agent Reasoning Trace ===\n");
        sb.append("Application: ").append(applicationId).append("\n");
        sb.append("Started: ").append(startTime).append("\n\n");
        
        sb.append("--- Conversation ---\n");
        for (Map<String, String> msg : conversationHistory) {
            String role = msg.get("role");
            String content = msg.get("content");
            // Truncate long messages
            if (content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }
            sb.append("[").append(role.toUpperCase()).append("]: ").append(content).append("\n\n");
        }
        
        sb.append("--- Tool Executions ---\n");
        for (ToolExecutionRecord exec : toolExecutions) {
            sb.append(String.format("Tool: %s -> %s\n", 
                exec.toolName(), exec.success() ? "SUCCESS" : "FAILED"));
        }
        
        return sb.toString();
    }
    
    /**
     * Record of a tool execution
     */
    public record ToolExecutionRecord(
        String toolName,
        Map<String, Object> parameters,
        boolean success,
        String output,
        Map<String, Object> data,
        Instant executedAt
    ) {}
}

