package com.hdfcbank.standin.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.io.Serializable;

/**
 * Loan Application model representing a home loan application request.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record LoanApplication(
    String applicationId,
    String applicantName,
    String panNumber,
    String aadhaarNumber,
    double annualIncome,
    double requestedLoanAmount,
    int loanTenureYears,
    int creditScore,
    double existingEmi,
    String employmentType,  // SALARIED, SELF_EMPLOYED, BUSINESS
    String employerName,
    int yearsOfExperience,
    String propertyType,    // APARTMENT, VILLA, PLOT
    double propertyValue,
    String propertyLocation
) implements Serializable {
    
    public static LoanApplication createSample() {
        return new LoanApplication(
            "LOAN-" + System.currentTimeMillis(),
            "Rahul Sharma",
            "BSRPS1234K",   // Valid PAN format: 5 letters + 4 digits + 1 letter
            "499118665246", // Valid Aadhaar: 12 digits, passes Verhoeff checksum
            2400000.0,  // 24 LPA (Monthly: 2L) - Higher income for better DTI
            5000000.0,  // 50 Lakhs loan
            20,         // 20 years tenure
            780,        // Good credit score
            10000.0,    // Low existing EMI (5% of monthly income)
            "SALARIED",
            "Tata Consultancy Services",
            8,
            "APARTMENT",
            8000000.0,  // 80 Lakhs property (LTV = 62.5%, well under 80%)
            "Mumbai"
        );
    }
}

