package uk.gov.pay.connector.dao;

import org.apache.commons.lang3.StringUtils;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.model.ChargeStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;
import static org.apache.commons.lang3.BooleanUtils.negate;

public class ChargeDao {
    private static final List<String> REQUIRED_FIELDS = newArrayList("amount", "gateway_account_id", "return_url");
    private DBI jdbi;

    public ChargeDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public String saveNewCharge(Map<String, Object> charge) throws PayDBIException {
        checkForMissingFields(charge);

        Map<String, Object> fixedCharge = copyAndConvertFieldToLong(charge, "gateway_account_id");
        return jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO charges(amount, gateway_account_id, status, return_url) " +
                                        "VALUES (:amount, :gateway_account_id, :status, :return_url)")
                                .bindFromMap(fixedCharge)
                                .bind("status", ChargeStatus.CREATED.getValue())
                                .executeAndReturnGeneratedKeys(StringMapper.FIRST)
                                .first()
        );
    }

    public Optional<Map<String, Object>> findById(String chargeId) {
        Map<String, Object> data = jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT charge_id, amount, gateway_account_id, status, return_url " +
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

    public void updateStatus(String chargeId, ChargeStatus newStatus) throws PayDBIException {
        Integer numberOfUpdates = jdbi.withHandle(handle ->
                        handle
                                .createStatement("UPDATE charges SET status=:status WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .bind("status", newStatus.getValue())
                                .execute()
        );

        if (numberOfUpdates != 1) {
            throw new PayDBIException(format("Could not update charge '%s' with status %s", chargeId, newStatus));
        }
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

    private void checkForMissingFields(Map<String, Object> charge) throws PayDBIException {
        String fieldsMissing = REQUIRED_FIELDS.stream()
                .filter(requiredKey -> negate(charge.containsKey(requiredKey)))
                .collect(Collectors.joining(", "));

        if (StringUtils.isNotBlank(fieldsMissing)) {
            throw new PayDBIException(format("Field(s) missing: %s", fieldsMissing));
        }
    }
}
