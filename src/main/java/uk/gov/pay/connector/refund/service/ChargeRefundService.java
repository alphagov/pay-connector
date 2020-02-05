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
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.queue.StateTransitionService;
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
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;

public class ChargeRefundService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeDao chargeDao;
    private final RefundDao refundDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final PaymentProviders providers;
    private final UserNotificationService userNotificationService;
    private StateTransitionService stateTransitionService;

    @Inject
    public ChargeRefundService(ChargeDao chargeDao, RefundDao refundDao, GatewayAccountDao gatewayAccountDao, PaymentProviders providers,
                               UserNotificationService userNotificationService, StateTransitionService stateTransitionService
    ) {
        this.chargeDao = chargeDao;
        this.refundDao = refundDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.providers = providers;
        this.userNotificationService = userNotificationService;
        this.stateTransitionService = stateTransitionService;
    }

    public ChargeRefundResponse doRefund(Long accountId, String chargeExternalId, RefundRequest refundRequest) {
        GatewayAccountEntity gatewayAccountEntity = gatewayAccountDao.findById(accountId).orElseThrow(
                () -> new GatewayAccountNotFoundException(accountId));
        RefundEntity refundEntity = createRefund(accountId, chargeExternalId, refundRequest);
        GatewayRefundResponse gatewayRefundResponse = providers
                .byName(PaymentGatewayName.valueFrom(gatewayAccountEntity.getGatewayName()))
                .refund(RefundGatewayRequest.valueOf(refundEntity, gatewayAccountEntity));
        RefundEntity refund = processRefund(gatewayRefundResponse, refundEntity.getId(), gatewayAccountEntity);
        return new ChargeRefundResponse(gatewayRefundResponse, refund);
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public RefundEntity createRefund(Long accountId, String chargeExternalId, RefundRequest refundRequest) {
        GatewayAccountEntity gatewayAccountEntity = gatewayAccountDao.findById(accountId).orElseThrow(
                () -> new GatewayAccountNotFoundException(accountId));
        return chargeDao.findByExternalIdAndGatewayAccount(chargeExternalId, accountId).map(chargeEntity -> {
            long availableAmount = validateRefundAndGetAvailableAmount(chargeEntity, gatewayAccountEntity, refundRequest);
            RefundEntity refundEntity = createRefundEntity(refundRequest, chargeEntity);

            logger.info("Card refund request sent - charge_external_id={}, status={}, amount={}, transaction_id={}, account_id={}, operation_type=Refund, amount_available_refund={}, amount_requested_refund={}, provider={}, provider_type={}, user_external_id={}",
                    chargeEntity.getExternalId(),
                    fromString(chargeEntity.getStatus()),
                    chargeEntity.getAmount(),
                    chargeEntity.getGatewayTransactionId(),
                    gatewayAccountEntity.getId(),
                    availableAmount,
                    refundRequest.getAmount(),
                    gatewayAccountEntity.getGatewayName(),
                    gatewayAccountEntity.getType(),
                    refundRequest.getUserExternalId());
            return refundEntity;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    public Optional<RefundEntity> findByProviderAndReference(String name, String reference) {
        return refundDao.findByProviderAndReference(name, reference);
    }

    private RefundEntity processRefund(GatewayRefundResponse gatewayRefundResponse, Long refundEntityId,
                                       GatewayAccountEntity gatewayAccountEntity) {
        RefundStatus refundStatus = determineRefundStatus(gatewayRefundResponse);

        if (refundStatus == REFUNDED) {
            // If the gateway confirms refunds immediately, the refund status needs
            // to be set to REFUND_SUBMITTED and then REFUNDED. This will  help
            // services to view refund history in detail in self service.
            // see Javadoc (RefundHistory) for details on how history is handled
            setRefundStatus(refundEntityId, REFUND_SUBMITTED);
        }

        return updateRefund(gatewayRefundResponse, refundEntityId, refundStatus, gatewayAccountEntity);
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public RefundEntity updateRefund(GatewayRefundResponse gatewayRefundResponse, Long refundEntityId,
                                     RefundStatus refundStatus, GatewayAccountEntity gatewayAccountEntity) {
        Optional<RefundEntity> refund = refundDao.findById(refundEntityId);

        refund.ifPresent(refundEntity -> {
            ChargeEntity chargeEntity = refundEntity.getChargeEntity();

            logger.info("Refund {} ({} {}) for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    refundEntity.getExternalId(), gatewayAccountEntity.getGatewayName(), refundEntity.getReference(),
                    chargeEntity.getExternalId(), gatewayAccountEntity.getGatewayName(), chargeEntity.getGatewayTransactionId(),
                    gatewayAccountEntity.getAnalyticsId(), gatewayAccountEntity.getId(),
                    gatewayRefundResponse, refundEntity.getStatus(), refundStatus);

            if (refundStatus == REFUNDED) {
                userNotificationService.sendRefundIssuedEmail(refundEntity);
            }

            getRefundReference(refundEntity, gatewayRefundResponse).ifPresent(refundEntity::setReference);
            transitionRefundState(refundEntity, refundStatus);
        });

        return refund.get();
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public void setRefundStatus(Long refundEntityId, RefundStatus refundStatus) {
        refundDao.findById(refundEntityId).ifPresent(refundEntity -> transitionRefundState(refundEntity, refundStatus));
    }

    private RefundStatus determineRefundStatus(GatewayRefundResponse gatewayRefundResponse) {
        if (gatewayRefundResponse.isSuccessful()) {
            switch (gatewayRefundResponse.state()) {
                case PENDING:
                    return REFUND_SUBMITTED;
                case COMPLETE:
                    return REFUNDED;
                default:
                    return REFUND_ERROR;
            }
        } else
            return RefundStatus.REFUND_ERROR;
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public RefundEntity createRefundEntity(RefundRequest refundRequest, ChargeEntity charge) {
        RefundEntity refundEntity = new RefundEntity(charge, refundRequest.getAmount(),
                refundRequest.getUserExternalId(), refundRequest.getUserEmail());
        transitionRefundState(refundEntity, RefundStatus.CREATED);
        charge.getRefunds().add(refundEntity);
        refundDao.persist(refundEntity);

        return refundEntity;
    }

    public void transitionRefundState(RefundEntity refundEntity, RefundStatus refundStatus) {
        refundEntity.setStatus(refundStatus);
        stateTransitionService.offerRefundStateTransition(refundEntity, refundStatus);
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

            throw RefundException.notAvailableForRefundException("Not sufficient amount available for refund", NOT_SUFFICIENT_AMOUNT_AVAILABLE);
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
     * if not successful (and the fact that we have got a proper response from Gateway, we have to assume
     * no refund has not gone through and no reference returned(or needed) to be stored.
     *
     * @see RefundGatewayRequest valueOf()
     */
    private Optional<String> getRefundReference(RefundEntity refundEntity, GatewayRefundResponse gatewayRefundResponse) {

        if (gatewayRefundResponse.isSuccessful()) {
            return Optional.ofNullable(gatewayRefundResponse.getReference().orElse(refundEntity.getExternalId()));
        } else return Optional.empty();
    }

    private long validateRefundAndGetAvailableAmount(ChargeEntity chargeEntity, 
                                                     GatewayAccountEntity gatewayAccountEntity,
                                                     RefundRequest refundRequest) {
        ExternalChargeRefundAvailability refundAvailability = providers
                .byName(PaymentGatewayName.valueFrom(gatewayAccountEntity.getGatewayName()))
                .getExternalChargeRefundAvailability(chargeEntity);
        checkIfChargeIsRefundableOrTerminate(chargeEntity, refundAvailability, gatewayAccountEntity);

        long availableToBeRefunded = RefundCalculator.getTotalAmountAvailableToBeRefunded(chargeEntity);
        checkIfRefundRequestIsInConflictOrTerminate(refundRequest, chargeEntity, availableToBeRefunded);

        checkIfRefundAmountWithinLimitOrTerminate(refundRequest, chargeEntity, refundAvailability, 
                gatewayAccountEntity, availableToBeRefunded);

        return availableToBeRefunded;
    }
}
