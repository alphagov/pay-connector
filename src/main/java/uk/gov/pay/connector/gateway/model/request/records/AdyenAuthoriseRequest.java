package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface AdyenAuthoriseRequest extends AdyenRequest, AuthoriseRequest
        permits AdyenApplePayAuthorisePayload, AdyenGooglePayAuthorisePayload {

    String reference();

}
