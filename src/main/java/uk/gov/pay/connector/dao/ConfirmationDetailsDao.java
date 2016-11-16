package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.ConfirmationDetailsEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;

@Transactional
public class ConfirmationDetailsDao extends JpaDao<ConfirmationDetailsEntity> {

    @Inject
    public ConfirmationDetailsDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<ConfirmationDetailsEntity> findById(long id) {
        return super.findById(ConfirmationDetailsEntity.class, id);
    }

    public Optional<ConfirmationDetailsEntity> findByChargeId(Long chargeId) {

        String query = "SELECT c FROM ConfirmationDetailsEntity c WHERE c.chargeEntity.id = :chargeId";

        return entityManager.get()
                .createQuery(query, ConfirmationDetailsEntity.class)
                .setParameter("chargeId", chargeId)
                .getResultList().stream().findFirst();
    }
}
