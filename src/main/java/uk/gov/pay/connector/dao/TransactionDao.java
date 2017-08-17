package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import uk.gov.pay.connector.model.TransactionDto;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static jersey.repackaged.com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.dao.ChargeDao.SQL_ESCAPE_SEQ;

public class TransactionDao {

    private final Provider<EntityManager> entityManager;
    private final UTCDateTimeConverter utcDateTimeConverter;

    @Inject
    public TransactionDao(Provider<EntityManager> entityManager, UTCDateTimeConverter utcDateTimeConverter) {
        this.entityManager = entityManager;
        this.utcDateTimeConverter = utcDateTimeConverter;
    }

    public List<TransactionDto> findAllBy(ChargeSearchParams params) {

        String chargesQuery = "SELECT 'charge' as transaction_type, charge.external_id, charge.reference, charge.description, charge.status, charge.email,  charge.gateway_account_id, charge.gateway_transaction_id, charge.created_date as date_created, charge.card_brand, charge.cardholder_name, charge.expiry_date, charge.last_digits_card_number, charge.address_city, charge.address_country, charge.address_county, charge.address_line1, charge.address_line2, charge.address_postcode, charge.amount " +
                "FROM charges AS charge ";


        String refundsQuery = "SELECT 'refund' as transaction_type, rcharge.external_id, rcharge.reference, rcharge.description, refund.status, rcharge.email, rcharge.gateway_account_id, rcharge.gateway_transaction_id, refund.created_date as date_created, rcharge.card_brand, rcharge.cardholder_name, rcharge.expiry_date, rcharge.last_digits_card_number, rcharge.address_city, rcharge.address_country, rcharge.address_county, rcharge.address_line1, rcharge.address_line2, rcharge.address_postcode, refund.amount " +
                "FROM refunds AS refund, charges AS rcharge WHERE rcharge.id=refund.charge_id ";

        List<String> chargesQueryFilters = new ArrayList<>();
        List<String> refundsQueryFilters = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        Map<Integer, Object> positionedParameters = new HashMap<>();

        if (params.getGatewayAccountId() != null) {
            chargesQueryFilters.add("charge.gateway_account_id = ? ");
            refundsQueryFilters.add("rcharge.gateway_account_id = ? ");
            parameters.add(params.getGatewayAccountId());
        }

        if (isNotBlank(params.getEmail())) {
            chargesQueryFilters.add("charge.email LIKE ? ");
            refundsQueryFilters.add("rcharge.email LIKE ? ");
            parameters.add('%' + escapeLikeClause(params.getEmail()) + '%');
        }

        if (isNotBlank(params.getCardBrand())) {
            chargesQueryFilters.add("charge.card_brand = ? ");
            refundsQueryFilters.add("rcharge.card_brand = ? ");
            parameters.add(params.getCardBrand());
        }

        if (isNotBlank(params.getReference())) {
            chargesQueryFilters.add("lower(charge.reference) LIKE ? ");
            refundsQueryFilters.add("lower(rcharge.reference) LIKE ? ");
            parameters.add('%' + escapeLikeClause(params.getReference().toLowerCase()) + '%');
        }

        /*if (params.getChargeStatuses() != null && !params.getChargeStatuses().isEmpty()) {

            String.join("", Collections.nCopies(params.getChargeStatuses().size(), "?"));

            chargesQueryFilters.add("charge.reference IN ? ");
            refundsQueryFilters.add("rcharge.reference IN ? ");
            parameters.add(charge.get(STATUS).in(params.getChargeStatuses()));
        }*/

        if (!chargesQueryFilters.isEmpty()) {

            StringBuilder chargesFilters = new StringBuilder();
            StringBuilder refundsFilters = new StringBuilder();

            for (int i = 0; i < chargesQueryFilters.size(); i++) {
                chargesFilters.append("AND ").append(chargesQueryFilters.get(i));
                positionedParameters.put(i + 1, parameters.get(i));
            }

            int refundsBaseParamPosition = chargesQueryFilters.size() + 1;
            for (int i = 0; i < refundsQueryFilters.size(); i++) {
                refundsFilters.append("AND ").append(refundsQueryFilters.get(i));
                positionedParameters.put(i + refundsBaseParamPosition, parameters.get(i));
            }

            chargesQuery = chargesQuery + chargesFilters.toString().replaceFirst("AND ", "WHERE ");
            refundsQuery = refundsQuery + refundsFilters.toString();
        }

        String query = "SELECT * FROM ((" + chargesQuery + ") UNION (" + refundsQuery + ")) AS car ORDER BY car.date_created DESC";

        System.out.println("query = " + query);

        Query getChargesQuery = entityManager.get().createNativeQuery(query);

        for (Map.Entry<Integer, Object> entry : positionedParameters.entrySet()) {
            getChargesQuery.setParameter(entry.getKey(), entry.getValue());
        }

        List<Object[]> resultList = getChargesQuery.getResultList();
        List<TransactionDto> transactions = newArrayList();

        for (Object[] o : resultList) {
            transactions.add(new TransactionDto((String) o[0], (String) o[1], (String) o[2], (String) o[3], (String) o[4], (String) o[5], (Long) o[6], (String) o[7], ZonedDateTime.ofInstant(((Timestamp) o[8]).toInstant(), ZoneId.of("UTC")), (String) o[9], (String) o[10], (String) o[11], (String) o[12], (String) o[13], (String) o[14], (String) o[15], (String) o[16], (String) o[17], (String) o[18], (Long) o[19]));
        }

        return transactions;
    }

    private String escapeLikeClause(String element) {
        return element
                .replaceAll("_", SQL_ESCAPE_SEQ + "_")
                .replaceAll("%", SQL_ESCAPE_SEQ + "%");
    }
}
