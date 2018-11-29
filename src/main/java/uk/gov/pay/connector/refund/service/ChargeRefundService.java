package uk.gov.pay.connector.refund.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.RefundCalculator;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
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
    private final UserNotificationService userNotificationService;

    @Inject
    public ChargeRefundService(ChargeDao chargeDao, RefundDao refundDao, PaymentProviders providers,
                               UserNotificationService userNotificationService
    ) {
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
        this.providers = providers;
        this.userNotificationService = userNotificationService;
    }

    public Response doRefund(Long accountId, String chargeId, RefundRequest refundRequest) {
        RefundEntity refundEntity = createRefund(accountId, chargeId, refundRequest);

        GatewayResponse<BaseRefundResponse> gatewayResponse =
                providers.byName(refundEntity.getChargeEntity().getPaymentGatewayName()).refund(RefundGatewayRequest.valueOf(refundEntity));

        updateRefundStatus(gatewayResponse, refundEntity.getId());
        RefundEntity refund = updateSandboxStatus(refundEntity.getId());

        return new Response(gatewayResponse, refund);
    }

    @Transactional
    private RefundEntity createRefund(Long accountId, String chargeId, RefundRequest refundRequest) {
        return chargeDao.findByExternalIdAndGatewayAccount(chargeId, accountId).map(chargeEntity -> {
            ExternalChargeRefundAvailability refundAvailability = providers.byName(chargeEntity.getPaymentGatewayName()).getExternalChargeRefundAvailability(chargeEntity);
            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
            checkIfChargeIsRefundableOrTerminate(chargeEntity, refundAvailability, gatewayAccount);

            long totalAmountToBeRefunded = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity);
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

    @Transactional
    private void updateRefundStatus(GatewayResponse gatewayResponse, Long refundEntityId) {
        RefundStatus status = gatewayResponse.isSuccessful() ? RefundStatus.REFUND_SUBMITTED : RefundStatus.REFUND_ERROR;
        refundDao.findById(refundEntityId).ifPresent(refundEntity -> {
            String reference = getRefundReference(refundEntity, gatewayResponse);
            ChargeEntity chargeEntity = refundEntity.getChargeEntity();

            logger.info("Refund {} ({} {}) for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    refundEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), refundEntity.getReference(),
                    chargeEntity.getExternalId(), chargeEntity.getPaymentGatewayName().getName(), chargeEntity.getGatewayTransactionId(),
                    chargeEntity.getGatewayAccount().getAnalyticsId(), chargeEntity.getGatewayAccount().getId(),
                    gatewayResponse, refundEntity.getStatus(), status);

            refundEntity.setStatus(status);
            refundEntity.setReference(reference);
            refundDao.merge(refundEntity);
        });
    }

    @Transactional
    private RefundEntity updateSandboxStatus(Long refundEntityId) {
        return refundDao.findById(refundEntityId).map(refund -> {
            ChargeEntity chargeEntity = refund.getChargeEntity();
            if (chargeEntity.getPaymentGatewayName() == PaymentGatewayName.SANDBOX
                    && refund.hasStatus(RefundStatus.REFUND_SUBMITTED)) {
                refund.setStatus(REFUNDED);
                userNotificationService.sendRefundIssuedEmail(refund);
                refundDao.merge(refund);
            }
            return refund;
        }).orElse(null);
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
}
