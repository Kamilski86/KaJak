package com.canda.epcis.application.downstream.subscription;

/**
 * DTO für das Registrieren einer neuen Subscription via POST /epcis/subscriptions.
 *
 * Alle Filter-Felder sind optional (null = kein Filter = alle Events passieren).
 * subscriptionId muss eindeutig sein.
 */
public record SubscriptionRegistration(
        String subscriptionId,
        String targetSystem,
        String callbackUrl,
        String authType,
        String authToken,
        String eventTypes,
        String bizSteps,
        String dispositions,
        String bizLocations,
        String readPoints,
        boolean active
) {
    public SubscriptionRegistration {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException("subscriptionId must not be blank");
        }
        if (targetSystem == null || targetSystem.isBlank()) {
            throw new IllegalArgumentException("targetSystem must not be blank");
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new IllegalArgumentException("callbackUrl must not be blank");
        }
    }
}
