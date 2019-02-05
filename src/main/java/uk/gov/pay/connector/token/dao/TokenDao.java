package uk.gov.pay.connector.token.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;

@Transactional
public class TokenDao extends JpaDao<TokenEntity> {

    @Inject
    public TokenDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<TokenEntity> findByTokenId(String tokenId) {
        return entityManager.get()
                .createQuery("SELECT t FROM TokenEntity t WHERE t.token = :token", TokenEntity.class)
                .setParameter("token", tokenId)
                .getResultList().stream()
                .findFirst();
    }

    public Optional<TokenEntity> findByChargeId(Long chargeId) {
        return entityManager.get()
                .createQuery("SELECT t FROM TokenEntity t WHERE t.chargeEntity.id = :chargeId", TokenEntity.class)
                .setParameter("chargeId", chargeId)
                .getResultList().stream()
                .findFirst();
    }
}
