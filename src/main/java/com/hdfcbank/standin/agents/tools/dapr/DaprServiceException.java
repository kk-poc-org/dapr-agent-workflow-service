package com.hdfcbank.standin.agents.tools.dapr;

/**
 * Exception thrown when a Dapr service invocation fails.
 */
public class DaprServiceException extends RuntimeException {
    
    private final String appId;
    private final String methodName;
    private final int statusCode;
    private final String responseBody;
    
    public DaprServiceException(String appId, String methodName, int statusCode, String responseBody) {
        super(String.format("Dapr service invocation failed: %s/%s returned %d: %s", 
                appId, methodName, statusCode, responseBody));
        this.appId = appId;
        this.methodName = methodName;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getResponseBody() {
        return responseBody;
    }
    
    public boolean isServiceUnavailable() {
        return statusCode == 503 || statusCode == 502 || statusCode == 504;
    }
    
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
    
    public boolean isServerError() {
        return statusCode >= 500;
    }
}

