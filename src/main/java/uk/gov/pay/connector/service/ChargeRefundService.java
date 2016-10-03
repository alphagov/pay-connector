package uk.gov.pay.connector.service;

import com.google.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.RefundDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.RefundException;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.RefundRequest;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.resources.PaymentGatewayName;
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

    public Optional<Response> doRefund(Long accountId, String chargeId, RefundRequest refundRequest) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId)
                .map(chargeEntity -> refundWithGateway(chargeEntity, refundRequest))
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private Optional<Response> refundWithGateway(ChargeEntity charge, RefundRequest refundRequest) {
        return Optional.ofNullable(transactionFlowProvider.get()
                .executeNext(prepareForRefund(charge, refundRequest))
                .executeNext(doGatewayRefund(providers))
                .executeNext(finishRefund())
                .complete().get(Response.class));
    }

    private PreTransactionalOperation<TransactionContext, RefundEntity> prepareForRefund(ChargeEntity chargeEntity, RefundRequest refundRequest) {
        return context -> {

            ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
            ExternalChargeRefundAvailability refundAvailability = valueOf(reloadedCharge);
            GatewayAccountEntity gatewayAccount = reloadedCharge.getGatewayAccount();

            if (EXTERNAL_AVAILABLE != refundAvailability) {

                logger.warn("Charge not available for refund - charge_external_id={}, status={}, refund_status={}, account_id={}, operation_type=Refund, provider={}, provider_type={}",
                        reloadedCharge.getId(),
                        fromString(reloadedCharge.getStatus()),
                        refundAvailability,
                        gatewayAccount.getId(),
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType());

                throw RefundException.notAvailableForRefundException(reloadedCharge.getExternalId(), refundAvailability);
            }

            long totalAmountToBeRefunded = reloadedCharge.getTotalAmountToBeRefunded();
            if (totalAmountToBeRefunded != refundRequest.getAmountAvailableForRefund()) {
                logger.info("Refund request has a mismatch on amount available for refund - charge_external_id={}, amount_actually_available_for_refund={}, refund_amount_available_in_request={}",
                        reloadedCharge.getExternalId(), totalAmountToBeRefunded, refundRequest.getAmountAvailableForRefund());
                throw RefundException.refundAmountAvailableMismatchException("Refund Amount Available Mismatch");
            }

            if (totalAmountToBeRefunded - refundRequest.getAmount() < 0) {

                logger.info("Charge doesn't have sufficient amount for refund - charge_external_id={}, status={}, refund_status={}, account_id={}, operation_type=Refund, provider={}, provider_type={}, amount_available_refund={}, amount_requested_refund={}",
                        reloadedCharge.getExternalId(),
                        fromString(reloadedCharge.getStatus()),
                        refundAvailability,
                        gatewayAccount.getId(),
                        gatewayAccount.getGatewayName(),
                        gatewayAccount.getType(),
                        totalAmountToBeRefunded,
                        refundRequest.getAmount());

                throw RefundException.refundException("Not sufficient amount available for refund", NOT_SUFFICIENT_AMOUNT_AVAILABLE);
            }

            RefundEntity refundEntity = new RefundEntity(reloadedCharge, refundRequest.getAmount());
            reloadedCharge.getRefunds().add(refundEntity);
            refundDao.persist(refundEntity);

            logger.info("Card refund request sent - charge_external_id={}, status={}, amount={}, transaction_id={}, account_id={}, operation_type=Refund, amount_available_refund={}, amount_requested_refund={}, provider={}, provider_type={}",
                    chargeEntity.getExternalId(),
                    fromString(chargeEntity.getStatus()),
                    chargeEntity.getAmount(),
                    chargeEntity.getGatewayTransactionId(),
                    gatewayAccount.getId(),
                    totalAmountToBeRefunded,
                    refundRequest.getAmount(),
                    gatewayAccount.getGatewayName(),
                    gatewayAccount.getType());

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
            RefundStatus status = RefundStatus.REFUND_ERROR;
            GatewayResponse gatewayResponse = context.get(GatewayResponse.class);
            ChargeEntity chargeEntity = refundEntity.getChargeEntity();

            if (gatewayResponse.isSuccessful()) {
                status = refundFinishSuccessStatusOf(chargeEntity.getPaymentGatewayName());
            }

            logger.info("Card refund response received - charge_external_id={}, transaction_id={}, status={}",
                    chargeEntity.getExternalId(), chargeEntity.getGatewayTransactionId(), status);
            logger.info("Refund status to update - charge_external_id={}, status={}, to_status={} for charge_id={}, refund_id={}, refund_external_id={}, amount={}",
                    chargeEntity.getExternalId(), refundEntity.getStatus(), status, chargeEntity.getId(), refundEntity.getId(), refundEntity.getExternalId(), refundEntity.getAmount());

            refundEntity.setStatus(status);

            return new Response(gatewayResponse, refundEntity);
        };
    }

    private RefundStatus refundFinishSuccessStatusOf(PaymentGatewayName paymentGatewayName) {
        if (paymentGatewayName == PaymentGatewayName.SANDBOX) {
            return RefundStatus.REFUNDED;
        }
        return RefundStatus.REFUND_SUBMITTED;
    }
}
