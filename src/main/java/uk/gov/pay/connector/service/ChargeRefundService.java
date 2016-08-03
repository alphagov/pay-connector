package uk.gov.pay.connector.service;

import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.RefundNotAvailableRuntimeException;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.RefundGatewayResponse;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.transaction.*;

import javax.inject.Inject;
import java.util.Optional;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeRefundService {

    public class Response {

        private RefundGatewayResponse refundGatewayResponse;
        private RefundEntity refundEntity;

        public Response(RefundGatewayResponse refundGatewayResponse, RefundEntity refundEntity) {
            this.refundGatewayResponse = refundGatewayResponse;
            this.refundEntity = refundEntity;
        }

        public RefundGatewayResponse getRefundGatewayResponse() {
            return refundGatewayResponse;
        }

        public RefundEntity getRefundEntity() {
            return refundEntity;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final ChargeStatus[] PENDING_STATUS = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_READY, AUTHORISATION_SUCCESS, CAPTURE_READY, CAPTURE_SUBMITTED
    };

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

    private PreTransactionalOperation<TransactionContext, RefundEntity> prepareForRefund(ChargeEntity chargeEntity, Long amount) {
        return context -> {
            ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

            RefundEntity refundEntity = new RefundEntity(reloadedCharge, amount);
            reloadedCharge.getRefunds().add(refundEntity);

            ExternalChargeRefundAvailability refundAvailablitiy = estabishChargeRefundAvailability(reloadedCharge);
            if (EXTERNAL_AVAILABLE != refundAvailablitiy) {
                logger.error(format("Charge with id [%s], status [%s] has refund availability: [%s]",
                        reloadedCharge.getId(), reloadedCharge.getStatus(), refundAvailablitiy));
                throw new RefundNotAvailableRuntimeException(reloadedCharge.getExternalId());
            }

            refundDao.persist(refundEntity);

            return refundEntity;
        };
    }

    private NonTransactionalOperation<TransactionContext, GatewayResponse> doGatewayRefund(PaymentProviders providers) {
        return context -> {
            RefundEntity refundEntity = context.get(RefundEntity.class);
            return providers.resolve(refundEntity.getChargeEntity().getGatewayAccount().getGatewayName())
                    .refund(RefundGatewayRequest.valueOf(refundEntity.getChargeEntity(), refundEntity.getAmount()));
        };
    }

    private TransactionalOperation<TransactionContext, Response> finishRefund() {
        return context -> {
            RefundEntity refundEntity = refundDao.merge(context.get(RefundEntity.class));

            RefundGatewayResponse gatewayResponse = context.get(RefundGatewayResponse.class);
            RefundStatus newStatus = gatewayResponse.isSuccessful() ? RefundStatus.REFUND_SUBMITTED : RefundStatus.REFUND_ERROR;
            logger.info("Refund status to update - from: {}, to: {} for Charge ID: {}, Refund ID: {}, amount: {}",
                    refundEntity.getStatus(), newStatus, refundEntity.getChargeEntity().getId(), refundEntity.getId(), refundEntity.getAmount());
            refundEntity.setStatus(newStatus);
            return new Response(gatewayResponse, refundEntity);
        };
    }

    public ExternalChargeRefundAvailability estabishChargeRefundAvailability(ChargeEntity chargeEntity) {
        if (chargeEntity.hasStatus(PENDING_STATUS)) {
            return ExternalChargeRefundAvailability.EXTERNAL_PENDING;
        }
        if (chargeEntity.hasStatus(CAPTURED)) {
            long refundedAmount = getRefundedAmount(chargeEntity);
            if (chargeEntity.getAmount() >= refundedAmount) {
                return EXTERNAL_AVAILABLE;
            }
            return EXTERNAL_FULL;
        }
        return EXTERNAL_UNAVAILABLE;
    }

    public Long getRefundAmountAvailable(ChargeEntity charge) {
        return charge.getAmount() - getRefundedAmount(charge);
    }

    public Long getRefundedAmount(ChargeEntity chargeEntity) {
        return chargeEntity.getRefunds()
                .stream()
                .filter(p -> p.hasStatus(RefundStatus.CREATED, RefundStatus.REFUND_SUBMITTED, RefundStatus.REFUNDED))
                .mapToLong(RefundEntity::getAmount)
                .sum();
    }
}
