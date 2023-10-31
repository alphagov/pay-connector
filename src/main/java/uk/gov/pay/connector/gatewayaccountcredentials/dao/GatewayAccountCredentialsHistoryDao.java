package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;

@Transactional
public class GatewayAccountCredentialsHistoryDao {

    protected final Provider<EntityManager> entityManager;

    @Inject
    public GatewayAccountCredentialsHistoryDao(Provider<EntityManager> entityManager) {
        this.entityManager = entityManager;
    }

    public int delete(Long gatewayAccountId) {
        String query = "DELETE FROM gateway_account_credentials_history WHERE gateway_account_id = ?1";
        
        return entityManager.get().createNativeQuery(query)
                .setParameter(1, gatewayAccountId)
                .executeUpdate();
    }
}
