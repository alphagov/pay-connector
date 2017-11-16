package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.jooq.*;
import org.jooq.impl.DSL;
import uk.gov.pay.connector.model.TransactionType;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.Transaction;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jooq.impl.DSL.*;

@Transactional
public class TransactionDao {

    private enum QueryType {SELECT, COUNT}

    private final Provider<EntityManager> entityManager;
    private final UTCDateTimeConverter utcDateTimeConverter;

    @Inject
    public TransactionDao(Provider<EntityManager> entityManager, UTCDateTimeConverter utcDateTimeConverter) {
        this.entityManager = entityManager;
        this.utcDateTimeConverter = utcDateTimeConverter;
    }

    public List<Transaction> findAllBy(Long gatewayAccountId, ChargeSearchParams params) {
        SelectSeekStep1 query = DSL
                .select(field("transaction_type"),
                        field("charge_id"),
                        field("external_id"),
                        field("reference"),
                        field("description"),
                        field("status"),
                        field("email"),
                        field("gateway_account_id"),
                        field("gateway_transaction_id"),
                        field("date_created"),
                        field("card_brand"),
                        field("card_brand_label"),
                        field("cardholder_name"),
                        field("expiry_date"),
                        field("last_digits_card_number"),
                        field("address_city"),
                        field("address_country"),
                        field("address_county"),
                        field("address_line1"),
                        field("address_line2"),
                        field("address_postcode"),
                        field("amount"))
                .from(buildQueryFor(gatewayAccountId, QueryType.SELECT, params))
                .orderBy(field("date_created").desc());

        if (params.getPage() != null && params.getDisplaySize() != null) {
            int offset = Long.valueOf((params.getPage() - 1) * params.getDisplaySize()).intValue();
            int limit = params.getDisplaySize().intValue();

            query.offset(offset).limit(limit);
        }

        // Extract the SQL statement from the jOOQ query:
        String sql = query.getSQL();
        Query result = entityManager.get().createNativeQuery(sql, "TransactionMapping");

        // Extract the bind values from the jOOQ query:
        List<Object> values = query.getBindValues();
        for (int i = 0; i < values.size(); i++) {
            result.setParameter(i + 1, values.get(i));
        }

        return result.getResultList();
    }

    public Long getTotalFor(Long gatewayAccountId, ChargeSearchParams params) {
        SelectJoinStep query = DSL
                .select(count())
                .from(buildQueryFor(gatewayAccountId, QueryType.COUNT, params));

        // Extract the SQL statement from the jOOQ query:
        Query result = entityManager.get().createNativeQuery(query.getSQL());

        // Extract the bind values from the jOOQ query:
        List<Object> values = query.getBindValues();
        for (int i = 0; i < values.size(); i++) {
            result.setParameter(i + 1, values.get(i));
        }

        return (long) result.getSingleResult();
    }

