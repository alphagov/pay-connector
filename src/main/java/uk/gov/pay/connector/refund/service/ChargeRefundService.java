package uk.gov.pay.connector.refund.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
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
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.fromString;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.refund.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;

public class ChargeRefundService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ChargeService chargeService;
    private final RefundDao refundDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final PaymentProviders providers;
    private final UserNotificationService userNotificationService;
    private StateTransitionService stateTransitionService;

    @Inject
    public ChargeRefundService(ChargeService chargeService, RefundDao refundDao, GatewayAccountDao gatewayAccountDao, PaymentProviders providers,
                               UserNotificationService userNotificationService, StateTransitionService stateTransitionService
    ) {
        this.chargeService = chargeService;
        this.refundDao = refundDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.providers = providers;
        this.userNotificationService = userNotificationService;
        this.stateTransitionService = stateTransitionService;
    }

    public ChargeRefundResponse doRefund(Long accountId, String chargeExternalId, RefundRequest refundRequest) {
        GatewayAccountEntity gatewayAccountEntity = gatewayAccountDao.findById(accountId).orElseThrow(
                () -> new GatewayAccountNotFoundException(accountId));
        Charge charge = chargeService.findCharge(chargeExternalId, gatewayAccountEntity.getId())
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
        RefundEntity refundEntity = createRefund(charge, gatewayAccountEntity, refundRequest);
        GatewayRefundResponse gatewayRefundResponse = providers
                .byName(PaymentGatewayName.valueFrom(gatewayAccountEntity.getGatewayName()))
                .refund(RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccountEntity));
        RefundEntity refund = processRefund(gatewayRefundResponse, refundEntity.getId(), gatewayAccountEntity, charge);
        return new ChargeRefundResponse(gatewayRefundResponse, refund);
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public RefundEntity createRefund(Charge charge, GatewayAccountEntity gatewayAccountEntity, RefundRequest refundRequest) {
        List<RefundEntity> refundEntityList = refundDao.findRefundsByChargeExternalId(charge.getExternalId());
        long availableAmount = validateRefundAndGetAvailableAmount(charge, gatewayAccountEntity, refundRequest, refundEntityList);
        RefundEntity refundEntity = createRefundEntity(refundRequest, charge);

        logger.info("Card refund request sent - charge_external_id={}, status={}, amount={}, transaction_id={}, account_id={}, operation_type=Refund, amount_available_refund={}, amount_requested_refund={}, provider={}, provider_type={}, user_external_id={}",
                charge.getExternalId(),
                fromString(charge.getStatus()),
                charge.getAmount(),
                charge.getGatewayTransactionId(),
                gatewayAccountEntity.getId(),
                availableAmount,
                refundRequest.getAmount(),
                gatewayAccountEntity.getGatewayName(),
                gatewayAccountEntity.getType(),
                refundRequest.getUserExternalId());

        return refundEntity;
    }

    public Optional<RefundEntity> findByProviderAndReference(String name, String reference) {
        return refundDao.findByProviderAndReference(name, reference);
    }

    private RefundEntity processRefund(GatewayRefundResponse gatewayRefundResponse, Long refundEntityId,
                                       GatewayAccountEntity gatewayAccountEntity, Charge charge) {
        RefundStatus refundStatus = determineRefundStatus(gatewayRefundResponse);

        if (refundStatus == REFUNDED) {
            // If the gateway confirms refunds immediately, the refund status needs
            // to be set to REFUND_SUBMITTED and then REFUNDED. This will  help
            // services to view refund history in detail in self service.
            // see Javadoc (RefundHistory) for details on how history is handled
            setRefundStatus(refundEntityId, REFUND_SUBMITTED);
        }

        return updateRefund(gatewayRefundResponse, refundEntityId, refundStatus, gatewayAccountEntity, charge);
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public RefundEntity updateRefund(GatewayRefundResponse gatewayRefundResponse, Long refundEntityId,
                                     RefundStatus refundStatus, GatewayAccountEntity gatewayAccountEntity,
                                     Charge charge) {
        Optional<RefundEntity> refund = refundDao.findById(refundEntityId);

        refund.ifPresent(refundEntity -> {
            logger.info("Refund {} ({} {}) for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    refundEntity.getExternalId(), gatewayAccountEntity.getGatewayName(), refundEntity.getReference(),
                    refundEntity.getChargeExternalId(), gatewayAccountEntity.getGatewayName(),
                    refundEntity.getGatewayTransactionId(),
                    gatewayAccountEntity.getAnalyticsId(), gatewayAccountEntity.getId(),
                    gatewayRefundResponse, refundEntity.getStatus(), refundStatus);

            if (refundStatus == REFUNDED) {
                userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
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
    public RefundEntity createRefundEntity(RefundRequest refundRequest, Charge charge) {
        RefundEntity refundEntity = new RefundEntity(refundRequest.getAmount(),
                refundRequest.getUserExternalId(), refundRequest.getUserEmail(), charge.getExternalId());
        transitionRefundState(refundEntity, RefundStatus.CREATED);
        refundDao.persist(refundEntity);

        return refundEntity;
    }

    public void transitionRefundState(RefundEntity refundEntity, RefundStatus refundStatus) {
        refundEntity.setStatus(refundStatus);
        stateTransitionService.offerRefundStateTransition(refundEntity, refundStatus);
    }

    private void checkIfRefundRequestIsInConflictOrTerminate(RefundRequest refundRequest, Charge reloadedCharge, long totalAmountToBeRefunded) {
        if (totalAmountToBeRefunded != refundRequest.getAmountAvailableForRefund()) {
            logger.info("Refund request has a mismatch on amount available for refund - charge_external_id={}, amount_actually_available_for_refund={}, refund_amount_available_in_request={}",
                    reloadedCharge.getExternalId(), totalAmountToBeRefunded, refundRequest.getAmountAvailableForRefund());
            throw RefundException.refundAmountAvailableMismatchException("Refund Amount Available Mismatch");
        }
    }

    private void checkIfRefundAmountWithinLimitOrTerminate(RefundRequest refundRequest, Charge reloadedCharge, ExternalChargeRefundAvailability refundAvailability, GatewayAccountEntity gatewayAccount, long totalAmountToBeRefunded) {
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

    private void checkIfChargeIsRefundableOrTerminate(Charge reloadedCharge, ExternalChargeRefundAvailability refundAvailability, GatewayAccountEntity gatewayAccount) {
        if (EXTERNAL_AVAILABLE != refundAvailability) {

            logger.warn("Charge not available for refund - charge_external_id={}, status={}, refund_status={}, account_id={}, operation_type=Refund, provider={}, provider_type={}",
                    reloadedCharge.getExternalId(),
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

    private long validateRefundAndGetAvailableAmount(Charge charge,
                                                     GatewayAccountEntity gatewayAccountEntity,
                                                     RefundRequest refundRequest,
                                                     List<RefundEntity> refundEntityList) {
        ExternalChargeRefundAvailability refundAvailability;

        if(charge.isHistoric()) {
            refundAvailability = ExternalChargeRefundAvailability.valueOf(charge.getRefundAvailabilityStatus());
        } else {
            refundAvailability = providers
                    .byName(PaymentGatewayName.valueFrom(gatewayAccountEntity.getGatewayName()))
                    .getExternalChargeRefundAvailability(charge, refundEntityList);
        }
        checkIfChargeIsRefundableOrTerminate(charge, refundAvailability, gatewayAccountEntity);

        List<RefundEntity> refundEntities = refundDao.findRefundsByChargeExternalId(charge.getExternalId());

        long availableToBeRefunded = RefundCalculator.getTotalAmountAvailableToBeRefunded(charge, refundEntities);
        checkIfRefundRequestIsInConflictOrTerminate(refundRequest, charge, availableToBeRefunded);

        checkIfRefundAmountWithinLimitOrTerminate(refundRequest, charge, refundAvailability, 
                gatewayAccountEntity, availableToBeRefunded);

        return availableToBeRefunded;
    }
}
