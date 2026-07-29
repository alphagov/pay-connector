package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface AuthoriseRequest permits
        CardAuthoriseRequest, ApplePayAuthoriseRequest, AdyenAuthoriseRequest, WorldpayAuthoriseRequest {
}
