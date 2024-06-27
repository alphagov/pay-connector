package uk.gov.pay.connector.gateway.stripe.request;

import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
            GatewayCredentials credentials,
            String transferGroup,
            StripeTransferMetadataReason reason) {
        super(amount, gatewayAccount, stripeChargeId, idempotencyKey, stripeGatewayConfig,
                govukPayTransactionExternalId, credentials, reason);
        this.transferGroup = transferGroup;
    }

    public static StripeTransferInRequest createRefundTransferRequest(RefundGatewayRequest request, String stripeChargeId,
                                                                      StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTransferInRequest(
                request.amount(),
                request.gatewayAccount(),
                stripeChargeId,
                request.refundExternalId(),
                stripeGatewayConfig,
                request.refundExternalId(),
                request.gatewayCredentials(),
                request.chargeExternalId(),
                StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT
        );
    }

    public static StripeTransferInRequest createFeesForFailedPaymentTransferRequest(
            String feeAmount,
            GatewayAccountEntity gatewayAccount,
            GatewayAccountCredentialsEntity gatewayAccountCredentials,
            String paymentIntentId,
            String chargeExternalId,
            StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTransferInRequest(
                feeAmount,
                gatewayAccount,
                paymentIntentId,
                chargeExternalId,
                stripeGatewayConfig,
                chargeExternalId,
                gatewayAccountCredentials.getCredentialsObject(),
                chargeExternalId,
                StripeTransferMetadataReason.TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT);
    }

    public static StripeTransferInRequest createDisputeTransferRequest(
            String amount,
            GatewayAccountEntity gatewayAccount,
            GatewayAccountCredentialsEntity gatewayAccountCredentials,
            String paymentIntentId,
            String disputeExternalId,
            String chargeExternalId,
            StripeGatewayConfig stripeGatewayConfig) {
        return new StripeTransferInRequest(
                amount,
                gatewayAccount,
                paymentIntentId,
                disputeExternalId,
                stripeGatewayConfig,
                disputeExternalId,
                gatewayAccountCredentials.getCredentialsObject(),
                chargeExternalId,
                StripeTransferMetadataReason.TRANSFER_DISPUTE_AMOUNT);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StripeTransferInRequest that = (StripeTransferInRequest) o;
        return Objects.equals(transferGroup, that.transferGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transferGroup);
    }
}
