package com.hdfcbank.standin.agents.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.standin.agents.tools.AgentTool;
import com.hdfcbank.standin.model.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.*;

/**
 * Base AI Agent with ReAct (Reasoning + Acting) Loop.
 * 
 * This is what makes a TRUE AI Agent:
 * 1. OBSERVE - Understand the input and context
 * 2. THINK - Reason about what to do
 * 3. ACT - Execute tools to gather information
 * 4. REFLECT - Analyze results and decide next steps
 * 5. CONCLUDE - Make final decision
 * 
 * The agent iterates through Think-Act-Reflect until it has enough information.
 */
public abstract class BaseAIAgent {
    
    private static final Logger log = LoggerFactory.getLogger(BaseAIAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    protected ChatClient chatClient;
    protected AgentToolRegistry toolRegistry;
    
    public abstract String getAgentName();
    public abstract String getAgentRole();
    public abstract List<String> getRequiredTools();
    
    /**
     * Main agent execution with ReAct loop
     */
    public AgentResult execute(Map<String, Object> input) {
        AgentContext context = new AgentContext(getAgentName(), input);
        log.info("🤖 Agent [{}] starting execution", getAgentName());
        
        try {
            // Step 1: OBSERVE - Understand the input
            observe(context);
            
            // Step 2: PLAN - Decide which tools to use
            List<AgentTool> tools = getToolsForAgent();
            plan(context, tools);
            
            // Step 3: ReAct Loop - Think, Act, Reflect
            while (context.canContinue()) {
                context.incrementIteration();
                log.info("🔄 Agent [{}] iteration {}", getAgentName(), context.getIterationCount());
                
                // THINK - What should I do next?
                String nextAction = think(context, tools);
                
                // Check if agent wants to conclude
                if (nextAction.contains("CONCLUDE") || nextAction.contains("FINAL_ANSWER")) {
                    context.addThought("Ready to make final decision", AgentContext.ThoughtType.CONCLUSION);
                    break;
                }
                
                // ACT - Execute the chosen tool
                boolean acted = act(context, nextAction, tools);
                
                // REFLECT - Analyze the results
                reflect(context);
                
                // If no action was taken, we're done
                if (!acted) break;
            }
            
            // Step 4: CONCLUDE - Make final decision
            return conclude(context);
            
        } catch (Exception e) {
            log.error("Agent [{}] failed: {}", getAgentName(), e.getMessage(), e);
            return AgentResult.failure(getAgentName(), 
                "Agent execution failed: " + e.getMessage(), List.of(e.getMessage()));
        }
    }
    
    protected void observe(AgentContext context) {
        context.addThought("Analyzing input: " + context.getInput().keySet(), 
            AgentContext.ThoughtType.OBSERVATION);
        log.debug("Agent [{}] observing input", getAgentName());
    }
    
    protected void plan(AgentContext context, List<AgentTool> tools) {
        String toolNames = tools.stream().map(AgentTool::getName).toList().toString();
        context.addThought("Available tools: " + toolNames, AgentContext.ThoughtType.PLAN);
        log.debug("Agent [{}] planning with tools: {}", getAgentName(), toolNames);
    }
    
    protected abstract String think(AgentContext context, List<AgentTool> tools);
    
    protected boolean act(AgentContext context, String action, List<AgentTool> tools) {
        // Parse the action to find tool call
        for (AgentTool tool : tools) {
            if (action.contains(tool.getName())) {
                Map<String, Object> params = extractToolParams(action, tool, context);
                context.addThought("Executing tool: " + tool.getName(), AgentContext.ThoughtType.ACTION);
                
                log.info("🔧 Agent [{}] calling tool: {}", getAgentName(), tool.getName());
                AgentTool.ToolResult result = tool.execute(params);
                context.addToolExecution(tool.getName(), params, result);
                
                return true;
            }
        }
        return false;
    }
    
    protected void reflect(AgentContext context) {
        String summary = context.getToolResultsSummary();
        context.addThought("Reflecting on results: " + 
            context.getToolExecutions().size() + " tools executed", 
            AgentContext.ThoughtType.REFLECTION);
        log.debug("Agent [{}] reflecting: {}", getAgentName(), summary);
    }
    
    protected abstract AgentResult conclude(AgentContext context);
    
    protected List<AgentTool> getToolsForAgent() {
        if (toolRegistry == null) return List.of();
        return toolRegistry.getTools(getRequiredTools().toArray(new String[0]));
    }
    
    protected Map<String, Object> extractToolParams(String action, AgentTool tool, AgentContext ctx) {
        // Default: extract from context input
        return new HashMap<>(ctx.getInput());
    }
    
    // Setters for dependency injection
    public void setChatClient(ChatClient chatClient) { this.chatClient = chatClient; }
    public void setToolRegistry(AgentToolRegistry registry) { this.toolRegistry = registry; }
}

