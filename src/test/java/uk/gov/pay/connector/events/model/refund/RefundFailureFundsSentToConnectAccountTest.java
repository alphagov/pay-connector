package uk.gov.pay.connector.events.model.refund;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.events.eventdetails.refund.RefundFailureFundsSentToConnectAccountEventDetails;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.GithubAndZendeskCredential;
import uk.gov.pay.connector.refund.model.domain.Refund;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;

class RefundFailureFundsSentToConnectAccountTest {

    @Test
    void shouldCreateRefundFailureFundsSentToConnectAccountEventFromRefundAndCharge() {

        ChargeEntityFixture chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withServiceId("service-id")
                .withGatewayAccountEntity(aGatewayAccountEntity().withType(LIVE).build());

        Charge charge = Charge.from(chargeEntityFixture.withPaymentProvider(STRIPE.getName()).build());

        Refund refund = Refund.from(RefundEntityFixture.aValidRefundEntity()
                .withChargeExternalId(charge.getExternalId())
                .withGatewayTransactionId("gateway-transaction-id")
                .build());

        GithubAndZendeskCredential githubAndZendeskCredential = new GithubAndZendeskCredential("1223333343", "John Doe (JohnDoeGds)");

        String githubUserId = githubAndZendeskCredential.githubUserId();
        String zendeskId = githubAndZendeskCredential.zendeskTicketId();
        String correctionPaymentId = "ExampleCorrectionPaymentId123";
        String transferIdFromStripe = "a-transfer-id-123";

        RefundFailureFundsSentToConnectAccount refundFailureFundsSentToConnectAccount = RefundFailureFundsSentToConnectAccount
                .from(correctionPaymentId, refund, charge, githubUserId, zendeskId, transferIdFromStripe);

        assertThat(refundFailureFundsSentToConnectAccount.getResourceExternalId(), is(correctionPaymentId));
        assertThat(refundFailureFundsSentToConnectAccount.isLive(), is(true));
        assertThat(refundFailureFundsSentToConnectAccount.getGatewayAccountId(), is(charge.getGatewayAccountId()));

        RefundFailureFundsSentToConnectAccountEventDetails details = (RefundFailureFundsSentToConnectAccountEventDetails) refundFailureFundsSentToConnectAccount.getEventDetails();

        assertThat(details.getAmount(), is(refund.getAmount()));
        assertThat(details.getAdminGithubId(), is("John Doe (JohnDoeGds)"));
        assertThat(details.getZendeskId(), is("1223333343"));
        assertThat(details.getUpdatedReason(), is("A refund failed and we returned the recovered funds to the service - Zendesk ticket " + zendeskId));
        assertThat(details.getGatewayAccountId(), is(charge.getGatewayAccountId()));
        assertThat(details.getPaymentProvider(), is(charge.getPaymentGatewayName()));
        assertThat(details.getReference(), is(charge.getReference()));
        assertThat(details.getDescription(), is("Failed refund correction for payment " + charge.getExternalId() + ". Returning funds from GOV.UK Pay platform."));
        assertThat(details.getGatewayTransactionId(), is(transferIdFromStripe));
    }
}
