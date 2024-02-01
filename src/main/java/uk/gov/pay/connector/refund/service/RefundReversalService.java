package uk.gov.pay.connector.refund.service;

import com.stripe.exception.StripeException;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClient;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClientFactory;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.Refund;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class RefundReversalService {
    private final LedgerService ledgerService;
    private final RefundDao refundDao;
    private final StripeSdkClientFactory stripeSdkClientFactory;

    @Inject
    public RefundReversalService(LedgerService ledgerService, RefundDao refundDao, StripeSdkClientFactory stripeSdkClientFactory) {
        this.ledgerService = ledgerService;
        this.refundDao = refundDao;
        this.stripeSdkClientFactory = stripeSdkClientFactory;
    }

    public Optional<Refund> findMaybeHistoricRefundByRefundId(String refundExternalId) {
        return refundDao.findByExternalId(refundExternalId)
                .map(Refund::from)
                .or(() -> ledgerService.getTransaction(refundExternalId).map(Refund::from));
    }

    public void reverseFailedRefund(GatewayAccountEntity gatewayAccount, Refund refund) {
        String stripeRefundId = refund.getGatewayTransactionId();
        boolean isLiveGatewayAccount = gatewayAccount.isLive();
        String refundExternalId = refund.getExternalId();

        StripeSdkClient stripeClient = stripeSdkClientFactory.getInstance();

        try {
            com.stripe.model.Refund refundFromStripe = stripeClient.getRefund(stripeRefundId, isLiveGatewayAccount);
            String refundStatus = refundFromStripe.getStatus();

            if ("failed".equals(refundStatus)) {
                // TODO: 04/01/2024  (PP-11555)
            } else {
                throw new WebApplicationException(badRequestResponse(
                        format("Refund with Refund ID: %s and Stripe ID: %s is not in a failed state", refundExternalId, stripeRefundId)));
            }
        } catch (StripeException e) {
            throw new WebApplicationException(format("Unexpected error trying to get refund with ID:%s from Stripe", refundExternalId));
        }
    }
}
