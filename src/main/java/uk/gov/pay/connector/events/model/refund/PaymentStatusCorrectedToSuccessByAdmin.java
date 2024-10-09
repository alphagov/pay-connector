package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.PaymentStatusCorrectedToSuccessByAdminEventDetails;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.refund.model.domain.GithubAndZendeskCredential;
import uk.gov.pay.connector.refund.model.domain.Refund;

import java.time.Instant;
import java.time.ZonedDateTime;

public class PaymentStatusCorrectedToSuccessByAdmin extends PaymentEvent {
    private PaymentStatusCorrectedToSuccessByAdmin(String serviceId, boolean live, Long gatewayAccountId,
                                                   String resourceExternalId,
                                                   PaymentStatusCorrectedToSuccessByAdminEventDetails eventDetails,
                                                   Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentStatusCorrectedToSuccessByAdmin from(Refund refund, Charge charge, Instant timestamp, String githubId, String zendeskId) {

        return new PaymentStatusCorrectedToSuccessByAdmin(
                charge.getServiceId(),
                charge.isLive(),
                charge.getGatewayAccountId(),
                charge.getExternalId(),
                new PaymentStatusCorrectedToSuccessByAdminEventDetails(
                        0L,
                        charge.getAmount(),
                        timestamp,
                        refund.getExternalStatus(),
                        "A refund failed and we returned the recovered funds to the service",
                        githubId,
                        charge.getGatewayAccountId(),
                        timestamp,
                        0L,
                        0L,
                        charge.getReference(),
                        zendeskId
                ),
                Instant.now()
        );
    }
}
