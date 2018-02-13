package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.Transaction;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;

@Transactional
public class ExperimentalSearchDao extends JpaDao<Transaction> {

    @Inject
    protected ExperimentalSearchDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public long getTotalFor(ChargeSearchParams params) {
        final String query = "SELECT COUNT(*) " +
                "FROM transactions t " +
                "WHERE t.gateway_account_id = ?gatewayAccountId ";

        Query typedQuery = entityManager.get().createNativeQuery(query);
        typedQuery.setParameter("gatewayAccountId", params.getGatewayAccountId());
        return (Long) typedQuery.getSingleResult();
    }

    public List<Transaction> search(ChargeSearchParams params) {
        StringBuilder query = new StringBuilder(
                "SELECT t.created_date AS date_created, " +
                        "t.operation AS transaction_type, " +
                        "t.status AS status, " +
                        "t2.email AS email, " +
                        "t2.gateway_transaction_id AS gateway_transaction_id, " +
                        "c.card_brand AS card_brand, " +
                        "'CHANGE_ME' AS card_brand_label, " +
                        "c.cardholder_name AS cardholder_name, " +
                        "c.expiry_date AS expiry_date, " +
                        "c.last_digits_card_number AS last_digits_card_number, " +
                        "c.address_line1 AS address_line1, " +
                        "c.address_line2 AS address_line2, " +
                        "c.address_postcode AS address_postcode, " +
                        "c.address_city AS address_city, " +
                        "c.address_county AS address_county, " +
                        "c.address_country AS address_country, " +
                        "p.external_id AS external_id, " +
                        "p.amount AS amount, " +
                        "p.reference AS reference, " +
                        "p.return_url AS return_url , " +
                        "p.description AS description, " +
                        "p.gateway_account_id AS gateway_account_id," +
                        "0 AS charge_id, " +
                        "g.payment_provider AS payment_provider " +
                        "FROM transactions t JOIN transactions t2 ON t.payment_request_id = t2.payment_request_id AND t2.operation = 'CHARGE' " +
                        "JOIN payment_requests p ON t.payment_request_id = p.id " +
                        "JOIN gateway_accounts g ON t.gateway_account_id = g.id " +
                        "LEFT JOIN cards c ON t2.id = c.transaction_id " +
                        "WHERE t.gateway_account_id = ?gatewayAccountId " +
                        "ORDER BY t.created_date DESC");

        Query typedQuery = entityManager.get().createNativeQuery(query.toString(), Transaction.class);
        typedQuery.setParameter("gatewayAccountId", params.getGatewayAccountId());
        if (params.getPage() != null && params.getDisplaySize() != null) {
            final long displaySize = params.getDisplaySize().intValue();
            long offset = (params.getPage() - 1) * displaySize;
            typedQuery = typedQuery.setFirstResult((int) offset).setMaxResults((int) displaySize);
        }

        return (List<Transaction>) typedQuery.getResultList();
    }
}
