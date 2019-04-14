package uk.gov.pay.connector.gateway.stripe.request;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;

/***
 * Represents a request to transfer an amount from a Stripe Connect account to 
 * Pay's Stripe Platform account
 */
public class StripeTransferInRequest extends StripeTransferRequest {
    private final String transferGroup;

    private StripeTransferInRequest(
            Long amount,
            GatewayAccountEntity gatewayAccount,
            String stripeChargeId,
            String idempotencyKey,
            String transferGroup,
            StripeGatewayConfig stripeGatewayConfig
    ) {
        super(amount, gatewayAccount, stripeChargeId, idempotencyKey, stripeGatewayConfig);
        this.transferGroup = transferGroup;
    }

    public static StripeTransferInRequest of(RefundGatewayRequest request, StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTransferInRequest(
                request.getAmount(),
                request.getGatewayAccount(),
                request.getTransactionId(),
                request.getRefundExternalId(),
                request.getChargeExternalId(),
                stripeGatewayConfig
        );
    }

    @Override
    public GatewayOrder getGatewayOrder() {
        List<BasicNameValuePair> params = getCommonPayloadParameters();
        params.add(new BasicNameValuePair("destination", stripeGatewayConfig.getPlatformAccountId()));
        params.add(new BasicNameValuePair("transfer_group", transferGroup));
        String payload = URLEncodedUtils.format(params, UTF_8);

        return new GatewayOrder(
                OrderRequestType.REFUND,
                payload,
                APPLICATION_FORM_URLENCODED_TYPE
        );
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> headers = super.getHeaders();
        headers.put("Stripe-Account", stripeConnectAccountId);

        return headers;
    }
}
