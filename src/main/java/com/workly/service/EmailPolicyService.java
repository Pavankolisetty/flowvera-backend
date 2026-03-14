package com.workly.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailPolicyService {

    private final Set<String> allowedDomains;

    public EmailPolicyService(
            @Value("${app.email.allowed-domains:gmail.com,yahoo.com,outlook.com,zoho.com}") String domains) {
        this.allowedDomains = Arrays.stream(domains.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public void validateAllowedDomain(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format.");
        }

        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        if (!allowedDomains.contains(domain)) {
            throw new IllegalArgumentException("Email domain not allowed. Please use Gmail, Outlook, Yahoo, or Zoho.");
        }
    }
}
