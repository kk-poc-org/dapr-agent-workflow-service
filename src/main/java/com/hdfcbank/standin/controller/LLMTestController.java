package com.hdfcbank.standin.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test controller to verify LLM connection.
 *
 * Current Configuration: Groq (FREE)
 * - Get API key from: https://console.groq.com/keys
 * - Model: llama-3.3-70b-versatile
 * - Free tier: 30 requests/minute
 */
@RestController
@RequestMapping("/api/llm")
public class LLMTestController {

    private static final Logger log = LoggerFactory.getLogger(LLMTestController.class);

    private final ChatClient chatClient;

    public LLMTestController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
            .defaultSystem("You are a helpful AI assistant for home loan processing.")
            .build();
        log.info("LLMTestController initialized with ChatClient");
    }

    /**
     * Simple test endpoint to verify LLM is working.
     * GET /api/llm/test
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testConnection() {
        log.info("=== LLM Test Connection Started ===");
        try {
            log.debug("Sending test prompt to LLM...");
            long startTime = System.currentTimeMillis();

            String response = chatClient.prompt()
                .user("Say 'Hello! Groq LLM is working!' in exactly those words.")
                .call()
                .content();

            long duration = System.currentTimeMillis() - startTime;
            log.info("LLM response received in {} ms", duration);
            log.debug("LLM Response: {}", response);

            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "model", "llama-3.3-70b-versatile (Groq)",
                "response", response
            ));
        } catch (Exception e) {
            log.error("LLM Test Connection Failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "ERROR",
                "message", e.getMessage(),
                "hint", "Check your Groq API key"
            ));
        }
    }

    /**
     * Test document verification prompt.
     * POST /api/llm/verify-document
     */
    @PostMapping("/verify-document")
    public ResponseEntity<Map<String, Object>> verifyDocument(@RequestBody DocumentRequest request) {
        log.info("=== Document Verification Started ===");
        log.info("Applicant: {}, Document Type: {}", request.applicantName(), request.documentType());
        log.debug("Document Details: {}", request.details());

        try {
            String prompt = """
                Analyze this document for a home loan application and return JSON:

                Applicant: %s
                Document Type: %s
                Document Details: %s

                Return JSON with structure:
                {
                    "isValid": boolean,
                    "confidence": number (0-100),
                    "issues": ["list of issues if any"],
                    "recommendation": "APPROVE/REJECT/MANUAL_REVIEW"
                }
                """.formatted(request.applicantName(), request.documentType(), request.details());

            log.debug("Sending prompt to LLM for document verification...");
            long startTime = System.currentTimeMillis();

            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Document verification completed in {} ms", duration);
            log.debug("LLM Analysis Response: {}", response);

            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "model", "llama-3.3-70b-versatile (Groq)",
                "analysis", response
            ));
        } catch (Exception e) {
            log.error("Document Verification Failed for applicant {}: {}", request.applicantName(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            ));
        }
    }

    public record DocumentRequest(
        String applicantName,
        String documentType,
        String details
    ) {}

    /**
     * Chat endpoint - accepts user input and returns LLM response.
     * POST /api/llm/chat
     *
     * Request Body: { "ask-anything": "your question here" }
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        log.info("=== Chat Request Received ===");

        try {
            String userQuery = request.get("ask-anything");
            log.debug("User Query: {}", userQuery);

            if (userQuery == null || userQuery.isBlank()) {
                log.warn("Empty or missing 'ask-anything' field in request");
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Please provide 'ask-anything' field in request body"
                ));
            }

            log.info("Processing query: {}", userQuery.length() > 50 ? userQuery.substring(0, 50) + "..." : userQuery);
            long startTime = System.currentTimeMillis();

            String response = chatClient.prompt()
                .user(userQuery)
                .call()
                .content();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Chat response generated in {} ms", duration);
            log.debug("LLM Response: {}", response);

            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "model", "llama-3.3-70b-versatile (Groq)",
                "ask-anything", userQuery,
                "response", response
            ));
        } catch (Exception e) {
            log.error("Chat Request Failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            ));
        }
    }
}

