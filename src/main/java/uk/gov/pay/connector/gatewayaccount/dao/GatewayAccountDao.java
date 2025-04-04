package uk.gov.pay.connector.gatewayaccount.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
    
    public boolean isATelephonePaymentNotificationAccount(String merchantId) {
        String query = "SELECT count(g) FROM gateway_accounts g, gateway_account_credentials gac " +
                " where g.id = gac.gateway_account_id " +
                " AND gac.credentials->>?1 = ?2 " +
                " and g.allow_telephone_payment_notifications is true";

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

        String queryTemplate = "SELECT ga.*" +
                " FROM gateway_accounts ga" +
                whereClause +
                " ORDER BY ga.id";

        var query = entityManager
                .get()
                .createNativeQuery(queryTemplate, GatewayAccountEntity.class);

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

    public List<GatewayAccountEntity> findByServiceIdAndAccountType(String serviceId, GatewayAccountType accountType) {
        String query = "SELECT g FROM GatewayAccountEntity g where g.serviceId = :serviceId and g.type = :accountType order by g.id DESC";

        return entityManager.get()
                .createQuery(query, GatewayAccountEntity.class)
                .setParameter("serviceId", serviceId)
                .setParameter("accountType", accountType)
                .getResultList();
    }

    public List<GatewayAccountEntity> findByServiceId(String serviceId) {
        String query = "SELECT g FROM GatewayAccountEntity g where g.serviceId = :serviceId";

        return entityManager.get()
                .createQuery(query, GatewayAccountEntity.class)
                .setParameter("serviceId", serviceId)
                .getResultList();
    }
}
