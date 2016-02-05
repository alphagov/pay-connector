package uk.gov.pay.connector.util;

import org.apache.commons.lang3.StringUtils;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ChargesSearch {

    private static String constructSearchTransactionsQuery(String reference, ExternalChargeStatus status, String fromDate, String toDate) {
        StringBuffer subQuery = new StringBuffer();

        String AND = " AND ";

        // Filter by reference or chargeId
        if (isNotBlank(reference)) {
            subQuery.append(AND);
            subQuery.append("c.reference LIKE :reference");
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
            subQuery.append("c.created_date >= :fromDate");
        }
        if (isNotBlank(toDate)) {
            subQuery.append(AND);
            subQuery.append("c.created_date <= :toDate");
        }

        return subQuery.toString();
    }

    public static List<Map<String, Object>> createQueryHandle(Handle handle, String query, String gatewayAccountId,
                                                              String reference, ExternalChargeStatus status, String fromDate, String toDate) {

        Query<Map<String, Object>> queryStmt = handle
                .createQuery(String.format(query, constructSearchTransactionsQuery(reference, status, fromDate, toDate)))
                .bind("gid", Long.valueOf(gatewayAccountId));

        // Filter by reference or chargeId
        if (StringUtils.isNotBlank(reference)) {
            queryStmt.bind("reference", "%" + reference + "%");
        }

        // Filter by Date(s)
        if (StringUtils.isNotBlank(fromDate)) {
            queryStmt.bind("fromDate", Timestamp.from(DateTimeUtils.toUTCZonedDateTime(fromDate).get().toInstant()));
        }
        if (StringUtils.isNotBlank(toDate)) {
            queryStmt.bind("toDate", Timestamp.from(DateTimeUtils.toUTCZonedDateTime(toDate).get().toInstant()));
        }
        return queryStmt.map(new DefaultMapper()).list();
    }
}