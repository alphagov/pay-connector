package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface ApplePayAuthoriseRequest extends AuthoriseRequest permits AdyenApplePayAuthoriseRequest {
}
