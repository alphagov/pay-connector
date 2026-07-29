package uk.gov.pay.connector.gateway.model.request.records;

public sealed interface CardAuthoriseRequest extends AuthoriseRequest permits WorldpayCardAuthoriseRequest {
}
