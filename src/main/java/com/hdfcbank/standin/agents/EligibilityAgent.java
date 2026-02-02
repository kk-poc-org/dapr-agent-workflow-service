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
 * Eligibility AI Agent - TRUE AI AGENT with ReAct Pattern.
 *
 * This agent demonstrates TRUE AI Agent behavior:
 * 1. AUTONOMY - Decides to fetch property valuation
 * 2. TOOL USE - Calls Property Valuation API
 * 3. REASONING - Uses LLM to analyze eligibility
 * 4. MEMORY - Maintains context of calculations
 * 5. ITERATION - Can adjust recommendations based on data
 */
@Component
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class EligibilityAgent implements WorkflowActivity {

    private static final Logger log = LoggerFactory.getLogger(EligibilityAgent.class);
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
        log.info("🤖 === Eligibility AI Agent Started ===");

        try {
            LoanApplication app = ctx.getInput(LoanApplication.class);
            log.info("📊 Calculating eligibility for: {} (Requested: ₹{})",
                app.applicantName(), app.requestedLoanAmount());

            // Create agent context
            AgentContext context = new AgentContext("EligibilityAgent", createInputMap(app));

            // === STEP 1: OBSERVE ===
            context.addThought("Analyzing loan request: ₹" + app.requestedLoanAmount() +
                " for " + app.loanTenureYears() + " years", AgentContext.ThoughtType.OBSERVATION);

            // === STEP 2: ACT - Execute Property Valuation Tool ===
            executePropertyValuation(context, app);

            // === STEP 3: CALCULATE - Compute eligibility metrics ===
            Map<String, Object> eligibility = calculateEligibility(app, context);
            context.remember("eligibilityMetrics", eligibility);

            // === STEP 4: REASON - Ask LLM to analyze ===
            String llmAnalysis = reasonWithLLM(context, app, eligibility);

            // === STEP 5: CONCLUDE ===
            return concludeEligibility(context, llmAnalysis, app, eligibility);

        } catch (Exception e) {
            log.error("❌ Eligibility Agent Failed: {}", e.getMessage(), e);
            return AgentResult.failure("EligibilityAgent",
                "Agent execution failed: " + e.getMessage(), List.of(e.getMessage()));
        }
    }

    private void executePropertyValuation(AgentContext context, LoanApplication app) {
        if (toolRegistry == null) {
            log.warn("Tool registry not available");
            return;
        }

        Optional<AgentTool> toolOpt = toolRegistry.getTool("get_property_valuation");
        if (toolOpt.isEmpty()) {
            log.warn("Property valuation tool not found");
            return;
        }

        context.addThought("Fetching property valuation for LTV calculation",
            AgentContext.ThoughtType.ACTION);

        AgentTool tool = toolOpt.get();
        AgentTool.ToolResult result = tool.execute(Map.of(
            "property_location", app.propertyLocation(),
            "property_type", app.propertyType(),
            "declared_value", app.propertyValue(),
            "carpet_area_sqft", 1000 // Default, could be added to LoanApplication
        ));

        context.addToolExecution("get_property_valuation", Map.of(), result);
        context.remember("propertyValuation", result);

        log.info("🔧 Property valuation: {}", result.success() ? "SUCCESS" : "FAILED");
    }

    private String reasonWithLLM(AgentContext context, LoanApplication app,
                                  Map<String, Object> eligibility) throws Exception {
        String toolResults = context.getToolResultsSummary();

        String prompt = """
            You are a Loan Eligibility AI Agent. Analyze the eligibility data and tool results.

            ## Applicant
            - Name: %s
            - Annual Income: ₹%.0f
            - Requested Loan: ₹%.0f
            - Tenure: %d years
            - Property: %s in %s (Value: ₹%.0f)

            ## Calculated Metrics
            - Monthly Income: ₹%.0f
            - Max EMI Allowed (50%%): ₹%.0f
            - Available EMI: ₹%.0f
            - Max Eligible Loan: ₹%.0f
            - Requested EMI: ₹%.0f
            - LTV Ratio: %.1f%%
            - Is Eligible: %s

            ## Tool Results
            %s

            ## Eligibility Rules
            1. LTV should be <= 80%%
            2. EMI should not exceed 50%% of monthly income
            3. Property valuation should be reasonable

            Return JSON:
            {
                "isEligible": true/false,
                "maxEligibleAmount": number,
                "recommendedAmount": number,
                "monthlyEmi": number,
                "confidence": 0-100,
                "recommendation": "APPROVE/REJECT/MANUAL_REVIEW",
                "reasoning": "explanation based on calculations and tool results"
            }

            Return ONLY JSON.
            """.formatted(
                app.applicantName(),
                app.annualIncome(),
                app.requestedLoanAmount(),
                app.loanTenureYears(),
                app.propertyType(), app.propertyLocation(), app.propertyValue(),
                (double) eligibility.get("monthlyIncome"),
                (double) eligibility.get("maxEmiAllowed"),
                (double) eligibility.get("availableEmi"),
                (double) eligibility.get("maxLoanAmount"),
                (double) eligibility.get("requestedEmi"),
                (double) eligibility.get("ltvRatio"),
                eligibility.get("isEligible"),
                toolResults
            );

        return callLLMWithRetry(prompt);
    }

    private AgentResult concludeEligibility(AgentContext context, String llmResponse,
                                             LoanApplication app, Map<String, Object> eligibility) {
        context.addThought("Analyzing eligibility results", AgentContext.ThoughtType.CONCLUSION);

        boolean isEligible = (boolean) eligibility.get("isEligible");
        double maxLoan = (double) eligibility.get("maxLoanAmount");
        double requestedEmi = (double) eligibility.get("requestedEmi");
        double ltvRatio = (double) eligibility.get("ltvRatio");

        int confidence = isEligible ? 85 : 70;
        String recommendation = isEligible ? "APPROVE" :
            (app.requestedLoanAmount() <= maxLoan * 1.1 ? "MANUAL_REVIEW" : "REJECT");

        // Override with LLM recommendation if present
        if (llmResponse.contains("\"APPROVE\"")) recommendation = "APPROVE";
        else if (llmResponse.contains("\"REJECT\"")) recommendation = "REJECT";

        String reasoning = String.format(
            "Max eligible: ₹%.0f. Requested: ₹%.0f. EMI: ₹%.0f. LTV: %.1f%%. Tools: %d",
            maxLoan, app.requestedLoanAmount(), requestedEmi, ltvRatio,
            context.getToolExecutions().size()
        );

        // Build extracted data
        Map<String, Object> extractedData = new HashMap<>(eligibility);
        extractedData.put("toolsExecuted", context.getToolExecutions().size());

        log.info("🎯 Eligibility Decision - Eligible: {}, LTV: {}%, Recommendation: {}",
            isEligible, String.format("%.1f", ltvRatio), recommendation);

        return AgentResult.withData("EligibilityAgent", confidence, recommendation,
            reasoning, extractedData);
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

    private Map<String, Object> createInputMap(LoanApplication app) {
        return Map.of(
            "applicantName", app.applicantName(),
            "requestedLoanAmount", app.requestedLoanAmount(),
            "propertyValue", app.propertyValue(),
            "annualIncome", app.annualIncome()
        );
    }

    private Map<String, Object> calculateEligibility(LoanApplication app, AgentContext context) {
        double monthlyIncome = app.annualIncome() / 12;
        double maxEmiAllowed = monthlyIncome * 0.5; // 50% of income for EMI
        double availableEmi = maxEmiAllowed - app.existingEmi();
        
        // Calculate max loan based on available EMI (assuming 8.5% interest)
        double interestRate = 8.5;
        double monthlyRate = interestRate / 12 / 100;
        int months = app.loanTenureYears() * 12;
        
        // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
        // P = EMI * ((1+r)^n - 1) / (r * (1+r)^n)
        double factor = Math.pow(1 + monthlyRate, months);
        double maxLoanAmount = availableEmi * (factor - 1) / (monthlyRate * factor);
        
        // Calculate EMI for requested amount
        double requestedEmi = app.requestedLoanAmount() * monthlyRate * factor / (factor - 1);
        
        // LTV ratio (Loan to Value)
        double ltvRatio = (app.requestedLoanAmount() / app.propertyValue()) * 100;
        
        return Map.of(
            "monthlyIncome", monthlyIncome,
            "maxEmiAllowed", maxEmiAllowed,
            "availableEmi", availableEmi,
            "maxLoanAmount", maxLoanAmount,
            "requestedEmi", requestedEmi,
            "interestRate", interestRate,
            "ltvRatio", ltvRatio,
            "isEligible", app.requestedLoanAmount() <= maxLoanAmount && ltvRatio <= 80
        );
    }
}

