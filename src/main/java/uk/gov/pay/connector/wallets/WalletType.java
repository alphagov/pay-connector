package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;

import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE_APPLE_PAY;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE_GOOGLE_PAY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.AUTHORISE_APPLE_PAY_ORDER_TEMPLATE_BUILDER;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.AUTHORISE_GOOGLE_PAY_ORDER_TEMPLATE_BUILDER;

public enum WalletType {
    
    APPLE_PAY(AUTHORISE_APPLE_PAY_ORDER_TEMPLATE_BUILDER, AUTHORISE_APPLE_PAY),
    GOOGLE_PAY(AUTHORISE_GOOGLE_PAY_ORDER_TEMPLATE_BUILDER, AUTHORISE_GOOGLE_PAY);
    
    private final TemplateBuilder worldPayTemplate;
    private final OrderRequestType orderRequestType;

    WalletType(TemplateBuilder worldPayTemplate, OrderRequestType orderRequestType) {
        this.worldPayTemplate = worldPayTemplate;
        this.orderRequestType = orderRequestType;
    }

    public TemplateBuilder getWorldPayTemplate() {
        return worldPayTemplate;
    }

    public OrderRequestType getOrderRequestType() {
        return orderRequestType;
    }
}
