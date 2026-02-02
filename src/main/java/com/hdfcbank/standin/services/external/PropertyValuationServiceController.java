package com.hdfcbank.standin.services.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mock Property Valuation Service.
 * 
 * In production, this would connect to registered property valuers,
 * government land records, and real estate databases.
 * 
 * Dapr App ID: property-valuation-service
 */
@RestController
@RequestMapping("/mock/property")
public class PropertyValuationServiceController {
    
    private static final Logger log = LoggerFactory.getLogger(PropertyValuationServiceController.class);
    
    // Location-based price multipliers (per sq ft)
    private static final Map<String, Integer> LOCATION_PRICES = Map.of(
        "MUMBAI", 25000,
        "DELHI", 18000,
        "BANGALORE", 12000,
        "CHENNAI", 10000,
        "HYDERABAD", 8000,
        "PUNE", 9000,
        "KOLKATA", 7000,
        "AHMEDABAD", 6000
    );
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "property-valuation-service",
            "timestamp", Instant.now().toString()
        ));
    }
    
    /**
     * Get property valuation.
     */
    @PostMapping("/valuate")
    public ResponseEntity<Map<String, Object>> valuateProperty(@RequestBody Map<String, Object> request) {
        String propertyType = (String) request.getOrDefault("property_type", "APARTMENT");
        String location = (String) request.getOrDefault("location", "MUMBAI");
        Object declaredValueObj = request.get("declared_value");
        Object areaSqFtObj = request.get("area_sqft");
        
        double declaredValue = declaredValueObj != null ? ((Number) declaredValueObj).doubleValue() : 0;
        int areaSqFt = areaSqFtObj != null ? ((Number) areaSqFtObj).intValue() : 1000;
        
        log.info("🏠 Property Valuation Request: {} in {}, Declared=₹{}", 
                propertyType, location, declaredValue);
        
        simulateApiLatency();
        
        // Calculate market value
        String normalizedLocation = location.toUpperCase().split(",")[0].trim();
        int pricePerSqFt = LOCATION_PRICES.getOrDefault(normalizedLocation, 5000);
        
        Random random = new Random(location.hashCode());
        // Add some variance (±10%)
        double variance = 0.9 + (random.nextDouble() * 0.2);
        double marketValue = areaSqFt * pricePerSqFt * variance;
        
        // Calculate valuation vs declared
        double valuationRatio = declaredValue > 0 ? marketValue / declaredValue : 1.0;
        String valuationStatus = getValuationStatus(valuationRatio);
        
        log.info("✅ Property valued: Market=₹{}, Ratio={}",
                String.format("%.0f", marketValue), String.format("%.2f", valuationRatio));

        Map<String, Object> data = new HashMap<>();
        data.put("marketValue", Math.round(marketValue));
        data.put("declaredValue", declaredValue);
        data.put("valuationRatio", Math.round(valuationRatio * 100) / 100.0);
        data.put("valuationStatus", valuationStatus);
        data.put("pricePerSqFt", pricePerSqFt);
        data.put("areaSqFt", areaSqFt);
        data.put("propertyType", propertyType);
        data.put("location", location);
        data.put("locationTier", getLocationTier(normalizedLocation));
        data.put("marketTrend", random.nextBoolean() ? "APPRECIATING" : "STABLE");
        data.put("legalStatus", "CLEAR");
        data.put("encumbranceCheck", "NO_ENCUMBRANCE");
        data.put("valuerId", "VAL-" + random.nextInt(1000));
        data.put("valuedAt", Instant.now().toString());

        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
    
    /**
     * Verify property ownership.
     */
    @PostMapping("/verify-ownership")
    public ResponseEntity<Map<String, Object>> verifyOwnership(@RequestBody Map<String, Object> request) {
        String propertyId = (String) request.getOrDefault("property_id", "PROP-001");
        String ownerName = (String) request.getOrDefault("owner_name", "Unknown");
        
        simulateApiLatency();
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "ownershipVerified", true,
                "registeredOwner", ownerName.toUpperCase(),
                "propertyId", propertyId,
                "registrationDate", "2020-05-15",
                "titleStatus", "CLEAR",
                "mortgageStatus", "NO_EXISTING_MORTGAGE",
                "verifiedAt", Instant.now().toString()
            )
        ));
    }
    
    private String getValuationStatus(double ratio) {
        if (ratio >= 0.9 && ratio <= 1.1) return "FAIR_VALUE";
        if (ratio > 1.1) return "UNDERVALUED";
        if (ratio < 0.9 && ratio >= 0.7) return "SLIGHTLY_OVERVALUED";
        return "SIGNIFICANTLY_OVERVALUED";
    }
    
    private String getLocationTier(String location) {
        if (java.util.List.of("MUMBAI", "DELHI", "BANGALORE").contains(location)) return "TIER_1";
        if (java.util.List.of("CHENNAI", "HYDERABAD", "PUNE", "KOLKATA").contains(location)) return "TIER_2";
        return "TIER_3";
    }

    private void simulateApiLatency() {
        try { Thread.sleep(200 + (int)(Math.random() * 300)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

