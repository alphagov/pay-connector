package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.BooleanMapper;
import org.skife.jdbi.v2.util.StringMapper;

import java.util.Map;
import java.util.Optional;

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
                .createQuery("SELECT gateway_account_id, payment_provider FROM gateway_accounts WHERE gateway_account_id=:id")
                .bind("id", Long.valueOf(gatewayAccountId))
                .map(new DefaultMapper())
                .first());

        return Optional.ofNullable(gatewayAccount);
    }
}
