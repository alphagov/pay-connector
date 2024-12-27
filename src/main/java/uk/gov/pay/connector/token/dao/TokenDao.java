package uk.gov.pay.connector.token.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import jakarta.persistence.EntityManager;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.token.model.domain.TokenEntity;

import jakarta.inject.Inject;
import java.time.ZonedDateTime;
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

    public int deleteTokensOlderThanSpecifiedDate(ZonedDateTime tokenExpiryDate) {
        return entityManager.get()
                .createQuery("DELETE FROM TokenEntity t WHERE t.createdDate < :tokenExpiryDate", TokenEntity.class)
                .setParameter("tokenExpiryDate", tokenExpiryDate)
                .executeUpdate();
    }
}
