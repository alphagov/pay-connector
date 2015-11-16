package uk.gov.pay.connector.dao;

import org.postgresql.util.PGobject;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.BooleanMapper;
import org.skife.jdbi.v2.util.StringMapper;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Maps.newHashMap;

public class GatewayAccountDao {
    private DBI jdbi;

    public GatewayAccountDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public String insertProviderAndReturnNewId(String paymentProvider) {
        return jdbi.withHandle(handle -> handle
                        .createStatement("INSERT INTO gateway_accounts(payment_provider) VALUES (:paymentProvider)")
                        .bind("paymentProvider", paymentProvider)
                        .executeAndReturnGeneratedKeys(StringMapper.FIRST)
                        .first()
        );
    }

    public boolean idIsMissing(String gatewayAccountId) {
        return jdbi.withHandle(handle -> handle
                .createQuery("SELECT NOT EXISTS(SELECT 1 from gateway_accounts where gateway_account_id=:id)")
                .bind("id", Long.valueOf(gatewayAccountId))
                .map(BooleanMapper.FIRST)
                .first());

    }

    public Optional<Map<String, Object>> findById(String gatewayAccountId) {
        Map<String, Object> gatewayAccount = jdbi.withHandle(handle -> handle
                .createQuery("SELECT gateway_account_id, payment_provider, credentials FROM gateway_accounts WHERE gateway_account_id=:id")
                .bind("id", Long.valueOf(gatewayAccountId))
                .map(new DefaultMapper())
                .first());

        if (gatewayAccount != null) {
            gatewayAccount = copyAndConvertFieldsToString(gatewayAccount);
        }

        return Optional.ofNullable(gatewayAccount);
    }

    public void saveCredentials(String credentialsJsonString, String gatewayAccountId) {
        jdbi.withHandle(handle -> handle
                .createStatement("UPDATE gateway_accounts SET credentials = :credentials WHERE gateway_account_id = :id")
                .bind("credentials", createPostgresCredentials(credentialsJsonString))
                .bind("id", Long.valueOf(gatewayAccountId))
                .execute()
        );
    }

    private PGobject createPostgresCredentials(String credentialsJsonString) {
        PGobject pgCredentials = new PGobject();
        pgCredentials.setType("json");
        try {
            pgCredentials.setValue(credentialsJsonString);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return pgCredentials;
    }

    private Map<String, Object> copyAndConvertFieldsToString(Map<String, Object> data) {
        Map<String, Object> copy = newHashMap(data);
        PGobject credentialsObject = (PGobject) data.get("credentials");
        copy.put("credentials", credentialsObject.toString());
        return copy;
    }

}
