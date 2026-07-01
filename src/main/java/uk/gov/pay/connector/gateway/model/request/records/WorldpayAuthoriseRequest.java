package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface WorldpayAuthoriseRequest extends WorldpayRequest, AuthoriseRequest
        permits WorldpayMotoAuthoriseRequest {
}
