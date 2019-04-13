package uk.gov.pay.connector.gateway.stripe.request;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;

public class StripeCaptureRequest extends StripeRequest {

    private String stripeChargeId;

    private StripeCaptureRequest(
            GatewayAccountEntity gatewayAccount,
            String stripeChargeId,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeChargeId = stripeChargeId;
    }
    
    public static StripeCaptureRequest of(CaptureGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeCaptureRequest(
                request.getGatewayAccount(),
                request.getTransactionId(),
                request.getExternalId(),
                stripeGatewayConfig
        );
    }

    @Override
    public URI getUrl() {
        return URI.create(stripeGatewayConfig.getUrl() + "/v1/charges/" + stripeChargeId + "/capture");
    }

    @Override
    public GatewayOrder getGatewayOrder() {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("expand[]", "balance_transaction"));
        String payload = URLEncodedUtils.format(params, UTF_8);

        return new GatewayOrder(
                OrderRequestType.CAPTURE,
                payload,
                APPLICATION_FORM_URLENCODED_TYPE
        );    
    }

    @Override
    protected String getIdempotencyKeyType() {
        return "capture";
    }
}
