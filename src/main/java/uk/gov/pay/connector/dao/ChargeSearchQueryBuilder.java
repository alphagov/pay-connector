package uk.gov.pay.connector.dao;

import com.google.common.base.Preconditions;
import com.google.inject.Provider;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ChargeSearchQueryBuilder {

    private static final String BASE_QUERY =
            "SELECT c " +
                    "FROM ChargeEntity c " +
                    "WHERE c.gatewayAccount.id = :gatewayAccountId";

    private static final String AND = " AND ";

    private Long gatewayAccountId;
    private String reference;
    private List<ChargeStatus> statuses = new ArrayList<>();
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;

    public ChargeSearchQueryBuilder(Long gatewayAccountId) {
        Preconditions.checkNotNull(gatewayAccountId);
        this.gatewayAccountId = gatewayAccountId;
    }

    public ChargeSearchQueryBuilder withReferenceLike(String reference) {
        this.reference = reference;
        return this;
    }

    public ChargeSearchQueryBuilder withStatusIn(ChargeStatus... statuses) {
        this.statuses = Arrays.asList(statuses);
        return this;
    }

    public ChargeSearchQueryBuilder withCreatedDateFrom(ZonedDateTime fromDate) {
        this.fromDate = fromDate;
        return this;
    }

    public ChargeSearchQueryBuilder withCreatedDateTo(ZonedDateTime toDate) {
        this.toDate = toDate;
        return this;
    }

    public TypedQuery<ChargeEntity> buildWith(Provider<EntityManager> entityManagerProvider) {
        TypedQuery<ChargeEntity> typedQuery = entityManagerProvider.get().createQuery(buildQuery(), ChargeEntity.class);

        typedQuery.setParameter("gatewayAccountId", gatewayAccountId);

        if (isNoneBlank(reference)) {
            typedQuery.setParameter("reference", "%" + reference + "%");
        }

        if (!statuses.isEmpty()) {
            typedQuery.setParameter("statuses", statuses);
        }

        if (fromDate != null) {
            typedQuery.setParameter("fromDate", fromDate);
        }

        if (toDate != null) {
            typedQuery.setParameter("toDate", toDate);
        }

        return typedQuery;
    }

    private String buildQuery() {
        StringBuilder query = new StringBuilder(BASE_QUERY);

        if (isNotBlank(reference)) {
            query.append(AND);
            query.append("c.reference LIKE :reference");
        }

        if (statuses != null && statuses.size() > 0) {
            query.append(AND).append("c.status IN :statuses");
        }

        if (fromDate != null) {
            query.append(AND);
            query.append("c.createdDate >= :fromDate");
        }
        if (toDate != null) {
            query.append(AND);
            query.append("c.createdDate <= :toDate");
        }

        query.append(" ORDER BY c.id DESC");

        System.out.println("query.toString() = " + query.toString());
        return query.toString();
    }
}
