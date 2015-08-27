package uk.gov.pay.connector.util;

import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.model.ChargeStatus;

public class DatabaseTestHelper {
    private DBI jdbi;

    public DatabaseTestHelper(DBI jdbi) {

        this.jdbi = jdbi;
    }

    public void addGatewayAccount(String accountId, String name) {
        jdbi.withHandle(h ->
                        h.update("INSERT INTO gateway_accounts(account_id, name) VALUES(?, ?)",
                                Long.valueOf(accountId), name)
        );
    }

    public void addCharge(String chargeId, String gatewayAccountId, long amount, ChargeStatus status) {
        jdbi.withHandle(h ->
                        h.update("INSERT INTO charges(charge_id, amount, status, gateway_account_id) VALUES(?, ?, ?, ?)",
                                Long.valueOf(chargeId), amount, status.getValue(), Long.valueOf(gatewayAccountId))
        );
    }
}
