package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundStatusCorrectedToErrorByAdminEventDetails;
import uk.gov.pay.connector.refund.model.domain.GithubAndZendeskCredential;
import uk.gov.pay.connector.refund.model.domain.Refund;

import java.time.Instant;

public class RefundStatusCorrectedToErrorByAdmin extends RefundEvent {
    
    private RefundStatusCorrectedToErrorByAdmin(String serviceId, boolean live, Long gatewayAccountId,
                                                String resourceExternalId, String parentResourceExternalId,
                                                RefundStatusCorrectedToErrorByAdminEventDetails eventDetails,
                                                Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, parentResourceExternalId, eventDetails, timestamp);
    }

    public static RefundStatusCorrectedToErrorByAdmin from(Refund refund, Charge charge, String githubId, String zendeskId) {
        return new RefundStatusCorrectedToErrorByAdmin(
                charge.getServiceId(),
                charge.isLive(),
                charge.getGatewayAccountId(),
                refund.getExternalId(),
                refund.getChargeExternalId(),
                new RefundStatusCorrectedToErrorByAdminEventDetails(
                        "Correct refund status to match Stripe",
                        githubId,
                        zendeskId
                ),
                Instant.now()
        );
    }
}
