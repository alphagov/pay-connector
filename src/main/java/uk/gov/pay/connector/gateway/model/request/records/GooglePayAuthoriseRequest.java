package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface GooglePayAuthoriseRequest extends WalletAuthoriseRequest permits AdyenGooglePayAuthorisePayload {
}
