package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.ChargeCardDetailsEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;

@Transactional
public class ChargeCardDetailsDao extends JpaDao<ChargeCardDetailsEntity> {

    @Inject
    public ChargeCardDetailsDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<ChargeCardDetailsEntity> findById(long id) {
        return super.findById(ChargeCardDetailsEntity.class, id);
    }

    public Optional<ChargeCardDetailsEntity> findByChargeId(Long chargeId) {

        String query = "SELECT c FROM ChargeCardDetailsEntity c WHERE c.chargeEntity.id = :chargeId";

        return entityManager.get()
                .createQuery(query, ChargeCardDetailsEntity.class)
                .setParameter("chargeId", chargeId)
                .getResultList().stream().findFirst();
    }
}
