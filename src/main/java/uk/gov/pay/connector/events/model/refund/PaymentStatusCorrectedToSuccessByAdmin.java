package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.events.eventdetails.refund.PaymentStatusCorrectedToSuccessByAdminEventDetails;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.refund.model.domain.Refund;

import java.time.Instant;

public class PaymentStatusCorrectedToSuccessByAdmin extends PaymentEvent {
    private PaymentStatusCorrectedToSuccessByAdmin(String serviceId, boolean live, Long gatewayAccountId,
                                                   String resourceExternalId,
                                                   PaymentStatusCorrectedToSuccessByAdminEventDetails eventDetails,
                                                   Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentStatusCorrectedToSuccessByAdmin from(String correctionPaymentId,  Refund refund,
                                                              Charge charge, Instant timestamp, String githubId, String zendeskId) {

        return new PaymentStatusCorrectedToSuccessByAdmin(
                charge.getServiceId(),
                charge.isLive(),
                charge.getGatewayAccountId(),
                correctionPaymentId,
                new PaymentStatusCorrectedToSuccessByAdminEventDetails(
                        0L,
                        refund.getAmount(),
                        timestamp,
                        ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE.getStatus(),
                        "A refund failed and we returned the recovered funds to the service - Zendesk ticket " + zendeskId,
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
