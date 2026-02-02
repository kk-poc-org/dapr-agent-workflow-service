package com.hdfcbank.standin.controller;

import com.hdfcbank.standin.agents.autonomous.AgentDecision;
import com.hdfcbank.standin.agents.autonomous.AutonomousLoanAgent;
import com.hdfcbank.standin.model.LoanApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for the Autonomous Loan Processing Agent.
 *
 * This exposes the truly agentic loan processing capability where:
 * - The LLM decides which tools to call
 * - The LLM decides the order of operations
 * - The LLM reasons through the entire process
 * - The LLM makes the final decision autonomously
 */
@RestController
@RequestMapping("/api/autonomous")
public class AutonomousAgentController {

    private static final Logger log = LoggerFactory.getLogger(AutonomousAgentController.class);

    private final AutonomousLoanAgent autonomousAgent;

    public AutonomousAgentController(AutonomousLoanAgent autonomousAgent) {
        this.autonomousAgent = autonomousAgent;
    }

    /**
     * Request DTO for loan application processing.
     */
    public record LoanRequest(
        String applicationId,
        String applicantName,
        String panNumber,
        String aadhaarNumber,
        double annualIncome,
        Double loanAmount,           // Maps to requestedLoanAmount
        Double requestedLoanAmount,  // Alternative field name
        Integer loanTenureYears,
        Integer creditScore,
        Double existingEmi,
        String employmentType,
        String employerName,
        Integer yearsOfExperience,
        String propertyType,
        Double propertyValue,
        String propertyCity,         // Maps to propertyLocation
        String propertyLocation      // Alternative field name
    ) {
        public LoanApplication toLoanApplication() {
            String appId = applicationId != null ? applicationId : "LOAN-" + System.currentTimeMillis();
            double loan = loanAmount != null ? loanAmount : (requestedLoanAmount != null ? requestedLoanAmount : 0);
            String location = propertyCity != null ? propertyCity : (propertyLocation != null ? propertyLocation : "Unknown");
            return new LoanApplication(
                appId,
                applicantName,
                panNumber,
                aadhaarNumber,
                annualIncome,
                loan,
                loanTenureYears != null ? loanTenureYears : 20,
                creditScore != null ? creditScore : 0,
                existingEmi != null ? existingEmi : 0,
                employmentType != null ? employmentType : "SALARIED",
                employerName,
                yearsOfExperience != null ? yearsOfExperience : 0,
                propertyType != null ? propertyType : "APARTMENT",
                propertyValue != null ? propertyValue : 0,
                location
            );
        }
    }

    /**
     * Process a loan application using the autonomous agent.
     * The agent will use LLM to decide which tools to call and make the final decision.
     */
    @PostMapping("/process")
    public ResponseEntity<AgentDecision> processLoanApplication(@RequestBody LoanRequest request) {
        LoanApplication application = request.toLoanApplication();
        log.info("🤖 Received autonomous processing request for application: {}", application.applicationId());

        try {
            AgentDecision decision = autonomousAgent.processApplication(application);

            log.info("✅ Autonomous agent completed. Decision: {} (Confidence: {}%)",
                decision.decision(), decision.confidenceScore());

            return ResponseEntity.ok(decision);
        } catch (Exception e) {
            log.error("❌ Autonomous processing failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(AgentDecision.manualReview(
                    application.applicationId(),
                    "Processing failed: " + e.getMessage(),
                    "Error occurred during autonomous processing"
                ));
        }
    }
    
    /**
     * Process a sample loan application for testing.
     */
    @PostMapping("/process/sample")
    public ResponseEntity<AgentDecision> processSampleApplication() {
        log.info("🧪 Processing sample loan application");
        LoanApplication sample = LoanApplication.createSample();
        AgentDecision decision = autonomousAgent.processApplication(sample);
        return ResponseEntity.ok(decision);
    }
    
    /**
     * Health check for the autonomous agent.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "agent", "AutonomousLoanAgent",
            "description", "Truly agentic loan processing with LLM-driven tool selection"
        ));
    }
}

