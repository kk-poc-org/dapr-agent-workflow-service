package com.hdfcbank.standin.agents.tools.dapr;

import com.hdfcbank.standin.agents.tools.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

/**
 * Base class for tools that can use Dapr Service Invocation.
 * 
 * Provides:
 * - Automatic fallback to local mock when Dapr is unavailable
 * - Consistent error handling
 * - Logging and metrics
 * 
 * Tools extending this class can work in two modes:
 * 1. DAPR mode: Calls external microservices via Dapr
 * 2. LOCAL mode: Uses built-in mock implementation (for testing/development)
 */
public abstract class DaprEnabledTool implements AgentTool {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected final DaprServiceClient daprClient;
    protected final boolean useDapr;
    
    protected DaprEnabledTool(
            DaprServiceClient daprClient,
            @Value("${tools.use-dapr:false}") boolean useDapr) {
        this.daprClient = daprClient;
        this.useDapr = useDapr;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        log.info("🔧 Tool [{}] executing with params: {}", getName(), parameters.keySet());
        
        try {
            if (useDapr) {
                return executeDapr(parameters);
            } else {
                return executeLocal(parameters);
            }
        } catch (DaprServiceException e) {
            log.warn("⚠️ Dapr service call failed, falling back to local: {}", e.getMessage());
            // Fallback to local implementation if Dapr fails
            return executeLocal(parameters);
        } catch (Exception e) {
            log.error("❌ Tool execution failed: {}", e.getMessage(), e);
            return ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Execute using Dapr Service Invocation.
     * Calls the external microservice via Dapr sidecar.
     */
    protected abstract ToolResult executeDapr(Map<String, Object> parameters);
    
    /**
     * Execute using local mock implementation.
     * Used for testing or when Dapr is unavailable.
     */
    protected abstract ToolResult executeLocal(Map<String, Object> parameters);
    
    /**
     * Get the Dapr app-id of the target service.
     */
    protected abstract String getServiceAppId();
    
    /**
     * Get the method name to invoke on the service.
     */
    protected abstract String getServiceMethod();
    
    /**
     * Helper to invoke Dapr service and convert response to ToolResult.
     */
    protected ToolResult invokeDaprService(Map<String, Object> requestBody) {
        Map<String, Object> response = daprClient.invoke(
            getServiceAppId(), 
            getServiceMethod(), 
            requestBody
        );
        
        boolean success = (Boolean) response.getOrDefault("success", false);
        
        if (success) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            String summary = buildSummary(data);
            return ToolResult.success(summary, data);
        } else {
            String error = (String) response.getOrDefault("error", "Unknown error");
            return ToolResult.failure(error);
        }
    }
    
    /**
     * Build a human-readable summary from the response data.
     * Override in subclasses for custom formatting.
     */
    protected String buildSummary(Map<String, Object> data) {
        return data.toString();
    }
}

