package uk.gov.pay.connector.gateway.worldpay.request;

import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.templates.TemplateBuilder;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gateway.worldpay.WorldpayAuthoriseOrderSessionId;

import java.util.Optional;

public class WorldpayAuthorise3dsRequest extends WorldpayOrderRequest {
    private static final TemplateBuilder templateBuilder = new TemplateBuilder("/worldpay/Worldpay3dsResponseAuthOrderTemplate.xml");
    
    private String paResponse3ds;
    private String sessionId;

    private WorldpayAuthorise3dsRequest(String transactionId, String merchantCode, String paResponse3ds, String sessionId) {
        super(transactionId, merchantCode);
        this.paResponse3ds = paResponse3ds;
        this.sessionId = sessionId;
    }
    
    public static WorldpayAuthorise3dsRequest from(Auth3dsResponseGatewayRequest request) {
        return new WorldpayAuthorise3dsRequest(
                request.getTransactionId().orElse(""),
                AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()),
                request.getAuth3dsResult().getPaResponse(),
                WorldpayAuthoriseOrderSessionId.of(request.getChargeExternalId()).toString()
        );
    }

    public Optional<String> getPaResponse3ds() {
        return Optional.ofNullable(paResponse3ds);
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public OrderRequestType getOrderRequestType() {
        return OrderRequestType.AUTHORISE_3DS;
    }

    @Override
    protected TemplateBuilder getTemplateBuilder() {
        return templateBuilder;
    }
}
