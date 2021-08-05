package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.HashMap;
import java.util.Map;

/***
 * Represents a request to transfer an amount from a Stripe Connect account to 
 * Pay's Stripe Platform account
 */
public class StripeTransferInRequest extends StripeTransferRequest {
    private final String transferGroup;

    private StripeTransferInRequest(
            String amount,
            GatewayAccountEntity gatewayAccount,
            String stripeChargeId,
            String idempotencyKey,
            StripeGatewayConfig stripeGatewayConfig,
            String govukPayTransactionExternalId,
            Map<String, String> credentials,
            String transferGroup) {
        super(amount, gatewayAccount, stripeChargeId, idempotencyKey, stripeGatewayConfig,
                govukPayTransactionExternalId, credentials);
        this.transferGroup = transferGroup;
    }

    public static StripeTransferInRequest of(RefundGatewayRequest request, String stripeChargeId,
                                             StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTransferInRequest(
                request.getAmount(),
                request.getGatewayAccount(),
                stripeChargeId,
                request.getRefundExternalId(),
                stripeGatewayConfig,
                request.getRefundExternalId(),
                request.getGatewayCredentials(),
                request.getChargeExternalId()
        );
    }

    @Override
    public Map<String, String> params() {
        Map<String, String> params = new HashMap<>();
        Map<String, String> transferOutParams = Map.of(
                "destination", stripeGatewayConfig.getPlatformAccountId(),
                "transfer_group", transferGroup
        );
        Map<String, String> commonParams = super.params();
        params.putAll(transferOutParams);
        params.putAll(commonParams);

        return params;
    }

    @Override
    public Map<String, String> headers() {
        return Map.of("Stripe-Account", stripeConnectAccountId);
    }

    @Override
    protected String idempotencyKeyType() {
        return "transfer_in";
    }

    @Override
    protected OrderRequestType orderRequestType() {
        return OrderRequestType.REFUND;
    }
}
