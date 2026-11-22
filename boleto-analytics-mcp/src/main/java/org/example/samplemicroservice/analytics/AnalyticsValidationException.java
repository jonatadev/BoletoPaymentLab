package org.example.samplemicroservice.analytics;

final class AnalyticsValidationException extends IllegalArgumentException {
    AnalyticsValidationException(String message) {
        super(message);
    }
}
