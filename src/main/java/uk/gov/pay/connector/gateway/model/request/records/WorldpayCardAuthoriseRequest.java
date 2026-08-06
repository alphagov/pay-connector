package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface WorldpayCardAuthoriseRequest extends CardAuthoriseRequest, WorldpayAuthoriseRequest
        permits WorldpayMotoAuthorisePayload {
}
