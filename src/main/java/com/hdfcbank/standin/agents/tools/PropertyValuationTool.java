package com.hdfcbank.standin.agents.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Property Valuation Tool.
 * Fetches property valuation and market data.
 * 
 * In production, this would integrate with property valuation APIs and government registries.
 */
@Component
public class PropertyValuationTool implements AgentTool {
    
    private static final Logger log = LoggerFactory.getLogger(PropertyValuationTool.class);
    
    // City-wise price per sq ft (approximate)
    private static final Map<String, Integer> CITY_RATES = Map.of(
        "mumbai", 25000, "delhi", 15000, "bangalore", 12000, "chennai", 10000,
        "hyderabad", 8000, "pune", 9000, "kolkata", 7000, "ahmedabad", 6000
    );
    
    @Override
    public String getName() {
        return "get_property_valuation";
    }
    
    @Override
    public String getDescription() {
        return "Fetches property valuation based on location, type, and market data. " +
               "Returns estimated market value, price per sq ft, and LTV calculation. " +
               "Use this when you need to assess property value for loan eligibility.";
    }
    
    @Override
    public String getParameterSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "property_location": {"type": "string", "description": "City/Location of property"},
                    "property_type": {"type": "string", "description": "APARTMENT/VILLA/PLOT/COMMERCIAL"},
                    "declared_value": {"type": "number", "description": "Declared property value"},
                    "carpet_area_sqft": {"type": "number", "description": "Carpet area in sq ft"}
                },
                "required": ["property_location", "declared_value"]
            }
            """;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        String location = (String) parameters.get("property_location");
        String propertyType = (String) parameters.getOrDefault("property_type", "APARTMENT");
        double declaredValue = ((Number) parameters.get("declared_value")).doubleValue();
        double carpetArea = ((Number) parameters.getOrDefault("carpet_area_sqft", 1000)).doubleValue();
        
        log.info("🔧 Tool [get_property_valuation] executing for: {} in {}", propertyType, location);
        
        simulateApiCall();
        
        // Get base rate for city
        String cityKey = location.toLowerCase().split(",")[0].trim();
        int baseRate = CITY_RATES.getOrDefault(cityKey, 8000);
        
        // Adjust for property type
        double typeMultiplier = switch (propertyType.toUpperCase()) {
            case "VILLA" -> 1.3;
            case "COMMERCIAL" -> 1.5;
            case "PLOT" -> 0.7;
            default -> 1.0; // APARTMENT
        };
        
        // Calculate estimated value
        double pricePerSqft = baseRate * typeMultiplier;
        double estimatedValue = carpetArea * pricePerSqft;
        
        // Add some variance for realism
        Random rand = new Random(location.hashCode());
        estimatedValue *= (0.9 + rand.nextDouble() * 0.2); // ±10%
        
        double variancePercent = ((declaredValue - estimatedValue) / estimatedValue) * 100;
        boolean isReasonable = Math.abs(variancePercent) < 20;
        
        String summary = String.format(
            "Property: %s in %s. Estimated Value: ₹%.0f. Declared: ₹%.0f. " +
            "Variance: %.1f%%. Assessment: %s",
            propertyType, location, estimatedValue, declaredValue, variancePercent,
            isReasonable ? "REASONABLE" : "NEEDS_REVIEW"
        );
        
        log.info("Property valuation: Estimated={}, Declared={}, Reasonable={}", 
                estimatedValue, declaredValue, isReasonable);
        
        return ToolResult.success(summary, Map.of(
            "estimatedValue", estimatedValue,
            "declaredValue", declaredValue,
            "pricePerSqft", pricePerSqft,
            "variancePercent", variancePercent,
            "isReasonable", isReasonable,
            "location", location,
            "propertyType", propertyType,
            "valuedAt", java.time.Instant.now().toString()
        ));
    }
    
    private void simulateApiCall() {
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

