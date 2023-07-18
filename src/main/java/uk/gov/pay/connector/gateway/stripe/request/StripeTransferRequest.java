package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class StripeTransferRequest extends StripePostRequest {
    protected String amount;
    protected String stripeChargeId;
    protected String govukPayTransactionExternalId;
    protected StripeTransferMetadataReason reason;

    protected StripeTransferRequest(
            String amount,
            GatewayAccountEntity gatewayAccount,
            String stripeChargeId,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            String govukPayTransactionExternalId,
            GatewayCredentials credentials,
            StripeTransferMetadataReason reason) {
        super(gatewayAccount, idempotencyKey, stripeGatewayConfig, credentials);
        this.stripeChargeId = stripeChargeId;
        this.amount = amount;
        this.govukPayTransactionExternalId = govukPayTransactionExternalId;
        this.reason = reason;
    }

    public String urlPath() {
        return "/v1/transfers";
    }

    @Override
    protected Map<String, String> params() {
        StripeTransferMetadata stripeTransferMetadata = new StripeTransferMetadata(stripeChargeId, govukPayTransactionExternalId, reason);
        Map<String, String> baseParams = new HashMap<>();
        baseParams.put("amount", amount);
        baseParams.put("currency", "GBP");
        baseParams.putAll(stripeTransferMetadata.getParams());
        return baseParams;
    }
    
    @Override
    protected List<String> expansionFields() {
        return List.of("balance_transaction", "destination_payment");
    }
}
