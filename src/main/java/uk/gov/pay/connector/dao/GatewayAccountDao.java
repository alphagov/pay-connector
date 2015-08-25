package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.BooleanMapper;
import org.skife.jdbi.v2.util.LongMapper;

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

    public boolean idIsMissing(Long gatewayAccountId) {
        return jdbi.withHandle(handle -> handle
                .createQuery("SELECT NOT EXISTS(SELECT 1 from gateway_accounts where account_id=:id)")
                .bind("id", gatewayAccountId)
                .map(BooleanMapper.FIRST)
                .first());

    }
}
