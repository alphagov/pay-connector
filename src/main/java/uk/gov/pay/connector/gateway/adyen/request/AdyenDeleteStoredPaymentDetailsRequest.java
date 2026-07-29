package uk.gov.pay.connector.gateway.adyen.request;

import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientDeleteRequest;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.DELETE_STORED_PAYMENT_DETAILS;

public record AdyenDeleteStoredPaymentDetailsRequest(URI url,
                                                     Map<String, String> headers,
                                                     Map<String, String> queryParams,
                                                     String gatewayAccountType) implements GatewayClientDeleteRequest {
    @Override
    public URI getUrl() {
        return url;
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String getGatewayAccountType() {
        return gatewayAccountType;
    }

    @Override
    public PaymentGatewayName getPaymentProvider() {
        return ADYEN;
    }

    @Override
    public OrderRequestType getOrderRequestType() {
        return DELETE_STORED_PAYMENT_DETAILS;
    }

    @Override
    public Map<String, String> getQueryParams() {
        return queryParams;
    }
}
