package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundFailureFundsSentToConnectAccountEventDetails;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.refund.model.domain.Refund;

import java.time.Instant;

public class RefundFailureFundsSentToConnectAccount extends PaymentEvent {
    private RefundFailureFundsSentToConnectAccount(String serviceId, boolean live, Long gatewayAccountId,
                                                   String resourceExternalId,
                                                   RefundFailureFundsSentToConnectAccountEventDetails eventDetails,
                                                   Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);

    }


    public static RefundFailureFundsSentToConnectAccount from(Refund refund, Charge charge, String githubUserId, String zendeskId) {
        return new RefundFailureFundsSentToConnectAccount(
                charge.getServiceId(),
                charge.isLive(),
                charge.getGatewayAccountId(),
                charge.getExternalId(),
                new RefundFailureFundsSentToConnectAccountEventDetails(
                        refund.getAmount(),
                        charge.getReference(),
                        "Failed refund correction for payment.",
                        githubUserId,
                        "A refund failed and we returned the recovered funds to the service",
                        charge.getPaymentGatewayName(),
                        charge.getGatewayAccountId(),
                        charge.getGatewayTransactionId(),
                        zendeskId
                ),
                Instant.now()
        );
    }

}