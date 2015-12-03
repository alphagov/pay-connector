package uk.gov.pay.connector.dao;

import org.postgresql.util.PGobject;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.BooleanMapper;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.mappers.ServiceAccountMapper;
import uk.gov.pay.connector.model.domain.ServiceAccount;

import java.sql.SQLException;
import java.util.Optional;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;

public class GatewayAccountDao {
    private DBI jdbi;

    public GatewayAccountDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public String createGatewayAccount(String paymentProvider) {
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

    public Optional<ServiceAccount> findById(String gatewayAccountId) {
        ServiceAccount serviceAccount = jdbi.withHandle(handle -> handle
                .createQuery("SELECT gateway_account_id, payment_provider, credentials FROM gateway_accounts WHERE gateway_account_id=:id")
                .bind("id", Long.valueOf(gatewayAccountId))
                .map(new ServiceAccountMapper())
                .first());
        return Optional.ofNullable(serviceAccount);
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

}
