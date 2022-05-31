package uk.gov.pay.connector.agreement.service;

import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.events.model.charge.AgreementCreated;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;

import javax.inject.Inject;
import java.time.Clock;
import java.util.Optional;

import static uk.gov.pay.connector.agreement.model.AgreementEntity.AgreementEntityBuilder.anAgreementEntity;

public class AgreementService {

    private final GatewayAccountDao gatewayAccountDao;
    private final AgreementDao agreementDao;
    private final LedgerService ledgerService;
    private final Clock clock;

    @Inject
    public AgreementService(AgreementDao agreementDao, GatewayAccountDao gatewayAccountDao, LedgerService ledgerService, Clock clock) {
        this.agreementDao = agreementDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.ledgerService = ledgerService;
        this.clock = clock;
    }

    public Optional<AgreementResponse> findByExternalId(String externalId) {
        return agreementDao.findByExternalId(externalId)
                .map(agreementEntity -> new AgreementResponseBuilder()
                        .withAgreementId(agreementEntity.getExternalId())
                        .withServiceId(agreementEntity.getServiceId())
                        .withReference(agreementEntity.getReference())
                        .withLive(agreementEntity.isLive())
                        .withCreatedDate(agreementEntity.getCreatedDate())
                        .withDescription(agreementEntity.getDescription())
                        .withUserIdentifier(agreementEntity.getUserIdentifier())
                        .build());
    }

    @Transactional
    public Optional<AgreementResponse> create(AgreementCreateRequest agreementCreateRequest, long accountId) {
        return gatewayAccountDao.findById(accountId).map(gatewayAccountEntity -> {
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
            return agreementEntity;
        }).map(agreementEntity -> {
            var agreementResponseBuilder = new AgreementResponseBuilder();
            agreementResponseBuilder.withAgreementId(agreementEntity.getExternalId());
            agreementResponseBuilder.withCreatedDate(agreementEntity.getCreatedDate());
            agreementResponseBuilder.withReference(agreementEntity.getReference());
            agreementResponseBuilder.withServiceId(agreementEntity.getServiceId());
            agreementResponseBuilder.withLive(agreementEntity.isLive());
            agreementResponseBuilder.withDescription(agreementEntity.getDescription());
            agreementResponseBuilder.withUserIdentifier(agreementEntity.getUserIdentifier());
            return agreementResponseBuilder.build();
        });
    }
}
