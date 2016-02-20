package uk.gov.pay.connector.util;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.persistence.TypedQuery;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ChargesJpaSearch {

    public static String constructSearchTransactionsQuery(String reference, ExternalChargeStatus status, String fromDate, String toDate) {
        StringBuffer subQuery = new StringBuffer();

        String AND = " AND ";

        // Filter by reference or chargeId
        if (isNotBlank(reference)) {
            subQuery.append(AND);
            subQuery.append("c.reference like :reference");
        }

        // Filter by status
        if (status != null) {
            subQuery.append(AND);
            subQuery.append("c.status IN (");
            subQuery.append(
                    Arrays.asList(status.getInnerStates())
                            .stream()
                            .map((s) -> "'" + s.getValue() + "'")
                            .collect(Collectors.joining(", ")));
            subQuery.append(")");
        }

        // Filter by Date(s)
        if (isNotBlank(fromDate)) {
            subQuery.append(AND);
            subQuery.append("c.createdDate >= :fromDate");
        }
        if (isNotBlank(toDate)) {
            subQuery.append(AND);
            subQuery.append("c.createdDate <= :toDate");
        }

        return subQuery.toString();
    }

    public static TypedQuery<ChargeEntity> setParameters(TypedQuery<ChargeEntity> query, Long gatewayAccountId,
                                                       String reference,
                                                       String fromDate, String toDate) {

        query.setParameter("gid", Long.valueOf(gatewayAccountId));

        // Filter by reference or chargeId
        if (StringUtils.isNotBlank(reference)) {
            query.setParameter("reference", "%" + reference + "%");
        }

        // Filter by Date(s)
        if (StringUtils.isNotBlank(fromDate)) {
            query.setParameter("fromDate", ZonedDateTime.parse(fromDate));
        }
        if (StringUtils.isNotBlank(toDate)) {
            query.setParameter("toDate", ZonedDateTime.parse(toDate));
        }
        return query;
    }
}