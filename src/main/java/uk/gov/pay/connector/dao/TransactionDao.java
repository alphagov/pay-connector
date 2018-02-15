package uk.gov.pay.connector.dao;

import com.google.common.collect.Streams;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.TransactionType;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.Transaction;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.model.domain.transaction.TransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Transactional
public class TransactionDao extends JpaDao<TransactionEntity> {

    private final UTCDateTimeConverter utcDateTimeConverter;

    @Inject
    protected TransactionDao(Provider<EntityManager> entityManager, UTCDateTimeConverter utcDateTimeConverter) {
        super(entityManager);
        this.utcDateTimeConverter = utcDateTimeConverter;
    }

    public List<Transaction> search(ChargeSearchParams params) {
        String queryStart =
                "SELECT " +
                        "t.operation AS transaction_type, " +
                        "t.id AS charge_id, " +
                        "p.external_id AS external_id, " +
                        "p.reference AS reference, " +
                        "p.description AS description, " +
                        "t.status AS status, " +
                        "t2.email AS email, " +
                        "p.gateway_account_id AS gateway_account_id," +
                        "t2.gateway_transaction_id AS gateway_transaction_id, " +
                        "t.created_date AS date_created, " +
                        "c.card_brand AS card_brand, " +
                        "'CHANGE_ME' AS card_brand_label, " +
                        "c.cardholder_name AS cardholder_name, " +
                        "c.expiry_date AS expiry_date, " +
                        "c.last_digits_card_number AS last_digits_card_number, " +
                        "c.address_city AS address_city, " +
                        "c.address_country AS address_country, " +
                        "c.address_county AS address_county, " +
                        "c.address_line1 AS address_line1, " +
                        "c.address_line2 AS address_line2, " +
                        "c.address_postcode AS address_postcode, " +
                        "p.amount AS amount " +
                        "FROM transactions t JOIN transactions t2 ON t.payment_request_id = t2.payment_request_id AND t2.operation = 'CHARGE' " +
                        "JOIN payment_requests p ON t.payment_request_id = p.id " +
                        "JOIN gateway_accounts g ON t.gateway_account_id = g.id " +
                        "LEFT JOIN cards c ON t2.id = c.transaction_id ";

        List<String> statuses = getStatuses(params);
        StringBuilder queryBuilder = buildQueryString(queryStart, params, statuses);

        String query = queryBuilder.append("ORDER BY t.created_date DESC LIMIT ?limit OFFSET ?offset").toString();

        Query typedQuery = entityManager.get().createNativeQuery(query, "TransactionMapping");
        typedQuery.setParameter("gatewayAccountId", params.getGatewayAccountId());
        typedQuery = setParams(typedQuery, params, statuses);

        setPagination(params, typedQuery);

        return (List<Transaction>) typedQuery.getResultList();
    }

    private void setPagination(ChargeSearchParams params, Query typedQuery) {
        final long displaySize = params.getDisplaySize().intValue();
        long offset = (params.getPage() - 1) * displaySize;
        typedQuery.setParameter("offset", offset);
        typedQuery.setParameter("limit", displaySize);
    }

    public long getTotal(ChargeSearchParams params) {
        List<String> statuses = getStatuses(params);
        StringBuilder queryPrefix = new StringBuilder("SELECT count(t.*) FROM transactions t ");

        if (isNotEmpty(params.getReference())) {
            queryPrefix.append("JOIN payment_requests p ON t.payment_request_id = p.id ");
        }
        if (isNotEmpty(params.getEmail()) || !params.getCardBrands().isEmpty()) {
            queryPrefix.append("JOIN transactions t2 ON t.payment_request_id = t2.payment_request_id AND t2.operation = 'CHARGE' ");
        }
        if (!params.getCardBrands().isEmpty()) {
            queryPrefix.append("LEFT JOIN cards c ON t2.id = c.transaction_id ");
        }
        StringBuilder query = buildQueryString(queryPrefix.toString(), params, statuses);

        Query typedQuery = entityManager.get().createNativeQuery(query.toString());
        typedQuery = setParams(typedQuery, params, statuses);

        return (Long) typedQuery.getSingleResult();
    }

