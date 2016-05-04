package uk.gov.pay.connector.dao;

import com.google.common.base.Preconditions;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ChargeSearch {

    private static final String REFERENCE = "reference";
    private static final String STATUSES = "statuses";
    private static final String FROM_DATE = "fromDate";
    private static final String TO_DATE = "toDate";

    private static final Map<String, String> QUERY_DEFINITIONS = new HashMap<String, String>() {{
        put(REFERENCE, " AND c.reference LIKE :reference");
        put(FROM_DATE, " AND c.createdDate >= :fromDate");
        put(STATUSES, " AND c.status IN :statuses");
        put(TO_DATE, " AND c.createdDate < :toDate");
    }};

    private Long gatewayAccountId;
    private Map<String, Object> queryParameters = new HashMap<>();
    private int offset = 0;
    private int limit = 100;

    public static ChargeSearch aChargeSearch(Long gatewayAccountId) {
        return new ChargeSearch(gatewayAccountId);
    }

    private ChargeSearch(Long gatewayAccountId) {
        Preconditions.checkNotNull(gatewayAccountId);
        this.gatewayAccountId = gatewayAccountId;
    }

    public ChargeSearch withReferenceLike(String reference) {
        if (isNotBlank(reference)) queryParameters.put(REFERENCE, "%" + reference + "%");
        return this;
    }

    public ChargeSearch withExternalStatus(ExternalChargeStatus status) {
        if (status != null) queryParameters.put(STATUSES, Arrays.asList(status.getInnerStates()));
        return this;
    }

    public ChargeSearch withCreatedDateFrom(ZonedDateTime fromDate) {
        if (fromDate != null) queryParameters.put(FROM_DATE, fromDate);
        return this;
    }

    public ChargeSearch withCreatedDateTo(ZonedDateTime toDate) {
        if (toDate != null) queryParameters.put(TO_DATE, toDate);
        return this;
    }

    public ChargeSearch withLimit(int limit) {
        if (limit >= 0) this.limit = limit;
        return this;
    }

    public ChargeSearch withOffset(int offset) {
        if (offset >=0) this.offset = offset;
        return this;
    }

    public TypedQuery<ChargeEntity> apply(EntityManager entityManager) {
        TypedQuery<ChargeEntity> typedQuery = entityManager.createQuery(buildQueryDefinition(), ChargeEntity.class);
        applyParametersTo(typedQuery);
        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(limit);
        return typedQuery;
    }

    private void applyParametersTo(TypedQuery<ChargeEntity> typedQuery) {
        typedQuery.setParameter("gatewayAccountId", gatewayAccountId);
        queryParameters.forEach(typedQuery::setParameter);
    }

    private String buildQueryDefinition() {
        StringBuilder query = new StringBuilder("SELECT c " +
                "FROM ChargeEntity c " +
                "WHERE c.gatewayAccount.id = :gatewayAccountId");
        queryParameters.forEach((parameterName, parameterValue) -> query.append(QUERY_DEFINITIONS.get(parameterName)));
        query.append(" ORDER BY c.id DESC");
        return query.toString();
    }
}
