package uk.gov.pay.connector.refund.service;


import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.client.ledger.exception.LedgerException;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.events.model.refund.PaymentStatusCorrectedToSuccessByAdmin;
import uk.gov.pay.connector.events.model.refund.RefundFailureFundsSentToConnectAccount;
import uk.gov.pay.connector.events.model.refund.RefundStatusCorrectedToErrorByAdmin;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClient;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClientFactory;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;


public class RefundReversalService {
    private final LedgerService ledgerService;
    private final RefundDao refundDao;
    private final StripeSdkClientFactory stripeSdkClientFactory;
    private final RefundReversalStripeConnectTransferRequestBuilder refundRequest;
    private final Logger logger = LoggerFactory.getLogger(LedgerService.class);


    @Inject
    public RefundReversalService(LedgerService ledgerService, RefundDao refundDao, StripeSdkClientFactory stripeSdkClientFactory,
                                 RefundReversalStripeConnectTransferRequestBuilder refundRequest) {
        this.ledgerService = ledgerService;
        this.refundDao = refundDao;
        this.stripeSdkClientFactory = stripeSdkClientFactory;
        this.refundRequest = refundRequest;
    }

    public Optional<Refund> findMaybeHistoricRefundByRefundId(String refundExternalId) {
        return refundDao.findByExternalId(refundExternalId)
                .map(Refund::from)
                .or(() -> ledgerService.getTransaction(refundExternalId).map(Refund::from));
    }


    public Response reverseFailedRefund(GatewayAccountEntity gatewayAccount, Refund refund, Charge charge, String githubUserId, String zendeskUserId) {

        RandomIdGenerator randomIdGenerator = new RandomIdGenerator();
        String correctionPaymentId = randomIdGenerator.random13ByteHexGenerator();
        String stripeRefundId = refund.getGatewayTransactionId();
        boolean isLiveGatewayAccount = gatewayAccount.isLive();
        String refundExternalId = refund.getExternalId();

        StripeSdkClient stripeClient = stripeSdkClientFactory.getInstance();

        com.stripe.model.Refund refundFromStripe;
        try {
            refundFromStripe = stripeClient.getRefund(stripeRefundId, isLiveGatewayAccount);

        } catch (StripeException e) {
            throw new WebApplicationException(
                    format("There was an error trying to get refund from Stripe with refund id: %s", refundExternalId));
        }

        String refundStatus = refundFromStripe.getStatus();

        if (!"failed".equals(refundStatus)) {
            throw new WebApplicationException(badRequestResponse(
                    format("Refund with Refund ID: %s and Stripe ID: %s is not in a failed state", refundExternalId, stripeRefundId)));
        }

        Map<String, Object> refundRequestBody = refundRequest.createRequest(correctionPaymentId, refundFromStripe);
        String transferId;
        try {

            transferId = stripeClient.createTransfer(refundRequestBody, isLiveGatewayAccount, refundExternalId);
        } catch (StripeException e) {
            if (e.getStripeError() != null && "insufficient_funds".equals(e.getStripeError().getDeclineCode())) {
                throw new WebApplicationException(badRequestResponse(
                        format("Transfer failed due to insufficient funds for refund with %s %s", refundExternalId, e.getMessage())));
            } else if (e.getStripeError() != null && "idempotency_error".equals(e.getStripeError().getType()))
                throw new WebApplicationException(badRequestResponse(format("failed transfer due to idempotency error for refund with %s %s", refundExternalId, e.getMessage())));
            else {
                throw new WebApplicationException(
                        format("There was an error trying to create transfer with id: %s from Stripe: %s", refundExternalId, e.getMessage()));
            }
        }

        try {
            ledgerService.postEvent(List.of(
                            PaymentStatusCorrectedToSuccessByAdmin.from(correctionPaymentId, refund, charge, Instant.now(), githubUserId, zendeskUserId),
                            RefundFailureFundsSentToConnectAccount.from(correctionPaymentId, refund, charge, githubUserId, zendeskUserId, transferId),
                            RefundStatusCorrectedToErrorByAdmin.from(refund, charge, githubUserId, zendeskUserId)
                    )
            );
            return Response.ok().build();
        } catch (LedgerException e) {
            logger.info(e.getMessage(),
                    kv("refundExternalId", refundExternalId),
                    kv("gatewayTransactionId", stripeRefundId));

            ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC,
                    "Stripe transfer successful but error updating payment and refunds");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponse)
                    .type(APPLICATION_JSON)
                    .build();
        }

    }
}
