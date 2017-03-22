package uk.gov.pay.connector.service.smartpay;

import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.service.OrderRequestBuilder;
import uk.gov.pay.connector.util.templates.PayloadBuilder;
import uk.gov.pay.connector.util.templates.TemplateBuilder;

public class SmartpayOrderRequestBuilder extends OrderRequestBuilder {
    static public class SmartpayTemplateData extends TemplateData {
        private String reference;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }
    }

    public static final TemplateBuilder AUTHORISE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayAuthoriseOrderTemplate.xml");
    public static final TemplateBuilder CAPTURE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayCaptureOrderTemplate.xml");
    public static final TemplateBuilder CANCEL_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayCancelOrderTemplate.xml");
    public static final TemplateBuilder REFUND_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayRefundOrderTemplate.xml");

    private SmartpayTemplateData smartpayTemplateData;

    public static SmartpayOrderRequestBuilder aSmartpayAuthoriseOrderRequestBuilder() {
        return new SmartpayOrderRequestBuilder(new SmartpayTemplateData(), AUTHORISE_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE);
    }

    public static SmartpayOrderRequestBuilder aSmartpayCaptureOrderRequestBuilder() {
        return new SmartpayOrderRequestBuilder(new SmartpayTemplateData(), CAPTURE_ORDER_TEMPLATE_BUILDER, OrderRequestType.CAPTURE);
    }

    public static SmartpayOrderRequestBuilder aSmartpayCancelOrderRequestBuilder() {
        return new SmartpayOrderRequestBuilder(new SmartpayTemplateData(), CANCEL_ORDER_TEMPLATE_BUILDER, OrderRequestType.CANCEL);
    }

    public static SmartpayOrderRequestBuilder aSmartpayRefundOrderRequestBuilder() {
        return new SmartpayOrderRequestBuilder(new SmartpayTemplateData(), REFUND_ORDER_TEMPLATE_BUILDER, OrderRequestType.REFUND);
    }

    public SmartpayOrderRequestBuilder(SmartpayTemplateData smartpayTemplateData, PayloadBuilder payloadBuilder, OrderRequestType orderRequestType) {
        super(smartpayTemplateData, payloadBuilder, orderRequestType);
        this.smartpayTemplateData = smartpayTemplateData;
    }

    public SmartpayOrderRequestBuilder withReference(String reference) {
        smartpayTemplateData.setReference(reference);
        return this;
    }
}
