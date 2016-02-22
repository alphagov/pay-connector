package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.ZonedDateTime;

public class ChargeSearchQueryBuilder {

    private static final String BASE_QUERY = "select c from ChargeEntity c where ";
    private static final String AND = " and ";

    private Long gatewayAccountId;
    private String reference;
    private String status;
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;

    public ChargeSearchQueryBuilder withGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
        return this;
    }

    public ChargeSearchQueryBuilder withReferenceLike(String reference) {
        this.reference = reference;
        return this;
    }

    public ChargeSearchQueryBuilder withStatus(String status) {
        this.status = status;
        return this;
    }

    public ChargeSearchQueryBuilder withCreatedDateBetween(ZonedDateTime fromDate, ZonedDateTime toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        return this;
    }

    public TypedQuery<ChargeEntity> build(Provider<EntityManager> entityManagerProvider) {

        String query = BASE_QUERY + "c.gatewayAccount.id = :gatewayAccountId" +
                AND + "c.reference like :reference" +
                AND + "c.status = :status" +
                AND + "c.createdDate between :fromDate and :toDate";

        TypedQuery<ChargeEntity> typedQuery = entityManagerProvider.get().createQuery(query, ChargeEntity.class);

        typedQuery.setParameter("gatewayAccountId", gatewayAccountId);
        typedQuery.setParameter("reference", "%" + reference + "%");
        typedQuery.setParameter("status", status);
        typedQuery.setParameter("fromDate", fromDate);
        typedQuery.setParameter("toDate", toDate);

        return typedQuery;
    }
}
