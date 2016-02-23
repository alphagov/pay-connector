package uk.gov.pay.connector.dao;

import org.postgresql.util.PGobject;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.BooleanMapper;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.mappers.GatewayAccountMapper;
import uk.gov.pay.connector.model.domain.GatewayAccount;

import java.sql.SQLException;
import java.util.Optional;

@Deprecated
public class GatewayAccountDao implements IGatewayAccountDao {

    private DBI jdbi;

    public GatewayAccountDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public String createGatewayAccount(String paymentProvider) {
        return jdbi.withHandle(handle -> handle
                .createStatement("INSERT INTO gateway_accounts(payment_provider) VALUES (:paymentProvider)")
                .bind("paymentProvider", paymentProvider)
                .executeAndReturnGeneratedKeys(StringMapper.FIRST)
                .first()
        );
    }

    @Override
    public boolean idIsMissing(String gatewayAccountId) {
        return jdbi.withHandle(handle -> handle
                .createQuery("SELECT NOT EXISTS(SELECT 1 from gateway_accounts where id=:id)")
                .bind("id", Long.valueOf(gatewayAccountId))
                .map(BooleanMapper.FIRST)
                .first());

    }

    @Override
    public Optional<GatewayAccount> findById(String gatewayAccountId) {
        GatewayAccount gatewayAccount = jdbi.withHandle(handle -> handle
                .createQuery("SELECT id, payment_provider, credentials FROM gateway_accounts WHERE id=:id")
                .bind("id", Long.valueOf(gatewayAccountId))
                .map(new GatewayAccountMapper())
                .first());
        return Optional.ofNullable(gatewayAccount);
    }

    @Override
    public void saveCredentials(String credentialsJsonString, String gatewayAccountId) {
        jdbi.withHandle(handle -> handle
                .createStatement("UPDATE gateway_accounts SET credentials = :credentials WHERE id=:id")
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
