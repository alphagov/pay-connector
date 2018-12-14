package uk.gov.pay.connector.charge.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.jooq.Condition;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectOrderByStep;
import org.jooq.SelectSeekStep1;
import org.jooq.impl.DSL;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumberConverter;
import uk.gov.pay.connector.charge.model.TransactionSearchStrategyTransactionType;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.Transaction;
import uk.gov.pay.connector.charge.model.domain.TransactionType;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.selectDistinct;
import static org.jooq.impl.DSL.table;

@Transactional
public class TransactionDao {

    private enum QueryType {SELECT, COUNT}

    private final Provider<EntityManager> entityManager;
    private final UTCDateTimeConverter utcDateTimeConverter;
    private final LastDigitsCardNumberConverter lastDigitsCardNumberConverter;

    @Inject
    public TransactionDao(Provider<EntityManager> entityManager, UTCDateTimeConverter utcDateTimeConverter, LastDigitsCardNumberConverter lastDigitsCardNumberConverter) {
        this.entityManager = entityManager;
        this.utcDateTimeConverter = utcDateTimeConverter;
        this.lastDigitsCardNumberConverter = lastDigitsCardNumberConverter;
    }

    public List<Transaction> findAllBy(Long gatewayAccountId, SearchParams params) {
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
                        field("first_digits_card_number"),
                        field("user_external_id"),
                        field("address_city"),
                        field("address_country"),
                        field("address_county"),
                        field("address_line1"),
                        field("address_line2"),
                        field("address_postcode"),
                        field("amount"),
                        field("language"),
                        field("delayed_capture"),
                        field("corporate_surcharge"))
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

    public Long getTotalFor(Long gatewayAccountId, SearchParams params) {
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

    private SelectOrderByStep buildQueryFor(Long gatewayAccountId, QueryType queryType, SearchParams params) {
        Condition queryFilters = field("c.gateway_account_id").eq(gatewayAccountId);

        if (params.getCardHolderName() != null && isNotBlank(params.getCardHolderName().toString())) {
            queryFilters = queryFilters.and(
                    field("c.cardholder_name").lower().like(buildLikeClauseContaining(params.getCardHolderName().toString().toLowerCase())));
        }

        if (params.getLastDigitsCardNumber() != null) {
            queryFilters = queryFilters.and(
                    field("c.last_digits_card_number").eq(lastDigitsCardNumberConverter.convertToDatabaseColumn(params.getLastDigitsCardNumber())));
        }

        if (params.getFirstDigitsCardNumber() != null && isNotBlank(params.getFirstDigitsCardNumber().toString())) {
            queryFilters = queryFilters.and(
                    field("c.first_digits_card_number").eq(params.getFirstDigitsCardNumber().toString()));
        }

        if (isNotBlank(params.getEmail())) {
            queryFilters = queryFilters.and(
                    field("c.email").lower().like(buildLikeClauseContaining(params.getEmail().toLowerCase())));
        }

        if (!params.getCardBrands().isEmpty()) {
            queryFilters = queryFilters.and(
                    field("c.card_brand").in(params.getCardBrands()));
        }

        if (params.getReference() != null && isNotBlank(params.getReference().toString())) {
            queryFilters = queryFilters.and(
                    field("c.reference").lower().like(buildLikeClauseContaining(params.getReference().toString().toLowerCase())));
        }

        Condition queryFiltersForCharges = queryFilters;
        Condition queryFiltersForRefunds = queryFilters;

        if (params.getTransactionSearchStrategyTransactionType() != null) {
            if (params.getTransactionSearchStrategyTransactionType() == TransactionSearchStrategyTransactionType.PAYMENT) {
                queryFiltersForRefunds = queryFiltersForRefunds.and(
                        field("'" + TransactionType.REFUND + "'").eq(TransactionType.CHARGE.toString()));
            }
            if (params.getTransactionSearchStrategyTransactionType() == TransactionSearchStrategyTransactionType.REFUND) {
                queryFiltersForCharges = queryFiltersForCharges.and(
                        field("'" + TransactionType.CHARGE + "'").eq(TransactionType.REFUND.toString()));
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
                field("'" + TransactionType.CHARGE + "'").as("transaction_type"),
                field("c.id").as("charge_id"),
                field("c.external_id"),
                field("c.reference"),
                field("c.description"),
                field("c.status"),
                field("c.email"),
                field("c.gateway_account_id"),
                inline((String) null).as("user_external_id"),
                field("c.gateway_transaction_id"),
                field("c.created_date").as("date_created"),
                field("c.card_brand"),
                field("t.label").as("card_brand_label"),
                field("c.cardholder_name"),
                field("c.expiry_date"),
                field("c.last_digits_card_number"),
                field("c.first_digits_card_number"),
                field("c.address_city"),
                field("c.address_country"),
                field("c.address_county"),
                field("c.address_line1"),
                field("c.address_line2"),
                field("c.address_postcode"),
                field("c.amount"),
                field("c.language"),
                field("c.delayed_capture"),
                field("c.corporate_surcharge"))
                .from(table("charges").as("c").leftJoin(selectDistinct().on(field("label")).from("card_types").asTable("t")).on("c.card_brand=t.brand"))
                .where(queryFiltersForCharges);

        SelectConditionStep queryForRefunds = DSL.select(
                field("'" + TransactionType.REFUND + "'").as("transaction_type"),
                field("c.id").as("charge_id"),
                field("c.external_id"),
                field("c.reference"),
                field("c.description"),
                field("r.status"),
                field("c.email"),
                field("c.gateway_account_id"),
                field("r.user_external_id").as("user_external_id"),
                field("c.gateway_transaction_id"),
                field("r.created_date").as("date_created"),
                field("c.card_brand"),
                field("t.label").as("card_brand_label"),
                field("c.cardholder_name"),
                field("c.expiry_date"),
                field("c.last_digits_card_number"),
                field("c.first_digits_card_number"),
                field("c.address_city"),
                field("c.address_country"),
                field("c.address_county"),
                field("c.address_line1"),
                field("c.address_line2"),
                field("c.address_postcode"),
                field("r.amount"),
                field("c.language"),
                field("c.delayed_capture"),
                field("c.corporate_surcharge"))
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
                .replaceAll("\\\\", "\\\\\\\\")
                .replaceAll("_", "\\\\_")
                .replaceAll("%", "\\\\%");
        return '%' + escapedLikeClause + '%';
    }

    private Set<String> mapChargeStatuses(Set<ChargeStatus> status) {
        return status.stream()
                .map(ChargeStatus::getValue)
                .collect(Collectors.toSet());
    }

    private Set<String> mapRefundStatuses(Set<RefundStatus> status) {
        return status.stream()
                .map(RefundStatus::getValue)
                .collect(Collectors.toSet());
    }
}
