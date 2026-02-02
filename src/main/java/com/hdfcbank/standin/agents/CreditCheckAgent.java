package com.hdfcbank.standin.agents;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.hdfcbank.standin.agents.core.AgentContext;
import com.hdfcbank.standin.agents.core.AgentToolRegistry;
import com.hdfcbank.standin.agents.tools.AgentTool;
import com.hdfcbank.standin.model.AgentResult;
import com.hdfcbank.standin.model.LoanApplication;
import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Credit Check AI Agent - TRUE AI AGENT with ReAct Pattern.
 *
 * This agent demonstrates TRUE AI Agent behavior:
 * 1. AUTONOMY - Decides to fetch credit report from bureau
 * 2. TOOL USE - Calls Credit Bureau API to get real credit data
 * 3. REASONING - Uses LLM to analyze credit risk
 * 4. MEMORY - Maintains context of credit analysis
 * 5. ITERATION - Can request additional data if needed
 */
@Component
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class CreditCheckAgent implements WorkflowActivity {

    private static final Logger log = LoggerFactory.getLogger(CreditCheckAgent.class);
    private static ChatClient chatClient;
    private static AgentToolRegistry toolRegistry;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    public static void setChatClient(ChatClient client) {
        chatClient = client;
    }

    public static void setToolRegistry(AgentToolRegistry registry) {
        toolRegistry = registry;
    }

    @Override
    public Object run(WorkflowActivityContext ctx) {
        log.info("🤖 === Credit Check AI Agent Started ===");

        try {
            LoanApplication app = ctx.getInput(LoanApplication.class);
            log.info("📊 Analyzing credit for: {} (Declared Score: {})",
                app.applicantName(), app.creditScore());

            // Create agent context
            AgentContext context = new AgentContext("CreditCheckAgent", createInputMap(app));

            // === STEP 1: OBSERVE ===
            context.addThought("Received credit check request for " + app.applicantName(),
                AgentContext.ThoughtType.OBSERVATION);
            context.addThought("Declared credit score: " + app.creditScore() +
                ", Income: ₹" + app.annualIncome(), AgentContext.ThoughtType.OBSERVATION);

            // === STEP 2: PLAN ===
            context.addThought("Plan: 1) Fetch credit report from bureau, " +
                "2) Verify income, 3) Calculate DTI, 4) Assess risk", AgentContext.ThoughtType.PLAN);

            // === STEP 3: ACT - Execute Tools ===
            executeTools(context, app);

            // === STEP 4: CALCULATE - Compute credit metrics ===
            Map<String, Object> metrics = calculateCreditMetrics(app, context);
            context.remember("creditMetrics", metrics);

            // === STEP 5: REASON - Ask LLM to analyze ===
            String llmAnalysis = reasonWithLLM(context, app, metrics);

            // === STEP 6: CONCLUDE ===
            return concludeCreditCheck(context, llmAnalysis, app, metrics);

        } catch (Exception e) {
            log.error("❌ Credit Check Agent Failed: {}", e.getMessage(), e);
            return AgentResult.failure("CreditCheckAgent",
                "Agent execution failed: " + e.getMessage(), List.of(e.getMessage()));
        }
    }

    private void executeTools(AgentContext context, LoanApplication app) {
        log.info("🔧 Executing credit analysis tools...");

        // Tool 1: Fetch Credit Report from Bureau
        executeTool(context, "fetch_credit_report", Map.of(
            "pan_number", app.panNumber(),
            "applicant_name", app.applicantName(),
            "declared_score", app.creditScore()
        ));

        // Tool 2: Verify Income (cross-reference)
        executeTool(context, "verify_income", Map.of(
            "pan_number", app.panNumber(),
            "declared_income", app.annualIncome(),
            "employer_name", app.employerName(),
            "employment_type", app.employmentType()
        ));

        log.info("✅ Credit tools executed. Total: {}", context.getToolExecutions().size());
    }

    private void executeTool(AgentContext context, String toolName, Map<String, Object> params) {
        if (toolRegistry == null) {
            log.warn("Tool registry not available, skipping: {}", toolName);
            return;
        }

        Optional<AgentTool> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            log.warn("Tool not found: {}", toolName);
            return;
        }

        AgentTool tool = toolOpt.get();
        context.addThought("Executing: " + toolName, AgentContext.ThoughtType.ACTION);

        AgentTool.ToolResult result = tool.execute(params);
        context.addToolExecution(toolName, params, result);
        context.remember(toolName + "_result", result);

        log.info("🔧 Tool [{}]: {}", toolName, result.success() ? "SUCCESS" : "FAILED");
    }

    private Map<String, Object> calculateCreditMetrics(LoanApplication app, AgentContext context) {
        double monthlyIncome = app.annualIncome() / 12;
        double currentDTI = (app.existingEmi() / monthlyIncome) * 100;

        // Calculate proposed EMI (simplified)
        double rate = 0.085 / 12; // 8.5% annual
        int months = app.loanTenureYears() * 12;
        double proposedEMI = (app.requestedLoanAmount() * rate * Math.pow(1 + rate, months))
                            / (Math.pow(1 + rate, months) - 1);

        double newDTI = ((app.existingEmi() + proposedEMI) / monthlyIncome) * 100;
        double maxAffordableEMI = monthlyIncome * 0.5; // 50% of income
        double maxLoanAmount = maxAffordableEMI * months / (1 + rate * months / 2);

        // Get bureau score if available
        int bureauScore = app.creditScore();
        AgentTool.ToolResult bureauResult = context.recall("fetch_credit_report_result");
        if (bureauResult != null && bureauResult.success() && bureauResult.data() != null) {
            Object score = bureauResult.data().get("creditScore");
            if (score instanceof Number) {
                bureauScore = ((Number) score).intValue();
            }
        }

        String scoreRating = bureauScore >= 750 ? "EXCELLENT" :
                            bureauScore >= 700 ? "GOOD" :
                            bureauScore >= 650 ? "FAIR" : "POOR";

        String riskLevel = (bureauScore >= 700 && newDTI < 50) ? "LOW" :
                          (bureauScore >= 650 && newDTI < 60) ? "MEDIUM" : "HIGH";

        return Map.of(
            "bureauScore", bureauScore,
            "scoreRating", scoreRating,
            "currentDTI", currentDTI,
            "proposedEMI", proposedEMI,
            "newDTI", newDTI,
            "maxAffordableEMI", maxAffordableEMI,
            "maxLoanAmount", maxLoanAmount,
            "riskLevel", riskLevel
        );
    }

    private String reasonWithLLM(AgentContext context, LoanApplication app,
                                  Map<String, Object> metrics) throws Exception {
        String toolResults = context.getToolResultsSummary();

        String prompt = """
            You are a Credit Analysis AI Agent. Analyze the credit data and tool results.

            ## Applicant
            - Name: %s
            - Employment: %s at %s (%d years)
            - Annual Income: ₹%.0f

            ## Credit Metrics (Calculated)
            - Bureau Credit Score: %d (%s)
            - Current DTI: %.1f%%
            - Proposed EMI: ₹%.0f
            - New DTI (with loan): %.1f%%
            - Max Affordable EMI: ₹%.0f
            - Risk Level: %s

            ## Tool Results
            %s

            ## Analysis Required
            1. Is the credit score acceptable for home loan?
            2. Is the DTI ratio sustainable?
            3. Can the applicant afford the proposed EMI?
            4. What is the overall credit risk?

            Return JSON:
            {
                "creditApproved": true/false,
                "confidence": 0-100,
                "riskLevel": "LOW/MEDIUM/HIGH",
                "maxRecommendedLoan": number,
                "recommendation": "APPROVE/REJECT/MANUAL_REVIEW",
                "reasoning": "explanation based on metrics and tool results"
            }

            Return ONLY JSON.
            """.formatted(
                app.applicantName(),
                app.employmentType(), app.employerName(), app.yearsOfExperience(),
                app.annualIncome(),
                (int) metrics.get("bureauScore"), metrics.get("scoreRating"),
                (double) metrics.get("currentDTI"),
                (double) metrics.get("proposedEMI"),
                (double) metrics.get("newDTI"),
                (double) metrics.get("maxAffordableEMI"),
                metrics.get("riskLevel"),
                toolResults
            );

        return callLLMWithRetry(prompt);
    }

    private AgentResult concludeCreditCheck(AgentContext context, String llmResponse,
                                             LoanApplication app, Map<String, Object> metrics) {
        context.addThought("Analyzing credit assessment results", AgentContext.ThoughtType.CONCLUSION);

        int bureauScore = (int) metrics.get("bureauScore");
        String riskLevel = (String) metrics.get("riskLevel");
        double newDTI = (double) metrics.get("newDTI");

        // Parse LLM response
        int confidence = extractConfidence(llmResponse, bureauScore);
        String recommendation = extractRecommendation(llmResponse, bureauScore, newDTI);

        String reasoning = String.format(
            "Bureau Score: %d (%s). DTI: %.1f%%. Risk: %s. Tools executed: %d",
            bureauScore, metrics.get("scoreRating"), newDTI, riskLevel,
            context.getToolExecutions().size()
        );

        // Build extracted data
        Map<String, Object> extractedData = new HashMap<>(metrics);
        extractedData.put("toolsExecuted", context.getToolExecutions().size());

        log.info("🎯 Credit Decision - Score: {}, Risk: {}, Recommendation: {}",
            bureauScore, riskLevel, recommendation);

        return AgentResult.withData("CreditCheckAgent", confidence, recommendation,
            reasoning, extractedData);
    }

    private int extractConfidence(String response, int creditScore) {
        try {
            if (response.contains("\"confidence\":")) {
                String val = response.split("\"confidence\":")[1].split("[,}]")[0].trim();
                return Integer.parseInt(val);
            }
        } catch (Exception ignored) {}
        return creditScore >= 750 ? 90 : creditScore >= 700 ? 80 : 70;
    }

    private String extractRecommendation(String response, int score, double dti) {
        // First check LLM response
        if (response.contains("\"APPROVE\"") || response.toLowerCase().contains("\"approve\"")) return "APPROVE";
        if (response.contains("\"REJECT\"") || response.toLowerCase().contains("\"reject\"")) return "REJECT";
        if (response.contains("\"MANUAL_REVIEW\"") || response.toLowerCase().contains("\"manual_review\"")) return "MANUAL_REVIEW";

        // Fallback logic based on metrics - prioritize good metrics
        // Excellent credit (750+) with good DTI (<40%) = APPROVE
        if (score >= 750 && dti < 40) return "APPROVE";
        // Good credit (700+) with acceptable DTI (<50%) = APPROVE
        if (score >= 700 && dti < 50) return "APPROVE";
        // Poor credit or very high DTI = REJECT
        if (score < 600 || dti > 65) return "REJECT";
        // Everything else needs review
        return "MANUAL_REVIEW";
    }

    private Map<String, Object> createInputMap(LoanApplication app) {
        return Map.of(
            "applicantName", app.applicantName(),
            "creditScore", app.creditScore(),
            "annualIncome", app.annualIncome(),
            "existingEmi", app.existingEmi(),
            "requestedLoanAmount", app.requestedLoanAmount()
        );
    }

    private String callLLMWithRetry(String prompt) throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("🧠 LLM reasoning attempt {}/{}", attempt, MAX_RETRIES);
                long start = System.currentTimeMillis();
                String response = chatClient.prompt().user(prompt).call().content();
                log.info("🧠 LLM completed in {} ms", System.currentTimeMillis() - start);
                return response;
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < MAX_RETRIES) Thread.sleep(RETRY_DELAY_MS);
            }
        }
        throw lastException;
    }
}

