package uk.gov.pay.connector.service.epdq;

import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.service.OrderRequestBuilder;
import uk.gov.pay.connector.service.epdq.EpdqSignedPayloadDefinition.EpdqSignedPayloadDefinitionFactory;
import uk.gov.pay.connector.util.templates.FormUrlEncodedStringBuilder;
import uk.gov.pay.connector.util.templates.PayloadBuilder;
import uk.gov.pay.connector.util.templates.PayloadDefinition;

import javax.ws.rs.core.MediaType;

import static uk.gov.pay.connector.service.epdq.EpdqPaymentProvider.EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET;
import static uk.gov.pay.connector.service.epdq.EpdqSignedPayloadDefinition.EpdqSignedPayloadDefinitionFactory.anEpdqSignedPayloadDefinitionFactory;

public class EpdqOrderRequestBuilder extends OrderRequestBuilder {
    public static class EpdqTemplateData extends OrderRequestBuilder.TemplateData {
        private String operationType;
        private String orderId;
        private String password;
        private String userId;
        private String shaInPassphrase;
        private String amount;
        private String frontendBaseUrl;

        public String getOperationType() {
            return operationType;
        }

        public void setOperationType(String operationType) {
            this.operationType = operationType;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getShaInPassphrase() {
            return shaInPassphrase;
        }

        public void setShaInPassphrase(String shaInPassphrase) {
            this.shaInPassphrase = shaInPassphrase;
        }

        @Override
        public String getAmount() {
            return amount;
        }

        @Override
        public void setAmount(String amount) {
            this.amount = amount;
        }

        public void setFrontendUrl(String frontendUrl) {
            this.frontendBaseUrl = frontendUrl;
        }

        public String getFrontendUrl() {
            return frontendBaseUrl;
        }
    }

    private static final String AUTHORISE_OPERATION_TYPE = "RES";
    private static final String CAPTURE_OPERATION_TYPE = "SAS";
    private static final String REFUND_OPERATION_TYPE = "RFD";
    private static final String CANCEL_OPERATION_TYPE = "DES";

    private static EpdqSignedPayloadDefinitionFactory signedPayloadDefinitionFactory = anEpdqSignedPayloadDefinitionFactory(new EpdqSha512SignatureGenerator());

    private static final PayloadBuilder AUTHORISE_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForNewOrder();
    private static final PayloadBuilder QUERY_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForQueryOrder();
    private static final PayloadBuilder AUTHORISE_3DS_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForNew3dsOrder();
    private static final PayloadBuilder CAPTURE_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();
    private static final PayloadBuilder CANCEL_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();
    private static final PayloadBuilder REFUND_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();

    private EpdqTemplateData epdqTemplateData;

    private static PayloadBuilder createPayloadBuilderForQueryOrder() {
        PayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForQueryOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition, EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
    }

    private static PayloadBuilder createPayloadBuilderForNewOrder() {
        PayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForNewOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition, EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
    }

    private static PayloadBuilder createPayloadBuilderForNew3dsOrder() {
        PayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForNew3dsOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition, EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
    }

    private static PayloadBuilder createPayloadBuilderForMaintenanceOrder() {
        PayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForMaintenanceOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition, EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
    }

    public static EpdqOrderRequestBuilder anEpdqAuthoriseOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), AUTHORISE_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE, AUTHORISE_OPERATION_TYPE);
    }

    public static EpdqOrderRequestBuilder anEpdqQueryOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), QUERY_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE, AUTHORISE_OPERATION_TYPE);
    }

    public static EpdqOrderRequestBuilder anEpdq3DsAuthoriseOrderRequestBuilder(String frontendUrl) {
        EpdqTemplateData epdqTemplateData = new EpdqTemplateData();
        epdqTemplateData.setFrontendUrl(frontendUrl);
        return new EpdqOrderRequestBuilder(epdqTemplateData, AUTHORISE_3DS_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE_3DS, AUTHORISE_OPERATION_TYPE);
    }

    public static EpdqOrderRequestBuilder anEpdqCaptureOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), CAPTURE_ORDER_TEMPLATE_BUILDER, OrderRequestType.CAPTURE, CAPTURE_OPERATION_TYPE);
    }

    public static EpdqOrderRequestBuilder anEpdqRefundOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), REFUND_ORDER_TEMPLATE_BUILDER, OrderRequestType.REFUND, REFUND_OPERATION_TYPE);
    }

    public static EpdqOrderRequestBuilder anEpdqCancelOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), CANCEL_ORDER_TEMPLATE_BUILDER, OrderRequestType.CANCEL, CANCEL_OPERATION_TYPE);
    }

    private EpdqOrderRequestBuilder(EpdqTemplateData epdqTemplateData, PayloadBuilder payloadBuilder, OrderRequestType orderRequestType, String operationType) {
        super(epdqTemplateData, payloadBuilder, orderRequestType);
        this.epdqTemplateData = epdqTemplateData;
        epdqTemplateData.setOperationType(operationType);
    }

    public EpdqOrderRequestBuilder withOrderId(String orderId) {
        epdqTemplateData.setOrderId(orderId);
        return this;
    }

    public EpdqOrderRequestBuilder withPassword(String password) {
        epdqTemplateData.setPassword(password);
        return this;
    }

    public EpdqOrderRequestBuilder withUserId(String userId) {
        epdqTemplateData.setUserId(userId);
        return this;
    }

    public EpdqOrderRequestBuilder withShaInPassphrase(String shaInPassphrase) {
        epdqTemplateData.setShaInPassphrase(shaInPassphrase);
        return this;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_FORM_URLENCODED_TYPE;
    }
}
