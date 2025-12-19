package com.hdfcbank.standin.workflow;

import com.hdfcbank.standin.agents.ApprovalAgent;
import com.hdfcbank.standin.agents.CreditCheckAgent;
import com.hdfcbank.standin.agents.DocumentVerificationAgent;
import com.hdfcbank.standin.agents.EligibilityAgent;
import com.hdfcbank.standin.model.AgentResult;
import com.hdfcbank.standin.model.LoanApplication;
import com.hdfcbank.standin.model.WorkflowResult;
import io.dapr.durabletask.interruption.OrchestratorBlockedException;
import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Home Loan Processing Workflow - Orchestrates all AI Agents.
 *
 * Workflow Steps:
 * 1. Document Verification Agent
 * 2. Credit Check Agent
 * 3. Eligibility Agent
 * 4. Approval Agent (Final Decision)
 */
public class HomeLoanWorkflow implements Workflow {

    private static final Logger log = LoggerFactory.getLogger(HomeLoanWorkflow.class);

    @Override
    public WorkflowStub create() {
        return ctx -> {
            log.info("=== Home Loan Workflow Started ===");
            log.info("Workflow Instance ID: {}", ctx.getInstanceId());

            // Get input loan application
            LoanApplication application = ctx.getInput(LoanApplication.class);
            log.info("Processing loan application for: {}", application.applicantName());
            log.info("Application ID: {}", application.applicationId());

            List<AgentResult> agentResults = new ArrayList<>();

            try {
                // Step 1: Document Verification
                log.info("Step 1: Calling Document Verification Agent...");
                AgentResult docResult = ctx.callActivity(
                    DocumentVerificationAgent.class.getName(),
                    application,
                    AgentResult.class
                ).await();
                agentResults.add(docResult);
                log.info("Document Verification: {} (Confidence: {}%)",
                    docResult.recommendation(), docResult.confidenceScore());

                // Early exit if documents are invalid - set output and return
                if (!docResult.success() && "REJECT".equals(docResult.recommendation())) {
                    log.warn("Workflow terminated: Document verification failed");
                    ctx.complete(WorkflowResult.rejected(
                        application.applicationId(),
                        "Document verification failed: " + docResult.reasoning(),
                        agentResults
                    ));
                    return;
                }

                // Step 2: Credit Check
                log.info("Step 2: Calling Credit Check Agent...");
                AgentResult creditResult = ctx.callActivity(
                    CreditCheckAgent.class.getName(),
                    application,
                    AgentResult.class
                ).await();
                agentResults.add(creditResult);
                log.info("Credit Check: {} (Confidence: {}%)",
                    creditResult.recommendation(), creditResult.confidenceScore());

                // Step 3: Eligibility Calculation
                log.info("Step 3: Calling Eligibility Agent...");
                AgentResult eligibilityResult = ctx.callActivity(
                    EligibilityAgent.class.getName(),
                    application,
                    AgentResult.class
                ).await();
                agentResults.add(eligibilityResult);
                log.info("Eligibility: {} (Confidence: {}%)",
                    eligibilityResult.recommendation(), eligibilityResult.confidenceScore());

                // Step 4: Final Approval Decision
                log.info("Step 4: Calling Approval Agent for final decision...");
                ApprovalAgent.ApprovalInput approvalInput = new ApprovalAgent.ApprovalInput(
                    application, docResult, creditResult, eligibilityResult
                );

                AgentResult approvalResult = ctx.callActivity(
                    ApprovalAgent.class.getName(),
                    approvalInput,
                    AgentResult.class
                ).await();
                agentResults.add(approvalResult);
                log.info("Final Decision: {} (Confidence: {}%)",
                    approvalResult.recommendation(), approvalResult.confidenceScore());

                // Build final result and complete workflow
                WorkflowResult result = buildFinalResult(application, agentResults, approvalResult);

                log.info("=== Home Loan Workflow Completed ===");
                log.info("Final Decision: {}", result.finalDecision());

                ctx.complete(result);

            } catch (OrchestratorBlockedException e) {
                // This is a control flow exception - DO NOT catch it, rethrow!
                throw e;
            } catch (Exception e) {
                log.error("Workflow failed with error: {}", e.getMessage(), e);
                ctx.complete(WorkflowResult.rejected(
                    application.applicationId(),
                    "Workflow error: " + e.getMessage(),
                    agentResults
                ));
            }
        };
    }

    private static WorkflowResult buildFinalResult(LoanApplication app,
                                                    List<AgentResult> results,
                                                    AgentResult approval) {
        String decision = approval.recommendation();
        int avgConfidence = results.stream()
            .mapToInt(AgentResult::confidenceScore)
            .sum() / results.size();

        if ("APPROVED".equals(decision) || "APPROVE".equals(decision)) {
            // Calculate EMI (8.5% interest rate)
            double rate = 8.5 / 12 / 100;
            int months = app.loanTenureYears() * 12;
            double factor = Math.pow(1 + rate, months);
            double emi = app.requestedLoanAmount() * rate * factor / (factor - 1);

            return WorkflowResult.approved(
                app.applicationId(),
                app.requestedLoanAmount(),
                8.5,
                emi,
                avgConfidence,
                "Loan approved. All verification checks passed.",
                results
            );
        } else if ("REJECTED".equals(decision) || "REJECT".equals(decision)) {
            return WorkflowResult.rejected(
                app.applicationId(),
                approval.reasoning(),
                results
            );
        } else {
            return WorkflowResult.manualReview(
                app.applicationId(),
                approval.reasoning(),
                avgConfidence,
                results
            );
        }
    }
}

