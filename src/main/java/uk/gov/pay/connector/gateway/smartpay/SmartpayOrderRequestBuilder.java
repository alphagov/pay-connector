package uk.gov.pay.connector.gateway.smartpay;

import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.gateway.templates.PayloadBuilder;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;
import uk.gov.pay.connector.service.OrderRequestBuilder;

import javax.ws.rs.core.MediaType;

public class SmartpayOrderRequestBuilder extends OrderRequestBuilder {
    static public class SmartpayTemplateData extends TemplateData {
        private String reference;
        private String paResponse;
        private String md;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getPaResponse() {
            return paResponse;
        }

        public void setPaResponse(final String paResponse) {
            this.paResponse = paResponse;
        }

        public String getMd() {
            return md;
        }

        public void setMd(final String md) {
            this.md = md;
        }
    }

    public static final TemplateBuilder AUTHORISE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayAuthoriseOrderTemplate.xml");
    public static final TemplateBuilder REQUIRED_3DS_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/Smartpay3dsRequiredOrderTemplate.xml");
    public static final TemplateBuilder AUTHORISE_3DS_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayAuthorise3dsOrderTemplate.xml");
    public static final TemplateBuilder CAPTURE_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayCaptureOrderTemplate.xml");
    public static final TemplateBuilder CANCEL_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayCancelOrderTemplate.xml");
    public static final TemplateBuilder REFUND_ORDER_TEMPLATE_BUILDER = new TemplateBuilder("/smartpay/SmartpayRefundOrderTemplate.xml");

    private SmartpayTemplateData smartpayTemplateData;

    public static SmartpayOrderRequestBuilder aSmartpayAuthoriseOrderRequestBuilder() {
        return new SmartpayOrderRequestBuilder(new SmartpayTemplateData(), AUTHORISE_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE);
    }

    public static SmartpayOrderRequestBuilder aSmartpay3dsRequiredOrderRequestBuilder() {
        return new SmartpayOrderRequestBuilder(new SmartpayTemplateData(), REQUIRED_3DS_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE_3DS);
    }

    public static SmartpayOrderRequestBuilder aSmartpayAuthorise3dsOrderRequestBuilder() {
        return new SmartpayOrderRequestBuilder(new SmartpayTemplateData(), AUTHORISE_3DS_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE_3DS);
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

    public SmartpayOrderRequestBuilder withPaResponse(String paResponse) {
        smartpayTemplateData.setPaResponse(paResponse);
        return this;
    }

    public SmartpayOrderRequestBuilder withMd(String md) {
        smartpayTemplateData.setMd(md);
        return this;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_XML_TYPE;
    }
}
