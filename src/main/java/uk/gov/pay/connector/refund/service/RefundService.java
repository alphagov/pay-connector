package uk.gov.pay.connector.refund.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.exception.RefundException;
import uk.gov.pay.connector.refund.model.RefundRequest;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import javax.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.pay.connector.charge.util.RefundCalculator.getTotalAmountAvailableToBeRefunded;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.refund.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;

public class RefundService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RefundDao refundDao;
    private final GatewayAccountDao gatewayAccountDao;
    private final PaymentProviders providers;
    private final UserNotificationService userNotificationService;
    private StateTransitionService stateTransitionService;
    private LedgerService ledgerService;

    @Inject
    public RefundService(RefundDao refundDao,
                         GatewayAccountDao gatewayAccountDao,
                         PaymentProviders providers,
                         UserNotificationService userNotificationService,
                         StateTransitionService stateTransitionService,
                         LedgerService ledgerService
    ) {
        this.refundDao = refundDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.providers = providers;
        this.userNotificationService = userNotificationService;
        this.stateTransitionService = stateTransitionService;
        this.ledgerService = ledgerService;
    }

    public ChargeRefundResponse doRefund(Long accountId, Charge charge, RefundRequest refundRequest) {
        GatewayAccountEntity gatewayAccountEntity = gatewayAccountDao.findById(accountId).orElseThrow(
                () -> new GatewayAccountNotFoundException(accountId));
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
        List<Refund> refundList = findRefunds(charge);
        long availableAmount = validateRefundAndGetAvailableAmount(charge, gatewayAccountEntity, refundRequest, refundList);
        RefundEntity refundEntity = createRefundEntity(refundRequest, charge);

        logger.info("Card refund request sent - charge_external_id={}, status={}, amount={}, transaction_id={}, account_id={}, operation_type=Refund, amount_available_refund={}, amount_requested_refund={}, provider={}, provider_type={}, user_external_id={}",
                charge.getExternalId(),
                charge.getExternalStatus(),
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

    public Optional<RefundEntity> findByChargeExternalIdAndGatewayTransactionId(String chargeExternalId, String gatewayTransactionId) {
        return refundDao.findByChargeExternalIdAndGatewayTransactionId(chargeExternalId, gatewayTransactionId);
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
            logger.info("Refund {} ({}) for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                    refundEntity.getExternalId(), gatewayAccountEntity.getGatewayName(),
                    refundEntity.getChargeExternalId(), gatewayAccountEntity.getGatewayName(),
                    refundEntity.getGatewayTransactionId(),
                    gatewayAccountEntity.getAnalyticsId(), gatewayAccountEntity.getId(),
                    gatewayRefundResponse, refundEntity.getStatus(), refundStatus);

            if (refundStatus == REFUNDED) {
                userNotificationService.sendRefundIssuedEmail(refundEntity, charge, gatewayAccountEntity);
            }

            getTransactionId(refundEntity, gatewayRefundResponse).ifPresent(refundEntity::setGatewayTransactionId);

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
                    reloadedCharge.getExternalStatus(),
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
                    reloadedCharge.getExternalStatus(),
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
    private Optional<String> getTransactionId(RefundEntity refundEntity, GatewayRefundResponse gatewayRefundResponse) {

        if (gatewayRefundResponse.isSuccessful()) {
            return Optional.ofNullable(gatewayRefundResponse.getReference().orElse(refundEntity.getExternalId()));
        } else return Optional.empty();
    }

    private long validateRefundAndGetAvailableAmount(Charge charge,
                                                     GatewayAccountEntity gatewayAccountEntity,
                                                     RefundRequest refundRequest,
                                                     List<Refund> refundList) {
        ExternalChargeRefundAvailability refundAvailability;

        refundAvailability = providers
                .byName(PaymentGatewayName.valueFrom(gatewayAccountEntity.getGatewayName()))
                .getExternalChargeRefundAvailability(charge, refundList);
        checkIfChargeIsRefundableOrTerminate(charge, refundAvailability, gatewayAccountEntity);

//      @TODO(sfount) is there a reason this currently needs to request new values from the database? This all happens in an @Transactional method
//                    can we just pass the refund list that's been passed into this method (will the newly created refund be picked up before the
//                    transaction has commited?         
        List<Refund> postRefundList = findRefunds(charge);

        long availableToBeRefunded = getTotalAmountAvailableToBeRefunded(charge, postRefundList);
        checkIfRefundRequestIsInConflictOrTerminate(refundRequest, charge, availableToBeRefunded);

        checkIfRefundAmountWithinLimitOrTerminate(refundRequest, charge, refundAvailability,
                gatewayAccountEntity, availableToBeRefunded);

        return availableToBeRefunded;
    }

    public List<RefundEntity> findNotExpungedRefunds(String chargeExternalId) {
        return refundDao.findRefundsByChargeExternalId(chargeExternalId);
    }

    public List<Refund> findRefunds(Charge charge) {
        List<Refund> refundsFromDatabase = refundDao
                .findRefundsByChargeExternalId(charge.getExternalId())
                .stream()
                .map(Refund::from)
                .collect(Collectors.toList());

        if (charge.isHistoric()) {
            // Combine refunds that have been expunged and so only exist in ledger with refunds that still exist in
            // the database, preferring records that still exist in the database as they might be in-flight.
            Stream<Refund> refundsOnlyInLedger = ledgerService
                    .getRefundsForPayment(charge.getGatewayAccountId(), charge.getExternalId())
                    .getTransactions()
                    .stream()
                    .map(Refund::from)
                    .filter(refund -> refundsFromDatabase.stream().noneMatch(refund1 -> refund1.getExternalId().equals(refund.getExternalId())));

            return Stream.concat(refundsFromDatabase.stream(), refundsOnlyInLedger).collect(Collectors.toList());
        } else {
            return refundsFromDatabase;
        }

    }

    @Transactional
    public void updateRefundParityStatus(String externalId, ParityCheckStatus parityCheckStatus) {
        refundDao.updateParityCheckStatus(externalId, ZonedDateTime.now(ZoneId.of("UTC")), parityCheckStatus);
    }
}
