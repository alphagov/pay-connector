package uk.gov.pay.connector.agreement.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.exception.AgreementNotFoundException;
import uk.gov.pay.connector.agreement.exception.RecurringCardPaymentsNotAllowedException;
import uk.gov.pay.connector.agreement.model.AgreementCancelRequest;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.pay.connector.charge.exception.AgreementNotFoundRuntimeException;
import uk.gov.pay.connector.charge.exception.PaymentInstrumentNotActiveException;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.agreement.AgreementCancelledByService;
import uk.gov.pay.connector.events.model.agreement.AgreementCancelledByUser;
import uk.gov.pay.connector.events.model.agreement.AgreementCreated;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static uk.gov.pay.connector.agreement.model.AgreementEntity.AgreementEntityBuilder.anAgreementEntity;

public class AgreementService {

    private final GatewayAccountDao gatewayAccountDao;
    private final AgreementDao agreementDao;
    private final LedgerService ledgerService;
    private final Clock clock;
    private final TaskQueueService taskQueueService;

    @Inject
    public AgreementService(AgreementDao agreementDao, GatewayAccountDao gatewayAccountDao, LedgerService ledgerService, Clock clock, TaskQueueService taskQueueService) {
        this.agreementDao = agreementDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.ledgerService = ledgerService;
        this.clock = clock;
        this.taskQueueService = taskQueueService;
    }

    public Optional<AgreementResponse> findByExternalId(String externalId, long gatewayAccountId) {
        return agreementDao.findByExternalId(externalId, gatewayAccountId)
                .map(AgreementResponse::from);
    }

    public AgreementEntity findByExternalId(String externalId) {
        return agreementDao.findByExternalId(externalId).orElseThrow(() -> new AgreementNotFoundRuntimeException(externalId));
    }

    @Transactional
    public Optional<AgreementResponse> createByGatewayAccountId(AgreementCreateRequest agreementCreateRequest, long accountId) {
        return gatewayAccountDao.findById(accountId)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(String.format("Gateway account {} not found", accountId));
                }).map(gatewayAccountEntity -> create(agreementCreateRequest, gatewayAccountEntity));
    }
    
    @Transactional
    public Optional<AgreementResponse> createByServiceIdAndAccountType(AgreementCreateRequest agreementCreateRequest, String serviceId, GatewayAccountType accountType) {
        return gatewayAccountDao.findByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(String.format("Gateway account not found for service ID [%s] and account type [%s]", serviceId, accountType));
                })
                .map(gatewayAccountEntity -> create(agreementCreateRequest, gatewayAccountEntity));
    }

    @Transactional
    private AgreementResponse create(AgreementCreateRequest agreementCreateRequest, GatewayAccountEntity gatewayAccountEntity) {
        if(!gatewayAccountEntity.isRecurringEnabled()) {
            throw new RecurringCardPaymentsNotAllowedException(
                    "Attempt to create an agreement for gateway account " +
                            gatewayAccountEntity.getId() +
                            ", which does not have recurring card payments enabled");
        }
        AgreementEntity agreementEntity = anAgreementEntity(clock.instant())
                .withReference(agreementCreateRequest.getReference())
                .withDescription(agreementCreateRequest.getDescription())
                .withUserIdentifier(agreementCreateRequest.getUserIdentifier())
                .withServiceId(gatewayAccountEntity.getServiceId())
                .withLive(gatewayAccountEntity.isLive())
                .build();
        agreementEntity.setGatewayAccount(gatewayAccountEntity);

        agreementDao.persist(agreementEntity);
        ledgerService.postEvent(AgreementCreated.from(agreementEntity));

        return new AgreementResponseBuilder()
                .withAgreementId(agreementEntity.getExternalId())
                .withCreatedDate(agreementEntity.getCreatedDate())
                .withReference(agreementEntity.getReference())
                .withServiceId(agreementEntity.getServiceId())
                .withLive(agreementEntity.isLive())
                .withDescription(agreementEntity.getDescription())
                .withUserIdentifier(agreementEntity.getUserIdentifier())
                .build();
    }

    @Transactional
    public void cancel(String agreementExternalId, long gatewayAccountId, AgreementCancelRequest agreementCancelRequest) {
        var agreement = agreementDao
                .findByExternalId(agreementExternalId, gatewayAccountId)
                .orElseThrow(() -> new AgreementNotFoundException("Agreement with ID [" + agreementExternalId + "] not found."));

        agreement.getPaymentInstrument()
                .filter(paymentInstrument -> paymentInstrument.getStatus() == PaymentInstrumentStatus.ACTIVE)
                .ifPresentOrElse(paymentInstrument -> {
                    Instant now = clock.instant();
                    paymentInstrument.setStatus(PaymentInstrumentStatus.CANCELLED);
                    agreement.setCancelledDate(now);
                    taskQueueService.addDeleteStoredPaymentDetailsTask(agreement, paymentInstrument);
                    if (agreementCancelRequest != null && agreementCancelRequest.getUserEmail() != null && agreementCancelRequest.getUserExternalId() != null) {
                        ledgerService.postEvent(AgreementCancelledByUser.from(agreement, agreementCancelRequest, now));
                    } else {
                        ledgerService.postEvent(AgreementCancelledByService.from(agreement, now));
                    }
                }, () -> {
                    throw new PaymentInstrumentNotActiveException("Payment instrument not active.");
                });
    }
}
