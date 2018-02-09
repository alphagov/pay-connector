package uk.gov.pay.connector.dao;

import com.google.common.collect.Streams;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.TransactionType;
import uk.gov.pay.connector.model.domain.ChargeStatus;
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

    public List<TransactionEntity> search(ChargeSearchParams params) {

        List<String> statuses = getStatuses(params);
        StringBuilder query = buildQueryString("SELECT t.* FROM transactions t ", params, statuses);

        query.append(" ORDER BY t.created_date DESC");

        Query typedQuery = entityManager.get().createNativeQuery(query.toString(), TransactionEntity.class);
        typedQuery = setParams(typedQuery, params, statuses);
        typedQuery = addPagination(params, typedQuery);

        return (List<TransactionEntity>) typedQuery.getResultList();
    }

    public long getTotal(ChargeSearchParams params) {
        List<String> statuses = getStatuses(params);
        StringBuilder query = buildQueryString("SELECT count(t.*) FROM transactions t ", params, statuses);

        Query typedQuery = entityManager.get().createNativeQuery(query.toString());
        typedQuery = setParams(typedQuery, params, statuses);

        return (Long) typedQuery.getSingleResult();
    }

    private Query addPagination(ChargeSearchParams params, Query typedQuery) {
        if (params.getPage() != null && params.getDisplaySize() != null) {
            final long displaySize = params.getDisplaySize().intValue();
            long offset = (params.getPage() - 1) * displaySize;
            typedQuery = typedQuery.setFirstResult((int) offset).setMaxResults((int) displaySize);
        }
        return typedQuery;
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
        if (isNotEmpty(params.getReference())) {
            query.append("JOIN payment_requests p ON t.payment_request_id = p.id ");
        }
        if (isNotEmpty(params.getEmail()) || !params.getCardBrands().isEmpty()) {
            query.append("JOIN transactions t2 ON t.payment_request_id = t2.payment_request_id AND t2.operation = 'CHARGE' ");
        }
        if (!params.getCardBrands().isEmpty()) {
            query.append("LEFT JOIN cards c ON t2.id = c.transaction_id ");
        }

        query.append("WHERE t.gateway_account_id = ?gatewayAccountId ");

        // here add the optional params
        if (isNotEmpty(params.getEmail())) {
            query.append("AND LOWER(t2.email) LIKE ?email ");
        }

        if (isNotEmpty(params.getReference())) {
            query.append("AND LOWER(p.reference) LIKE ?reference ");
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
