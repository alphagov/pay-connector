package uk.gov.pay.connector.app.adyen;

import jakarta.validation.constraints.NotEmpty;

public record AdyenIds(
        @NotEmpty String test,
        @NotEmpty String live
) {}