    private SelectOrderByStep buildQueryFor(Long gatewayAccountId, QueryType queryType, ChargeSearchParams params) {
        Condition queryFilters = field("c.gateway_account_id").eq(gatewayAccountId);

        if (isNotBlank(params.getEmail())) {
            queryFilters = queryFilters.and(
                    field("c.email").lower().like(buildLikeClauseContaining(params.getEmail().toLowerCase())));
        }

        if (isNotBlank(params.getCardBrand())) {
            queryFilters = queryFilters.and(
                    field("c.card_brand").in(params.getCardBrand()));
        }

        if (isNotBlank(params.getReference())) {
            queryFilters = queryFilters.and(
                    field("c.reference").lower().like(buildLikeClauseContaining(params.getReference().toLowerCase())));
        }

        Condition queryFiltersForCharges = queryFilters;
        Condition queryFiltersForRefunds = queryFilters;

        if (params.getTransactionType() != null) {
            if (params.getTransactionType() == TransactionType.PAYMENT) {
                queryFiltersForRefunds = queryFiltersForRefunds.and(
                        field("'refund'").eq("charge"));
            }
            if (params.getTransactionType() == TransactionType.REFUND) {
                queryFiltersForCharges = queryFiltersForCharges.and(
                        field("'charge'").eq("refund"));

            }
        }
        if (params.getFromDate() != null) {
            queryFiltersForCharges = queryFiltersForCharges.and(
                    field("c.created_date").greaterOrEqual(utcDateTimeConverter.convertToDatabaseColumn(params.getFromDate())));
            queryFiltersForRefunds = queryFiltersForRefunds.and(
                    field("r.created_date").greaterOrEqual(utcDateTimeConverter.convertToDatabaseColumn(params.getFromDate())));
        }
        if (params.getToDate() != null) {
            queryFiltersForCharges = queryFiltersForCharges.and(
                    field("c.created_date").lessThan(utcDateTimeConverter.convertToDatabaseColumn(params.getToDate())));
            queryFiltersForRefunds = queryFiltersForRefunds.and(
                    field("r.created_date").lessThan(utcDateTimeConverter.convertToDatabaseColumn(params.getToDate())));
        }
        if (params.getExternalChargeStates() != null && !params.getExternalChargeStates().isEmpty()) {
            queryFiltersForCharges = queryFiltersForCharges.and(
                    field("c.status").in(mapChargeStatuses(params.getInternalChargeStatuses())));
        }
        if (params.getExternalRefundStates() != null && !params.getExternalRefundStates().isEmpty()) {
            queryFiltersForRefunds = queryFiltersForRefunds.and(
                    field("r.status").in(mapRefundStatuses(params.getInternalRefundStatuses())));
        }

        SelectConditionStep queryForCharges = DSL.select(
                field("'charge'").as("transaction_type"),
                field("c.id").as("charge_id"),
                field("c.external_id"),
                field("c.reference"),
                field("c.description"),
                field("c.status"),
                field("c.email"),
                field("c.gateway_account_id"),
                field("c.gateway_transaction_id"),
                field("c.created_date").as("date_created"),
                field("c.card_brand"),
                field("t.label").as("card_brand_label"),
                field("c.cardholder_name"),
                field("c.expiry_date"),
                field("c.last_digits_card_number"),
                field("c.address_city"),
                field("c.address_country"),
                field("c.address_county"),
                field("c.address_line1"),
                field("c.address_line2"),
                field("c.address_postcode"),
                field("c.amount"))
                .from(table("charges").as("c").leftJoin(selectDistinct().on(field("label")).from("card_types").asTable("t")).on("c.card_brand=t.brand"))
                .where(queryFiltersForCharges);

        SelectConditionStep queryForRefunds = DSL.select(
                field("'refund'").as("transaction_type"),
                field("c.id").as("charge_id"),
                field("c.external_id"),
                field("c.reference"),
                field("c.description"),
                field("r.status"),
                field("c.email"),
                field("c.gateway_account_id"),
                field("c.gateway_transaction_id"),
                field("r.created_date").as("date_created"),
                field("c.card_brand"),
                field("t.label").as("card_brand_label"),
                field("c.cardholder_name"),
                field("c.expiry_date"),
                field("c.last_digits_card_number"),
                field("c.address_city"),
                field("c.address_country"),
                field("c.address_county"),
                field("c.address_line1"),
                field("c.address_line2"),
                field("c.address_postcode"),
                field("r.amount"))
                .from(table("charges").as("c").leftJoin(selectDistinct().on(field("label")).from("card_types").asTable("t")).on("c.card_brand=t.brand"))
                .join(table("refunds").as("r"))
                .on(field("c.id").eq(field("r.charge_id")))
                .where(queryFiltersForRefunds);

        if (queryType == QueryType.SELECT) {
            queryForCharges.orderBy(field("c.created_date").desc());
            queryForRefunds.orderBy(field("r.created_date").desc());

            if (params.getPage() != null && params.getDisplaySize() != null) {
                int offset = Long.valueOf((params.getPage() - 1) * params.getDisplaySize()).intValue();
                int limit = params.getDisplaySize().intValue();

                queryForCharges.limit(offset + limit);
                queryForRefunds.limit(offset + limit);
            }
        }

        return queryForCharges.unionAll(queryForRefunds);
    }

    private String buildLikeClauseContaining(String textToFind) {
        String escapedLikeClause = textToFind
                .replaceAll("_", "\\\\_")
                .replaceAll("%", "\\\\%");
        return '%' + escapedLikeClause + '%';
    }

    private Set<String> mapChargeStatuses(Set<ChargeStatus> status) {
        return status.stream()
                .map(s -> s.getValue())
                .collect(Collectors.toSet());
    }

    private Set<String> mapRefundStatuses(Set<RefundStatus> status) {
        return status.stream()
                .map(s -> s.getValue())
                .collect(Collectors.toSet());
    }
}
