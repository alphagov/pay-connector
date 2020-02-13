package uk.gov.pay.connector.util;

import com.google.gson.Gson;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.Jdbi;
import org.postgresql.util.PGobject;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.exception.ExternalMetadataConverterException;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.wallets.WalletType;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.sql.Types.OTHER;
import static java.time.ZonedDateTime.now;

public class DatabaseTestHelper {

    private Jdbi jdbi;

    public DatabaseTestHelper(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public void addGatewayAccount(AddGatewayAccountParams params) {
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            if (params.getCredentials() == null || params.getCredentials().size() == 0) {
                jsonObject.setValue("{}");
            } else {
                jsonObject.setValue(new Gson().toJson(params.getCredentials()));
            }
            jdbi.withHandle(h ->
                    h.createUpdate("INSERT INTO gateway_accounts (id, payment_provider, credentials, " +
                            "service_name, type, description, analytics_id, email_collection_mode, " +
                            "integration_version_3ds, corporate_credit_card_surcharge_amount, " +
                            "corporate_debit_card_surcharge_amount, corporate_prepaid_credit_card_surcharge_amount, " +
                            "corporate_prepaid_debit_card_surcharge_amount, allow_moto, allow_apple_pay, " +
                            "allow_google_pay, requires_3ds) " +
                            "VALUES (:id, :payment_provider, :credentials, :service_name, :type, " +
                            ":description, :analytics_id, :email_collection_mode, :integration_version_3ds, " +
                            ":corporate_credit_card_surcharge_amount, :corporate_debit_card_surcharge_amount, " +
                            ":corporate_prepaid_credit_card_surcharge_amount, " +
                            ":corporate_prepaid_debit_card_surcharge_amount, :allow_moto, :allow_apple_pay, " +
                            ":allow_google_pay, :requires_3ds)")
                            .bind("id", Long.valueOf(params.getAccountId()))
                            .bind("payment_provider", params.getPaymentGateway())
                            .bindBySqlType("credentials", jsonObject, OTHER)
                            .bind("service_name", params.getServiceName())
                            .bind("type", params.getType())
                            .bind("description", params.getDescription())
                            .bind("analytics_id", params.getAnalyticsId())
                            .bind("email_collection_mode", params.getEmailCollectionMode())
                            .bind("integration_version_3ds", params.getIntegrationVersion3ds())
                            .bind("corporate_credit_card_surcharge_amount", params.getCorporateCreditCardSurchargeAmount())
                            .bind("corporate_debit_card_surcharge_amount", params.getCorporateDebitCardSurchargeAmount())
                            .bind("corporate_prepaid_credit_card_surcharge_amount", params.getCorporatePrepaidCreditCardSurchargeAmount())
                            .bind("corporate_prepaid_debit_card_surcharge_amount", params.getCorporatePrepaidDebitCardSurchargeAmount())
                            .bind("allow_moto", params.isAllowMoto())
                            .bind("allow_apple_pay", params.isAllowApplePay())
                            .bind("allow_google_pay", params.isAllowGooglePay())
                            .bind("requires_3ds", params.isRequires3ds())
                            .execute());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addCharge(AddChargeParams addChargeParams) {
        PGobject jsonMetadata = new PGobject();
        jsonMetadata.setType("json");
        try {
            if (addChargeParams.getExternalMetadata() != null &&
                    !addChargeParams.getExternalMetadata().getMetadata().isEmpty()) {
                jsonMetadata.setValue(new Gson().toJson(addChargeParams.getExternalMetadata().getMetadata()));
            }
        } catch (SQLException e) {
            throw new ExternalMetadataConverterException("Failed to persist external metadata");
        }
        jdbi.withHandle(h ->
                h.createUpdate("INSERT INTO charges(id, external_id, amount, " +
                        "status, gateway_account_id, return_url, gateway_transaction_id, " +
                        "description, created_date, reference, version, email, language, " +
                        "delayed_capture, corporate_surcharge, parity_check_status, parity_check_date, " +
                        "external_metadata, card_type) " +
                        "VALUES(:id, :external_id, :amount, " +
                        ":status, :gateway_account_id, :return_url, :gateway_transaction_id, " +
                        ":description, :created_date, :reference, :version, :email, :language, " +
                        ":delayed_capture, :corporate_surcharge, :parity_check_status, :parity_check_date, " +
                        ":external_metadata, :card_type)")
                        .bind("id", addChargeParams.getChargeId())
                        .bind("external_id", addChargeParams.getExternalChargeId())
                        .bind("amount", addChargeParams.getAmount())
                        .bind("status", addChargeParams.getStatus().getValue())
                        .bind("gateway_account_id", Long.valueOf(addChargeParams.getGatewayAccountId()))
                        .bind("return_url", addChargeParams.getReturnUrl())
                        .bind("gateway_transaction_id", addChargeParams.getTransactionId())
                        .bind("description", addChargeParams.getDescription())
                        .bind("created_date", Timestamp.from(addChargeParams.getCreatedDate().toInstant()))
                        .bind("reference", addChargeParams.getReference().toString())
                        .bind("version", addChargeParams.getVersion())
                        .bind("email", addChargeParams.getEmail())
                        .bind("language", addChargeParams.getLanguage().toString())
                        .bind("delayed_capture", addChargeParams.isDelayedCapture())
                        .bind("corporate_surcharge", addChargeParams.getCorporateSurcharge())
                        .bind("parity_check_status", addChargeParams.getParityCheckStatus())
                        .bind("parity_check_date", addChargeParams.getParityCheckDate())
                        .bindBySqlType("external_metadata", jsonMetadata, OTHER)
                        .bind("card_type", addChargeParams.getCardType())
                        .execute());
    }

    public void deleteAllChargesOnAccount(long accountId) {
        jdbi.withHandle(handle ->
                handle.createUpdate("DELETE FROM charges where gateway_account_id = :accountId")
                        .bind("accountId", accountId)
                        .execute());
    }

    public int addRefund(String externalId, String reference, long amount, RefundStatus status, String gatewayTransactionId, ZonedDateTime createdDate, String chargeExternalId) {
        return addRefund(externalId, reference, amount, status, gatewayTransactionId, createdDate, null, null, chargeExternalId);
    }

    public int addRefund(String externalId, String reference, long amount, RefundStatus status,
                         String gatewayTransactionId, ZonedDateTime createdDate, String submittedByUserExternalId,
                         String userEmail, String chargeExternalId) {
        int refundId = RandomUtils.nextInt();
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO refunds(id, external_id, reference, amount, status, " +
                                " gateway_transaction_id, created_date, user_external_id, user_email, charge_external_id) " +
                                "VALUES (:id, :external_id, :reference, :amount, :status, " +
                                ":gateway_transaction_id, :created_date, :user_external_id, :user_email, :charge_external_id)")
                        .bind("id", refundId)
                        .bind("external_id", externalId)
                        .bind("reference", reference)
                        .bind("amount", amount)
                        .bind("status", status.getValue())
                        .bind("gateway_transaction_id", gatewayTransactionId)
                        .bind("created_date", Timestamp.from(createdDate.toInstant()))
                        .bind("user_external_id", submittedByUserExternalId)
                        .bind("user_email", userEmail)
                        .bind("charge_external_id", chargeExternalId)
                        .bind("version", 1)
                        .execute()
        );
        return refundId;
    }

    public void addRefundHistory(long id, String externalId, String reference, long amount, String status, ZonedDateTime createdDate, ZonedDateTime historyStartDate, ZonedDateTime historyEndDate, String submittedByUserExternalId, String userEmail, String chargeExternalId) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO refunds_history(id, external_id, reference, amount, status, created_date, history_start_date, history_end_date, user_external_id, user_email, charge_external_id) VALUES (:id, :external_id, :reference, :amount, :status, :created_date, :history_start_date, :history_end_date, :user_external_id, :user_email, :charge_external_id)")
                        .bind("id", id)
                        .bind("external_id", externalId)
                        .bind("reference", reference)
                        .bind("amount", amount)
                        .bind("status", status)
                        .bind("created_date", Timestamp.from(createdDate.toInstant()))
                        .bind("history_start_date", Timestamp.from(historyStartDate.toInstant()))
                        .bind("history_end_date", Timestamp.from(historyEndDate.toInstant()))
                        .bind("user_external_id", submittedByUserExternalId)
                        .bind("user_email", userEmail)
                        .bind("version", 1)
                        .bind("charge_external_id", chargeExternalId)
                        .execute()
        );
    }

    public void addRefundHistory(long id, String externalId, String reference, long amount, String status, ZonedDateTime createdDate, ZonedDateTime historyStartDate, String submittedByUserExternalId, String chargeExternalId) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO refunds_history(id, external_id, reference, amount, status, created_date, history_start_date, user_external_id, charge_external_id) VALUES (:id, :external_id, :reference, :amount, :status, :created_date, :history_start_date, :user_external_id, :charge_external_id)")
                        .bind("id", id)
                        .bind("external_id", externalId)
                        .bind("reference", reference)
                        .bind("amount", amount)
                        .bind("status", status)
                        .bind("created_date", Timestamp.from(createdDate.toInstant()))
                        .bind("history_start_date", Timestamp.from(historyStartDate.toInstant()))
                        .bind("user_external_id", submittedByUserExternalId)
                        .bind("version", 1)
                        .bind("charge_external_id", chargeExternalId)
                        .execute()
        );
    }

    public void updateChargeCardDetails(Long chargeId, String cardBrand, String lastDigitsCardNumber, String firstDigitsCardNumber, String cardHolderName, String expiryDate, String cardType,
                                        String line1, String line2, String postcode, String city, String county, String country) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("UPDATE charges SET card_brand=:card_brand, last_digits_card_number=:last_digits_card_number, first_digits_card_number=:first_digits_card_number, cardholder_name=:cardholder_name, expiry_date=:expiry_date, address_line1=:address_line1, address_line2=:address_line2, address_postcode=:address_postcode, address_city=:address_city, address_county=:address_county, address_country=:address_country, card_type=:card_type WHERE id=:id")
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
                        .bind("card_type", cardType)
                        .execute()
        );
    }

    public void updateCorporateSurcharge(Long chargeId, Long corporateSurcharge) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("UPDATE charges SET corporate_surcharge =:corporate_surcharge WHERE id=:id")
                        .bind("id", chargeId)
                        .bind("corporate_surcharge", corporateSurcharge)
                        .execute());
    }

    public void updateCharge3dsDetails(Long chargeId, String issuerUrl, String paRequest, String htmlOut) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("UPDATE charges SET pa_request_3ds=:pa_request_3ds, issuer_url_3ds=:issuer_url_3ds, html_out_3ds=:html_out_3ds WHERE id=:id")
                        .bind("id", chargeId)
                        .bind("pa_request_3ds", paRequest)
                        .bind("issuer_url_3ds", issuerUrl)
                        .bind("html_out_3ds", htmlOut)
                        .execute()
        );
    }

    public void updateCharge3dsFlexChallengeDetails(Long chargeId, String acsUrl, String transactionId, String payload, String version) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("UPDATE charges " +
                                " SET worldpay_challenge_acs_url_3ds = :acsUrl, " +
                                " worldpay_challenge_transaction_id_3ds = :transactionId, " +
                                " worldpay_challenge_payload_3ds = :payload, " +
                                " version_3ds = :version " +
                                " WHERE id = :id")
                        .bind("id", chargeId)
                        .bind("acsUrl", acsUrl)
                        .bind("transactionId", transactionId)
                        .bind("payload", payload)
                        .bind("version", version)
                        .execute()
        );
    }

    public String getChargeTokenId(Long chargeId) {

        return jdbi.withHandle(h ->
                h.createQuery("SELECT secure_redirect_token from tokens WHERE charge_id = :charge_id ORDER BY id DESC")
                        .bind("charge_id", chargeId)
                        .mapTo(String.class)
                        .first()
        );
    }

    public boolean isChargeTokenUsed(String tokenId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT used FROM tokens WHERE secure_redirect_token = :token_id")
                        .bind("token_id", tokenId)
                        .mapTo(Boolean.class)
                        .first()
        );
    }

    public List<Map<String, Object>> getRefund(long refundId) {
        List<Map<String, Object>> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT external_id, reference, amount, status, created_date, user_external_id, user_email, charge_external_id " +
                        "FROM refunds " +
                        "WHERE id = :refund_id")
                        .bind("refund_id", refundId)
                        .mapToMap()
                        .list());
        return ret;
    }

    public List<Map<String, Object>> getRefundsByChargeExternalId(String chargeExternalId) {
        List<Map<String, Object>> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT external_id, reference, amount, status, created_date, user_external_id, user_email, charge_external_id " +
                        "FROM refunds r " +
                        "WHERE charge_external_id = :charge_external_id")
                        .bind("charge_external_id", chargeExternalId)
                        .mapToMap()
                        .list());
        return ret;
    }

    public Map<String, Object> getChargeCardDetailsByChargeId(Long chargeId) {
        Map<String, Object> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT id, card_brand, last_digits_card_number, first_digits_card_number, cardholder_name, expiry_date, address_line1, address_line2, address_postcode, address_city, address_county, address_country " +
                        "FROM charges " +
                        "WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .mapToMap()
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
                null,
                authCardDetails.getAddress().map(Address::getLine1).orElse(null),
                authCardDetails.getAddress().map(Address::getLine2).orElse(null),
                authCardDetails.getAddress().map(Address::getPostcode).orElse(null),
                authCardDetails.getAddress().map(Address::getCity).orElse(null),
                authCardDetails.getAddress().map(Address::getCounty).orElse(null),
                authCardDetails.getAddress().map(Address::getCountry).orElse(null));
    }

    public Map<String, Object> getChargeCardDetails(long chargeId) {
        Map<String, Object> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT id, last_digits_card_number, first_digits_card_number, cardholder_name, expiry_date, address_line1, address_line2, address_postcode, address_city, address_county, address_country, card_type " +
                        "FROM charges " +
                        "WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .mapToMap()
                        .first());
        return ret;
    }

    public Map<String, Object> getChargeByExternalId(String externalId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM charges WHERE external_id = :external_id")
                        .bind("external_id", externalId)
                        .mapToMap()
                        .first());
    }

    public boolean containsChargeWithExternalId(String externalId) {
        var result = jdbi.withHandle(h ->
                h.createQuery("SELECT count(*) FROM charges WHERE external_id = :external_id")
                        .bind("external_id", externalId)
                        .mapTo(Integer.class)
                        .first());
        return result > 0;
    }


    public Map<String, Object> getChargeByGatewayTransactionId(String gatewayTransactionId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT * FROM charges WHERE gateway_transaction_id = :gatewayTransactionId")
                        .bind("gatewayTransactionId", gatewayTransactionId)
                        .mapToMap()
                        .first());
    }

    public Map<String, Object> getEmailForAccountAndType(Long accountId, EmailNotificationType type) {

        return jdbi.withHandle(h ->
                h.createQuery("SELECT template_body, enabled from email_notifications WHERE account_id = :account_id AND type = :type")
                        .bind("account_id", accountId)
                        .bind("type", type)
                        .mapToMap().findOne().orElse(null)
        );
    }

    public String getChargeTokenByExternalChargeId(String externalChargeId) {

        String chargeId = jdbi.withHandle(h ->
                h.createQuery("SELECT id from charges WHERE external_id = :external_id")
                        .bind("external_id", externalChargeId)
                        .mapTo(String.class)
                        .first()
        );

        return getChargeTokenId(Long.valueOf(chargeId));
    }

    public Map<String, String> getAccountCredentials(Long gatewayAccountId) {

        String jsonString = jdbi.withHandle(h ->
                h.createQuery("SELECT credentials from gateway_accounts WHERE id = :gatewayAccountId")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .mapTo(String.class)
                        .findFirst()
                        .orElse(null)
        );
        return new Gson().fromJson(jsonString, Map.class);
    }

    public String getAccountServiceName(Long gatewayAccountId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT service_name from gateway_accounts WHERE id = :gatewayAccountId")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .mapTo(String.class)
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
                        .mapToMap()
                        .list());
        return ret;
    }

    public List<Map<String, Object>> getChargeEvents(long chargeId) {
        List<Map<String, Object>> ret = jdbi.withHandle(h ->
                h.createQuery("SELECT ce.id, ce.charge_id, ce.status, ce.updated " +
                        "FROM charge_events ce " +
                        "WHERE ce.charge_id = :chargeId")
                        .bind("chargeId", chargeId)
                        .mapToMap()
                        .list());
        return ret;
    }

    public void addToken(Long chargeId, String tokenId) {
        addToken(chargeId, tokenId, false);
    }

    public void addToken(Long chargeId, String tokenId, boolean used) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO tokens(charge_id, secure_redirect_token, used) VALUES (:charge_id, :secure_redirect_token, :used)")
                        .bind("charge_id", chargeId)
                        .bind("secure_redirect_token", tokenId)
                        .bind("used", used)
                        .execute()
        );
    }

    public void addEmailNotification(Long accountId, String templateBody, boolean enabled, EmailNotificationType type) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO email_notifications(account_id, template_body, enabled, type) VALUES (:account_id, :templateBody, :enabled, :type)")
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
                        .createUpdate("DELETE FROM accepted_card_types; DELETE FROM card_types;")
                        .execute()
        );
    }

    public void addAcceptedCardType(long accountId, UUID cardTypeId) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO accepted_card_types(gateway_account_id, card_type_id) VALUES (:accountId, :cardTypeId)")
                        .bind("accountId", accountId)
                        .bind("cardTypeId", cardTypeId)
                        .execute()
        );
    }

    public String getChargeStatus(Long chargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT status from charges WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .mapTo(String.class)
                        .first()
        );
    }

    public String getChargeCardBrand(Long chargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT card_brand from charges WHERE id = :charge_id")
                        .bind("charge_id", chargeId)
                        .mapTo(String.class)
                        .first()
        );
    }

    public void updateCredentialsFor(long accountId, String credentials) {
        try {
            PGobject pgCredentials = new PGobject();
            pgCredentials.setType("json");
            pgCredentials.setValue(credentials);
            jdbi.withHandle(handle ->
                    handle.createUpdate("UPDATE gateway_accounts set credentials=:credentials WHERE id=:gatewayAccountId")
                            .bind("gatewayAccountId", accountId)
                            .bindBySqlType("credentials", pgCredentials, OTHER)
                            .execute()
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateServiceNameFor(long accountId, String serviceName) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE gateway_accounts set service_name=:serviceName WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .bind("serviceName", serviceName)
                        .execute()
        );
    }

    public void updateCorporateCreditCardSurchargeAmountFor(long accountId, long corporateCreditCardSurchargeAmount) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE gateway_accounts set corporate_credit_card_surcharge_amount=:corporateCreditCardSurchargeAmount WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .bind("corporateCreditCardSurchargeAmount", corporateCreditCardSurchargeAmount)
                        .execute()
        );
    }

    public void updateCorporateDebitCardSurchargeAmountFor(long accountId, long corporateDebitCardSurchargeAmount) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE gateway_accounts set corporate_debit_card_surcharge_amount=:corporateDebitCardSurchargeAmount WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .bind("corporateDebitCardSurchargeAmount", corporateDebitCardSurchargeAmount)
                        .execute()
        );
    }

    public void allowApplePay(long accountId) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE gateway_accounts set allow_apple_pay=true WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .execute()
        );
    }

    public void allowZeroAmount(long accountId) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE gateway_accounts set allow_zero_amount=true WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .execute()
        );
    }

    public void blockPrepaidCards(Long accountId) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE gateway_accounts set block_prepaid_cards=true WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .execute()
        );
    }

    public void allowMoto(long accountId) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE gateway_accounts set allow_moto=true WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .execute()
        );
    }


    public void addWalletType(long chargeId, WalletType walletType) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE CHARGES set wallet=:walletType WHERE id=:chargeId")
                        .bind("chargeId", chargeId)
                        .bind("walletType", walletType)
                        .execute()
        );
    }

    public void addExternalMetadata(long chargeId, ExternalMetadata externalMetadata) {
        PGobject jsonExternMetadata = new PGobject();
        jsonExternMetadata.setType("json");

        if (externalMetadata != null) {
            try {
                jsonExternMetadata.setValue(new Gson().toJson(externalMetadata.getMetadata()));
            } catch (SQLException e) {
                throw new ExternalMetadataConverterException("Failed to serialize metadata");
            }
        }

        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE CHARGES set external_metadata=:metadata WHERE id=:chargeId")
                        .bind("chargeId", chargeId)
                        .bindBySqlType("metadata", jsonExternMetadata, OTHER)
                        .execute()
        );
    }

    public void enable3dsForGatewayAccount(long accountId) {
        jdbi.withHandle(handle ->
                handle.createUpdate("UPDATE gateway_accounts set requires_3ds=true WHERE id=:gatewayAccountId")
                        .bind("gatewayAccountId", accountId)
                        .execute()
        );
    }

    public void addNotificationCredentialsFor(long accountId, String username, String password) {
        jdbi.withHandle(handle ->
                handle.createUpdate("INSERT INTO notification_credentials(account_id, username, password, version) VALUES (:accountId, :username, :password, 1)")
                        .bind("accountId", accountId)
                        .bind("username", username)
                        .bind("password", password)
                        .execute()
        );
    }

    public void addEvent(Long chargeId, String chargeStatus) {
        addEvent(chargeId, chargeStatus, now());
    }

    public void addEvent(Long chargeId, String chargeStatus, ZonedDateTime updated) {
        String sql = "INSERT INTO charge_events(charge_id, status, updated) VALUES(:charge_id, :status, :updated)";
        jdbi.withHandle(h -> {
            ZonedDateTime utcValue = updated.withZoneSameInstant(ZoneId.of("UTC"));
            return h.createUpdate("INSERT INTO charge_events(charge_id, status, updated) " +
                    "VALUES(:charge_id, :status, :updated)")
                    .bind("charge_id", chargeId)
                    .bind("status", chargeStatus)
                    .bind("updated", Timestamp.valueOf(utcValue.toLocalDateTime()))
                    .execute();
        });
    }

    public List<String> getInternalEvents(String externalChargeId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT status from charge_events WHERE charge_id = (SELECT id from charges WHERE external_id=:external_id) order by charge_events.id")
                        .bind("external_id", externalChargeId)
                        .mapTo(String.class)
                        .list()
        );
    }

    public String getCardTypeId(String brand, String type) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT id from card_types WHERE brand = :brand AND type = :type")
                        .bind("brand", brand)
                        .bind("type", type)
                        .mapTo(String.class)
                        .first()
        );
    }

    public CardTypeEntity getCardTypeByBrandAndType(String brand, CardType type) {
        return getCardTypeByBrandAndType(brand, type.toString());
    }

    public CardTypeEntity getMastercardCreditCard() {
        return getCardTypeByBrandAndType("master-card", CardType.CREDIT);
    }

    public CardTypeEntity getMastercardDebitCard() {
        return getCardTypeByBrandAndType("master-card", CardType.DEBIT);
    }

    public CardTypeEntity getVisaCreditCard() {
        return getCardTypeByBrandAndType("visa", CardType.CREDIT);
    }

    public CardTypeEntity getVisaDebitCard() {
        return getCardTypeByBrandAndType("visa", CardType.DEBIT);
    }

    public CardTypeEntity getMaestroCard() {
        return getCardTypeByBrandAndType("maestro", CardType.DEBIT);
    }

    public CardTypeEntity getCardTypeByBrandAndType(String brand, String type) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT id, brand, type, label, requires_3ds from card_types WHERE brand = :brand AND type = :type")
                        .bind("brand", brand)
                        .bind("type", type)
                        .mapToBean(CardTypeEntity.class)
                        .first()
        );
    }

    public List<Map<String, Object>> getRefundsHistoryByChargeExternalId(String chargeExternalId) {
        return jdbi.withHandle(h ->
                h.createQuery("SELECT status FROM refunds_history WHERE charge_external_id = :chargeExternalId order by history_start_date desc")
                        .bind("chargeExternalId", chargeExternalId)
                        .mapToMap()
                        .list()
        );
    }

    public Map<String, String> getNotifySettings(Long gatewayAccountId) {

        String jsonString = jdbi.withHandle(h ->
                h.createQuery("SELECT notify_settings from gateway_accounts WHERE id = :gatewayAccountId")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .mapTo(String.class)
                        .first()
        );
        return new Gson().fromJson(jsonString, Map.class);
    }

    public void truncateEmittedEvents() {
        jdbi.withHandle(h -> h.createUpdate("TRUNCATE TABLE emitted_events").execute());
    }

    public void truncateAllData() {
        jdbi.withHandle(h -> h.createUpdate("TRUNCATE TABLE gateway_accounts CASCADE").execute());
        jdbi.withHandle(h -> h.createUpdate("TRUNCATE TABLE emitted_events CASCADE").execute());
        jdbi.withHandle(h -> h.createUpdate("TRUNCATE TABLE tokens").execute());
        jdbi.withHandle(h -> h.createUpdate("TRUNCATE TABLE refunds").execute());
        jdbi.withHandle(h -> h.createUpdate("TRUNCATE TABLE refunds_history").execute());
    }

    public Long getChargeIdByExternalId(String externalChargeId) {

        String chargeId = jdbi.withHandle(h ->
                h.createQuery("SELECT id from charges WHERE external_id = :external_id")
                        .bind("external_id", externalChargeId)
                        .mapTo(String.class)
                        .first()
        );

        return (Long.valueOf(chargeId));
    }

    public void addGatewayAccountsStripeSetupTask(long accountId, StripeAccountSetupTask task) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO gateway_accounts_stripe_setup(gateway_account_id, task) VALUES (:accountId, :task)")
                        .bind("accountId", accountId)
                        .bind("task", task)
                        .execute()
        );
    }

    public void addFee(String externalId, long chargeId, long feeDue, long feeCollected, ZonedDateTime createdDate, String gatewayTransactionId) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO fees(external_id, charge_id, amount_due, amount_collected, created_date, gateway_transaction_id) VALUES (:external_id, :charge_id, :amount_due, :amount_collected, :created_date, :gateway_transaction_id)")
                        .bind("external_id", externalId)
                        .bind("charge_id", chargeId)
                        .bind("amount_due", feeDue)
                        .bind("amount_collected", feeCollected)
                        .bind("created_date", Timestamp.from(createdDate.toInstant()))
                        .bind("gateway_transaction_id", gatewayTransactionId)
                        .execute()
        );
    }

    public void addEmittedEvent(String resourceType, String externalId, Instant eventDate, String eventType,
                                Instant emittedDate, Instant doNotRetryEmitUntil) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO emitted_events(resource_type, resource_external_id, event_date, " +
                                " event_type, emitted_date, do_not_retry_emit_until) VALUES (:resourceType, :externalId, :eventDate, " +
                                " :eventType, :emittedDate, :doNotRetryEmitUntil)")
                        .bind("resourceType", resourceType)
                        .bind("externalId", externalId)
                        .bind("eventDate", Timestamp.from(eventDate))
                        .bind("eventType", eventType)
                        .bind("emittedDate", emittedDate)
                        .bind("doNotRetryEmitUntil", doNotRetryEmitUntil == null ? null : Timestamp.from(doNotRetryEmitUntil))
                        .execute()
        );
    }

    public Map<String, Object> readEmittedEvent(Long id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * from emitted_events WHERE id = :id")
                        .bind("id", id)
                        .mapToMap()
                        .first()
        );
    }

    public List<Map<String, Object>> readEmittedEvents() {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * from emitted_events")
                        .mapToMap()
                        .list()
        );
    }

    public void insertWorldpay3dsFlexCredential(Long gatewayAccountId, String jwtMacKey, String issuer, String organisationalUnitId, Long version) {
        jdbi.withHandle(handle ->
                handle
                        .createUpdate("INSERT INTO worldpay_3ds_flex_credentials(gateway_account_id, jwt_mac_key, issuer, organisational_unit_id, version) VALUES (:gatewayAccountId, :jwtMacKey, :issuer, :organisationalUnitId, :version)")
                        .bind("gatewayAccountId", gatewayAccountId)
                        .bind("jwtMacKey", jwtMacKey)
                        .bind("issuer", issuer)
                        .bind("organisationalUnitId", organisationalUnitId)
                        .bind("version", version)
                        .execute()
        );
    }

    public Map<String, Object> getWorldpay3dsFlexCredentials(Long accountId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM worldpay_3ds_flex_credentials WHERE gateway_account_id = :accountId")
                        .bind("accountId", accountId)
                        .mapToMap()
                        .first());
    }

    public Map<String, Object> getGatewayAccount(Long accountId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT * FROM gateway_accounts WHERE id = :accountId")
                        .bind("accountId", accountId)
                        .mapToMap()
                        .first());
    }
}
