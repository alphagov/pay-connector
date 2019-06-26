package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.OrderRequestBuilder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinition;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNew3dsOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForNewOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForQueryOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.templates.FormUrlEncodedStringBuilder;

import javax.ws.rs.core.MediaType;

public class EpdqOrderRequestBuilder {
    private final FormUrlEncodedStringBuilder payloadBuilder;
    private OrderRequestType orderRequestType;

    public GatewayOrder build() { 
        final String payload = payloadBuilder.buildWith(epdqTemplateData);
        return new GatewayOrder(
                orderRequestType,
                payload,
                MediaType.APPLICATION_FORM_URLENCODED_TYPE
        );
    }

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

    private static EpdqSignedPayloadDefinition.EpdqSignedPayloadDefinitionFactory signedPayloadDefinitionFactory = EpdqSignedPayloadDefinition.EpdqSignedPayloadDefinitionFactory.anEpdqSignedPayloadDefinitionFactory(new EpdqSha512SignatureGenerator());

    private static final FormUrlEncodedStringBuilder AUTHORISE_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForNewOrder();
    private static final FormUrlEncodedStringBuilder QUERY_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForQueryOrder();
    private static final FormUrlEncodedStringBuilder AUTHORISE_3DS_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForNew3dsOrder();
    private static final FormUrlEncodedStringBuilder CAPTURE_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();
    private static final FormUrlEncodedStringBuilder CANCEL_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();
    private static final FormUrlEncodedStringBuilder REFUND_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();

    private EpdqTemplateData epdqTemplateData;

    private static FormUrlEncodedStringBuilder createPayloadBuilderForQueryOrder() {
        EpdqPayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForQueryOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition, EpdqPaymentProvider.EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
    }

    private static FormUrlEncodedStringBuilder createPayloadBuilderForNewOrder() {
        EpdqPayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForNewOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition, EpdqPaymentProvider.EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
    }

    private static FormUrlEncodedStringBuilder createPayloadBuilderForNew3dsOrder() {
        EpdqPayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForNew3dsOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition, EpdqPaymentProvider.EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
    }

    private static FormUrlEncodedStringBuilder createPayloadBuilderForMaintenanceOrder() {
        EpdqPayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForMaintenanceOrder());
        return createNewPayloadBuilder(payloadDefinition);
    }
    
    private static FormUrlEncodedStringBuilder createNewPayloadBuilder(EpdqPayloadDefinition payloadDefinition) {
        return new FormUrlEncodedStringBuilder(payloadDefinition, EpdqPaymentProvider.EPDQ_APPLICATION_X_WWW_FORM_URLENCODED_CHARSET);
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

    private EpdqOrderRequestBuilder(EpdqTemplateData epdqTemplateData, FormUrlEncodedStringBuilder payloadBuilder, OrderRequestType orderRequestType, String operationType) {
        this.epdqTemplateData = epdqTemplateData;
        this.payloadBuilder = payloadBuilder;
        this.orderRequestType = orderRequestType;
        epdqTemplateData.setOperationType(operationType);
    }

    EpdqOrderRequestBuilder withOrderId(String orderId) {
        epdqTemplateData.setOrderId(orderId);
        return this;
    }

    EpdqOrderRequestBuilder withPassword(String password) {
        epdqTemplateData.setPassword(password);
        return this;
    }

    EpdqOrderRequestBuilder withUserId(String userId) {
        epdqTemplateData.setUserId(userId);
        return this;
    }

    EpdqOrderRequestBuilder withShaInPassphrase(String shaInPassphrase) {
        epdqTemplateData.setShaInPassphrase(shaInPassphrase);
        return this;
    }
    
    EpdqOrderRequestBuilder withMerchantCode(String merchantId) {
        epdqTemplateData.setMerchantCode(merchantId);
        return this;
    }
    
    EpdqOrderRequestBuilder withTransactionId(String transactionId) {
        epdqTemplateData.setTransactionId(transactionId);
        return this;
    }

    EpdqOrderRequestBuilder withAmount(String amount) {
        epdqTemplateData.setAmount(amount);
        return this;
    }
    
    EpdqOrderRequestBuilder withAuthorisationDetails(AuthCardDetails authCardDetails) {
        epdqTemplateData.setAuthCardDetails(authCardDetails);
        return this;
    }
    
    EpdqOrderRequestBuilder withDescription(String description) {
        epdqTemplateData.setDescription(description);
        return this;
    }
}
