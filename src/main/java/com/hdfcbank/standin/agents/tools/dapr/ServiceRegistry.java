package com.hdfcbank.standin.agents.tools.dapr;

/**
 * Registry of external Dapr service app-ids.
 * 
 * These are the Dapr app-ids of the microservices that provide
 * the actual API implementations. In production, these services
 * would connect to real external APIs (NSDL, UIDAI, CIBIL, etc.)
 * 
 * Service Discovery:
 * - In Kubernetes: Dapr uses DNS-based discovery
 * - In Self-hosted: Dapr uses mDNS or placement service
 * - No hardcoded URLs needed!
 */
public final class ServiceRegistry {
    
    private ServiceRegistry() {} // Utility class
    
    // ============ DOCUMENT VERIFICATION SERVICES ============
    
    /**
     * PAN Validation Service
     * Validates PAN numbers against NSDL/Income Tax Department
     */
    public static final String PAN_VALIDATION_SERVICE = "pan-validation-service";
    
    /**
     * Aadhaar Validation Service
     * Validates Aadhaar numbers against UIDAI
     */
    public static final String AADHAAR_VALIDATION_SERVICE = "aadhaar-validation-service";
    
    /**
     * Income Verification Service
     * Verifies income against ITR records
     */
    public static final String INCOME_VERIFICATION_SERVICE = "income-verification-service";
    
    /**
     * Fraud Detection Service
     * Checks for fraud indicators and risk scoring
     */
    public static final String FRAUD_DETECTION_SERVICE = "fraud-detection-service";
    
    // ============ CREDIT SERVICES ============
    
    /**
     * Credit Bureau Service
     * Fetches credit reports from CIBIL/Experian/Equifax
     */
    public static final String CREDIT_BUREAU_SERVICE = "credit-bureau-service";
    
    // ============ PROPERTY SERVICES ============
    
    /**
     * Property Valuation Service
     * Gets property valuations from registered valuers
     */
    public static final String PROPERTY_VALUATION_SERVICE = "property-valuation-service";
    
    // ============ METHOD NAMES ============
    
    public static final class Methods {
        // PAN Service
        public static final String VALIDATE_PAN = "validate";
        
        // Aadhaar Service
        public static final String VALIDATE_AADHAAR = "validate";
        public static final String VERIFY_OTP = "verify-otp";
        
        // Income Service
        public static final String VERIFY_INCOME = "verify";
        public static final String GET_ITR_SUMMARY = "itr-summary";
        
        // Fraud Service
        public static final String CHECK_FRAUD = "check";
        public static final String GET_RISK_SCORE = "risk-score";
        
        // Credit Bureau Service
        public static final String FETCH_REPORT = "fetch-report";
        public static final String GET_SCORE = "score";
        
        // Property Service
        public static final String GET_VALUATION = "valuate";
        public static final String VERIFY_OWNERSHIP = "verify-ownership";
        
        // Common
        public static final String HEALTH = "health";
    }
}

