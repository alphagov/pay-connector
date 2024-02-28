package uk.gov.pay.connector.card.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.card.model.Auth3dsRequiredEntity;
import uk.gov.pay.connector.card.model.OperationType;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.card.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.card.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.common.exception.OperationAlreadyInProgressRuntimeException;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.agreement.AgreementInactivated;
import uk.gov.pay.connector.events.model.charge.Gateway3dsInfoObtained;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsSubmittedByAPI;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsTakenFromPaymentInstrument;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.service.payments.commons.model.AuthorisationMode.AGREEMENT;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

public class CardService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardService.class);
    
    private final EventService eventService;
    private final ChargeService chargeService;
    private final ChargeDao chargeDao;
    private final LedgerService ledgerService;
    private final PaymentInstrumentService paymentInstrumentService;
    private final TaskQueueService taskQueueService;

    private final AuthCardDetailsToCardDetailsEntityConverter authCardDetailsToCardDetailsEntityConverter;

    @Inject
    public CardService(EventService eventService, ChargeService chargeService, ChargeDao chargeDao, LedgerService ledgerService, PaymentInstrumentService paymentInstrumentService, TaskQueueService taskQueueService, AuthCardDetailsToCardDetailsEntityConverter authCardDetailsToCardDetailsEntityConverter) {
        this.eventService = eventService;
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
        this.ledgerService = ledgerService;
        this.paymentInstrumentService = paymentInstrumentService;
        this.taskQueueService = taskQueueService;
        this.authCardDetailsToCardDetailsEntityConverter = authCardDetailsToCardDetailsEntityConverter;
    }

    public ChargeEntity updateChargePostCardAuthorisation(String chargeExternalId,
                                                          ChargeStatus newStatus,
                                                          String transactionId,
                                                          Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                          ProviderSessionIdentifier sessionIdentifier,
                                                          AuthCardDetails authCardDetails,
                                                          Map<String, String> recurringAuthToken,
                                                          Boolean canRetry,
                                                          String rejectedReason) {
        return updateChargeAndEmitEventPostAuthorisation(chargeExternalId, newStatus, authCardDetails, transactionId, auth3dsRequiredDetails, sessionIdentifier,
                null, null, recurringAuthToken, canRetry, rejectedReason);

    }

    public ChargeEntity updateChargePostWalletAuthorisation(String chargeExternalId,
                                                            ChargeStatus status,
                                                            String transactionId,
                                                            ProviderSessionIdentifier sessionIdentifier,
                                                            AuthCardDetails authCardDetails,
                                                            WalletType walletType,
                                                            String emailAddress,
                                                            Optional<Auth3dsRequiredEntity> auth3dsRequiredDetails) {
        return updateChargeAndEmitEventPostAuthorisation(chargeExternalId, status, authCardDetails, transactionId, auth3dsRequiredDetails.orElse(null), sessionIdentifier,
                walletType, emailAddress, null, null, null);
    }

    private ChargeEntity updateChargeAndEmitEventPostAuthorisation(String chargeExternalId,
                                                                   ChargeStatus newStatus,
                                                                   AuthCardDetails authCardDetails,
                                                                   String transactionId,
                                                                   Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                                   ProviderSessionIdentifier sessionIdentifier,
                                                                   WalletType walletType,
                                                                   String emailAddress,
                                                                   Map<String, String> recurringAuthToken,
                                                                   Boolean canRetry,
                                                                   String rejectedReason) {
        updateChargePostAuthorisation(chargeExternalId, newStatus, authCardDetails, transactionId,
                auth3dsRequiredDetails, sessionIdentifier, walletType, emailAddress, recurringAuthToken, canRetry, rejectedReason);
        ChargeEntity chargeEntity = chargeService.findChargeByExternalId(chargeExternalId);
        if (chargeEntity.getAuthorisationMode() == MOTO_API) {
            eventService.emitAndRecordEvent(PaymentDetailsSubmittedByAPI.from(chargeEntity));
        } else if (chargeEntity.getAuthorisationMode() == AGREEMENT) {
            eventService.emitAndRecordEvent(PaymentDetailsTakenFromPaymentInstrument.from(chargeEntity));
        } else {
            eventService.emitAndRecordEvent(PaymentDetailsEntered.from(chargeEntity));
        }

        return chargeEntity;
    }

    // cannot be private: Guice requires @Transactional methods to be public
    @Transactional
    public ChargeEntity updateChargePostAuthorisation(String chargeExternalId,
                                                      ChargeStatus newStatus,
                                                      AuthCardDetails authCardDetails,
                                                      String transactionId,
                                                      Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                      ProviderSessionIdentifier sessionIdentifier,
                                                      WalletType walletType,
                                                      String emailAddress,
                                                      Map<String, String> recurringAuthToken,
                                                      Boolean canRetry,
                                                      String rejectedReason) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            setTransactionId(charge, transactionId);
            Optional.ofNullable(sessionIdentifier).map(ProviderSessionIdentifier::toString).ifPresent(identifier -> charge.getChargeCardDetails().setProviderSessionId(identifier));
            Optional.ofNullable(auth3dsRequiredDetails).ifPresent(threeDsDetails -> charge.getChargeCardDetails().set3dsRequiredDetails(threeDsDetails));
            Optional.ofNullable(walletType).ifPresent(charge::setWalletType);
            Optional.ofNullable(emailAddress).ifPresent(charge::setEmail);

            CardDetailsEntity detailsEntity = authCardDetailsToCardDetailsEntityConverter.convert(authCardDetails);

            // propagate details that aren't mapped from payment instrument to auth card details onto the charge
            // this logic should be removable when payment instruments are modelled and used for all authorisation types
            if (charge.getAuthorisationMode() == AuthorisationMode.AGREEMENT) {
                charge.getPaymentInstrument()
                        .ifPresent(paymentInstrument -> {
                            detailsEntity.setFirstDigitsCardNumber(paymentInstrument.getCardDetails().getFirstDigitsCardNumber());
                            detailsEntity.setLastDigitsCardNumber(paymentInstrument.getCardDetails().getLastDigitsCardNumber());
                        });

                charge.setCanRetry(canRetry);

                if (canRetry != null && !canRetry) {
                    inactivateAgreement(charge, rejectedReason);
                }
            }
            charge.getChargeCardDetails().setCardDetails(detailsEntity);

            if (charge.isSavePaymentInstrumentToAgreement()) {
                Optional.ofNullable(recurringAuthToken).ifPresent(token -> setPaymentInstrument(token, charge));
            }

            chargeService.transitionChargeState(charge, newStatus);

            LOGGER.info("Stored confirmation details for charge - charge_external_id={}",
                    chargeExternalId);

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    @Transactional
    public ChargeEntity updateChargePost3dsAuthorisation(String chargeExternalId, ChargeStatus status,
                                                         OperationType operationType,
                                                         String transactionId,
                                                         Auth3dsRequiredEntity auth3dsRequiredDetails,
                                                         ProviderSessionIdentifier sessionIdentifier,
                                                         Map<String, String> recurringAuthToken) {
        return chargeDao.findByExternalId(chargeExternalId).map(charge -> {
            try {
                setTransactionId(charge, transactionId);
                chargeService.transitionChargeState(charge, status);
                Optional.ofNullable(auth3dsRequiredDetails).ifPresent(threeDsDetails -> charge.getChargeCardDetails().set3dsRequiredDetails(threeDsDetails));
                Optional.ofNullable(sessionIdentifier).map(ProviderSessionIdentifier::toString).ifPresent(identifier -> charge.getChargeCardDetails().setProviderSessionId(identifier));
                if (charge.isSavePaymentInstrumentToAgreement()) {
                    Optional.ofNullable(recurringAuthToken).ifPresent(token -> setPaymentInstrument(token, charge));
                }
            } catch (InvalidStateTransitionException e) {
                if (chargeService.chargeIsInLockedStatus(operationType, charge)) {
                    throw new OperationAlreadyInProgressRuntimeException(operationType.getValue(), charge.getExternalId());
                }
                throw new IllegalStateRuntimeException(charge.getExternalId());
            }

            if (auth3dsRequiredDetails != null && isNotBlank(auth3dsRequiredDetails.getThreeDsVersion())) {
                eventService.emitAndRecordEvent(Gateway3dsInfoObtained.from(charge, Instant.now()));
            }

            return charge;
        }).orElseThrow(() -> new ChargeNotFoundRuntimeException(chargeExternalId));
    }

    public ChargeEntity updateChargePostCapture(ChargeEntity chargeEntity, ChargeStatus nextStatus) {
        if (nextStatus == CAPTURED) {
            chargeService.transitionChargeState(chargeEntity, CAPTURE_SUBMITTED);
            chargeService.transitionChargeState(chargeEntity, CAPTURED);
        } else {
            chargeService.transitionChargeState(chargeEntity, nextStatus);
        }
        return chargeEntity;
    }

    private void setTransactionId(ChargeEntity chargeEntity, String transactionId) {
        if (transactionId != null && !transactionId.isBlank()) {
            chargeEntity.setGatewayTransactionId(transactionId);
        }
    }

    private void inactivateAgreement(ChargeEntity charge, String rejectedReason) {
        charge.getAgreement().ifPresent(agreementEntity -> {
            AgreementInactivated inactivatedEvent = AgreementInactivated
                    .from(agreementEntity, rejectedReason, Instant.now());
            ledgerService.postEvent(inactivatedEvent);
        });
        charge.getPaymentInstrument().ifPresent(paymentInstrumentEntity ->
                paymentInstrumentEntity.setStatus(PaymentInstrumentStatus.INACTIVE)
        );
    }

    private void setPaymentInstrument(Map<String, String> recurringAuthToken, ChargeEntity charge) {
        var paymentInstrument = paymentInstrumentService.createPaymentInstrument(charge, recurringAuthToken);
        charge.setPaymentInstrument(paymentInstrument);
    }
}
