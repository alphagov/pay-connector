package uk.gov.pay.connector.app.adyen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record BaseUrls(
        @Valid
        @NotNull
        CheckoutUrls checkout,

        @Valid
        @NotNull
        BalancePlatformUrls balancePlatform,

        @Valid
        @NotNull
        LegalEntityManagementUrls legalEntityManagement,
        
        @Valid
        @NotNull
        ManagementUrls management
) {
    public record CheckoutUrls(@NotNull String test, @NotNull String live) {
    }

    public record LegalEntityManagementUrls(String test) {
    }

    public record BalancePlatformUrls(String test) {
    }

    public record ManagementUrls(String test) {
    }
}
