package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

public class ChargeDao {
    private DBI jdbi;

    public ChargeDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public String saveNewCharge(Map<String, Object> charge) throws PayDBIException {
        Map<String, Object> fixedCharge = copyAndConvertFieldToLong(charge, "gateway_account_id");
        return jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO charges(amount, gateway_account_id, status, return_url, description, reference) " +
                                        "VALUES (:amount, :gateway_account_id, :status, :return_url, :description, :reference)")
                                .bindFromMap(fixedCharge)
                                .bind("status", CREATED.getValue())
                                .executeAndReturnGeneratedKeys(StringMapper.FIRST)
                                .first()
        );
    }

    public Optional<Map<String, Object>> findChargeForAccount(String chargeId, String accountId) {
        Map<String, Object> data = jdbi.withHandle(handle ->
                handle
                        .createQuery("SELECT charge_id, amount, gateway_account_id, status, return_url, gateway_transaction_id " +
                                "FROM charges WHERE charge_id=:charge_id AND gateway_account_id=:account_id")
                        .bind("charge_id", Long.valueOf(chargeId))
                        .bind("account_id", Long.valueOf(accountId))
                        .map(new DefaultMapper())
                        .first()
        );

        if (data != null) {
            data = copyAndConvertFieldsToString(data, "charge_id", "gateway_account_id");
        }
        return Optional.ofNullable(data);
    }

    public Optional<Map<String, Object>> findById(String chargeId) {
        Map<String, Object> data = jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT charge_id, amount, gateway_account_id, status, return_url, gateway_transaction_id, description, reference " +
                                        "FROM charges WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .map(new DefaultMapper())
                                .first()
        );

        if (data != null) {
            data = copyAndConvertFieldsToString(data, "charge_id", "gateway_account_id");
        }
        return Optional.ofNullable(data);
    }

    public void updateGatewayTransactionId(String chargeId, String transactionId) throws PayDBIException {
        Integer numberOfUpdates = jdbi.withHandle(handle ->
                        handle
                                .createStatement("UPDATE charges SET gateway_transaction_id=:transactionId WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .bind("transactionId", transactionId)
                                .execute()
        );

        if (numberOfUpdates != 1) {
            throw new PayDBIException(format("Could not update charge '%s' with gateway transaction id %s, updated %d rows.", chargeId, transactionId, numberOfUpdates));
        }
    }

    public void updateStatusWithGatewayInfo(String gatewayTransactionId, ChargeStatus newStatus) {
        Integer numberOfUpdates = jdbi.withHandle(handle ->
                        handle
                                .createStatement("UPDATE charges SET status=:status WHERE gateway_transaction_id=:gateway_transaction_id")
                                .bind("gateway_transaction_id", gatewayTransactionId)
                                .bind("status", newStatus.getValue())
                                .execute()
        );

        if (numberOfUpdates != 1) {
            throw new PayDBIException(format("Could not update charge (gateway_transaction_id: %s) with status %s, updated %d rows.", gatewayTransactionId, newStatus, numberOfUpdates));
        }
    }

    public void updateStatus(String chargeId, ChargeStatus newStatus) {
        Integer numberOfUpdates = jdbi.withHandle(handle ->
                        handle
                                .createStatement("UPDATE charges SET status=:status WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .bind("status", newStatus.getValue())
                                .execute()
        );

        if (numberOfUpdates != 1) {
            throw new PayDBIException(format("Could not update charge '%s' with status %s, updated %d rows.", chargeId, newStatus, numberOfUpdates));
        }
    }

    // updates the new status only if the charge is in one of the old statuses and returns num of rows affected
    // very specific transition happening here so check for a valid state before transitioning
    public int updateNewStatusWhereOldStatusIn(String chargeId, ChargeStatus newStatus, List<ChargeStatus> oldStatuses) {
        String sql = format("UPDATE charges SET status=:newStatus WHERE charge_id=:charge_id and status in (%s)", getStringFromStatusList(oldStatuses));

        return jdbi.withHandle(handle ->
                        handle.createStatement(sql)
                                .bind("charge_id", Long.valueOf(chargeId))
                                .bind("newStatus", newStatus.getValue())
                                .execute()
        );
    }

    public List<Map<String, Object>> findAllBy(String gatewayAccountId) {
        List<Map<String, Object>> rawData = jdbi.withHandle(handle ->
                handle.createQuery("SELECT charge_id, gateway_transaction_id, status, amount, description, reference " +
                        "FROM charges WHERE gateway_account_id=:gid " +
                        "ORDER BY charge_id DESC")
                        .bind("gid", Long.valueOf(gatewayAccountId))
                        .map(new DefaultMapper())
                        .list());

        return copyAndConvertFieldsToString(rawData, "charge_id", "gateway_account_id");
    }

    private String getStringFromStatusList(List<ChargeStatus> oldStatuses) {
        return oldStatuses
                .stream()
                .map(t -> "'" + t.getValue() + "'")
                .collect(Collectors.joining(","));
    }

    private Map<String, Object> copyAndConvertFieldToLong(Map<String, Object> charge, String field) {
        Map<String, Object> copy = newHashMap(charge);
        Long fieldAsLong = Long.valueOf(copy.remove(field).toString());
        copy.put(field, fieldAsLong);
        return copy;
    }

    private Map<String, Object> copyAndConvertFieldsToString(Map<String, Object> data, String... fields) {
        Map<String, Object> copy = newHashMap(data);
        for (String field : fields) {
            copy.put(field, String.valueOf(copy.remove(field)));
        }
        return copy;
    }

    private List<Map<String, Object>> copyAndConvertFieldsToString(List<Map<String, Object>> data, final String... fields) {
        return data.stream()
                .map(charge -> copyAndConvertFieldsToString(charge, fields))
                .collect(toList());
    }
}
