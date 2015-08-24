package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.LongMapper;
import org.skife.jdbi.v2.util.StringMapper;

import java.util.Map;
import java.util.Optional;

public class GatewayAccountDao {
    private DBI jdbi;

    public GatewayAccountDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public Long insertNameAndReturnNewId(String name) {
        return jdbi.withHandle(handle -> handle
                                .createStatement("INSERT INTO gateway_accounts(name) VALUES (:name)")
                                .bind("name", name)
                                .executeAndReturnGeneratedKeys(LongMapper.FIRST)
                                .first()
        );
    }

    public Optional<String> findNameById(long l) {
        Optional<Map<String, Object>> account = Optional.ofNullable(
                jdbi.withHandle(handle -> handle
                        .createQuery("SELECT name FROM gateway_accounts WHERE account_id=:id")
                        .bind("id", l)
                        .first()));
        return account.map(gatewayAccount -> gatewayAccount
                        .get("name")
                        .toString());
    }
}
