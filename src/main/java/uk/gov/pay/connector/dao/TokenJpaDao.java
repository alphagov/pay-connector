package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.TokenEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

@Transactional
public class TokenJpaDao extends JpaDao<TokenEntity> implements ITokenDao {

    @Inject
    public TokenJpaDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    @Override
    public void insertNewToken(String chargeId, String tokenId) {
        super.persist(new TokenEntity(Long.valueOf(chargeId), tokenId));
    }

    @Override
    public String findByChargeId(String chargeId) {

        String token = null;

        TypedQuery<TokenEntity> query = entityManager.get().createQuery("SELECT t FROM TokenEntity t WHERE t.chargeId = :chargeId", TokenEntity.class);
        query.setParameter("chargeId", Long.valueOf(chargeId));

        List<TokenEntity> result = query.getResultList();

        if (!result.isEmpty()) {
            token = result.get(0).getToken();
        }
        return token;
    }

    @Override
    public Optional<String> findChargeByTokenId(String tokenId) {

        String chargeId = null;

        TypedQuery<TokenEntity> query = entityManager.get().createQuery("SELECT t FROM TokenEntity t WHERE t.token = :token", TokenEntity.class);
        query.setParameter("token", tokenId);

        List<TokenEntity> result = query.getResultList();

        if (!result.isEmpty()) {
            chargeId = String.valueOf(result.get(0).getChargeId());
        }

        return Optional.ofNullable(chargeId);
    }

    @Override
    public void deleteByTokenId(String tokenId) {

        int rowsDeleted = entityManager.get().createQuery("DELETE FROM TokenEntity t WHERE t.token = :token")
                .setParameter("token", tokenId)
                .executeUpdate();

        if (rowsDeleted != 1) {
            throw new PayDBIException(format("Unexpected: Failed to delete chargeTokenId = '%s' from tokens table", tokenId));
        }
    }
}
