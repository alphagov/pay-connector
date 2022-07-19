package uk.gov.pay.connector.gateway.stripe.request;

import com.google.gson.GsonBuilder;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.STRIPE_UPDATE_DISPUTE;

public class StripeSubmitTestDisputeEvidenceRequest implements GatewayClientPostRequest {
    private final String disputeId;
    private final String evidenceText;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final String idempotencyKey;
    private static final String URL_PATH = "/v1/disputes/%s";

    private StripeSubmitTestDisputeEvidenceRequest(StripeGatewayConfig stripeGatewayConfig,
                                                   String disputeId,
                                                   String evidenceText,
                                                   String transactionExternalId) {
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.disputeId = disputeId;
        this.evidenceText = evidenceText;
        this.idempotencyKey = transactionExternalId;
    }

    public static StripeSubmitTestDisputeEvidenceRequest of(StripeGatewayConfig stripeGatewayConfig,
                                                            String disputeId,
                                                            String evidenceText,
                                                            String transactionExternalId) {
        return new StripeSubmitTestDisputeEvidenceRequest(stripeGatewayConfig, disputeId, evidenceText,
                transactionExternalId);
    }

    public OrderRequestType orderRequestType() {
        return STRIPE_UPDATE_DISPUTE;
    }

    @Override
    public URI getUrl() {
        return URI.create(stripeGatewayConfig.getUrl() + format(URL_PATH, disputeId));
    }

    @Override
    public GatewayOrder getGatewayOrder() {
        return createGatewayOrder();
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> result = new HashMap<>();
        Optional.ofNullable(idempotencyKey).ifPresent(idempotencyKey -> result.put("Idempotency-Key", orderRequestType().toString() + idempotencyKey));
        result.putAll(AuthUtil.getStripeAuthHeader(stripeGatewayConfig, false));

        return result;
    }

    @Override
    public String getGatewayAccountType() {
        return GatewayAccountType.TEST.toString();
    }

    @Override
    public PaymentGatewayName getPaymentProvider() {
        return PaymentGatewayName.STRIPE;
    }

    private GatewayOrder createGatewayOrder() {
        String payload = new GsonBuilder()
                .create()
                .toJson(Map.of("evidence", Map.of("uncategorized_text", evidenceText),
                        "submit", true));
        Map<String, Object> result = Map.of("evidence", Map.of("uncategorized_text", evidenceText),
                "submit", true);
        GatewayOrder order = new GatewayOrder(orderRequestType(), payload, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        return order;
    }
}
