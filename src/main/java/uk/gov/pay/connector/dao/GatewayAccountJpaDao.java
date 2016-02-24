package uk.gov.pay.connector.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GatewayAccountJpaDao extends JpaDao<GatewayAccountEntity> implements IGatewayAccountDao {

    @Inject
    public GatewayAccountJpaDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    @Override
    @Transactional
    public String createGatewayAccount(String paymentProvider) {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity(paymentProvider, new HashMap<>());
        super.persist(gatewayAccount);
        entityManager.get().flush();
        return gatewayAccount.getId().toString();
    }

    @Override
    public boolean idIsMissing(String gatewayAccountId) {
        return !super.findById(GatewayAccountEntity.class, Long.valueOf(gatewayAccountId)).isPresent();
    }

    @Override
    public Optional<GatewayAccount> findById(String gatewayAccountId) {
        return super.findById(GatewayAccountEntity.class, Long.valueOf(gatewayAccountId)).map(gatewayAccountEntity -> {
            GatewayAccount gatewayAccount = null;
            if (gatewayAccountEntity != null) {
                gatewayAccount = new GatewayAccount(gatewayAccountEntity.getId(), gatewayAccountEntity.getGatewayName(), gatewayAccountEntity.getCredentials());
            }
            return gatewayAccount;
        });
    }

    @Override
    public Optional<GatewayAccountEntity> findById(Long gatewayAccountId) {
        return super.findById(GatewayAccountEntity.class, gatewayAccountId);
    }

    @Override
    @Transactional
    public void saveCredentials(String credentialsJsonString, String gatewayAccountId) {
        findById(GatewayAccountEntity.class, Long.valueOf(gatewayAccountId)).ifPresent(
                entity -> {
                    try {
                        Map<String, String> credentials = new ObjectMapper().readValue(credentialsJsonString, HashMap.class);
                        entity.setCredentials(credentials);
                    } catch (IOException e) {
                        Throwables.propagate(e);
                    }
                }
        );
    }
}
