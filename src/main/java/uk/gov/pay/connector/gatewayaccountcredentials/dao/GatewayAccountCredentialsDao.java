package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;

@Transactional
public class GatewayAccountCredentialsDao extends JpaDao<GatewayAccountCredentialsEntity> {

    @Inject
    public GatewayAccountCredentialsDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    @Override
    public void persist(GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity) {
        super.persist(gatewayAccountCredentialsEntity);
    }
}
