package uk.gov.pay.connector.gateway.stripe.request;

import org.apache.http.message.BasicNameValuePair;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public abstract class StripeTransferRequest extends StripeRequest {
    protected Long amount;
    protected String stripeChargeId;

    protected StripeTransferRequest(
            Long amount,
            GatewayAccountEntity gatewayAccount,
            String stripeChargeId,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeChargeId = stripeChargeId;
        this.amount = amount;
    }

    public abstract GatewayOrder getGatewayOrder();

    @Override
    public URI getUrl() {
        return URI.create(stripeGatewayConfig.getUrl() + "/v1/transfers");
    }

    protected List<BasicNameValuePair> getCommonPayloadParameters() {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("amount", String.valueOf(amount)));
        params.add(new BasicNameValuePair("currency", "GBP"));
        params.add(new BasicNameValuePair("expand[]", "balance_transaction"));
        params.add(new BasicNameValuePair("expand[]", "destination_payment"));

        return params;
    }
}
