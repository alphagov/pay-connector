package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface WalletAuthoriseRequest extends AuthoriseRequest permits ApplePayAuthoriseRequest, GooglePayAuthoriseRequest {
}
