package uk.gov.pay.connector.refund.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.exception.GatewayAccountDisabledException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountCredentialsNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.exception.RefundException;
import uk.gov.pay.connector.refund.model.RefundRequest;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.service.UserNotificationService;

import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.charge.util.RefundCalculator.getTotalAmountAvailableToBeRefunded;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_UNAVAILABLE;
import static uk.gov.pay.connector.refund.exception.RefundException.ErrorCode.NOT_SUFFICIENT_AMOUNT_AVAILABLE;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class RefundService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RefundDao refundDao;
    private final PaymentProviders providers;
    private final UserNotificationService userNotificationService;
    private StateTransitionService stateTransitionService;
    private LedgerService ledgerService;
    private GatewayAccountCredentialsService gatewayAccountCredentialsService;

    @Inject
    public RefundService(RefundDao refundDao,
                         PaymentProviders providers,
                         UserNotificationService userNotificationService,
                         StateTransitionService stateTransitionService,
                         LedgerService ledgerService,
                         GatewayAccountCredentialsService gatewayAccountCredentialsService
    ) {
        this.refundDao = refundDao;
        this.providers = providers;
        this.userNotificationService = userNotificationService;
        this.stateTransitionService = stateTransitionService;
        this.ledgerService = ledgerService;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
    }
    
    public ChargeRefundResponse submitRefund(GatewayAccountEntity gatewayAccountEntity, Charge charge, RefundRequest refundRequest) {
        if (PaymentGatewayName.isUnsupported(charge.getPaymentGatewayName())) {
            throw new NotFoundException();
        }        
        if (gatewayAccountEntity.isDisabled()) {
            throw new GatewayAccountDisabledException("Attempt to create a refund for a disabled gateway account");
        }
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)
                .orElseThrow(() -> new GatewayAccountCredentialsNotFoundException("Unable to find gateway account credentials to use to refund charge."));

        var paymentProvider = providers.byName(PaymentGatewayName.valueFrom(charge.getPaymentGatewayName()));

        RefundEntity refundEntity = createRefund(charge, gatewayAccountEntity, refundRequest, paymentProvider.getRefundEntityFactory());

        GatewayRefundResponse gatewayRefundResponse = paymentProvider
                .refund(RefundGatewayRequest.valueOf(charge, refundEntity, gatewayAccountEntity, gatewayAccountCredentialsEntity));

        RefundStatus refundStatus = determineRefundStatus(gatewayRefundResponse);

        if (refundStatus == REFUNDED) {
            // If the gateway confirms refunds immediately, the refund status needs
            // to be set to REFUND_SUBMITTED and then REFUNDED. This will  help
            // services to view refund history in detail in self service.
            // see Javadoc (RefundHistory) for details on how history is handled
             RefundEntity refundWithSubmittedState = setRefundStatus(refundEntity.getId(), gatewayAccountEntity, REFUND_SUBMITTED, charge);
             userNotificationService.sendRefundIssuedEmail(refundWithSubmittedState, charge, gatewayAccountEntity);
        }
        
        RefundEntity updatedRefundEntity = updateRefundWithTransactionIdAndState(gatewayRefundResponse, refundEntity.getId(), refundStatus, gatewayAccountEntity, charge);
        return new ChargeRefundResponse(gatewayRefundResponse, updatedRefundEntity);
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public RefundEntity createRefund(Charge charge, GatewayAccountEntity gatewayAccountEntity, RefundRequest refundRequest, RefundEntityFactory refundEntityFactory) {
        List<Refund> refundList = findRefunds(charge);
        long availableAmount = validateRefundAndGetAvailableAmount(charge, gatewayAccountEntity, refundRequest, refundList);
        RefundEntity refundEntity = createRefundEntity(refundRequest, gatewayAccountEntity, charge, refundEntityFactory);

        logger.info("Card refund request sent - charge_external_id={}, status={}, amount={}, transaction_id={}, account_id={}, operation_type=Refund, amount_available_refund={}, amount_requested_refund={}, provider={}, provider_type={}, user_external_id={}",
                charge.getExternalId(),
                charge.getExternalStatus(),
                charge.getAmount(),
                charge.getGatewayTransactionId(),
                gatewayAccountEntity.getId(),
                availableAmount,
                refundRequest.getAmount(),
                charge.getPaymentGatewayName(),
                gatewayAccountEntity.getType(),
                refundRequest.getUserExternalId());

        return refundEntity;
    }

    public Optional<RefundEntity> findByChargeExternalIdAndGatewayTransactionId(String chargeExternalId, String gatewayTransactionId) {
        return refundDao.findByChargeExternalIdAndGatewayTransactionId(chargeExternalId, gatewayTransactionId);
    }

    public Optional<Refund> findHistoricRefundByChargeExternalIdAndGatewayTransactionId(Charge charge,
                                                                                        String gatewayTransactionId) {
        List<Refund> refunds = getHistoricRefunds(charge);
        return refunds.stream()
                .filter(refund -> isNotBlank(refund.getGatewayTransactionId())
                        && refund.getGatewayTransactionId().equals(gatewayTransactionId))
                .findFirst();
    }
    
    @Transactional
    public RefundEntity updateRefundWithTransactionIdAndState(GatewayRefundResponse gatewayRefundResponse, Long refundEntityId,
                                                              RefundStatus refundStatus, GatewayAccountEntity gatewayAccountEntity,
                                                              Charge charge) {

        RefundEntity refundEntity = reloadRefundFromDatabase(refundEntityId);
        
        logger.info("Refund {} ({}) for {} ({} {}) for {} ({}) - {} .'. {} -> {}",
                refundEntity.getExternalId(), charge.getPaymentGatewayName(),
                refundEntity.getChargeExternalId(), charge.getPaymentGatewayName(),
                refundEntity.getGatewayTransactionId(),
                gatewayAccountEntity.getAnalyticsId(), gatewayAccountEntity.getId(),
                gatewayRefundResponse, refundEntity.getStatus(), refundStatus);
        
        getTransactionId(refundEntity, gatewayRefundResponse).ifPresent(refundEntity::setGatewayTransactionId);
        transitionRefundState(refundEntity, gatewayAccountEntity, refundStatus, charge);
        return refundEntity;
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public RefundEntity setRefundStatus(Long refundEntityId, GatewayAccountEntity gatewayAccountEntity, RefundStatus refundStatus, Charge charge) {
        RefundEntity refundEntity = reloadRefundFromDatabase(refundEntityId);
        transitionRefundState(refundEntity, gatewayAccountEntity, refundStatus, charge);
        return refundEntity;
    }

    private RefundStatus determineRefundStatus(GatewayRefundResponse gatewayRefundResponse) {
        if (gatewayRefundResponse.isSuccessful()) {
            return switch (gatewayRefundResponse.state()) {
                case PENDING -> REFUND_SUBMITTED;
                case COMPLETE -> REFUNDED;
                default -> REFUND_ERROR;
            };
        } else
            return RefundStatus.REFUND_ERROR;
    }

    @Transactional
    @SuppressWarnings("WeakerAccess")
    public RefundEntity createRefundEntity(RefundRequest refundRequest, GatewayAccountEntity gatewayAccountEntity, Charge charge, RefundEntityFactory refundEntityFactory) {
        var refundEntity = refundEntityFactory.create(refundRequest.getAmount(),
                refundRequest.getUserExternalId(), refundRequest.getUserEmail(), charge.getExternalId());
        transitionRefundState(refundEntity, gatewayAccountEntity, RefundStatus.CREATED, charge);
        refundDao.persist(refundEntity);

        return refundEntity;
    }

    public void transitionRefundState(RefundEntity refundEntity, GatewayAccountEntity gatewayAccountEntity,
                                      RefundStatus refundStatus, Charge charge) {
        String fromState = (refundEntity.hasStatus()) ? refundEntity.getStatus().getValue() : "UNDEFINED";
        if (isRefundInTerminalState(fromState)) {
            logger.info("Skipping refund transition: refund [{}] cannot be set as [{}] because it is already in terminal state [{}].",
            refundEntity.getExternalId(), refundStatus, fromState);
        } else {
            logger.info("Changing refund status for externalId [{}] [{}]->[{}]",
                    refundEntity.getExternalId(), fromState, refundStatus.getValue(),
                    kv(PAYMENT_EXTERNAL_ID, refundEntity.getChargeExternalId()),
                    kv(REFUND_EXTERNAL_ID, refundEntity.getExternalId()),
                    kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                    kv(PROVIDER, charge.getPaymentGatewayName()),
                    kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                    kv("from_state", fromState),
                    kv("to_state", refundStatus.getValue()));

            refundEntity.setStatus(refundStatus);
            stateTransitionService.offerRefundStateTransition(refundEntity, refundStatus);
        }
    }

    private boolean isRefundInTerminalState(String fromState) {
        return REFUNDED.getValue().equals(fromState) || REFUND_ERROR.getValue().equals(fromState);
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
                    reloadedCharge.getPaymentGatewayName(),
                    gatewayAccount.getType(),
                    totalAmountToBeRefunded,
                    refundRequest.getAmount());

            throw RefundException.notAvailableForRefundException("Not sufficient amount available for refund", NOT_SUFFICIENT_AMOUNT_AVAILABLE);
        }
    }

    private void checkIfChargeIsRefundableOrTerminate(Charge reloadedCharge, ExternalChargeRefundAvailability refundAvailability, GatewayAccountEntity gatewayAccount) {
        if (refundAvailability == EXTERNAL_UNAVAILABLE && reloadedCharge.getDisputed() != null && reloadedCharge.getDisputed().equals(Boolean.TRUE)) {
            throw RefundException.unavailableDueToChargeDisputed();
        } else if (refundAvailability != EXTERNAL_AVAILABLE) {
            logger.warn("Charge not available for refund - charge_external_id={}, status={}, refund_status={}, account_id={}, operation_type=Refund, provider={}, provider_type={}",
                    reloadedCharge.getExternalId(),
                    reloadedCharge.getExternalStatus(),
                    refundAvailability,
                    gatewayAccount.getId(),
                    reloadedCharge.getPaymentGatewayName(),
                    gatewayAccount.getType());

            throw RefundException.notAvailableForRefundException(reloadedCharge.getExternalId(), refundAvailability);
        }
    }

    /**
     * <p>Worldpay -> Worldpay doesn't return reference. We use our externalId because that's what we sent in the
     * request as our reference and it will be sent by Worldpay with the notification. We should have already set
     * this when the refund was created (see {@link uk.gov.pay.connector.refund.service.WorldpayRefundEntityFactory)}).</p>
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
                .byName(PaymentGatewayName.valueFrom(charge.getPaymentGatewayName()))
                .getExternalChargeRefundAvailability(charge, refundList);
        checkIfChargeIsRefundableOrTerminate(charge, refundAvailability, gatewayAccountEntity);

        // We re-check the database for any newly created refunds that could have been made when we were making the
        // network request to find the external refund-ability
        List<Refund> updatedRefunds = checkForNewRefunds(charge, refundList);

        long availableToBeRefunded = getTotalAmountAvailableToBeRefunded(charge, updatedRefunds);
        checkIfRefundRequestIsInConflictOrTerminate(refundRequest, charge, availableToBeRefunded);

        checkIfRefundAmountWithinLimitOrTerminate(refundRequest, charge, refundAvailability,
                gatewayAccountEntity, availableToBeRefunded);

        return availableToBeRefunded;
    }

    private List<Refund> checkForNewRefunds(Charge charge, List<Refund> refundList) {
        List<RefundEntity> databaseRefunds = findNotExpungedRefunds(charge.getExternalId());

        // Add any new or updated refunds to our existing list. If a refund has been expunged since we last queried and 
        // so no longer exists in the database, continue using the existing record from the original refund list.
        Map<String, Refund> updatedRefunds = refundList.stream().collect(Collectors.toMap(Refund::getExternalId, refund -> refund));
        databaseRefunds.forEach(refundEntity -> updatedRefunds.put(refundEntity.getExternalId(), Refund.from(refundEntity)));
        return new ArrayList<>(updatedRefunds.values());
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
            Stream<Refund> refundsOnlyInLedger = getHistoricRefunds(charge).stream()
                    .filter(refund -> refundsFromDatabase
                            .stream()
                            .noneMatch(refund1 -> refund1.getExternalId().equals(refund.getExternalId()))
                    );

            return Stream.concat(refundsFromDatabase.stream(), refundsOnlyInLedger).collect(Collectors.toList());
        } else {
            return refundsFromDatabase;
        }

    }

    private List<Refund> getHistoricRefunds(Charge charge) {
        return ledgerService
                .getRefundsForPayment(charge.getGatewayAccountId(), charge.getExternalId())
                .getTransactions()
                .stream()
                .map(Refund::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateRefundParityStatus(String externalId, ParityCheckStatus parityCheckStatus) {
        refundDao.updateParityCheckStatus(externalId, ZonedDateTime.now(ZoneId.of("UTC")), parityCheckStatus);
    }

    public Optional<RefundEntity> findRefundByExternalId(String externalId) {
        return refundDao.findByExternalId(externalId);
    }
    
    // This is necessary in transactional methods to ensure the refund entity exists within the transaction persistence context
    // if called after a refund entity has been created, this should never cause an error
    private RefundEntity reloadRefundFromDatabase(Long refundId) {
       return refundDao.findById(refundId).orElseThrow(() -> new RuntimeException(format("Refund %s no longer exists in database", refundId)));
    }
}
