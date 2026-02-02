package com.hdfcbank.standin.agents.core;

import com.hdfcbank.standin.agents.tools.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry of all available tools for AI Agents.
 * Agents query this registry to discover and use tools.
 */
@Component
public class AgentToolRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(AgentToolRegistry.class);
    private final Map<String, AgentTool> tools = new HashMap<>();
    
    public AgentToolRegistry(List<AgentTool> agentTools) {
        for (AgentTool tool : agentTools) {
            tools.put(tool.getName(), tool);
            log.info("Registered agent tool: {} - {}", tool.getName(), tool.getDescription());
        }
        log.info("Total tools registered: {}", tools.size());
    }
    
    /**
     * Get a tool by name
     */
    public Optional<AgentTool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }
    
    /**
     * Get all available tools
     */
    public Collection<AgentTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }
    
    /**
     * Get tools by names
     */
    public List<AgentTool> getTools(String... names) {
        List<AgentTool> result = new ArrayList<>();
        for (String name : names) {
            AgentTool tool = tools.get(name);
            if (tool != null) {
                result.add(tool);
            }
        }
        return result;
    }
    
    /**
     * Generate tool descriptions for LLM prompt
     */
    public String getToolDescriptionsForPrompt(List<AgentTool> toolsToDescribe) {
        StringBuilder sb = new StringBuilder();
        sb.append("You have access to the following tools:\n\n");
        
        for (AgentTool tool : toolsToDescribe) {
            sb.append(String.format("### %s\n", tool.getName()));
            sb.append(String.format("Description: %s\n", tool.getDescription()));
            sb.append(String.format("Parameters: %s\n\n", tool.getParameterSchema()));
        }
        
        return sb.toString();
    }
    
    /**
     * Generate tool descriptions for all tools
     */
    public String getAllToolDescriptions() {
        return getToolDescriptionsForPrompt(new ArrayList<>(tools.values()));
    }
}

