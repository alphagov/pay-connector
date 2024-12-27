package uk.gov.pay.connector.cardtype.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.common.dao.JpaDao;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional
public class CardTypeDao extends JpaDao<CardTypeEntity> {

    @Inject
    public CardTypeDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<CardTypeEntity> findById(UUID id) {
        return super.findById(CardTypeEntity.class, id);
    }

    public List<CardTypeEntity> findAll() {
        String query = "SELECT ct FROM CardTypeEntity ct";

        return super.entityManager.get()
                .createQuery(query, CardTypeEntity.class)
                .getResultList();
    }

    public List<CardTypeEntity> findByBrand(String brand) {
        String query = "SELECT ct FROM CardTypeEntity ct " +
                "WHERE ct.brand = :brand ";

        return entityManager.get()
                .createQuery(query, CardTypeEntity.class)
                .setParameter("brand", brand)
                .getResultList();
    }

    public List<CardTypeEntity> findAllNon3ds() {
        String query = "SELECT ct FROM CardTypeEntity ct " +
                "WHERE ct.requires3ds = false ";

        return entityManager.get()
                .createQuery(query, CardTypeEntity.class)
                .getResultList();
    }
}
