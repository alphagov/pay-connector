package uk.gov.pay.connector.util;

import com.google.gson.Gson;
import org.apache.commons.lang3.RandomUtils;
import org.postgresql.util.PGobject;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Map;

import static java.time.ZonedDateTime.now;

public class DatabaseTestHelper {

    private DBI jdbi;

    public DatabaseTestHelper(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public void addGatewayAccount(String accountId, String paymentGateway, Map<String, String> credentials) {
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            if (credentials == null || credentials.size() == 0) {
                jsonObject.setValue("{}");
            } else {
                jsonObject.setValue(new Gson().toJson(credentials));
            }
            jdbi.withHandle(h ->
                    h.update("INSERT INTO gateway_accounts(id, payment_provider, credentials) VALUES(?, ?, ?)",
                            Long.valueOf(accountId), paymentGateway, jsonObject)
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addGatewayAccount(String accountId, String paymentProvider) {
        addGatewayAccount(accountId, paymentProvider, null);
    }

    public void addCharge(Long chargeId, String externalChargeId, String gatewayAccountId, long amount, ChargeStatus status, String returnUrl, String transactionId) {
        addCharge(chargeId, externalChargeId, gatewayAccountId, amount, status, returnUrl, transactionId, "Test description", "Test reference", now(), 1);
    }

    public void addCharge(String externalChargeId, String gatewayAccountId, long amount, ChargeStatus status, String returnUrl, String transactionId) {
        addCharge((long) RandomUtils.nextInt(1, 9999999), externalChargeId, gatewayAccountId, amount, status, returnUrl, transactionId, "Test description", "Test reference", now(), 1);
    }

    public void addCharge(Long chargeId, String externalChargeId, String accountId, long amount, ChargeStatus chargeStatus, String returnUrl, String transactionId, String reference, ZonedDateTime createdDate) {
        addCharge(chargeId, externalChargeId, accountId, amount, chargeStatus, returnUrl, transactionId, "Test description", reference, createdDate == null ? now() : createdDate, 1);
    }

    private void addCharge(
            Long chargeId,
            String externalChargeId,
            String gatewayAccountId,
            long amount,
            ChargeStatus status,
            String returnUrl,
            String transactionId,
            String description,
            String reference,
            ZonedDateTime createdDate,
            long version
    ) {
        jdbi.withHandle(h ->
                h.update(
                        "INSERT INTO" +
                                "    charges(\n" +
                                "        id,\n" +
                                "        external_id,\n" +
                                "        amount,\n" +
                                "        status,\n" +
                                "        gateway_account_id,\n" +
                                "        return_url,\n" +
                                "        gateway_transaction_id,\n" +
                                "        description,\n" +
                                "        created_date,\n" +
                                "        reference,\n" +
                                "        version\n" +
                                "    )\n" +
                                "   VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n",
                        chargeId,
                        externalChargeId,
                        amount,
                        status.getValue(),
                        Long.valueOf(gatewayAccountId),
                        returnUrl,
                        transactionId,
                        description,
                        Timestamp.from(createdDate.toInstant()),
                        reference,
                        version
                )
        );
    }

    public String getChargeTokenId(Long chargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT secure_redirect_token from tokens WHERE charge_id = :charge_id")
                        .bind("charge_id", chargeId)
                        .map(StringMapper.FIRST)
                        .first()
        );
    }

    public String getChargeTokenByExternalChargeId(String externalChargeId) {

        String chargeId = jdbi.withHandle(h ->
                h.createQuery("SELECT id from charges WHERE external_id = :external_id")
                        .bind("external_id", externalChargeId)
                        .map(StringMapper.FIRST)
                        .first()
        );

        return getChargeTokenId(Long.valueOf(chargeId));
    }

    public Map<String, String> getAccountCredentials(Long gatewayAccountId) {

        String jsonString = jdbi.withHandle(h ->
                h.createQuery("SELECT credentials from gateway_accounts WHERE id = :gatewayAccountId")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .map(StringMapper.FIRST)
                        .first()
        );
        return new Gson().fromJson(jsonString, Map.class);
    }

    public void addToken(Long chargeId, String tokenId) {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("INSERT INTO tokens(charge_id, secure_redirect_token) VALUES (:charge_id, :secure_redirect_token)")
                        .bind("charge_id", chargeId)
                        .bind("secure_redirect_token", tokenId)
                        .execute()
        );
    }

    public String getChargeStatus(Long chargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT status from charges WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .map(StringMapper.FIRST)
                        .first()
        );
    }

    public void updateCredentialsFor(String accountId, String credentials) {
        try {
            PGobject pgCredentials = new PGobject();
            pgCredentials.setType("json");
            pgCredentials.setValue(credentials);
            jdbi.withHandle(handle ->
                    handle.createStatement("UPDATE gateway_accounts set credentials=:credentials WHERE id=:gatewayAccountId")
                            .bind("gatewayAccountId", Integer.parseInt(accountId))
                            .bind("credentials", pgCredentials)
                            .execute()
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addEvent(Long chargeId, String chargeStatus) {
        jdbi.withHandle(
                h -> h.update("INSERT INTO charge_events(charge_id,status) values(?,?)",
                        chargeId, chargeStatus)
        );
    }
}
