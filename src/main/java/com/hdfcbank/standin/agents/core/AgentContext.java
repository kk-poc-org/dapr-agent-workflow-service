package com.hdfcbank.standin.agents.core;

import com.hdfcbank.standin.agents.tools.AgentTool;

import java.util.*;

/**
 * Agent Context - Maintains state and memory during agent execution.
 * 
 * This is what gives an AI Agent "memory" - it can remember:
 * - Tool execution results
 * - Reasoning steps taken
 * - Observations made
 * - Decisions and their rationale
 */
public class AgentContext {
    
    private final String agentName;
    private final Map<String, Object> input;
    private final List<ThoughtStep> thoughtProcess;
    private final List<ToolExecution> toolExecutions;
    private final Map<String, Object> memory;
    private int iterationCount;
    private static final int MAX_ITERATIONS = 5;
    
    public AgentContext(String agentName, Map<String, Object> input) {
        this.agentName = agentName;
        this.input = new HashMap<>(input);
        this.thoughtProcess = new ArrayList<>();
        this.toolExecutions = new ArrayList<>();
        this.memory = new HashMap<>();
        this.iterationCount = 0;
    }
    
    /**
     * Record a thought/reasoning step
     */
    public void addThought(String thought, ThoughtType type) {
        thoughtProcess.add(new ThoughtStep(thought, type, java.time.Instant.now()));
    }
    
    /**
     * Record a tool execution
     */
    public void addToolExecution(String toolName, Map<String, Object> params, 
                                  AgentTool.ToolResult result) {
        toolExecutions.add(new ToolExecution(toolName, params, result, java.time.Instant.now()));
    }
    
    /**
     * Store something in memory for later use
     */
    public void remember(String key, Object value) {
        memory.put(key, value);
    }
    
    /**
     * Recall something from memory
     */
    @SuppressWarnings("unchecked")
    public <T> T recall(String key) {
        return (T) memory.get(key);
    }
    
    /**
     * Check if we should continue iterating
     */
    public boolean canContinue() {
        return iterationCount < MAX_ITERATIONS;
    }
    
    public void incrementIteration() {
        iterationCount++;
    }
    
    public int getIterationCount() {
        return iterationCount;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public Map<String, Object> getInput() {
        return Collections.unmodifiableMap(input);
    }
    
    public List<ThoughtStep> getThoughtProcess() {
        return Collections.unmodifiableList(thoughtProcess);
    }
    
    public List<ToolExecution> getToolExecutions() {
        return Collections.unmodifiableList(toolExecutions);
    }
    
    /**
     * Get a summary of all tool results for the LLM
     */
    public String getToolResultsSummary() {
        if (toolExecutions.isEmpty()) {
            return "No tools have been executed yet.";
        }
        
        StringBuilder sb = new StringBuilder("Tool Execution Results:\n");
        for (ToolExecution exec : toolExecutions) {
            sb.append(String.format("- %s: %s\n", exec.toolName(), 
                exec.result().success() ? exec.result().output() : "FAILED: " + exec.result().errorMessage()));
        }
        return sb.toString();
    }
    
    /**
     * Get the reasoning trace for debugging/logging
     */
    public String getReasoningTrace() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Agent Reasoning Trace: ").append(agentName).append(" ===\n");
        
        for (ThoughtStep step : thoughtProcess) {
            sb.append(String.format("[%s] %s: %s\n", 
                step.type(), step.timestamp().toString().substring(11, 19), step.thought()));
        }
        
        if (!toolExecutions.isEmpty()) {
            sb.append("\n--- Tool Executions ---\n");
            for (ToolExecution exec : toolExecutions) {
                sb.append(String.format("Tool: %s -> %s\n", exec.toolName(), 
                    exec.result().success() ? "SUCCESS" : "FAILED"));
            }
        }
        
        return sb.toString();
    }
    
    // Inner records for structured data
    public record ThoughtStep(String thought, ThoughtType type, java.time.Instant timestamp) {}
    
    public record ToolExecution(String toolName, Map<String, Object> params, 
                                 AgentTool.ToolResult result, java.time.Instant timestamp) {}
    
    public enum ThoughtType {
        OBSERVATION,  // What the agent observes from input
        REASONING,    // Agent's reasoning process
        PLAN,         // What the agent plans to do
        ACTION,       // Action being taken
        REFLECTION,   // Agent reflecting on results
        CONCLUSION    // Final conclusion
    }
}

