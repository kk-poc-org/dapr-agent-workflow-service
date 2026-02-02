package com.hdfcbank.standin.agents.autonomous;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.standin.agents.core.AgentToolRegistry;
import com.hdfcbank.standin.agents.tools.AgentTool;
import com.hdfcbank.standin.model.LoanApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Truly Autonomous Loan Processing Agent.
 * 
 * This agent embodies TRUE AGENTIC BEHAVIOR:
 * - LLM decides WHICH tools to call (not hardcoded)
 * - LLM decides the ORDER of tool calls
 * - LLM REASONS about results and decides next steps
 * - LLM makes the FINAL decision autonomously
 * 
 * The agent uses a ReAct (Reasoning + Acting) loop:
 * 1. THINK: Analyze current state and decide next action
 * 2. ACT: Execute the chosen tool
 * 3. OBSERVE: Process tool results
 * 4. REPEAT until ready to make final decision
 */
@Component
public class AutonomousLoanAgent {
    
    private static final Logger log = LoggerFactory.getLogger(AutonomousLoanAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_ITERATIONS = 10;
    
    private final ChatClient chatClient;
    private final AgentToolRegistry toolRegistry;
    
    public AutonomousLoanAgent(ChatClient.Builder chatClientBuilder, AgentToolRegistry toolRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.toolRegistry = toolRegistry;
        log.info("🤖 AutonomousLoanAgent initialized with {} tools", toolRegistry.getAllTools().size());
    }
    
    /**
     * Process a loan application autonomously.
     * The LLM decides everything - which tools to use, in what order, and the final decision.
     */
    public AgentDecision processApplication(LoanApplication application) {
        log.info("🚀 Starting autonomous processing for application: {}", application.applicationId());
        
        AgentSession session = new AgentSession(application);
        
        // Build the system prompt with available tools
        String systemPrompt = buildSystemPrompt();
        
        // Initial user message with application details
        String initialMessage = buildInitialMessage(application);
        session.addMessage("user", initialMessage);
        
        // ReAct Loop
        int iteration = 0;
        while (iteration < MAX_ITERATIONS) {
            iteration++;
            log.info("🔄 Iteration {}/{}", iteration, MAX_ITERATIONS);
            
            // THINK: Ask LLM what to do next
            String llmResponse = callLLM(systemPrompt, session.getConversationHistory());
            session.addMessage("assistant", llmResponse);
            
            log.info("💭 LLM Response:\n{}", llmResponse);
            
            // Check if LLM wants to make final decision
            if (containsFinalDecision(llmResponse)) {
                log.info("✅ LLM made final decision");
                return parseFinalDecision(llmResponse, session);
            }
            
            // ACT: Parse and execute tool calls
            List<ToolCall> toolCalls = parseToolCalls(llmResponse);
            
            if (toolCalls.isEmpty()) {
                log.warn("⚠️ No tool calls found, asking LLM to continue");
                session.addMessage("user", "Please either call a tool or make a final decision using FINAL_DECISION.");
                continue;
            }
            
            // Execute each tool and collect results
            StringBuilder toolResults = new StringBuilder("Tool Results:\n\n");
            for (ToolCall toolCall : toolCalls) {
                log.info("🔧 Executing tool: {} with params: {}", toolCall.name, toolCall.parameters);
                
                Optional<AgentTool> toolOpt = toolRegistry.getTool(toolCall.name);
                if (toolOpt.isEmpty()) {
                    toolResults.append(String.format("Tool '%s' not found.\n\n", toolCall.name));
                    continue;
                }
                
                AgentTool tool = toolOpt.get();
                AgentTool.ToolResult result = tool.execute(toolCall.parameters);
                session.addToolExecution(toolCall.name, toolCall.parameters, result);
                
                if (result.success()) {
                    toolResults.append(String.format("### %s - SUCCESS\n%s\n\n", 
                        toolCall.name, formatToolOutput(result)));
                } else {
                    toolResults.append(String.format("### %s - FAILED\n%s\n\n", 
                        toolCall.name, result.errorMessage()));
                }
            }
            
            // OBSERVE: Feed results back to LLM
            session.addMessage("user", toolResults.toString());
        }
        
        // Max iterations reached - force a decision
        log.warn("⚠️ Max iterations reached, forcing decision");
        return AgentDecision.manualReview(
            application.applicationId(),
            "Agent reached maximum iterations without conclusive decision",
            session.getReasoningTrace()
        );
    }
    
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are an autonomous AI loan processing agent for HDFC Bank.
            Your job is to evaluate home loan applications by gathering information using tools,
            reasoning about the data, and making a final approval decision.
            
            ## Your Capabilities
            You have access to the following tools to gather information:
            
            """);
        
        // Add tool descriptions
        for (AgentTool tool : toolRegistry.getAllTools()) {
            sb.append(String.format("### %s\n", tool.getName()));
            sb.append(String.format("Description: %s\n", tool.getDescription()));
            sb.append(String.format("Parameters: %s\n\n", tool.getParameterSchema()));
        }
        
        sb.append("""
            ## How to Use Tools
            To call a tool, use this exact format:
            ```tool
            {"tool": "tool_name", "parameters": {"param1": "value1", "param2": "value2"}}
            ```
            
            You can call multiple tools in one response.
            
            ## Decision Criteria
            - Credit Score: Minimum 650 for approval, 750+ is excellent
            - Debt-to-Income Ratio: Should be below 50%
            - LTV (Loan-to-Value): Should be below 80%
            - Document Verification: PAN and Aadhaar must be valid
            - Fraud Check: No high-risk indicators
            - Income Verification: Declared income should match ITR records (within 20% variance)
            
            ## Making Final Decision
            When you have gathered enough information, make your final decision using:
            ```decision
            {"decision": "APPROVED|REJECTED|MANUAL_REVIEW", "confidence": 0-100, "reasoning": "your detailed reasoning"}
            ```
            
            ## Important Guidelines
            1. Always verify documents (PAN, Aadhaar) first
            2. Check credit score and history
            3. Verify income against declared amount
            4. Check for fraud indicators
            5. Evaluate property if relevant
            6. Consider all factors before making a decision
            7. Be thorough but efficient - don't call unnecessary tools
            """);

        return sb.toString();
    }

    private String buildInitialMessage(LoanApplication app) {
        return String.format("""
            Please process this home loan application:

            ## Applicant Details
            - Application ID: %s
            - Name: %s
            - PAN: %s
            - Aadhaar: %s
            - Employment: %s at %s (%d years experience)

            ## Financial Details
            - Annual Income: ₹%.0f
            - Requested Loan: ₹%.0f
            - Loan Tenure: %d years
            - Declared Credit Score: %d
            - Existing EMI: ₹%.0f

            ## Property Details
            - Type: %s
            - Location: %s
            - Value: ₹%.0f

            Please analyze this application using the available tools and make a decision.
            Start by verifying the applicant's documents, then assess creditworthiness.
            """,
            app.applicationId(), app.applicantName(), app.panNumber(), app.aadhaarNumber(),
            app.employmentType(), app.employerName(), app.yearsOfExperience(),
            app.annualIncome(), app.requestedLoanAmount(), app.loanTenureYears(),
            app.creditScore(), app.existingEmi(),
            app.propertyType(), app.propertyLocation(), app.propertyValue()
        );
    }

    private String callLLM(String systemPrompt, List<Map<String, String>> conversationHistory) {
        StringBuilder fullPrompt = new StringBuilder();
        fullPrompt.append("System: ").append(systemPrompt).append("\n\n");

        for (Map<String, String> msg : conversationHistory) {
            fullPrompt.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n\n");
        }

        try {
            return chatClient.prompt()
                .user(fullPrompt.toString())
                .call()
                .content();
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage(), e);
            return "Error calling LLM: " + e.getMessage();
        }
    }

    private boolean containsFinalDecision(String response) {
        return response.contains("```decision") ||
               response.contains("FINAL_DECISION") ||
               (response.contains("\"decision\"") && response.contains("\"reasoning\""));
    }

    private List<ToolCall> parseToolCalls(String response) {
        List<ToolCall> toolCalls = new ArrayList<>();

        // Pattern to match tool calls in ```tool blocks
        Pattern toolBlockPattern = Pattern.compile("```tool\\s*\\n?([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher matcher = toolBlockPattern.matcher(response);

        while (matcher.find()) {
            String toolJson = matcher.group(1).trim();
            try {
                JsonNode node = objectMapper.readTree(toolJson);
                String toolName = node.get("tool").asText();
                Map<String, Object> params = new HashMap<>();

                JsonNode paramsNode = node.get("parameters");
                if (paramsNode != null) {
                    paramsNode.fields().forEachRemaining(entry -> {
                        JsonNode value = entry.getValue();
                        if (value.isTextual()) {
                            params.put(entry.getKey(), value.asText());
                        } else if (value.isNumber()) {
                            params.put(entry.getKey(), value.numberValue());
                        } else if (value.isBoolean()) {
                            params.put(entry.getKey(), value.asBoolean());
                        }
                    });
                }

                toolCalls.add(new ToolCall(toolName, params));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse tool call: {}", toolJson, e);
            }
        }

        return toolCalls;
    }

    private AgentDecision parseFinalDecision(String response, AgentSession session) {
        // Pattern to match decision blocks
        Pattern decisionPattern = Pattern.compile("```decision\\s*\\n?([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher matcher = decisionPattern.matcher(response);

        if (matcher.find()) {
            String decisionJson = matcher.group(1).trim();
            try {
                JsonNode node = objectMapper.readTree(decisionJson);
                String decision = node.get("decision").asText();
                int confidence = node.has("confidence") ? node.get("confidence").asInt() : 75;
                String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "No reasoning provided";

                return new AgentDecision(
                    session.getApplicationId(),
                    decision,
                    confidence,
                    reasoning,
                    session.getReasoningTrace(),
                    session.getToolExecutions()
                );
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse decision: {}", decisionJson, e);
            }
        }

        // Fallback: try to extract decision from text
        String decision = "MANUAL_REVIEW";
        if (response.toUpperCase().contains("APPROVED")) decision = "APPROVED";
        else if (response.toUpperCase().contains("REJECTED")) decision = "REJECTED";

        return new AgentDecision(
            session.getApplicationId(),
            decision,
            60,
            response,
            session.getReasoningTrace(),
            session.getToolExecutions()
        );
    }

    private String formatToolOutput(AgentTool.ToolResult result) {
        if (result.data() == null || result.data().isEmpty()) {
            return result.output();
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.data());
        } catch (JsonProcessingException e) {
            return result.output();
        }
    }

    // Inner classes
    private record ToolCall(String name, Map<String, Object> parameters) {}
}
