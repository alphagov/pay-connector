package uk.gov.pay.connector.events.model.refund;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.events.eventdetails.refund.PaymentStatusCorrectedToSuccessByAdminEventDetails;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.GithubAndZendeskCredential;
import uk.gov.pay.connector.refund.model.domain.Refund;

import java.time.Instant;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;

class PaymentStatusCorrectedToSuccessByAdminTest {
    @Test
    void shouldCreatePaymentStatusCorrectedToSuccessByAdminEventFromRefundAndCharge() {

        Instant fixedTimestamp = Instant.now();

        ChargeEntityFixture chargeEntityFixture = ChargeEntityFixture.aValidChargeEntity()
                .withServiceId("service-id")
                .withGatewayAccountEntity(aGatewayAccountEntity().withType(LIVE).build());

        Charge charge = Charge.from(chargeEntityFixture.build());

        Refund refund = Refund.from(RefundEntityFixture.aValidRefundEntity()
                .withChargeExternalId(charge.getExternalId())
                .withGatewayTransactionId("gateway-transaction-id")
                .build());
        GithubAndZendeskCredential githubAndZendeskCredential = new GithubAndZendeskCredential("1223333343", "John Doe (JohnDoeGds)");

        String githubUserId = githubAndZendeskCredential.githubUserId();
        String zendeskId = githubAndZendeskCredential.zendeskTicketId();

        PaymentStatusCorrectedToSuccessByAdmin paymentStatusCorrectedToSuccessByAdmin = PaymentStatusCorrectedToSuccessByAdmin
                .from(refund, charge, fixedTimestamp, githubUserId, zendeskId);

        assertThat(paymentStatusCorrectedToSuccessByAdmin.isLive(), is(true));
        assertThat(paymentStatusCorrectedToSuccessByAdmin.getGatewayAccountId(), is(charge.getGatewayAccountId()));
        assertThat(paymentStatusCorrectedToSuccessByAdmin.getResourceExternalId(), is(charge.getExternalId()));

        PaymentStatusCorrectedToSuccessByAdminEventDetails details = (PaymentStatusCorrectedToSuccessByAdminEventDetails) paymentStatusCorrectedToSuccessByAdmin.getEventDetails();

        assertThat(details.getFee(), is(0L));
        assertThat(details.getAdminGithubId(), is("John Doe (JohnDoeGds)"));
        assertThat(details.getZendeskId(), is("1223333343"));
        assertThat(details.getUpdatedReason(), is("A refund failed and we returned the recovered funds to the service"));
        assertThat(details.getCapturedDate(), is(fixedTimestamp));
        assertThat(details.getNetAmount(), is(charge.getAmount()));
        assertThat(details.getRefundAmountAvailable(), is(0L));
        assertThat(details.getRefundAmountRefunded(), is(0L));
        assertThat(details.getRefundStatus(), is(refund.getExternalStatus()));
        assertThat(details.getGatewayAccountId(), is(charge.getGatewayAccountId()));
        assertThat(details.getCaptureSubmittedDate(), is(fixedTimestamp));
        assertThat(details.getReference(), is(charge.getReference()));
    }
}
