package uk.gov.pay.connector.gateway.worldpay.request;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;

import javax.ws.rs.core.MediaType;

public abstract class WorldpayOrderRequest {
    private String transactionId;
    private String merchantCode;

    public WorldpayOrderRequest(String transactionId, String merchantCode) {
        this.transactionId = transactionId;
        this.merchantCode = merchantCode;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public abstract OrderRequestType getOrderRequestType();
    
    protected abstract TemplateBuilder getTemplateBuilder();
    
    public GatewayOrder buildGatewayOrder() {
        String payload = getTemplateBuilder().buildWith(this);
        return new GatewayOrder(getOrderRequestType(), payload, MediaType.APPLICATION_XML_TYPE);
    }
}
