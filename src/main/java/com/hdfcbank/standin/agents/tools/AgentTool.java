package com.hdfcbank.standin.agents.tools;

import java.util.Map;

/**
 * Base interface for all Agent Tools.
 * Tools are external capabilities that AI Agents can invoke to gather information
 * or perform actions in the real world.
 * 
 * This is what makes an AI Agent different from a simple LLM call:
 * - Agents can USE TOOLS to interact with external systems
 * - Agents can REASON about which tools to use
 * - Agents can ITERATE based on tool results
 */
public interface AgentTool {
    
    /**
     * Unique name of the tool (used by LLM to invoke it)
     */
    String getName();
    
    /**
     * Description of what the tool does (helps LLM decide when to use it)
     */
    String getDescription();
    
    /**
     * JSON schema of the input parameters
     */
    String getParameterSchema();
    
    /**
     * Execute the tool with given parameters
     * @param parameters Map of parameter name to value
     * @return Tool execution result
     */
    ToolResult execute(Map<String, Object> parameters);
    
    /**
     * Result of tool execution
     */
    record ToolResult(
        boolean success,
        String output,
        Map<String, Object> data,
        String errorMessage
    ) {
        public static ToolResult success(String output, Map<String, Object> data) {
            return new ToolResult(true, output, data, null);
        }
        
        public static ToolResult failure(String errorMessage) {
            return new ToolResult(false, null, Map.of(), errorMessage);
        }
    }
}

