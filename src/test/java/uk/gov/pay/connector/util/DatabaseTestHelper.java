package uk.gov.pay.connector.util;

import com.google.gson.Gson;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.util.PGobject;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.StringColumnMapper;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.ZonedDateTime.now;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;

public class DatabaseTestHelper {

    private DBI jdbi;

    public DatabaseTestHelper(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public void addGatewayAccount(String accountId,
                                  String paymentGateway,
                                  Map<String, String> credentials,
                                  String serviceName,
                                  GatewayAccountEntity.Type providerUrlType,
                                  String description,
                                  String analyticsId,
                                  EmailCollectionMode emailCollectionMode,
                                  long corporateCreditCardSurchargeAmount,
                                  long corporateDebitCardSurchargeAmount,
                                  long corporatePrepaidCreditCardSurchargeAmount,
                                  long corporatePrepaidDebitCardSurchargeAmount) {
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            if (credentials == null || credentials.size() == 0) {
                jsonObject.setValue("{}");
            } else {
                jsonObject.setValue(new Gson().toJson(credentials));
            }
            jdbi.withHandle(h ->
                    h.update("INSERT INTO gateway_accounts (id,\n" +
                                    "                              payment_provider,\n" +
                                    "                              credentials,\n" +
                                    "                              service_name,\n" +
                                    "                              type,\n" +
                                    "                              description,\n" +
                                    "                              analytics_id,\n" +
                                    "                              email_collection_mode,\n" +
                                    "                              corporate_credit_card_surcharge_amount,\n" +
                                    "                              corporate_debit_card_surcharge_amount,\n" +
                                    "                              corporate_prepaid_credit_card_surcharge_amount,\n" +
                                    "                              corporate_prepaid_debit_card_surcharge_amount)\n" +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            Long.valueOf(accountId),
                            paymentGateway,
                            jsonObject,
                            serviceName,
                            providerUrlType,
                            description,
                            analyticsId,
                            emailCollectionMode,
                            corporateCreditCardSurchargeAmount,
                            corporateDebitCardSurchargeAmount,
                            corporatePrepaidCreditCardSurchargeAmount,
                            corporatePrepaidDebitCardSurchargeAmount)
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addGatewayAccount(String accountId, String paymentProvider, Map<String, String> credentials) {
        addGatewayAccount(accountId, paymentProvider, credentials, null, TEST, null, null, EmailCollectionMode.MANDATORY, 0, 0, 0, 0);
    }

    public void addGatewayAccount(String accountId, String paymentProvider, Map<String, String> credentials, GatewayAccountEntity.Type gatewayAccountType) {
        addGatewayAccount(accountId, paymentProvider, credentials, null, gatewayAccountType, null, null, EmailCollectionMode.MANDATORY, 0, 0, 0, 0);
    }

    public void addGatewayAccount(long accountId, String paymentProvider) {
        addGatewayAccount(String.valueOf(accountId), paymentProvider);
    }

    public void addGatewayAccount(String accountId, String paymentProvider) {
        addGatewayAccount(accountId, paymentProvider, null, "a cool service", TEST, null, null, EmailCollectionMode.MANDATORY, 0, 0, 0, 0);
    }

    public void addGatewayAccount(String accountId, String paymentProvider, String description, String analyticsId, long corporateCreditCardSurchargeAmount, long corporateDebitCardSurchargeAmount, long corporatePrepaidCreditCardSurchargeAmount, long corporatePrepaidDebitCardSurchargeAmount) {
        addGatewayAccount(accountId, paymentProvider, null, "a cool service", TEST, description, analyticsId, EmailCollectionMode.MANDATORY, corporateCreditCardSurchargeAmount, corporateDebitCardSurchargeAmount, corporatePrepaidCreditCardSurchargeAmount, corporatePrepaidDebitCardSurchargeAmount);
    }

    public void addGatewayAccount(String accountId, String paymentProvider, Map<String, String> credentials, String serviceName, GatewayAccountEntity.Type type, String description, String analyticsId, long corporateCreditCardSurchargeAmount, long corporateDebitCardSurchargeAmount, long corporatePrepaidCreditCardSurchargeAmount, long corporatePrepaidDebitCardSurchargeAmount) {
        addGatewayAccount(accountId, paymentProvider, credentials, serviceName, type, description, analyticsId, EmailCollectionMode.MANDATORY, corporateCreditCardSurchargeAmount, corporateDebitCardSurchargeAmount, corporatePrepaidCreditCardSurchargeAmount, corporatePrepaidDebitCardSurchargeAmount);
    }

    public void addCharge(Long chargeId, String externalChargeId, String gatewayAccountId, long amount, ChargeStatus status, String returnUrl,
                          String transactionId) {
        addCharge(chargeId, externalChargeId, gatewayAccountId, amount, status, returnUrl, transactionId, "Test description",
                ServicePaymentReference.of("Test reference"), now(), 1, "email@fake.test", SupportedLanguage.ENGLISH, false);
    }

    public void addCharge(Long chargeId, String externalChargeId, String gatewayAccountId, long amount, ChargeStatus status, String returnUrl,
                          String transactionId, String description) {
        addCharge(chargeId, externalChargeId, gatewayAccountId, amount, status, returnUrl, transactionId, description,
                ServicePaymentReference.of("Test reference"), now(), 1, "email@fake.test", SupportedLanguage.ENGLISH, false);
    }

    public void addCharge(String externalChargeId, String gatewayAccountId, long amount, ChargeStatus status, String returnUrl, String transactionId) {
        addCharge((long) RandomUtils.nextInt(), externalChargeId, gatewayAccountId, amount, status, returnUrl, transactionId,
                "Test description", ServicePaymentReference.of("Test reference"), now(), 1, null, SupportedLanguage.ENGLISH, false);
    }

    public void addCharge(Long chargeId, String externalChargeId, String accountId, long amount, ChargeStatus chargeStatus, String returnUrl,
                          String transactionId, ServicePaymentReference reference, ZonedDateTime createdDate) {
        addCharge(chargeId, externalChargeId, accountId, amount, chargeStatus, returnUrl, transactionId, "Test description", reference,
                createdDate == null ? now() : createdDate, 1, null, SupportedLanguage.ENGLISH, false);
    }

    public void addCharge(Long chargeId, String externalChargeId, String accountId, long amount, ChargeStatus chargeStatus, String returnUrl,
                          String transactionId, ServicePaymentReference reference, ZonedDateTime createdDate, SupportedLanguage
                                  language,
                          boolean delayedCapture) {
        addCharge(chargeId, externalChargeId, accountId, amount, chargeStatus, returnUrl, transactionId, "Test description", reference,
                createdDate == null ? now() : createdDate, 1, null, language, delayedCapture);
    }

    public void addCharge(Long chargeId, String externalChargeId, String accountId, long amount, ChargeStatus chargeStatus, String returnUrl,
                          String transactionId, ServicePaymentReference reference, ZonedDateTime createdDate, String email) {
        addCharge(chargeId, externalChargeId, accountId, amount, chargeStatus, returnUrl, transactionId, "Test description", reference,
                createdDate == null ? now() : createdDate, 1, email, SupportedLanguage.ENGLISH, false);
    }

    public void addCharge(Long chargeId, String externalChargeId, String accountId, long amount, ChargeStatus chargeStatus, String returnUrl,
                          String transactionId, ServicePaymentReference reference, String description, ZonedDateTime createdDate, String email) {
        addCharge(chargeId, externalChargeId, accountId, amount, chargeStatus, returnUrl, transactionId, description, reference,
                createdDate == null ? now() : createdDate, 1, email, SupportedLanguage.ENGLISH, false);
    }

    public void addCharge(Long chargeId, String externalChargeId, String accountId, long amount, ChargeStatus chargeStatus, String returnUrl,
                          String transactionId, ServicePaymentReference reference, String description, ZonedDateTime createdDate, String email,
                          SupportedLanguage language, Long corporateCardSurcharge) {
        addCharge(chargeId, externalChargeId, accountId, amount, chargeStatus, returnUrl, transactionId, description, reference,
                createdDate == null ? now() : createdDate, 1, email, language, false, corporateCardSurcharge);
    }

    public void addCharge(Long chargeId, String externalChargeId, String accountId, long amount, ChargeStatus chargeStatus, String returnUrl,
                          String transactionId, ServicePaymentReference reference, ZonedDateTime createdDate, String email, boolean delayedCapture) {
        addCharge(chargeId, externalChargeId, accountId, amount, chargeStatus, returnUrl, transactionId, "Test description", reference,
                createdDate == null ? now() : createdDate, 1, email, SupportedLanguage.ENGLISH, delayedCapture);
    }

    public void addChargeWithCorporateCardSurcharge(Long chargeId, String externalChargeId, String accountId, long amount,
                                                    ChargeStatus chargeStatus, String returnUrl, String transactionId,
                                                    ServicePaymentReference reference, ZonedDateTime createdDate,
                                                    SupportedLanguage language, boolean delayedCapture, Long corporateCardSurcharge) {
        addCharge(chargeId, externalChargeId, accountId, amount, chargeStatus, returnUrl, transactionId,
                "Test description", reference, createdDate == null ? now() : createdDate, 1, null,
                language, delayedCapture, corporateCardSurcharge);
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
            ServicePaymentReference reference,
            ZonedDateTime createdDate,
            long version,
            String email,
            SupportedLanguage language,
            boolean delayedCapture
    ) {
        addCharge(chargeId, externalChargeId, gatewayAccountId, amount, status, returnUrl, transactionId, description, reference,
                createdDate, version, email, language, delayedCapture, null);
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
            ServicePaymentReference reference,
            ZonedDateTime createdDate,
            long version,
            String email,
            SupportedLanguage language,
            boolean delayedCapture,
            Long corporateSurcharge
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
                                "        version,\n" +
                                "        email,\n" +
                                "        language,\n" +
                                "        delayed_capture,\n" +
                                "        corporate_surcharge\n" +
                                "    )\n" +
                                "   VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n",
                        chargeId,
                        externalChargeId,
                        amount,
                        status.getValue(),
                        Long.valueOf(gatewayAccountId),
                        returnUrl,
                        transactionId,
                        description,
                        Timestamp.from(createdDate.toInstant()),
                        reference.toString(),
                        version,
                        email,
                        language.toString(),
                        delayedCapture,
                        corporateSurcharge
                )
        );
    }

    public void deleteAllChargesOnAccount(long accountId) {
        jdbi.withHandle(handle ->
                handle.createStatement("DELETE FROM charges where gateway_account_id = :accountId")
                        .bind("accountId", accountId).execute());
    }

    public int addRefund(String externalId, String reference, long amount, RefundStatus status, Long chargeId, ZonedDateTime createdDate) {
        return addRefund(externalId, reference, amount, status, chargeId, createdDate, null);
    }

    public int addRefund(String externalId, String reference, long amount, RefundStatus status, Long chargeId, ZonedDateTime createdDate, String submittedByUserExternalId) {
        int refundId = RandomUtils.nextInt();
        jdbi.withHandle(handle ->
                handle
                        .createStatement("INSERT INTO refunds(id, external_id, reference, amount, status, charge_id, created_date, user_external_id) VALUES (:id, :external_id, :reference, :amount, :status, :charge_id, :created_date, :user_external_id)")
                        .bind("id", refundId)
                        .bind("external_id", externalId)
                        .bind("reference", reference)
                        .bind("amount", amount)
                        .bind("status", status.getValue())
                        .bind("charge_id", chargeId)
                        .bind("created_date", Timestamp.from(createdDate.toInstant()))
                        .bind("user_external_id", submittedByUserExternalId)
                        .bind("version", 1)
                        .execute()
        );
        return refundId;
    }

    public void addRefundHistory(long id, String externalId, String reference, long amount, String status, Long chargeId, ZonedDateTime createdDate, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate, String submittedByUserExternalId) {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("INSERT INTO refunds_history(id, external_id, reference, amount, status, charge_id, created_date, history_start_date, history_end_date, user_external_id) VALUES (:id, :external_id, :reference, :amount, :status, :charge_id, :created_date, :history_start_date, :history_end_date, :user_external_id)")
                        .bind("id", id)
                        .bind("external_id", externalId)
                        .bind("reference", reference)
                        .bind("amount", amount)
                        .bind("status", status)
                        .bind("charge_id", chargeId)
                        .bind("created_date", Timestamp.from(createdDate.toInstant()))
                        .bind("history_start_date", Timestamp.from(historyStartDate.toInstant()))
                        .bind("history_end_date", Timestamp.from(historyEndDate.toInstant()))
                        .bind("user_external_id", submittedByUserExternalId)
                        .bind("version", 1)
                        .execute()
        );
    }

