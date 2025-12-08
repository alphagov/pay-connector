package uk.gov.pay.connector.agreement.dao;

import com.google.inject.Provider;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import java.util.Optional;

public class AgreementDao extends JpaDao<AgreementEntity> {

    @Inject
    public AgreementDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<AgreementEntity> findById(Long agreementId) {
        return super.findById(AgreementEntity.class, agreementId);
    }

    public Optional<AgreementEntity> findByExternalIdAndGatewayAccountId(String externalId, long gatewayAccountId) {

        String query = "SELECT ae FROM AgreementEntity ae " +
                "WHERE ae.externalId = :externalId " +
                "AND ae.gatewayAccount.id = :gatewayAccountId";

        return entityManager.get()
                .createQuery(query, AgreementEntity.class)
                .setParameter("externalId", externalId)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .getResultList().stream().findFirst();
    }

    public Optional<AgreementEntity> findByExternalIdAndServiceIdAndAccountType(String externalId, String serviceId, GatewayAccountType accountType) {
        boolean live = accountType == GatewayAccountType.LIVE;
        String query = "SELECT ae FROM AgreementEntity ae " +
                "WHERE ae.externalId = :externalId " +
                "AND ae.serviceId = :serviceId " +
                "AND ae.live = :live";

        return entityManager.get()
                .createQuery(query, AgreementEntity.class)
                .setParameter("externalId", externalId)
                .setParameter("serviceId", serviceId)
                .setParameter("live", live)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<AgreementEntity> findByExternalId(String externalId) {

        String query = "SELECT ae FROM AgreementEntity ae " +
                "WHERE ae.externalId = :externalId";

        return entityManager.get()
                .createQuery(query, AgreementEntity.class)
                .setParameter("externalId", externalId)
                .getResultList().stream().findFirst();
    }
}