    private List<String> getStatuses(ChargeSearchParams params) {
        Set<ChargeStatus> internalStates = params.getInternalStates();

        Set<ChargeStatus> internalChargeStatuses = params.getInternalChargeStatuses();
        List<String> statuses = Stream.concat(internalStates.stream(), internalChargeStatuses.stream())
                .map(Enum::name)
                .collect(toList());
        statuses.addAll(params.getInternalRefundStatuses().stream()
                .map(Enum::name)
                .collect(toList()));
        return statuses;
    }

    private Query setParams(Query typedQuery, ChargeSearchParams params, List<String> statuses) {
        typedQuery.setParameter("gatewayAccountId", params.getGatewayAccountId());

        // here add the values for the optional params
        if (isNotEmpty(params.getEmail())) {
            String formattedEmail = getEscapedString(params.getEmail());
            typedQuery.setParameter("email", "%" + formattedEmail + "%");
        }

        if (isNotEmpty(params.getReference())) {
            typedQuery.setParameter("reference", "%" + getEscapedString(params.getReference()) + "%");
        }

        if (params.getFromDate() != null) {
            final Timestamp databaseFormatted = utcDateTimeConverter.convertToDatabaseColumn(params.getFromDate());
            typedQuery.setParameter("createdDate", databaseFormatted);
        }

        if (params.getToDate() != null) {
            final Timestamp databaseFormatted = utcDateTimeConverter.convertToDatabaseColumn(params.getToDate());
            typedQuery.setParameter("toDate", databaseFormatted);
        }

        if (params.getTransactionType() != null) {
            TransactionOperation operation = params.getTransactionType().equals(TransactionType.PAYMENT) ?
                    TransactionOperation.CHARGE : TransactionOperation.REFUND;
            typedQuery.setParameter("operation", operation.name());
        }

        addInParameters(typedQuery, params.getCardBrands(), "cardBrand");
        addInParameters(typedQuery, statuses, "status");

        return typedQuery;
    }

    private StringBuilder buildQueryString(String queryString, ChargeSearchParams params, List<String> statuses) {
        StringBuilder query = new StringBuilder(queryString);
        query.append("WHERE t.gateway_account_id = ?gatewayAccountId ");
        // here add the optional params
        if (isNotEmpty(params.getEmail())) {
            query.append("AND t2.email ILIKE ?email ");
        }

        if (isNotEmpty(params.getReference())) {
            query.append("AND p.reference ILIKE ?reference ");
        }

        if (params.getFromDate() != null) {
            query.append("AND t.created_date >= ?createdDate ");
        }

        if (params.getToDate() != null) {
            query.append("AND t.created_date < ?toDate ");
        }

        if (params.getTransactionType() != null) {
            query.append("AND t.operation = ?operation ");
        }

        addInToQueryString(query, params.getCardBrands(), "cardBrand", "c.card_brand");
        addInToQueryString(query, statuses, "status", "t.status");
        return query;
    }

    //Had to add as EclipseLink addParameter cannot work out lists.
    private void addInToQueryString(StringBuilder query, List<?> values, String parameterName, String tableColumnName) {
        if (!values.isEmpty()) {
            final String arguments = Streams.mapWithIndex(values.stream(), (status, counter) -> "?" + parameterName + counter)
                    .collect(Collectors.joining(","));
            query.append("AND ").append(tableColumnName).append(" IN (").append(arguments).append(") ");
        }
    }

    //Had to add as EclipseLink addParameter cannot work out lists.
    private void addInParameters(Query typedQuery, List<String> values, String parameterName) {
        if (!values.isEmpty()) {
            for (int counter = 0; counter < values.size(); counter++) {
                typedQuery.setParameter(parameterName + counter, values.get(counter));
            }
        }
    }

    private String getEscapedString(String inputString) {
        return inputString.toLowerCase()
                .replaceAll("_", "\\\\_")
                .replaceAll("%", "\\\\%");
    }
}
