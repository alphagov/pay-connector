package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.Pattern;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.apache.commons.lang3.StringUtils.isBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GatewayAccountSearchParams {

    private static final String ACCOUNT_IDS_SQL_FIELD = "accountIds";
    private static final String SERVICE_IDS_SQL_FIELD = "serviceId";
    private static final String ALLOW_MOTO_SQL_FIELD = "allowMoto";
    private static final String ALLOW_APPLE_PAY_SQL_FIELD = "allowApplePay";
    private static final String ALLOW_GOOGLE_PAY_SQL_FIELD = "allowGooglePay";
    private static final String REQUIRES_3DS_SQL_FIELD = "requires3ds";
    private static final String TYPE_SQL_FIELD = "type";
    private static final String PAYMENT_PROVIDER_SQL_FIELD = "gatewayName";
    private static final String PAYMENT_PROVIDER_ACCOUNT_ID_SQL_FIELD = "paymentProviderAccountId";
    private static final String PROVIDER_SWITCH_ENABLED_SQL_FIELD = "providerSwitchEnabled";
    private static final String RECURRING_PAYMENTS_ENABLED = "recurringEnabled";

    @QueryParam("accountIds")
    @Pattern(regexp = "^[\\d,]+$",
            message = "Parameter [accountIds] must be a comma separated list of numbers")
    @JsonProperty("accountIds")
    @Schema(example = "1,2", description = "Comma separate list of gateway account IDs")
    private String accountIds;

    @QueryParam("serviceIds")
    @Pattern(regexp = "^(?:[A-z0-9]+,?)+$",
            message = "Parameter [serviceIds] must be a comma separated list of alphanumeric strings")
    @JsonProperty("serviceIds")
    @Schema(example = "46eb1b601348499196c99de90482ee68,service-external-id-2", description = "Comma separated list of service external IDs")
    private String serviceIds;

    // This is a string value rather than boolean as if the parameter isn't provided, it should not filter by
    // moto enabled/disabled
    @QueryParam("moto_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [moto_enabled] must be true or false")
    @JsonProperty("moto_enabled")
    @Schema(example = "true", description = "The accounts will be filtered by whether or not MOTO payment are enabled for the account if this parameter is provided. \"true\" or \"false\"")
    private String motoEnabled;

    @QueryParam("apple_pay_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [apple_pay_enabled] must be true or false")
    @JsonProperty("apple_pay_enabled")
    @Schema(example = "true", description = "The accounts will be filtered by whether or not Apple pay is enabled for the account if this parameter is provided. \"true\" or \"false\".")
    private String applePayEnabled;

    @QueryParam("google_pay_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [google_pay_enabled] must be true or false")
    @JsonProperty("google_pay_enabled")
    @Schema(example = "true", description = "The accounts will be filtered by whether or not Google pay is enabled for the account if this parameter is provided. \"true\" or \"false\".")
    private String googlePayEnabled;

    @QueryParam("requires_3ds")
    @Pattern(regexp = "true|false",
            message = "Parameter [requires_3ds] must be true or false")
    @JsonProperty("requires_3ds")
    @Schema(example = "true", description = "The accounts will be filtered by whether or not 3DS is required for the account if this parameter is provided. \"true\" or \"false\".")
    private String requires3ds;

    @QueryParam("type")
    @Pattern(regexp = "live|test",
            message = "Parameter [type] must be 'live' or 'test'")
    @JsonProperty("type")
    @Schema(example = "live", description = "The accounts will be filtered by type if this parameter is provided. \"test\" or \"live\".")
    private String type;

    @QueryParam("payment_provider")
    @Pattern(regexp = "sandbox|worldpay|smartpay|epdq|stripe",
            message = "Parameter [payment_provider] must be one of 'sandbox', 'worldpay', 'smartpay', 'epdq' or 'stripe'")
    @JsonProperty("payment_provider")
    @Schema(example = "live", description = "The accounts will be filtered by payment provider if this parameter is provided. One of \"sandbox\" or \"worldpay\", \"smartpay\", \"epdq\" or \"stripe\"")
    private String paymentProvider;

    @QueryParam("payment_provider_account_id")
    @JsonProperty("payment_provider_account_id")
    @Schema(example = "payment-provider-account-id", description = "Accounts will be filtered by payment provider account ID")
    private String paymentProviderAccountId;

    @QueryParam("provider_switch_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [provider_switch_enabled] must be true or false")
    @JsonProperty("provider_switch_enabled")
    @Schema(example = "true", description = "The accounts will be filtered by whether or not payment provider switch is enabled for the account if this parameter is provided. \"true\" or \"false\".")
    private String providerSwitchEnabled;

    @QueryParam("recurring_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [recurring_enabled] must be true or false")
    @JsonProperty("recurring_enabled")
    @Schema(example = "true", description = "The accounts will be filtered by whether or not recurring payments are enabled for the account when this parameter is provided.")
    private String recurringEnabled;

    public void setAccountIds(String accountIds) {
        this.accountIds = accountIds;
    }

    public void setMotoEnabled(String motoEnabled) {
        this.motoEnabled = motoEnabled;
    }

    public void setApplePayEnabled(String applePayEnabled) {
        this.applePayEnabled = applePayEnabled;
    }

    public void setGooglePayEnabled(String googlePayEnabled) {
        this.googlePayEnabled = googlePayEnabled;
    }

    public void setRequires3ds(String requires3ds) {
        this.requires3ds = requires3ds;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public void setPaymentProviderAccountId(String paymentProviderAccountId) {
        this.paymentProviderAccountId = paymentProviderAccountId;
    }

    public void setProviderSwitchEnabled(String providerSwitchEnabled) {
        this.providerSwitchEnabled = providerSwitchEnabled;
    }

    public void setRecurringEnabled(String recurringEnabled) {
        this.recurringEnabled = recurringEnabled;
    }

    public void setServiceIds(String serviceIds) {
        this.serviceIds = serviceIds;
    }

    private List<String> getAccountIdsAsList() {
        return isBlank(accountIds)
                ? List.of()
                : List.of(accountIds.split(","));
    }

    private List<String> getServiceIdsAsList() {
        return isBlank(serviceIds)
                ? List.of()
                : List.of(serviceIds.split(","));
    }

    @JsonIgnore
    public List<String> getFilterTemplates() {
        List<String> filters = new ArrayList<>();

        List<String> accountIdsList = getAccountIdsAsList();
        if (!accountIdsList.isEmpty()) {
            StringJoiner filter = new StringJoiner(",", " ga.id IN (", ")");
            for (int i = 0; i < accountIdsList.size(); i++) {
                filter.add("#" + ACCOUNT_IDS_SQL_FIELD + i);
            }
            filters.add(filter.toString());
        }
        List<String> serviceIdsList = getServiceIdsAsList();
        if (!serviceIdsList.isEmpty()) {
            StringJoiner filter = new StringJoiner(",", " ga.service_id IN (", ")");
            for (int i = 0; i < serviceIdsList.size(); i++) {
                filter.add("#" + SERVICE_IDS_SQL_FIELD + i);
            }
            filters.add(filter.toString());
        }
        if (StringUtils.isNotEmpty(motoEnabled)) {
            filters.add(" ga.allow_moto = #" + ALLOW_MOTO_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(applePayEnabled)) {
            filters.add(" ga.allow_apple_pay = #" + ALLOW_APPLE_PAY_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(googlePayEnabled)) {
            filters.add(" ga.allow_google_pay = #" + ALLOW_GOOGLE_PAY_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(requires3ds)) {
            filters.add(" ga.requires_3ds = #" + REQUIRES_3DS_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(type)) {
            filters.add(" ga.type = #" + TYPE_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(paymentProvider)) {
            filters.add(" ga.id in ( " +
                    "  select gateway_account_id " +
                    "  from ( " +
                    "    select gateway_account_id, payment_provider, row_number() over(partition by gateway_account_id order by " +
                    "    (case when state='ACTIVE' then 1 when state = 'RETIRED' then 1000 else 2 end) asc, created_date asc ) rn  " +
                    "    from gateway_account_credentials gac " +
                    "    where gac.gateway_account_id = ga.id " +
                    "  ) a " +
                    "  where rn = 1 " +
                    "  and a.payment_provider = #" + PAYMENT_PROVIDER_SQL_FIELD +
                    ")");
        }
        if (StringUtils.isNotEmpty(paymentProviderAccountId)) {
            filters.add(" ga.id in ( " +
                    "  select gateway_account_id " +
                    "  from ( " +
                    "    select gateway_account_id " +
                    "    from gateway_account_credentials gac " +
                    "    where gac.gateway_account_id = ga.id and gac.credentials->>'stripe_account_id' = #" + PAYMENT_PROVIDER_ACCOUNT_ID_SQL_FIELD +
                    "    or gac.credentials->'one_off_customer_initiated'->>'merchant_id' = #" + PAYMENT_PROVIDER_ACCOUNT_ID_SQL_FIELD + ") a" +
                    ")");
        }
        if (StringUtils.isNotEmpty(providerSwitchEnabled)) {
            filters.add(" ga.provider_switch_enabled = #" + PROVIDER_SWITCH_ENABLED_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(recurringEnabled)) {
            filters.add(" ga.recurring_enabled = #" + RECURRING_PAYMENTS_ENABLED);
        }

        return List.copyOf(filters);
    }

    @JsonIgnore
    public Map<String, Object> getQueryMap() {
        HashMap<String, Object> queryMap = new HashMap<>();

        List<String> accountIdsList = getAccountIdsAsList();
        if (!accountIdsList.isEmpty()) {
            for (int i = 0; i < accountIdsList.size(); i++) {
                String id = accountIdsList.get(i);
                queryMap.put(ACCOUNT_IDS_SQL_FIELD + i, Long.valueOf(id));
            }
        }
        List<String> serviceIdsList = getServiceIdsAsList();
        if (!serviceIdsList.isEmpty()) {
            for (int i = 0; i < serviceIdsList.size(); i++) {
                String id = serviceIdsList.get(i);
                queryMap.put(SERVICE_IDS_SQL_FIELD + i, id);
            }
        }
        if (StringUtils.isNotEmpty(motoEnabled)) {
            queryMap.put(ALLOW_MOTO_SQL_FIELD, Boolean.valueOf(motoEnabled));
        }
        if (StringUtils.isNotEmpty(applePayEnabled)) {
            queryMap.put(ALLOW_APPLE_PAY_SQL_FIELD, Boolean.valueOf(applePayEnabled));
        }
        if (StringUtils.isNotEmpty(googlePayEnabled)) {
            queryMap.put(ALLOW_GOOGLE_PAY_SQL_FIELD, Boolean.valueOf(googlePayEnabled));
        }
        if (StringUtils.isNotEmpty(requires3ds)) {
            queryMap.put(REQUIRES_3DS_SQL_FIELD, Boolean.valueOf(requires3ds));
        }
        if (StringUtils.isNotEmpty(type)) {
            queryMap.put(TYPE_SQL_FIELD, type.toUpperCase());
        }
        if (StringUtils.isNotEmpty(paymentProvider)) {
            queryMap.put(PAYMENT_PROVIDER_SQL_FIELD, paymentProvider);
        }
        if (StringUtils.isNotEmpty(paymentProviderAccountId)) {
            queryMap.put(PAYMENT_PROVIDER_ACCOUNT_ID_SQL_FIELD, paymentProviderAccountId);
        }
        if (StringUtils.isNotEmpty(providerSwitchEnabled)) {
            queryMap.put(PROVIDER_SWITCH_ENABLED_SQL_FIELD, Boolean.valueOf(providerSwitchEnabled));
        }
        if (StringUtils.isNotEmpty(recurringEnabled)) {
            queryMap.put(RECURRING_PAYMENTS_ENABLED, Boolean.valueOf(recurringEnabled));
        }

        return queryMap;
    }

    @Override
    public String toString() {
        return "GatewayAccountSearchParams{" +
                "accountIds=" + accountIds +
                ", serviceIds=" + serviceIds +
                ", motoEnabled='" + motoEnabled + '\'' +
                ", applePayEnabled='" + applePayEnabled + '\'' +
                ", googlePayEnabled='" + googlePayEnabled + '\'' +
                ", requires3ds='" + requires3ds + '\'' +
                ", type='" + type + '\'' +
                ", paymentProvider='" + paymentProvider + '\'' +
                ", providerSwitchEnabled='" + providerSwitchEnabled + '\'' +
                ", recurringEnabled='" + recurringEnabled + '\'' +
                ", paymentProviderAccountId='" + paymentProviderAccountId + '\'' +
                '}';
    }
}
