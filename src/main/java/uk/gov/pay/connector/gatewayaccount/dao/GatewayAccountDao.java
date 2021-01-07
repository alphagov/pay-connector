package uk.gov.pay.connector.gatewayaccount.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

@Transactional
public class GatewayAccountDao extends JpaDao<GatewayAccountEntity> {

    @Inject
    public GatewayAccountDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<GatewayAccountEntity> findById(Long gatewayAccountId) {
        return super.findById(GatewayAccountEntity.class, gatewayAccountId);
    }

    public Optional<GatewayAccountEntity> findByNotificationCredentialsUsername(String username) {
        String query = "SELECT gae FROM GatewayAccountEntity gae " +
                "WHERE gae.notificationCredentials.userName = :username";

        return entityManager.get()
                .createQuery(query, GatewayAccountEntity.class)
                .setParameter("username", username)
                .getResultList().stream().findFirst();
    }

    public Optional<GatewayAccountEntity> findByCredentialsKeyValue(String key, String value) {
        String query = "SELECT * FROM gateway_accounts where credentials->>?1 = ?2";

        return entityManager.get()
                .createNativeQuery(query, GatewayAccountEntity.class)
                .setParameter(1, key)
                .setParameter(2, value)
                .getResultList().stream().findFirst();
    }

    public boolean isATelephonePaymentNotificationAccount(String merchantId) {
        String query = "SELECT count(g) FROM gateway_accounts g where g.credentials->>?1 = ?2 " +
                " and allow_telephone_payment_notifications is true";

        var count = (Number) entityManager.get()
                .createNativeQuery(query)
                .setParameter(1, CREDENTIALS_MERCHANT_ID)
                .setParameter(2, merchantId)
                .getSingleResult();

        return count.intValue() > 0;
    }

    public List<GatewayAccountEntity> search(GatewayAccountSearchParams params) {
        List<String> filterTemplates = params.getFilterTemplates();
        String whereClause = filterTemplates.isEmpty() ?
                "" :
                " WHERE " + String.join(" AND ", filterTemplates);

        String queryTemplate = "SELECT gae" +
                " FROM GatewayAccountEntity gae" +
                whereClause +
                " ORDER BY gae.id";

        var query = entityManager
                .get()
                .createQuery(queryTemplate, GatewayAccountEntity.class);

        params.getQueryMap().forEach(query::setParameter);

        return query.getResultList();
    }

    public Optional<GatewayAccountEntity> findByExternalId(String externalId) {
        String query = "SELECT g FROM GatewayAccountEntity g where g.externalId = :externalId";

        return entityManager.get()
                .createQuery(query, GatewayAccountEntity.class)
                .setParameter("externalId", externalId)
                .getResultList().stream().findFirst();
    }
}
