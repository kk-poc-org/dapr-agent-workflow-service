package com.hdfcbank.standin.agents.tools.dapr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Dapr Service Invocation Client.
 * 
 * Uses Dapr's Service Invocation building block to call external microservices.
 * This provides:
 * - Service discovery (no hardcoded URLs)
 * - Load balancing
 * - Retries and circuit breakers (via resiliency policies)
 * - mTLS security between services
 * - Distributed tracing
 * 
 * @see <a href="https://docs.dapr.io/developing-applications/building-blocks/service-invocation/">Dapr Service Invocation</a>
 */
@Component
public class DaprServiceClient {
    
    private static final Logger log = LoggerFactory.getLogger(DaprServiceClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final HttpClient httpClient;
    private final String daprHost;
    private final int daprPort;
    
    public DaprServiceClient(
            @Value("${dapr.host:localhost}") String daprHost,
            @Value("${dapr.http.port:3500}") int daprPort) {
        this.daprHost = daprHost;
        this.daprPort = daprPort;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("DaprServiceClient initialized: {}:{}", daprHost, daprPort);
    }
    
    /**
     * Invoke a method on a Dapr service.
     * 
     * @param appId The Dapr app-id of the target service
     * @param methodName The method/endpoint to invoke
     * @param requestBody The request payload
     * @return Response as a Map
     */
    public Map<String, Object> invoke(String appId, String methodName, Map<String, Object> requestBody) {
        return invoke(appId, methodName, requestBody, new TypeReference<Map<String, Object>>() {});
    }
    
    /**
     * Invoke a method on a Dapr service with typed response.
     */
    public <T> T invoke(String appId, String methodName, Map<String, Object> requestBody, TypeReference<T> responseType) {
        String url = String.format("http://%s:%d/v1.0/invoke/%s/method/%s", 
                daprHost, daprPort, appId, methodName);
        
        log.info("🔗 Dapr Service Invocation: {} -> {}/{}", appId, methodName, requestBody.keySet());
        
        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("dapr-app-id", appId)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("✅ Service {} responded successfully", appId);
                return objectMapper.readValue(response.body(), responseType);
            } else {
                log.error("❌ Service {} returned error: {} - {}", appId, response.statusCode(), response.body());
                throw new DaprServiceException(appId, methodName, response.statusCode(), response.body());
            }
            
        } catch (DaprServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Failed to invoke service {}: {}", appId, e.getMessage());
            throw new DaprServiceException(appId, methodName, 500, e.getMessage());
        }
    }
    
    /**
     * Invoke with GET method (for simple queries).
     */
    public Map<String, Object> invokeGet(String appId, String methodName, Map<String, String> queryParams) {
        StringBuilder urlBuilder = new StringBuilder(
                String.format("http://%s:%d/v1.0/invoke/%s/method/%s", daprHost, daprPort, appId, methodName));
        
        if (queryParams != null && !queryParams.isEmpty()) {
            urlBuilder.append("?");
            queryParams.forEach((k, v) -> urlBuilder.append(k).append("=").append(v).append("&"));
        }
        
        String url = urlBuilder.toString();
        log.info("🔗 Dapr GET: {}", url);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("dapr-app-id", appId)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                throw new DaprServiceException(appId, methodName, response.statusCode(), response.body());
            }
        } catch (DaprServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DaprServiceException(appId, methodName, 500, e.getMessage());
        }
    }
    
    /**
     * Check if a service is available.
     */
    public boolean isServiceAvailable(String appId) {
        try {
            invokeGet(appId, "health", null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

