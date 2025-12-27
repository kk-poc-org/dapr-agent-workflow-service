package com.hdfcbank.standin.controller;

import com.hdfcbank.standin.agents.autonomous.AgentDecision;
import com.hdfcbank.standin.agents.autonomous.AutonomousLoanAgent;
import com.hdfcbank.standin.model.LoanApplication;
import com.hdfcbank.standin.model.WorkflowResult;
import com.hdfcbank.standin.workflow.HomeLoanWorkflow;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.dapr.workflows.client.WorkflowInstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Home Loan Workflow operations.
 * 
 * Endpoints:
 * - POST /api/loan/apply - Submit new loan application
 * - GET /api/loan/status/{instanceId} - Get workflow status
 * - POST /api/loan/sample - Submit sample loan application
 */
@RestController
@RequestMapping("/api/loan")
public class LoanWorkflowController {
    
    private static final Logger log = LoggerFactory.getLogger(LoanWorkflowController.class);
    
	    private final DaprWorkflowClient workflowClient;
	    private final AutonomousLoanAgent autonomousLoanAgent;
	    
	    public LoanWorkflowController(AutonomousLoanAgent autonomousLoanAgent) {
	        this.workflowClient = new DaprWorkflowClient();
	        this.autonomousLoanAgent = autonomousLoanAgent;
	    }
    
    /**
     * Submit a new loan application.
     * POST /api/loan/apply
     */
    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> applyForLoan(@RequestBody LoanApplication application) {
        log.info("=== New Loan Application Received ===");
        log.info("Applicant: {}", application.applicantName());
        log.info("Requested Amount: ₹{}", application.requestedLoanAmount());
        
        try {
            // Generate instance ID if not provided
            String instanceId = application.applicationId() != null ? 
                application.applicationId() : "LOAN-" + UUID.randomUUID().toString().substring(0, 8);
            
            // Create application with ID
            LoanApplication appWithId = new LoanApplication(
                instanceId,
                application.applicantName(),
                application.panNumber(),
                application.aadhaarNumber(),
                application.annualIncome(),
                application.requestedLoanAmount(),
                application.loanTenureYears(),
                application.creditScore(),
                application.existingEmi(),
                application.employmentType(),
                application.employerName(),
                application.yearsOfExperience(),
                application.propertyType(),
                application.propertyValue(),
                application.propertyLocation()
            );
            
            // Start the workflow
            log.info("Starting HomeLoanWorkflow with instance ID: {}", instanceId);
            String workflowInstanceId = workflowClient.scheduleNewWorkflow(
                HomeLoanWorkflow.class,
                appWithId,
                instanceId
            );
            
            log.info("Workflow started successfully. Instance ID: {}", workflowInstanceId);
            
            return ResponseEntity.accepted().body(Map.of(
                "status", "ACCEPTED",
                "message", "Loan application submitted successfully",
                "instanceId", workflowInstanceId,
                "applicationId", instanceId,
                "applicantName", application.applicantName(),
                "requestedAmount", application.requestedLoanAmount(),
                "statusUrl", "/api/loan/status/" + workflowInstanceId
            ));
            
        } catch (Exception e) {
            log.error("Failed to start workflow: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "ERROR",
                "message", "Failed to submit loan application: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get workflow status by instance ID.
     * GET /api/loan/status/{instanceId}
     */
    @GetMapping("/status/{instanceId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String instanceId) {
        log.info("Getting status for workflow: {}", instanceId);
        
        try {
            WorkflowInstanceStatus status = workflowClient.getInstanceState(instanceId, true);
            
            if (status == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = Map.of(
                "instanceId", instanceId,
                "workflowName", status.getName(),
                "runtimeStatus", status.getRuntimeStatus().name(),
                "createdAt", status.getCreatedAt().toString(),
                "lastUpdatedAt", status.getLastUpdatedAt().toString(),
                "isCompleted", status.isCompleted(),
                "output", status.isCompleted() ?
                    status.readOutputAs(WorkflowResult.class) : "Workflow in progress"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get workflow status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "ERROR",
                "message", "Failed to get status: " + e.getMessage()
            ));
        }
    }
    
	    /**
	     * Submit a sample loan application for testing.
	     * POST /api/loan/sample
	     */
	    @PostMapping("/sample")
	    public ResponseEntity<Map<String, Object>> submitSampleApplication() {
	        log.info("=== Submitting Sample Loan Application ===");
	        
	        LoanApplication sample = LoanApplication.createSample();
	        return applyForLoan(sample);
	    }

	    /**
	     * Submit a loan application using the truly agentic LLM-powered processor.
	     * POST /api/loan/apply/agentic
	     */
	    @PostMapping("/apply/agentic")
	    public ResponseEntity<AgentDecision> applyForLoanAgentic(@RequestBody LoanApplication application) {
	        log.info("=== New Agentic Loan Application Received ===");
	        log.info("Applicant: {}", application.applicantName());
	        log.info("Requested Amount: {}", application.requestedLoanAmount());

	        try {
	            String applicationId = application.applicationId() != null
	                ? application.applicationId()
	                : "LOAN-" + UUID.randomUUID().toString().substring(0, 8);

	            LoanApplication appWithId = new LoanApplication(
	                applicationId,
	                application.applicantName(),
	                application.panNumber(),
	                application.aadhaarNumber(),
	                application.annualIncome(),
	                application.requestedLoanAmount(),
	                application.loanTenureYears(),
	                application.creditScore(),
	                application.existingEmi(),
	                application.employmentType(),
	                application.employerName(),
	                application.yearsOfExperience(),
	                application.propertyType(),
	                application.propertyValue(),
	                application.propertyLocation()
	            );

	            AgentDecision decision = autonomousLoanAgent.processApplication(appWithId);
	            log.info("0 Agentic decision: {} (Confidence: {}%)", decision.decision(), decision.confidenceScore());
	            return ResponseEntity.ok(decision);
	        } catch (Exception e) {
	            log.error(" Agentic processing failed: {}", e.getMessage(), e);
	            return ResponseEntity.internalServerError()
	                .body(AgentDecision.manualReview(
	                    application.applicationId() != null ? application.applicationId() : "UNKNOWN",
	                    "Processing failed: " + e.getMessage(),
	                    "Error occurred during agentic processing"
	                ));
	        }
	    }
     
    /**
     * Wait for workflow completion.
     * GET /api/loan/wait/{instanceId}
     */
    @GetMapping("/wait/{instanceId}")
    public ResponseEntity<Map<String, Object>> waitForCompletion(
            @PathVariable String instanceId,
            @RequestParam(defaultValue = "60") int timeoutSeconds) {
        log.info("Waiting for workflow completion: {} (timeout: {}s)", instanceId, timeoutSeconds);
        
        try {
            WorkflowInstanceStatus status = workflowClient.waitForInstanceCompletion(
                instanceId, 
                Duration.ofSeconds(timeoutSeconds), 
                true
            );
            
            if (status == null) {
                return ResponseEntity.notFound().build();
            }
            
            WorkflowResult result = status.readOutputAs(WorkflowResult.class);
            
            return ResponseEntity.ok(Map.of(
                "instanceId", instanceId,
                "runtimeStatus", status.getRuntimeStatus().name(),
                "result", result
            ));
            
        } catch (Exception e) {
            log.error("Error waiting for workflow: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            ));
        }
    }
}

