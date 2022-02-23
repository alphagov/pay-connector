package uk.gov.pay.connector.agreement.service;

import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;

import javax.inject.Inject;
import java.time.Clock;
import java.util.Optional;

import static uk.gov.pay.connector.agreement.model.AgreementEntity.AgreementEntityBuilder.anAgreementEntity;

public class AgreementService {

    private final GatewayAccountDao gatewayAccountDao;
    private final AgreementDao agreementDao;
    private final Clock clock;

    @Inject
    public AgreementService(AgreementDao agreementDao, GatewayAccountDao gatewayAccountDao, Clock clock) {
        this.agreementDao = agreementDao;
        this.gatewayAccountDao = gatewayAccountDao;
        this.clock = clock;
    }

    public Optional<AgreementResponse> create(AgreementCreateRequest agreementCreateRequest, long accountId) {
        return gatewayAccountDao.findById(accountId).map(gatewayAccountEntity -> {
            AgreementEntity agreementEntity = anAgreementEntity(clock.instant())
                    .withReference(agreementCreateRequest.getReference())
                    .withServiceId(gatewayAccountEntity.getServiceId())
                    .withLive(gatewayAccountEntity.isLive()).build();
            agreementDao.persist(agreementEntity);
            return agreementEntity;
        }).map(agreementEntity -> {
            var agreementResponseBuilder = new AgreementResponseBuilder();
            agreementResponseBuilder.withAgreementId(agreementEntity.getExternalId());
            agreementResponseBuilder.withCreatedDate(agreementEntity.getCreatedDate());
            agreementResponseBuilder.withServiceId(agreementEntity.getServiceId());
            agreementResponseBuilder.withLive(agreementEntity.isLive());
            return agreementResponseBuilder.build();
        });
    }
}
