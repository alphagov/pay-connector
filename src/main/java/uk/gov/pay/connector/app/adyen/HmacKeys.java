package uk.gov.pay.connector.app.adyen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record HmacKeys(
        @Valid
        @NotNull
        WebhookHmacKeyPair payments
) {
    public record WebhookHmacKeyPair(
            @Valid
            @NotNull
            WebhookHmacKeys test,

            @Valid
            @NotNull
            WebhookHmacKeys live
    ) {}

}