    public void addRefundHistory(long id, String externalId, String reference, long amount, String status, Long chargeId, ZonedDateTime createdDate, ZonedDateTime historyStartDate, String submittedByUserExternalId) {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("INSERT INTO refunds_history(id, external_id, reference, amount, status, charge_id, created_date, history_start_date, user_external_id) VALUES (:id, :external_id, :reference, :amount, :status, :charge_id, :created_date, :history_start_date, :user_external_id)")
                        .bind("id", id)
                        .bind("external_id", externalId)
                        .bind("reference", reference)
                        .bind("amount", amount)
                        .bind("status", status)
                        .bind("charge_id", chargeId)
                        .bind("created_date", Timestamp.from(createdDate.toInstant()))
                        .bind("history_start_date", Timestamp.from(historyStartDate.toInstant()))
                        .bind("user_external_id", submittedByUserExternalId)
                        .bind("version", 1)
                        .execute()
        );
    }

    public void updateChargeCardDetails(Long chargeId, String cardBrand, String lastDigitsCardNumber, String firstDigitsCardNumber, String cardHolderName, String expiryDate,
                                        String line1, String line2, String postcode, String city, String county, String country) {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("UPDATE charges SET card_brand=:card_brand, last_digits_card_number=:last_digits_card_number, first_digits_card_number=:first_digits_card_number, cardholder_name=:cardholder_name, expiry_date=:expiry_date, address_line1=:address_line1, address_line2=:address_line2, address_postcode=:address_postcode, address_city=:address_city, address_county=:address_county, address_country=:address_country WHERE id=:id")
                        .bind("id", chargeId)
                        .bind("card_brand", cardBrand)
                        .bind("last_digits_card_number", lastDigitsCardNumber)
                        .bind("first_digits_card_number", firstDigitsCardNumber)
                        .bind("cardholder_name", cardHolderName)
                        .bind("expiry_date", expiryDate)
                        .bind("address_line1", line1)
                        .bind("address_line2", line2)
                        .bind("address_postcode", postcode)
                        .bind("address_city", city)
                        .bind("address_county", county)
                        .bind("address_country", country)
                        .execute()
        );
    }

    public void updateCorporateSurcharge(Long chargeId, Long corporateSurcharge) {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("UPDATE charges SET corporate_surcharge =:corporate_surcharge WHERE id=:id")
                        .bind("id", chargeId)
                        .bind("corporate_surcharge", corporateSurcharge)
                        .execute());
    }

    public void updateCharge3dsDetails(Long chargeId, String issuerUrl, String paRequest, String htmlOut) {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("UPDATE charges SET pa_request_3ds=:pa_request_3ds, issuer_url_3ds=:issuer_url_3ds, html_out_3ds=:html_out_3ds WHERE id=:id")
                        .bind("id", chargeId)
                        .bind("pa_request_3ds", paRequest)
                        .bind("issuer_url_3ds", issuerUrl)
                        .bind("html_out_3ds", htmlOut)
                        .execute()
        );
    }

    public String getChargeTokenId(Long chargeId) {

        return jdbi.withHandle(h ->
                h.createQuery("SELECT secure_redirect_token from tokens WHERE charge_id = :charge_id ORDER BY id DESC")
                        .bind("charge_id", chargeId)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );
    }

    public List<Map<String, Object>> getRefund(long refundId) {
        List<Map<String, Object>> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT external_id, reference, amount, status, created_date, charge_id, user_external_id " +
                        "FROM refunds " +
                        "WHERE id = :refund_id")
                        .bind("refund_id", refundId)
                        .list());
        return ret;
    }

    public List<Map<String, Object>> getRefundsByChargeId(long chargeId) {
        List<Map<String, Object>> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT external_id, reference, amount, status, created_date, charge_id, user_external_id " +
                        "FROM refunds r " +
                        "WHERE charge_id = :charge_id")
                        .bind("charge_id", chargeId)
                        .list());
        return ret;
    }

    public Map<String, Object> getChargeCardDetailsByChargeId(Long chargeId) {
        Map<String, Object> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT id, card_brand, last_digits_card_number, first_digits_card_number, cardholder_name, expiry_date, address_line1, address_line2, address_postcode, address_city, address_county, address_country " +
                        "FROM charges " +
                        "WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .first());
        return ret;
    }

    public void updateChargeCardDetails(Long chargeId, AuthCardDetails authCardDetails) {

        updateChargeCardDetails(chargeId,
                authCardDetails.getCardBrand(),
                StringUtils.right(authCardDetails.getCardNo(), 4),
                StringUtils.left(authCardDetails.getCardNo(), 6),
                authCardDetails.getCardHolder(),
                authCardDetails.getEndDate(),
                authCardDetails.getAddress().map(Address::getLine1).orElse(null),
                authCardDetails.getAddress().map(Address::getLine2).orElse(null),
                authCardDetails.getAddress().map(Address::getPostcode).orElse(null),
                authCardDetails.getAddress().map(Address::getCity).orElse(null),
                authCardDetails.getAddress().map(Address::getCounty).orElse(null),
                authCardDetails.getAddress().map(Address::getCountry).orElse(null));
    }

    public Map<String, Object> getChargeCardDetails(long chargeId) {
        Map<String, Object> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT id, last_digits_card_number, first_digits_card_number, cardholder_name, expiry_date, address_line1, address_line2, address_postcode, address_city, address_county, address_country " +
                        "FROM charges " +
                        "WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .first());
        return ret;
    }

    public Map<String, Object> getEmailForAccountAndType(Long accountId, EmailNotificationType type) {

        return jdbi.withHandle(h ->
                h.createQuery("SELECT template_body, enabled from email_notifications WHERE account_id = :account_id AND type = :type")
                        .bind("account_id", accountId)
                        .bind("type", type)
                        .first()
        );
    }

    public String getChargeTokenByExternalChargeId(String externalChargeId) {

        String chargeId = jdbi.withHandle(h ->
                h.createQuery("SELECT id from charges WHERE external_id = :external_id")
                        .bind("external_id", externalChargeId)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );

        return getChargeTokenId(Long.valueOf(chargeId));
    }

    public Map<String, String> getAccountCredentials(Long gatewayAccountId) {

        String jsonString = jdbi.withHandle(h ->
                h.createQuery("SELECT credentials from gateway_accounts WHERE id = :gatewayAccountId")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );
        return new Gson().fromJson(jsonString, Map.class);
    }

    public String getAccountServiceName(Long gatewayAccountId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT service_name from gateway_accounts WHERE id = :gatewayAccountId")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );
    }

    public List<Map<String, Object>> getAcceptedCardTypesByAccountId(Long gatewayAccountId) {

        List<Map<String, Object>> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT ct.id, ct.label, ct.type, ct.brand, ct.version " +
                        "FROM card_types ct " +
                        "LEFT JOIN accepted_card_types act ON ct.id = act.card_type_id " +
                        "WHERE act.gateway_account_id = :gatewayAccountId")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .list());
        return ret;
    }

    public List<Map<String, Object>> getChargeEvents(long chargeId) {
        List<Map<String, Object>> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT ce.id, ce.charge_id, ce.status, ce.updated " +
                        "FROM charge_events ce " +
                        "WHERE ce.charge_id = :chargeId")
                        .bind("chargeId", chargeId)
                        .list());
        return ret;
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

    public void addEmailNotification(Long accountId, String templateBody, boolean enabled, EmailNotificationType type) {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("INSERT INTO email_notifications(account_id, template_body, enabled, type) VALUES (:account_id, :templateBody, :enabled, :type)")
                        .bind("account_id", accountId)
                        .bind("templateBody", templateBody)
                        .bind("enabled", enabled)
                        .bind("type", type)
                        .execute()
        );
    }

    public void deleteAllCardTypes() {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("DELETE FROM accepted_card_types; DELETE FROM card_types;")
                        .execute()
        );
    }

    public void addAcceptedCardType(long accountId, UUID cardTypeId) {
        jdbi.withHandle(handle ->
                handle
                        .createStatement("INSERT INTO accepted_card_types(gateway_account_id, card_type_id) VALUES (:accountId, :cardTypeId)")
                        .bind("accountId", accountId)
                        .bind("cardTypeId", cardTypeId)
                        .execute()
        );
    }

    public String getChargeStatus(Long chargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT status from charges WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );
    }

    public String getChargeCardBrand(Long chargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT card_brand from charges WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );
    }

    public void updateCredentialsFor(long accountId, String credentials) {
        try {
            PGobject pgCredentials = new PGobject();
            pgCredentials.setType("json");
            pgCredentials.setValue(credentials);
            jdbi.withHandle(handle ->
                    handle.createStatement("UPDATE gateway_accounts set credentials=:credentials WHERE id=:gatewayAccountId")
                            .bind("gatewayAccountId", accountId)
                            .bind("credentials", pgCredentials)
                            .execute()
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateServiceNameFor(long accountId, String serviceName) {
        jdbi.withHandle(handle ->
                handle.createStatement("UPDATE gateway_accounts set service_name=:serviceName WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .bind("serviceName", serviceName)
                        .execute()
        );
    }

    public void updateCorporateCreditCardSurchargeAmountFor(long accountId, long corporateCreditCardSurchargeAmount) {
        jdbi.withHandle(handle ->
                handle.createStatement("UPDATE gateway_accounts set corporate_credit_card_surcharge_amount=:corporateCreditCardSurchargeAmount WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .bind("corporateCreditCardSurchargeAmount", corporateCreditCardSurchargeAmount)
                        .execute()
        );
    }

    public void updateCorporateDebitCardSurchargeAmountFor(long accountId, long corporateDebitCardSurchargeAmount) {
        jdbi.withHandle(handle ->
                handle.createStatement("UPDATE gateway_accounts set corporate_debit_card_surcharge_amount=:corporateDebitCardSurchargeAmount WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .bind("corporateDebitCardSurchargeAmount", corporateDebitCardSurchargeAmount)
                        .execute()
        );
    }

    public void enable3dsForGatewayAccount(long accountId) {
        jdbi.withHandle(handle ->
                handle.createStatement("UPDATE gateway_accounts set requires_3ds=true WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .execute()
        );
    }

    public void addNotificationCredentialsFor(long accountId, String username, String password) {
        jdbi.withHandle(handle ->
                handle.createStatement("INSERT INTO notification_credentials(account_id, username, password, version) VALUES (:accountId, :username, :password, 1)")
                        .bind("accountId", accountId)
                        .bind("username", username)
                        .bind("password", password)
                        .execute()
        );
    }

    public void addEvent(Long chargeId, String chargeStatus) {
        addEvent(chargeId, chargeStatus, ZonedDateTime.now());
    }

    public void addEvent(Long chargeId, String chargeStatus, ZonedDateTime updated) {
        jdbi.withHandle(
                h -> h.update("INSERT INTO charge_events(charge_id,status,updated) values(?,?,?)",
                        chargeId, chargeStatus, Timestamp.from(updated.toInstant()))
        );
    }

    public List<String> getInternalEvents(String externalChargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT status from charge_events WHERE charge_id = (SELECT id from charges WHERE external_id=:external_id)")
                        .bind("external_id", externalChargeId)
                        .map(StringColumnMapper.INSTANCE)
                        .list()
        );
    }

    public String getCardTypeId(String brand, String type) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT id from card_types WHERE brand = :brand AND type = :type")
                        .bind("brand", brand)
                        .bind("type", type)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );
    }

    public CardTypeEntity getCardTypeByBrandAndType(String brand, CardTypeEntity.SupportedType type) {
        return getCardTypeByBrandAndType(brand, type.toString());
    }

    public CardTypeEntity getMastercardCreditCard() {
        return getCardTypeByBrandAndType("master-card", CardTypeEntity.SupportedType.CREDIT);
    }

    public CardTypeEntity getMastercardDebitCard() {
        return getCardTypeByBrandAndType("master-card", CardTypeEntity.SupportedType.DEBIT);
    }

    public CardTypeEntity getVisaCreditCard() {
        return getCardTypeByBrandAndType("visa", CardTypeEntity.SupportedType.CREDIT);
    }

    public CardTypeEntity getVisaDebitCard() {
        return getCardTypeByBrandAndType("visa", CardTypeEntity.SupportedType.DEBIT);
    }

    public CardTypeEntity getMaestroCard() {
        return getCardTypeByBrandAndType("maestro", CardTypeEntity.SupportedType.DEBIT);
    }

    public CardTypeEntity getCardTypeByBrandAndType(String brand, String type) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT id, brand, type, label, requires_3ds from card_types WHERE brand = :brand AND type = :type")
                        .bind("brand", brand)
                        .bind("type", type)
                        .map(CardTypeEntity.class)
                        .first()
        );
    }

    public List<Map<String, Object>> getRefundsHistoryByChargeId(Long chargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT status FROM refunds_history WHERE charge_id = :chargeId order by history_start_date desc")
                        .bind("chargeId", chargeId).list()
        );
    }

    public Map<String, String> getNotifySettings(Long gatewayAccountId) {

        String jsonString = jdbi.withHandle(h ->
                h.createQuery("SELECT notify_settings from gateway_accounts WHERE id = :gatewayAccountId")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );
        return new Gson().fromJson(jsonString, Map.class);
    }

    public void truncateAllData() {
        jdbi.withHandle(h -> h.createStatement("TRUNCATE TABLE gateway_accounts CASCADE").execute());
    }

    public Long getChargeIdByExternalId(String externalChargeId) {

        String chargeId = jdbi.withHandle(h ->
                h.createQuery("SELECT id from charges WHERE external_id = :external_id")
                        .bind("external_id", externalChargeId)
                        .map(StringColumnMapper.INSTANCE)
                        .first()
        );

        return (Long.valueOf(chargeId));
    }
}
