package com.hdfcbank.standin.agents.tools;

import com.hdfcbank.standin.agents.tools.dapr.DaprEnabledTool;
import com.hdfcbank.standin.agents.tools.dapr.DaprServiceClient;
import com.hdfcbank.standin.agents.tools.dapr.ServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Property Valuation Tool.
 * Fetches property valuation and market data.
 *
 * Uses Dapr Service Invocation to call the property valuation microservice,
 * which in production connects to property valuation APIs and government registries.
 */
@Component
public class PropertyValuationTool extends DaprEnabledTool {

    private static final Map<String, Integer> CITY_RATES = Map.of(
        "mumbai", 25000, "delhi", 15000, "bangalore", 12000, "chennai", 10000,
        "hyderabad", 8000, "pune", 9000, "kolkata", 7000, "ahmedabad", 6000
    );

    public PropertyValuationTool(
            DaprServiceClient daprClient,
            @Value("${tools.use-dapr:false}") boolean useDapr) {
        super(daprClient, useDapr);
    }

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
                    "property_address": {"type": "string", "description": "Full property address"},
                    "city": {"type": "string", "description": "City of property"},
                    "property_type": {"type": "string", "description": "RESIDENTIAL/COMMERCIAL"},
                    "declared_value": {"type": "number", "description": "Declared property value"},
                    "area_sqft": {"type": "number", "description": "Area in sq ft"}
                },
                "required": ["property_address", "declared_value"]
            }
            """;
    }

    @Override
    protected String getServiceAppId() {
        return ServiceRegistry.PROPERTY_VALUATION_SERVICE;
    }

    @Override
    protected String getServiceMethod() {
        return ServiceRegistry.Methods.GET_VALUATION;
    }

    @Override
    protected ToolResult executeDapr(Map<String, Object> parameters) {
        log.info("🔗 Calling property valuation service via Dapr");
        return invokeDaprService(parameters);
    }

    @Override
    protected ToolResult executeLocal(Map<String, Object> parameters) {
        String location = (String) parameters.getOrDefault("property_address",
                (String) parameters.get("city"));
        String propertyType = (String) parameters.getOrDefault("property_type", "RESIDENTIAL");
        double declaredValue = parseDouble(parameters.get("declared_value"));
        double carpetArea = parseDouble(parameters.getOrDefault("area_sqft",
                parameters.getOrDefault("carpet_area_sqft", 1000)));

        log.info("📋 Local property valuation for: {} in {}", propertyType, location);

        simulateApiCall();

        String cityKey = location.toLowerCase().split(",")[0].trim();
        int baseRate = CITY_RATES.getOrDefault(cityKey, 8000);

        double typeMultiplier = "COMMERCIAL".equalsIgnoreCase(propertyType) ? 1.5 : 1.0;
        double pricePerSqft = baseRate * typeMultiplier;
        double estimatedValue = carpetArea * pricePerSqft;

        Random rand = new Random(location.hashCode());
        estimatedValue *= (0.9 + rand.nextDouble() * 0.2);

        double variancePercent = ((declaredValue - estimatedValue) / estimatedValue) * 100;
        boolean isReasonable = Math.abs(variancePercent) < 20;

        String summary = String.format(
            "Property: %s in %s. Estimated Value: ₹%.0f. Declared: ₹%.0f. " +
            "Variance: %.1f%%. Assessment: %s",
            propertyType, location, estimatedValue, declaredValue, variancePercent,
            isReasonable ? "REASONABLE" : "NEEDS_REVIEW"
        );

        log.info("✅ Property valuation: Estimated={}, Declared={}, Reasonable={}",
                estimatedValue, declaredValue, isReasonable);

        return ToolResult.success(summary, Map.of(
            "marketValue", estimatedValue,
            "declaredValue", declaredValue,
            "ratePerSqft", pricePerSqft,
            "valuationVariance", variancePercent,
            "isReasonable", isReasonable,
            "city", location,
            "propertyType", propertyType,
            "valuedAt", java.time.Instant.now().toString(),
            "source", "LOCAL"
        ));
    }

    @Override
    protected String buildSummary(Map<String, Object> data) {
        double marketValue = ((Number) data.get("marketValue")).doubleValue();
        double declaredValue = ((Number) data.get("declaredValue")).doubleValue();
        double variance = ((Number) data.get("valuationVariance")).doubleValue();
        boolean isReasonable = (Boolean) data.get("isReasonable");

        return String.format(
            "Market Value: ₹%.0f. Declared: ₹%.0f. Variance: %.1f%%. Assessment: %s",
            marketValue, declaredValue, variance, isReasonable ? "REASONABLE" : "NEEDS_REVIEW"
        );
    }

    private void simulateApiCall() {
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private double parseDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) {
            try { return Double.parseDouble((String) obj); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}

