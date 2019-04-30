package uk.gov.pay.connector.gateway.stripe.request;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;

public class StripeTransferOutRequest extends StripeTransferRequest {

    private StripeTransferOutRequest(String amount,
                                    GatewayAccountEntity gatewayAccount,
                                    String sourceTransactionId,
                                    String idempotencyKey,
                                    StripeGatewayConfig stripeGatewayConfig
    ) {
        super(amount, gatewayAccount, sourceTransactionId, idempotencyKey, stripeGatewayConfig);
    }
    
    public static StripeTransferOutRequest of(String amount, CaptureGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTransferOutRequest(
                amount,
                request.getGatewayAccount(),
                request.getTransactionId(),
                request.getExternalId(),
                stripeGatewayConfig
        );
    }

    @Override
    public GatewayOrder getGatewayOrder() {
        List<BasicNameValuePair> params = getCommonPayloadParameters();
        params.add(new BasicNameValuePair("destination", stripeConnectAccountId));
        params.add(new BasicNameValuePair("source_transaction", stripeChargeId));
        String payload = URLEncodedUtils.format(params, UTF_8);

        return new GatewayOrder(
                OrderRequestType.CAPTURE,
                payload,
                APPLICATION_FORM_URLENCODED_TYPE
        );
    }

    @Override
    protected String getIdempotencyKeyType() {
        return "transfer_out";
    }
}
