package com.hdfcbank.standin.config;

import com.hdfcbank.standin.agents.ApprovalAgent;
import com.hdfcbank.standin.agents.CreditCheckAgent;
import com.hdfcbank.standin.agents.DocumentVerificationAgent;
import com.hdfcbank.standin.agents.EligibilityAgent;
import com.hdfcbank.standin.agents.core.AgentToolRegistry;
import com.hdfcbank.standin.workflow.HomeLoanWorkflow;
import io.dapr.workflows.runtime.WorkflowRuntime;
import io.dapr.workflows.runtime.WorkflowRuntimeBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Dapr Workflow registration.
 * Registers all workflows and activities with the Dapr Workflow Runtime.
 *
 * Now supports TRUE AI AGENTS with:
 * - Tool Registry for agent tools (PAN, Aadhaar, Credit Bureau, etc.)
 * - ChatClient for LLM reasoning
 */
@Configuration
public class WorkflowConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowConfig.class);

    private final ChatClient chatClient;
    private final AgentToolRegistry toolRegistry;
    private WorkflowRuntime workflowRuntime;

    public WorkflowConfig(ChatClient.Builder chatClientBuilder, AgentToolRegistry toolRegistry) {
        this.chatClient = chatClientBuilder.build();
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    public void initWorkflowRuntime() {
        log.info("=== Initializing Dapr Workflow Runtime with AI Agents ===");

        try {
            // Inject ChatClient into all agents
            DocumentVerificationAgent.setChatClient(chatClient);
            CreditCheckAgent.setChatClient(chatClient);
            EligibilityAgent.setChatClient(chatClient);
            ApprovalAgent.setChatClient(chatClient);
            log.info("✅ ChatClient injected into all AI Agents");

            // Inject Tool Registry into agents that use tools
            DocumentVerificationAgent.setToolRegistry(toolRegistry);
            CreditCheckAgent.setToolRegistry(toolRegistry);
            EligibilityAgent.setToolRegistry(toolRegistry);
            log.info("✅ ToolRegistry injected into AI Agents (Tools: {})",
                toolRegistry.getAllTools().size());
            
            // Build workflow runtime
            WorkflowRuntimeBuilder builder = new WorkflowRuntimeBuilder();
            
            // Register the main workflow
            builder.registerWorkflow(HomeLoanWorkflow.class);
            log.info("Registered workflow: HomeLoanWorkflow");
            
            // Register all activity agents
            builder.registerActivity(DocumentVerificationAgent.class);
            log.info("Registered activity: DocumentVerificationAgent");
            
            builder.registerActivity(CreditCheckAgent.class);
            log.info("Registered activity: CreditCheckAgent");
            
            builder.registerActivity(EligibilityAgent.class);
            log.info("Registered activity: EligibilityAgent");
            
            builder.registerActivity(ApprovalAgent.class);
            log.info("Registered activity: ApprovalAgent");
            
            // Build and start the runtime
            workflowRuntime = builder.build();
            log.info("Workflow runtime built successfully");
            
            // Start the workflow runtime in a separate thread
            Thread workflowThread = new Thread(() -> {
                try {
                    log.info("Starting Dapr Workflow Runtime...");
                    workflowRuntime.start();
                } catch (Exception e) {
                    log.error("Workflow runtime error: {}", e.getMessage(), e);
                }
            }, "dapr-workflow-runtime");
            workflowThread.setDaemon(true);
            workflowThread.start();
            
            log.info("=== Dapr Workflow Runtime Started ===");
            
        } catch (Exception e) {
            log.error("Failed to initialize Dapr Workflow Runtime: {}", e.getMessage(), e);
            throw new RuntimeException("Workflow initialization failed", e);
        }
    }
    
    @PreDestroy
    public void shutdownWorkflowRuntime() {
        if (workflowRuntime != null) {
            log.info("Shutting down Dapr Workflow Runtime...");
            try {
                workflowRuntime.close();
                log.info("Dapr Workflow Runtime shutdown complete");
            } catch (Exception e) {
                log.error("Error shutting down workflow runtime: {}", e.getMessage(), e);
            }
        }
    }
}

