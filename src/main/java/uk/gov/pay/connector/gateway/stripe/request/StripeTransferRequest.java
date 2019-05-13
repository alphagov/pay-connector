package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.List;
import java.util.Map;

public abstract class StripeTransferRequest extends StripeRequest {
    protected String amount;
    protected String stripeChargeId;

    protected StripeTransferRequest(
            String amount,
            GatewayAccountEntity gatewayAccount,
            String stripeChargeId,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeChargeId = stripeChargeId;
        this.amount = amount;
    }

    public String urlPath() {
        return "/v1/transfers";
    }

    @Override
    protected Map<String, String> params() {
        return Map.of(
                "amount", amount,
                "currency", "GBP",
                "metadata[stripe_charge_id]", stripeChargeId
        );
    }
    
    @Override
    protected List<String> expansionFields() {
        return List.of("balance_transaction", "destination_payment");
    }
}
