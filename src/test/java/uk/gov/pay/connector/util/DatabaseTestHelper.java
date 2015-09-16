package uk.gov.pay.connector.util;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.ChargeStatus;

public class DatabaseTestHelper {
    private DBI jdbi;
    private TokenDao tokenDao;

    public DatabaseTestHelper(DBI jdbi) {
        this.jdbi = jdbi;
        tokenDao = new TokenDao(jdbi);
    }

    public void addGatewayAccount(String accountId, String name) {
        jdbi.withHandle(h ->
                        h.update("INSERT INTO gateway_accounts(gateway_account_id, name) VALUES(?, ?)",
                                Long.valueOf(accountId), name)
        );
    }

    public void addCharge(String chargeId, String gatewayAccountId, long amount, ChargeStatus status, String returnUrl) {
        jdbi.withHandle(h ->
                        h.update("INSERT INTO charges(charge_id, amount, status, gateway_account_id, return_url) VALUES(?, ?, ?, ?, ?)",
                                Long.valueOf(chargeId), amount, status.getValue(), Long.valueOf(gatewayAccountId), returnUrl)
        );
    }

    public String getChargeTokenId(String chargeId) {
        return jdbi.withHandle(h ->
                        h.createQuery("SELECT token_id from tokens WHERE charge_id = :charge_id")
                        .bind("charge_id", Long.valueOf(chargeId))
                        .map(StringMapper.FIRST)
                        .first()
        );
    }

    public void addToken(String chargeId, String tokenId) {
        tokenDao.insertNewToken(chargeId, tokenId);
    }
}
