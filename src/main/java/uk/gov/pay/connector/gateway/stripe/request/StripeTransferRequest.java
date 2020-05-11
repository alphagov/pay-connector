package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class StripeTransferRequest extends StripeRequest {
    protected String amount;
    protected String stripeChargeId;
    protected String reconciliationTransactionId;

    protected StripeTransferRequest(
            String amount,
            GatewayAccountEntity gatewayAccount,
            String stripeChargeId,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            String reconciliationTransactionId
    ) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig);
        this.stripeChargeId = stripeChargeId;
        this.amount = amount;
        this.reconciliationTransactionId = reconciliationTransactionId;
    }

    public String urlPath() {
        return "/v1/transfers";
    }

    @Override
    protected Map<String, String> params() {
        StripeTransferMetadata stripeTransferMetadata = new StripeTransferMetadata(this.stripeChargeId, this.reconciliationTransactionId);
        Map<String, String> baseParams = new HashMap<>();
        baseParams.put("amount", amount);
        baseParams.put("currency", "GBP");
        baseParams.putAll(stripeTransferMetadata.format());
        return baseParams;
    }
    
    @Override
    protected List<String> expansionFields() {
        return List.of("balance_transaction", "destination_payment");
    }
}
