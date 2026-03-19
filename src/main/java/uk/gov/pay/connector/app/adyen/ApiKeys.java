package uk.gov.pay.connector.app.adyen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ApiKeys(
        @Valid
        @NotNull
        CompanyAccountApiKeys companyAccount,
        @Valid
        @NotNull
        BalancePlatformApiKeys balancePlatform,
        @Valid
        @NotNull
        LegalEntityManagementApiKeys legalEntityManagement
) {

    public record CompanyAccountApiKeys(@NotNull String test, @NotNull String live) {
    }

    public record BalancePlatformApiKeys(@NotNull String test, @NotNull String live) {
    }

    public record LegalEntityManagementApiKeys(@NotNull String test, @NotNull String live) {
    }
}
