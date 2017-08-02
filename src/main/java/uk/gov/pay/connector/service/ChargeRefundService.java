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
import uk.gov.pay.connector.service.transaction.*;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.valueOf;
import static uk.gov.pay.connector.model.domain.ChargeStatus.fromString;

public class ChargeRefundService {

    public class Response {

        private GatewayResponse refundGatewayResponse;
        private RefundEntity refundEntity;

        public Response(GatewayResponse refundGatewayResponse, RefundEntity refundEntity) {
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
                .executeNext(prepareForRefund(providers, charge, refundRequest))
                .executeNext(doGatewayRefund(providers))
                .executeNext(finishRefund())
                .complete().get(Response.class));
    }

    private PreTransactionalOperation<TransactionContext, RefundEntity> prepareForRefund(PaymentProviders providers, ChargeEntity chargeEntity, RefundRequest refundRequest) {
        return context -> {

            ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);
            ExternalChargeRefundAvailability refundAvailability = valueOf(reloadedCharge);
            GatewayAccountEntity gatewayAccount = reloadedCharge.getGatewayAccount();
            checkIfChargeIsRefundableOrTerminate(reloadedCharge, refundAvailability, gatewayAccount);

            long totalAmountToBeRefunded = reloadedCharge.getTotalAmountToBeRefunded();
            checkIfRefundRequestIsInConflictOrTerminate(refundRequest, reloadedCharge, totalAmountToBeRefunded);

            checkIfRefundAmountWithinLimitOrTerminate(refundRequest, reloadedCharge, refundAvailability, gatewayAccount, totalAmountToBeRefunded);

            RefundEntity refundEntity = completePrepareRefund(refundRequest, reloadedCharge);

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
            return getPaymentProviderFor(providers, refundEntity.getChargeEntity()).refund(RefundGatewayRequest.valueOf(refundEntity));
        };
    }

    private TransactionalOperation<TransactionContext, Response> finishRefund() {
        return context -> {
            RefundEntity refundEntity = refundDao.merge(context.get(RefundEntity.class));
            GatewayResponse<BaseRefundResponse> gatewayResponse = context.get(GatewayResponse.class);
            ChargeEntity chargeEntity = refundEntity.getChargeEntity();

            RefundStatus status = determineRefundStatus(gatewayResponse, chargeEntity);
            String reference = getRefundReference(refundEntity,gatewayResponse);

            logger.info("Card refund response received -  transaction_id={}, charge_id={}, charge_external_id={}, refund_id={}, refund_external_id={}, refund_reference={}, refund_status={}, refund_amount={}",
                    chargeEntity.getGatewayTransactionId(), chargeEntity.getId(), chargeEntity.getExternalId(), refundEntity.getId(), refundEntity.getExternalId(), reference, refundEntity.getStatus(), refundEntity.getAmount());
            logger.info("Refund status to update - status={}, to_status={} for transaction_id={}, charge_id={}, charge_external_id={}, refund_id={}, refund_external_id={}, refund_reference={}, refund_status={}, refund_amount={}",
                    refundEntity.getStatus(), status, chargeEntity.getGatewayTransactionId(), chargeEntity.getId(), chargeEntity.getExternalId(), refundEntity.getId(), refundEntity.getExternalId(), reference, refundEntity.getStatus(), refundEntity.getAmount());

            refundEntity.setStatus(status);
            refundEntity.setReference(reference);
            return new Response(gatewayResponse, refundEntity);
        };
    }

    private RefundEntity completePrepareRefund(RefundRequest refundRequest, ChargeEntity reloadedCharge) {
        RefundEntity refundEntity = new RefundEntity(reloadedCharge, refundRequest.getAmount());
        reloadedCharge.getRefunds().add(refundEntity);
        refundDao.persist(refundEntity);
        return refundEntity;
    }

    private void checkIfRefundRequestIsInConflictOrTerminate(RefundRequest refundRequest, ChargeEntity reloadedCharge, long totalAmountToBeRefunded) {
        if (totalAmountToBeRefunded != refundRequest.getAmountAvailableForRefund()) {
            logger.info("Refund request has a mismatch on amount available for refund - charge_external_id={}, amount_actually_available_for_refund={}, refund_amount_available_in_request={}",
                    reloadedCharge.getExternalId(), totalAmountToBeRefunded, refundRequest.getAmountAvailableForRefund());
            throw RefundException.refundAmountAvailableMismatchException("Refund Amount Available Mismatch");
        }
    }

    private void checkIfRefundAmountWithinLimitOrTerminate(RefundRequest refundRequest, ChargeEntity reloadedCharge, ExternalChargeRefundAvailability refundAvailability, GatewayAccountEntity gatewayAccount, long totalAmountToBeRefunded) {
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
    }

    private void checkIfChargeIsRefundableOrTerminate(ChargeEntity reloadedCharge, ExternalChargeRefundAvailability refundAvailability, GatewayAccountEntity gatewayAccount) {
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
    }

    /**
     * <p>Worldpay -> Worldpay doesn't return reference. We use our externalId because that's what we sent in the
     * request as our reference and it will be sent by Worldpay with the notification.</p>
     * <p>Smartpay -> We get the pspReference returned by them. This will also be sent with the notification.</p>
     * <p>ePDQ -> We construct PAYID/PAYIDSUB and use that as the reference. PAYID and PAYIDSUB will be sent with the
     * notification.</p>
     * @see RefundGatewayRequest valueOf()
     */
    private String getRefundReference(RefundEntity refundEntity, GatewayResponse<BaseRefundResponse> gatewayResponse) {
        if (gatewayResponse.isSuccessful()) {

            return gatewayResponse.getBaseResponse().get().getReference().orElse(refundEntity.getExternalId());
        }
        /**
         * if not successful (and the fact that we have got a proper response from Gateway, we have to assume
         * no refund has not gone through and no reference returned(or needed) to be stored.
         */
        return "";
    }

    private RefundStatus determineRefundStatus(GatewayResponse<BaseRefundResponse> gatewayResponse, ChargeEntity chargeEntity) {
        if (gatewayResponse.isSuccessful()) {
            return refundFinishSuccessStatusOf(chargeEntity.getPaymentGatewayName());
        }
        return RefundStatus.REFUND_ERROR;
    }

    // TODO: this will be removed as part of PP-1063
    private RefundStatus refundFinishSuccessStatusOf(PaymentGatewayName paymentGatewayName) {
        if (paymentGatewayName == PaymentGatewayName.SANDBOX) {
            return RefundStatus.REFUNDED;
        }
        return RefundStatus.REFUND_SUBMITTED;
    }

    public PaymentProvider<BaseRefundResponse, ?> getPaymentProviderFor(PaymentProviders providers, ChargeEntity chargeEntity) {
        PaymentProvider<BaseRefundResponse, ?> paymentProvider = providers.byName(chargeEntity.getPaymentGatewayName());
        return paymentProvider;
    }
}
