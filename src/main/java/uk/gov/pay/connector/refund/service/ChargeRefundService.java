package uk.gov.pay.connector.refund.service;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.transaction.NonTransactionalOperation;
import uk.gov.pay.connector.charge.service.transaction.PreTransactionalOperation;
import uk.gov.pay.connector.charge.service.transaction.TransactionContext;
import uk.gov.pay.connector.charge.service.transaction.TransactionFlow;
import uk.gov.pay.connector.charge.service.transaction.TransactionalOperation;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.exception.RefundException;
import uk.gov.pay.connector.refund.model.RefundRequest;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.refund.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;

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
    private final UserNotificationService userNotificationService;
    @Inject
    public ChargeRefundService(ChargeDao chargeDao, RefundDao refundDao, PaymentProviders providers,
                               Provider<TransactionFlow> transactionFlowProvider,
                               UserNotificationService userNotificationService
                               ) {
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
        this.providers = providers;
        this.transactionFlowProvider = transactionFlowProvider;
        this.userNotificationService = userNotificationService;
    }

    public Optional<Response> doRefund(Long accountId, String chargeId, RefundRequest refundRequest) {

        return Optional.ofNullable(transactionFlowProvider.get()
                .executeNext(prepareForRefund(providers, accountId, chargeId, refundRequest))
                .executeNext(doGatewayRefund(providers))
                .executeNext(setAsSubmitted())
                .executeNext(setSandboxAsRefunded())
                .complete().get(Response.class));
    }

    private TransactionalOperation<TransactionContext, Response> setSandboxAsRefunded() {
        return context -> {
            Response response = context.get(Response.class);
            RefundEntity refund = response.getRefundEntity();
            if (refund.getChargeEntity().getPaymentGatewayName() == PaymentGatewayName.SANDBOX
                    && refund.hasStatus(RefundStatus.REFUND_SUBMITTED)) {
                RefundEntity refundEntity = refundDao.findById(refund.getId()).get();
                refundEntity.setStatus(REFUNDED);
                userNotificationService.sendRefundIssuedEmail(refundEntity);
                response = new Response(response.getRefundGatewayResponse(), refundEntity);
            }
            return response;
        };
    }

    private PreTransactionalOperation<TransactionContext, RefundEntity> prepareForRefund(PaymentProviders providers, Long accountId, String chargeId, RefundRequest refundRequest) {
        return context -> chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId).map(chargeEntity -> {

            ExternalChargeRefundAvailability refundAvailability = providers.byName(chargeEntity.getPaymentGatewayName()).getExternalChargeRefundAvailability(chargeEntity);
            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
            checkIfChargeIsRefundableOrTerminate(chargeEntity, refundAvailability, gatewayAccount);

            long totalAmountToBeRefunded = chargeEntity.getTotalAmountToBeRefunded();
            checkIfRefundRequestIsInConflictOrTerminate(refundRequest, chargeEntity, totalAmountToBeRefunded);

            checkIfRefundAmountWithinLimitOrTerminate(refundRequest, chargeEntity, refundAvailability, gatewayAccount, totalAmountToBeRefunded);

            RefundEntity refundEntity = completePrepareRefund(refundRequest, chargeEntity);

            logger.info("Card refund request sent - charge_external_id={}, status={}, amount={}, transaction_id={}, account_id={}, operation_type=Refund, amount_available_refund={}, amount_requested_refund={}, provider={}, provider_type={}, user_external_id={}",
                    chargeEntity.getExternalId(),
                    fromString(chargeEntity.getStatus()),
                    chargeEntity.getAmount(),
                    chargeEntity.getGatewayTransactionId(),
                    gatewayAccount.getId(),
                    totalAmountToBeRefunded,
                    refundRequest.getAmount(),
                    gatewayAccount.getGatewayName(),
                    gatewayAccount.getType(),
                    refundRequest.getUserExternalId());

            return refundEntity;

        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeId));
    }

    private NonTransactionalOperation<TransactionContext, GatewayResponse> doGatewayRefund(PaymentProviders providers) {
        return context -> {
            RefundEntity refundEntity = context.get(RefundEntity.class);
            return getPaymentProviderFor(providers, refundEntity.getChargeEntity()).refund(RefundGatewayRequest.valueOf(refundEntity));
        };
    }

    private TransactionalOperation<TransactionContext, Response> setAsSubmitted() {
        return context -> {
            RefundEntity refundEntity = refundDao.findById(context.get(RefundEntity.class).getId()).get();

            GatewayResponse<BaseRefundResponse> gatewayResponse = context.get(GatewayResponse.class);
            ChargeEntity chargeEntity = refundEntity.getChargeEntity();

            RefundStatus status = gatewayResponse.isSuccessful() ? RefundStatus.REFUND_SUBMITTED : RefundStatus.REFUND_ERROR;
            String reference = getRefundReference(refundEntity, gatewayResponse);

            logger.info("Refund {} ({} {}) for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    refundEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), refundEntity.getReference(),
                    chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), chargeEntity.getGatewayTransactionId(),
                    chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                    gatewayResponse, refundEntity.getStatus(), status);

            refundEntity.setStatus(status);
            refundEntity.setReference(reference);

            return new Response(gatewayResponse, refundEntity);
        };
    }

    @Transactional
    private RefundEntity completePrepareRefund(RefundRequest refundRequest, ChargeEntity charge) {
        RefundEntity refundEntity = new RefundEntity(charge, refundRequest.getAmount(), refundRequest.getUserExternalId());
        charge.getRefunds().add(refundEntity);
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
     *
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

    public PaymentProvider<BaseRefundResponse, ?> getPaymentProviderFor(PaymentProviders providers, ChargeEntity chargeEntity) {
        PaymentProvider<BaseRefundResponse, ?> paymentProvider = providers.byName(chargeEntity.getPaymentGatewayName());
        return paymentProvider;
    }
}
