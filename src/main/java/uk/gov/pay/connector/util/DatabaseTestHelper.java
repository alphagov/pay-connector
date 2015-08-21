package uk.gov.pay.connector.util;

import org.skife.jdbi.v2.DBI;

import java.util.UUID;

public class DatabaseTestHelper {
    private DBI jdbi;

    public DatabaseTestHelper(DBI jdbi) {

        this.jdbi = jdbi;
    }

    public void addGatewayAccount(long accountId, String name) {
        jdbi.withHandle(h ->
                h.update("INSERT INTO gateway_accounts(account_id, name) VALUES(?, ?)", accountId, name)
        );
    }
}
