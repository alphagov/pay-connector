package uk.gov.pay.connector.service.epdq;

import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.service.OrderRequestBuilder;
import uk.gov.pay.connector.service.epdq.EpdqSignedPayloadDefinition.EpdqSignedPayloadDefinitionFactory;
import uk.gov.pay.connector.util.templates.FormUrlEncodedStringBuilder;
import uk.gov.pay.connector.util.templates.PayloadBuilder;
import uk.gov.pay.connector.util.templates.PayloadDefinition;

import javax.ws.rs.core.MediaType;

import static uk.gov.pay.connector.service.epdq.EpdqSignedPayloadDefinition.EpdqSignedPayloadDefinitionFactory.anEpdqSignedPayloadDefinitionFactory;

public class EpdqOrderRequestBuilder extends OrderRequestBuilder {
    static public class EpdqTemplateData extends OrderRequestBuilder.TemplateData {
        private String operationType;
        private String orderId;
        private String password;
        private String userId;
        private String shaInPassphrase;

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
    }

    public static final String AUTHORISE_OPERATION_TYPE = "RES";
    public static final String CAPTURE_OPERATION_TYPE = "SAS";
    public static final String CANCEL_OPERATION_TYPE = "DES";
    public static final String REFUND_OPERATION_TYPE = "RFD"; // RFD=partial refund

    private static EpdqSignedPayloadDefinitionFactory signedPayloadDefinitionFactory = anEpdqSignedPayloadDefinitionFactory(new EpdqSha512SignatureGenerator());

    public static final PayloadBuilder AUTHORISE_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForNewOrder();
    public static final PayloadBuilder CAPTURE_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();
    public static final PayloadBuilder CANCEL_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();
    public static final PayloadBuilder REFUND_ORDER_TEMPLATE_BUILDER = createPayloadBuilderForMaintenanceOrder();

    private EpdqTemplateData epdqTemplateData;

    private static PayloadBuilder createPayloadBuilderForNewOrder() {
        PayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForNewOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition);
    }

    private static PayloadBuilder createPayloadBuilderForMaintenanceOrder() {
        PayloadDefinition payloadDefinition = signedPayloadDefinitionFactory.create(new EpdqPayloadDefinitionForMaintenanceOrder());
        return new FormUrlEncodedStringBuilder(payloadDefinition);
    }

    public static EpdqOrderRequestBuilder anEpdqAuthoriseOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), AUTHORISE_ORDER_TEMPLATE_BUILDER, OrderRequestType.AUTHORISE, AUTHORISE_OPERATION_TYPE);
    }

    public static EpdqOrderRequestBuilder anEpdqCaptureOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), CAPTURE_ORDER_TEMPLATE_BUILDER, OrderRequestType.CAPTURE, CAPTURE_OPERATION_TYPE);
    }

    public static EpdqOrderRequestBuilder anEpdqCancelOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), CANCEL_ORDER_TEMPLATE_BUILDER, OrderRequestType.CANCEL, CANCEL_OPERATION_TYPE);
    }

    public static EpdqOrderRequestBuilder anEpdqRefundOrderRequestBuilder() {
        return new EpdqOrderRequestBuilder(new EpdqTemplateData(), REFUND_ORDER_TEMPLATE_BUILDER, OrderRequestType.REFUND, REFUND_OPERATION_TYPE);
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
