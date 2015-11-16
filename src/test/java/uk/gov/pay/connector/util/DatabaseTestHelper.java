package uk.gov.pay.connector.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.dao.TokenDao;
import uk.gov.pay.connector.model.domain.ChargeStatus;

public class DatabaseTestHelper {
    private DBI jdbi;
    private TokenDao tokenDao;

    public DatabaseTestHelper(DBI jdbi) {
        this.jdbi = jdbi;
        this.tokenDao = new TokenDao(jdbi);
    }

    public void addGatewayAccount(String accountId, String paymentProvider) {
        jdbi.withHandle(h ->
                        h.update("INSERT INTO gateway_accounts(gateway_account_id, payment_provider) VALUES(?, ?)",
                                Long.valueOf(accountId), paymentProvider)
        );
    }

    public void addCharge(String chargeId, String gatewayAccountId, long amount, ChargeStatus status, String returnUrl, String transactionId) {
        addCharge(chargeId, gatewayAccountId, amount, status, returnUrl, transactionId, "Test description", "Test reference");
    }

    private void addCharge(
            String chargeId,
            String gatewayAccountId,
            long amount,
            ChargeStatus status,
            String returnUrl,
            String transactionId,
            String description,
            String reference
    ) {
        jdbi.withHandle(h ->
                        h.update(
                                "INSERT INTO" +
                                        "    charges(\n" +
                                        "        charge_id,\n" +
                                        "        amount,\n" +
                                        "        status,\n" +
                                        "        gateway_account_id,\n" +
                                        "        return_url,\n" +
                                        "        gateway_transaction_id,\n" +
                                        "        description,\n" +
                                        "        reference\n" +
                                        "    )\n" +
                                        "   VALUES(?, ?, ?, ?, ?, ?, ?, ?)\n",
                                Long.valueOf(chargeId),
                                amount,
                                status.getValue(),
                                Long.valueOf(gatewayAccountId),
                                returnUrl,
                                transactionId,
                                description,
                                reference
                        )
        );
    }

    public String getChargeTokenId(String chargeId) {
        return jdbi.withHandle(h ->
                        h.createQuery("SELECT secure_redirect_token from tokens WHERE charge_id = :charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .map(StringMapper.FIRST)
                                .first()
        );
    }

    public JsonObject getAccountCredentials(String gatewayAccountId) {
        String jsonString = jdbi.withHandle(h ->
                        h.createQuery("SELECT credentials from gateway_accounts WHERE gateway_account_id = :gatewayAccountId")
                                .bind("gatewayAccountId", Integer.parseInt(gatewayAccountId))
                                .map(StringMapper.FIRST)
                                .first()
        );
        return new Gson().fromJson(jsonString, JsonObject.class);
    }

    public void addToken(String chargeId, String tokenId) {
        tokenDao.insertNewToken(chargeId, tokenId);
    }

    public String getChargeStatus(String chargeId) {
        return jdbi.withHandle(h ->
                        h.createQuery("SELECT status from charges WHERE charge_id = :charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .map(StringMapper.FIRST)
                                .first()
        );
    }
}
