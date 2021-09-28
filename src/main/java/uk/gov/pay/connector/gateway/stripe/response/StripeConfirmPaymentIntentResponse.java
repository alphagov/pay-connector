package uk.gov.pay.connector.gateway.stripe.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import java.util.Map;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeConfirmPaymentIntentResponse extends StripePaymentIntentResponse {
    private static Map<String, BaseAuthoriseResponse.AuthoriseStatus> statusMap = Map.of(
            "requires_capture", BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED,
            "requires_action", BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS
    );

    private String status;

    public String getStatus() {
        return status;
    }

    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthoriseStatus() {
        return Optional.ofNullable(statusMap.get(status));
    }
}
