package uk.gov.pay.connector.service;

import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.RefundException;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.transaction.*;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.valueOf;
import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;

public class ChargeRefundService {

    public class Response {

        private GatewayResponse<BaseRefundResponse> refundGatewayResponse;
        private RefundEntity refundEntity;

        public Response(GatewayResponse<BaseRefundResponse> refundGatewayResponse, RefundEntity refundEntity) {
            this.refundGatewayResponse = refundGatewayResponse;
            this.refundEntity = refundEntity;
        }

        public GatewayResponse<BaseRefundResponse> getRefundGatewayResponse() {
            return refundGatewayResponse;
        }

        public RefundEntity getRefundEntity() {
            return refundEntity;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final RefundDao refundDao;
    private final PaymentProviders providers;
    private final Provider<TransactionFlow> transactionFlowProvider;

    @Inject
    public ChargeRefundService(ChargeDao chargeDao, RefundDao refundDao, PaymentProviders providers,
                               Provider<TransactionFlow> transactionFlowProvider) {
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
    }

    public Optional<Response> doRefund(Long accountId, String chargeId, Long amount) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> refundWithGateway(chargeEntity, amount))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private Optional<Response> refundWithGateway(ChargeEntity charge, Long amount) {
        return Optional.ofNullable(transactionFlowProvider.get()
                .executeNext(prepareForRefund(charge, amount))
                .executeNext(doGatewayRefund(providers))
                .executeNext(finishRefund())
                .complete().get(Response.class));
    }

    private PreTransactionalOperation<TransactionContext, RefundEntity> prepareForRefund(ChargeEntity chargeEntity, Long amountToBeRefunded) {
        return context -> {

            ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
            ExternalChargeRefundAvailability refundAvailability = valueOf(reloadedCharge);

            if (EXTERNAL_AVAILABLE != refundAvailability) {
                logger.error(format("ChargeId=%s, status=%s, refundStatus=%s, message=Not available for refund",
                        reloadedCharge.getId(), reloadedCharge.getStatus(), refundAvailability));
                throw RefundException.notAvailableForRefundException(reloadedCharge.getExternalId(), refundAvailability);
            }

            long totalAmountToBeRefunded = reloadedCharge.getTotalAmountToBeRefunded();

            if (totalAmountToBeRefunded - amountToBeRefunded < 0) {
                logger.error(format("ChargeId=%s, status=%s, refundStatus=%s, message=doesn't have sufficient amount for refund ([%s]), amount requested for refund is [%s]",
                        reloadedCharge.getId(), reloadedCharge.getStatus(), refundAvailability, totalAmountToBeRefunded, amountToBeRefunded));
                throw RefundException.refundException("Not sufficient amount available for refund", NOT_SUFFICIENT_AMOUNT_AVAILABLE);
            }

            RefundEntity refundEntity = new RefundEntity(reloadedCharge, amountToBeRefunded);
            reloadedCharge.getRefunds().add(refundEntity);
            refundDao.persist(refundEntity);

            logger.info(format("Card refund request sent - charge_external_id=%s, transaction_id=%s, status=%s",
                    chargeEntity.getExternalId(), chargeEntity.getGatewayTransactionId(), fromString(chargeEntity.getStatus())));

            return refundEntity;
        };
    }

    private NonTransactionalOperation<TransactionContext, GatewayResponse> doGatewayRefund(PaymentProviders providers) {
        return context -> {
            RefundEntity refundEntity = context.get(RefundEntity.class);
            return providers.byName(refundEntity.getChargeEntity().getPaymentGatewayName())
                    .refund(RefundGatewayRequest.valueOf(refundEntity));
        };
    }

    private TransactionalOperation<TransactionContext, Response> finishRefund() {
        return context -> {
            RefundEntity refundEntity = refundDao.merge(context.get(RefundEntity.class));

            GatewayResponse gatewayResponse = context.get(GatewayResponse.class);
            RefundStatus status = gatewayResponse.isSuccessful() ? RefundStatus.REFUND_SUBMITTED : RefundStatus.REFUND_ERROR;
            ChargeEntity chargeEntity = refundEntity.getChargeEntity();

            logger.info(format("Card refund response received - charge_external_id=%s, transaction_id=%s, status=%s",
                    chargeEntity.getExternalId(), chargeEntity.getGatewayTransactionId(), status));
            logger.info("Refund status to update - from={}, to={} for charge_id={}, charge_external_id={}, refund_id={}, refund_external_id={}, amount={}",
                    refundEntity.getStatus(), status, chargeEntity.getId(), chargeEntity.getExternalId(), refundEntity.getId(), refundEntity.getExternalId(), refundEntity.getAmount());
            refundEntity.setStatus(status);

            return new Response(gatewayResponse, refundEntity);
        };
    }
}
