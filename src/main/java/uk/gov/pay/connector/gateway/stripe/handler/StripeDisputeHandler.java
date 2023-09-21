package uk.gov.pay.connector.gateway.stripe.handler;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.request.StripeSubmitTestDisputeEvidenceRequest;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.util.JsonObjectMapper;

public class StripeDisputeHandler {
    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;

    public StripeDisputeHandler(GatewayClient client, StripeGatewayConfig stripeGatewayConfig,
                                JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public StripeDisputeData submitTestDisputeEvidence(String disputeId, String evidenceText, String transactionId) throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        StripeSubmitTestDisputeEvidenceRequest request = StripeSubmitTestDisputeEvidenceRequest.of(stripeGatewayConfig,
                disputeId, evidenceText, transactionId);
        String jsonResponse = client.postRequestFor(request).getEntity();
        return jsonObjectMapper.getObject(jsonResponse, StripeDisputeData.class);
    }
}
