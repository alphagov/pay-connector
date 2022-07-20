package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeAuthTokens;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@RunWith(MockitoJUnitRunner.class)
public class StripeTransferInRequestTest {
    private final String refundExternalId = "payRefundExternalId";
    private final String chargeExternalId = "payChargeExternalId";
    private final String stripeChargeId = "stripeChargeId";
    private final long refundAmount = 100L;
    private final String feeAmount = "5";
    private final String disputeAmount = "1000";
    private final String disputeExternalId = "a-dispute-id";
    private final String stripeBaseUrl = "stripeUrl";
    private final String stripePlatformAccountId = "stripePlatformAccountId";
    private final String stripeConnectAccountId = "stripe_account_id";

    @Mock
    RefundEntity refundEntity;
    @Mock
    Charge charge;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    @Mock
    StripeAuthTokens stripeAuthTokens;

    private StripeTransferInRequest refundTransferInRequest;
    private StripeTransferInRequest feeTransferInRequest;
    private StripeTransferInRequest disputeTransferInRequest;
    private GatewayAccountEntity gatewayAccount;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @Before
    public void setUp() {
        gatewayAccount = aGatewayAccountEntity()
                .withGatewayName("stripe")
                .build();
        gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", stripeConnectAccountId))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccount.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        when(charge.getExternalId()).thenReturn(chargeExternalId);

        when(refundEntity.getAmount()).thenReturn(refundAmount);
        when(refundEntity.getExternalId()).thenReturn(refundExternalId);

        when(stripeGatewayConfig.getUrl()).thenReturn(stripeBaseUrl);
        when(stripeGatewayConfig.getAuthTokens()).thenReturn(stripeAuthTokens);
        when(stripeGatewayConfig.getPlatformAccountId()).thenReturn(stripePlatformAccountId);

        final RefundGatewayRequest refundGatewayRequest = RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccount, gatewayAccountCredentialsEntity);

        refundTransferInRequest = StripeTransferInRequest.createRefundTransferRequest(refundGatewayRequest, stripeChargeId, stripeGatewayConfig);
        feeTransferInRequest = StripeTransferInRequest.createFeesForFailedPaymentTransferRequest(feeAmount, gatewayAccount, gatewayAccountCredentialsEntity, stripeChargeId, chargeExternalId, stripeGatewayConfig);
        
        disputeTransferInRequest = StripeTransferInRequest.createDisputeTransferRequest(disputeAmount,
                gatewayAccount, gatewayAccountCredentialsEntity, stripeChargeId, disputeExternalId, chargeExternalId,
                stripeGatewayConfig);
    }

    @Test
    public void shouldCreateCorrectUrl() {
        assertThat(refundTransferInRequest.getUrl(), is(URI.create(stripeBaseUrl + "/v1/transfers")));
    }

    @Test
    public void shouldCreateCorrectPayload_forRefundTransfer() {
        String payload = refundTransferInRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString("destination=" + stripePlatformAccountId));
        assertThat(payload, containsString("amount=" + refundAmount));
        assertThat(payload, containsString("transfer_group=" + chargeExternalId));
        assertThat(payload, containsString("expand%5B%5D=balance_transaction"));
        assertThat(payload, containsString("expand%5B%5D=destination_payment"));
        assertThat(payload, containsString("currency=GBP"));
        assertThat(payload, containsString("metadata%5Bstripe_charge_id%5D=" + stripeChargeId));
        assertThat(payload, containsString("metadata%5Bgovuk_pay_transaction_external_id%5D=" + refundExternalId));
        assertThat(payload, containsString("metadata%5Breason%5D=" + StripeTransferMetadataReason.TRANSFER_REFUND_AMOUNT));
    }

    @Test
    public void shouldCreateCorrectPayload_forFeeTransfer() {
        String payload = feeTransferInRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString("destination=" + stripePlatformAccountId));
        assertThat(payload, containsString("amount=" + feeAmount));
        assertThat(payload, containsString("transfer_group=" + chargeExternalId));
        assertThat(payload, containsString("expand%5B%5D=balance_transaction"));
        assertThat(payload, containsString("expand%5B%5D=destination_payment"));
        assertThat(payload, containsString("currency=GBP"));
        assertThat(payload, containsString("metadata%5Bstripe_charge_id%5D=" + stripeChargeId));
        assertThat(payload, containsString("metadata%5Bgovuk_pay_transaction_external_id%5D=" + chargeExternalId));
        assertThat(payload, containsString("metadata%5Breason%5D=" + StripeTransferMetadataReason.TRANSFER_FEE_AMOUNT_FOR_FAILED_PAYMENT));
    }

    @Test
    public void shouldCreateCorrectPayload_forDisputeTransfer() {
        String payload = disputeTransferInRequest.getGatewayOrder().getPayload();

        assertThat(payload, containsString("destination=" + stripePlatformAccountId));
        assertThat(payload, containsString("amount=" + disputeAmount));
        assertThat(payload, containsString("transfer_group=" + chargeExternalId));
        assertThat(payload, containsString("expand%5B%5D=balance_transaction"));
        assertThat(payload, containsString("expand%5B%5D=destination_payment"));
        assertThat(payload, containsString("currency=GBP"));
        assertThat(payload, containsString("metadata%5Bstripe_charge_id%5D=" + stripeChargeId));
        assertThat(payload, containsString("metadata%5Bgovuk_pay_transaction_external_id%5D=" + disputeExternalId));
        assertThat(payload, containsString("metadata%5Breason%5D=" + StripeTransferMetadataReason.TRANSFER_DISPUTE_AMOUNT));
    }

    @Test
    public void shouldHaveStripeAccountHeaderSetToStripeConnectAccountId() {
        assertThat(
                refundTransferInRequest.getHeaders().get("Stripe-Account"),
                is(stripeConnectAccountId)
        );
    }

    @Test
    public void shouldHaveIdempotencyKeySetToRefundExternalId_forRefundTransfer() {
        assertThat(
                refundTransferInRequest.getHeaders().get("Idempotency-Key"),
                is("transfer_in" + refundExternalId)
        );
    }

    @Test
    public void shouldHaveIdempotencyKeySetToRefundExternalId_forFeeTransfer() {
        assertThat(
                feeTransferInRequest.getHeaders().get("Idempotency-Key"),
                is("transfer_in" + chargeExternalId)
        );
    }

    @Test
    public void shouldHaveIdempotencyKeySetToDisputeExternalId_forDisputeTransfer() {
        assertThat(
                disputeTransferInRequest.getHeaders().get("Idempotency-Key"),
                is("transfer_in" + disputeExternalId)
        );
    }
}
