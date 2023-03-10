package uk.gov.pay.connector.agreement.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.common.dao.JpaDao;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;

public class AgreementDao extends JpaDao<AgreementEntity> {

    @Inject
    public AgreementDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<AgreementEntity> findById(Long agreementId) {
        return super.findById(AgreementEntity.class, agreementId);
    }

    public Optional<AgreementEntity> findByExternalId(String externalId, long gatewayAccountId) {

        String query = "SELECT ae FROM AgreementEntity ae " +
                "WHERE ae.externalId = :externalId " +
                "AND ae.gatewayAccount.id = :gatewayAccountId";

        return entityManager.get()
                .createQuery(query, AgreementEntity.class)
                .setParameter("externalId", externalId)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .getResultList().stream().findFirst();
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
