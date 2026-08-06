package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface ApplePayAuthoriseRequest extends WalletAuthoriseRequest permits AdyenApplePayAuthorisePayload {
}
