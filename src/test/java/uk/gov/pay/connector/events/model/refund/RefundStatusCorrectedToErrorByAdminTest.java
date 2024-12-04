package uk.gov.pay.connector.events.model.refund;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.events.eventdetails.refund.RefundStatusCorrectedToErrorByAdminEventDetails;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.GithubAndZendeskCredential;
import uk.gov.pay.connector.refund.model.domain.Refund;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;

class RefundStatusCorrectedToErrorByAdminTest {
    @Test
    void shouldCreateRefundStatusCorrectedToErrorByAdminEventFromRefundAndCharge() {

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

        RefundStatusCorrectedToErrorByAdmin refundStatusCorrectedToErrorByAdmin = RefundStatusCorrectedToErrorByAdmin
                .from(refund, charge, githubUserId, zendeskId);

        assertThat(refundStatusCorrectedToErrorByAdmin.getParentResourceExternalId(), is(charge.getExternalId()));
        assertThat(refundStatusCorrectedToErrorByAdmin.isLive(), is(true));
        assertThat(refundStatusCorrectedToErrorByAdmin.getGatewayAccountId(), is(charge.getGatewayAccountId()));
        assertThat(refundStatusCorrectedToErrorByAdmin.getResourceExternalId(), is(refund.getExternalId()));
        assertThat(refundStatusCorrectedToErrorByAdmin.getParentResourceExternalId(), is(refund.getChargeExternalId()));

        RefundStatusCorrectedToErrorByAdminEventDetails details = (RefundStatusCorrectedToErrorByAdminEventDetails)
                refundStatusCorrectedToErrorByAdmin.getEventDetails();

        assertThat(details.getUpdatedReason(), is("Correct refund status to match Stripe - Zendesk ticket " + zendeskId));
        assertThat(details.getAdminGithubId(), is("John Doe (JohnDoeGds)"));
        assertThat(details.getZendeskId(), is("1223333343"));
    }
}
