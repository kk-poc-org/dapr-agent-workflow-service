package com.hdfcbank.standin.agents;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfcbank.standin.model.AgentResult;
import com.hdfcbank.standin.model.LoanApplication;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Approval Agent - Makes final decision based on all agent outputs.
 * Implemented as a Dapr Workflow Activity.
 */
@Component
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApprovalAgent implements WorkflowActivity {
    
    private static final Logger log = LoggerFactory.getLogger(ApprovalAgent.class);
    private static ChatClient chatClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void setChatClient(ChatClient client) {
        chatClient = client;
    }
    
    // Input record for approval agent
    public record ApprovalInput(
        LoanApplication application,
        AgentResult documentResult,
        AgentResult creditResult,
        AgentResult eligibilityResult
    ) {}
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    @Override
    public Object run(WorkflowActivityContext ctx) {
        log.info("=== Approval Agent Started ===");

        try {
            ApprovalInput input = ctx.getInput(ApprovalInput.class);
            log.info("Making final decision for: {}", input.application().applicantName());

            String prompt = buildPrompt(input);
            log.debug("Sending prompt to LLM for final approval decision...");

            String response = callLLMWithRetry(prompt);

            return parseResponse(response, input);

        } catch (Exception e) {
            log.error("Approval Decision Failed after retries: {}", e.getMessage(), e);
            return AgentResult.failure("ApprovalAgent",
                "Failed to make approval decision: " + e.getMessage(),
                List.of(e.getMessage()));
        }
    }

    private String callLLMWithRetry(String prompt) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("LLM call attempt {}/{}", attempt, MAX_RETRIES);
                long startTime = System.currentTimeMillis();

                String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

                long duration = System.currentTimeMillis() - startTime;
                log.info("Approval decision completed in {} ms", duration);
                log.debug("LLM Response: {}", response);

                return response;

            } catch (Exception e) {
                lastException = e;
                log.warn("LLM call attempt {} failed: {}", attempt, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    log.info("Retrying in {} ms...", RETRY_DELAY_MS);
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }
        }

        throw lastException;
    }
    
    private String buildPrompt(ApprovalInput input) {
        LoanApplication app = input.application();
        
        return """
            You are the Final Approval AI Agent for a Home Loan Application.
            Review all agent assessments and make the final decision.
            
            Application Summary:
            - Applicant: %s
            - Requested Amount: ₹%.2f
            - Property Value: ₹%.2f
            - Credit Score: %d
            
            Agent Assessments:
            
            1. Document Verification Agent:
               - Success: %s
               - Confidence: %d%%
               - Recommendation: %s
               - Reasoning: %s
            
            2. Credit Check Agent:
               - Success: %s
               - Confidence: %d%%
               - Recommendation: %s
               - Reasoning: %s
            
            3. Eligibility Agent:
               - Success: %s
               - Confidence: %d%%
               - Recommendation: %s
               - Reasoning: %s
            
            Decision Rules:
            1. If ANY agent recommends REJECT with confidence > 80%%, REJECT
            2. If ALL agents recommend APPROVE with avg confidence > 75%%, APPROVE
            3. If mixed recommendations or low confidence, MANUAL_REVIEW
            4. Consider overall risk profile
            
            Return your final decision in this exact JSON format:
            {
                "finalDecision": "APPROVED/REJECTED/MANUAL_REVIEW",
                "approvedAmount": number (0 if rejected),
                "interestRate": number,
                "monthlyEmi": number,
                "overallConfidence": 0-100,
                "reasoning": "detailed explanation of decision",
                "conditions": ["list of conditions if approved"]
            }
            
            Return ONLY the JSON, no other text.
            """.formatted(
                app.applicantName(),
                app.requestedLoanAmount(),
                app.propertyValue(),
                app.creditScore(),
                input.documentResult().success(),
                input.documentResult().confidenceScore(),
                input.documentResult().recommendation(),
                input.documentResult().reasoning(),
                input.creditResult().success(),
                input.creditResult().confidenceScore(),
                input.creditResult().recommendation(),
                input.creditResult().reasoning(),
                input.eligibilityResult().success(),
                input.eligibilityResult().confidenceScore(),
                input.eligibilityResult().recommendation(),
                input.eligibilityResult().reasoning()
            );
    }
    
    private AgentResult parseResponse(String response, ApprovalInput input) {
        // Calculate aggregate metrics
        int avgConfidence = (input.documentResult().confidenceScore() + 
                            input.creditResult().confidenceScore() + 
                            input.eligibilityResult().confidenceScore()) / 3;
        
        // Count recommendations
        long approveCount = List.of(
            input.documentResult().recommendation(),
            input.creditResult().recommendation(),
            input.eligibilityResult().recommendation()
        ).stream().filter(r -> "APPROVE".equals(r)).count();
        
        long rejectCount = List.of(
            input.documentResult().recommendation(),
            input.creditResult().recommendation(),
            input.eligibilityResult().recommendation()
        ).stream().filter(r -> "REJECT".equals(r)).count();
        
        String finalDecision;
        if (rejectCount >= 2) {
            finalDecision = "REJECTED";
        } else if (approveCount >= 2 && avgConfidence >= 70) {
            finalDecision = "APPROVED";
        } else {
            finalDecision = "MANUAL_REVIEW";
        }
        
        // Try to extract from LLM response - but respect MANUAL_REVIEW from fallback
        // Only override if LLM explicitly says MANUAL_REVIEW, or if fallback wasn't MANUAL_REVIEW
        if (response.contains("\"finalDecision\":")) {
            if (response.contains("\"MANUAL_REVIEW\"")) {
                finalDecision = "MANUAL_REVIEW";
            } else if (!"MANUAL_REVIEW".equals(finalDecision)) {
                // Only override if fallback wasn't MANUAL_REVIEW
                if (response.contains("\"APPROVED\"")) finalDecision = "APPROVED";
                else if (response.contains("\"REJECTED\"")) finalDecision = "REJECTED";
            }
            // If fallback was MANUAL_REVIEW, keep it unless LLM explicitly says MANUAL_REVIEW
        }
        
        String reasoning = String.format(
            "Final Decision: %s. Avg Confidence: %d%%. Approvals: %d, Rejections: %d",
            finalDecision, avgConfidence, approveCount, rejectCount
        );
        
        Map<String, Object> decisionData = Map.of(
            "finalDecision", finalDecision,
            "approvedAmount", "APPROVED".equals(finalDecision) ? 
                input.application().requestedLoanAmount() : 0,
            "avgConfidence", avgConfidence
        );
        
        return AgentResult.withData("ApprovalAgent", avgConfidence, finalDecision, 
            reasoning, decisionData);
    }
}

