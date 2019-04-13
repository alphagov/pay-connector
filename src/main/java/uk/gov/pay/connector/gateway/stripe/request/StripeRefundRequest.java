package uk.gov.pay.connector.gateway.stripe.request;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;

public class StripeRefundRequest extends StripeRequest {
    private final String stripeChargeId;
    private final String amount;
    
    private StripeRefundRequest(
            String amount,
            GatewayAccountEntity gatewayAccount,
            String idempotencyKey,
            String stripeChargeId,
            StripeGatewayConfig stripeGatewayConfig
            ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeChargeId = stripeChargeId;
        this.amount = amount;
    }
    
    public static StripeRefundRequest of(RefundGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeRefundRequest(              
                request.getAmount(),
                request.getGatewayAccount(),
                request.getRefundExternalId(),
                request.getTransactionId(),
                stripeGatewayConfig
        );
    }

    @Override
    public URI getUrl() {
        return URI.create(stripeGatewayConfig.getUrl() + "/v1/refunds");
    }

    @Override
    public GatewayOrder getGatewayOrder() {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("charge", stripeChargeId));
        params.add(new BasicNameValuePair("amount", amount));
        params.add(new BasicNameValuePair("refund_application_fee", "true"));
        params.add(new BasicNameValuePair("reverse_transfer", "true"));
        String payload = URLEncodedUtils.format(params, UTF_8);

        return new GatewayOrder(
                OrderRequestType.REFUND,
                payload,
                APPLICATION_FORM_URLENCODED_TYPE
        );    
    }
}
