package uk.gov.pay.connector.gatewayaccount.model;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.common.model.api.CommaDelimitedSetParameter;

import javax.validation.constraints.Pattern;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GatewayAccountSearchParams {

    private static final String ACCOUNT_IDS_SQL_FIELD = "accountIds";
    private static final String ALLOW_MOTO_SQL_FIELD = "allowMoto";
    private static final String ALLOW_APPLE_PAY_SQL_FIELD = "allowApplePay";
    private static final String ALLOW_GOOGLE_PAY_SQL_FIELD = "allowGooglePay";
    private static final String REQUIRES_3DS_SQL_FIELD = "requires3ds";
    private static final String TYPE_SQL_FIELD = "type";
    private static final String PAYMENT_PROVIDER_SQL_FIELD = "gatewayName";
    private static final String PROVIDER_SWITCH_ENABLED_SQL_FIELD = "providerSwitchEnabled";
    
    @QueryParam("accountIds")
    private CommaDelimitedSetParameter accountIds;
    
    // This is a string value rather than boolean as if the parameter isn't provided, it should not filter by
    // moto enabled/disabled
    @QueryParam("moto_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [moto_enabled] must be true or false")
    private String motoEnabled;

    @QueryParam("apple_pay_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [apple_pay_enabled] must be true or false")
    private String applePayEnabled;
    
    @QueryParam("google_pay_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [google_pay_enabled] must be true or false")
    private String googlePayEnabled;

    @QueryParam("requires_3ds")
    @Pattern(regexp = "true|false",
            message = "Parameter [requires_3ds] must be true or false")
    private String requires3ds;
    
    @QueryParam("type")
    @Pattern(regexp = "live|test",
            message = "Parameter [type] must be 'live' or 'test'")
    private String type;

    @QueryParam("payment_provider")
    @Pattern(regexp = "sandbox|worldpay|smartpay|epdq|stripe",
            message = "Parameter [payment_provider] must be one of 'sandbox', 'worldpay', 'smartpay', 'epdq' or 'stripe'")
    private String paymentProvider;

    @QueryParam("provider_switch_enabled")
    @Pattern(regexp = "true|false",
            message = "Parameter [provider_switch_enabled] must be true or false")
    private String providerSwitchEnabled;

    public void setAccountIds(CommaDelimitedSetParameter accountIds) {
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

    public void setProviderSwitchEnabled(String providerSwitchEnabled) {
        this.providerSwitchEnabled = providerSwitchEnabled;
    }

    public List<String> getFilterTemplates() {
        List<String> filters = new ArrayList<>();

        if (accountIds != null && accountIds.isNotEmpty()) {
            filters.add(" gae.id IN :" + ACCOUNT_IDS_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(motoEnabled)) {
            filters.add(" gae.allowMoto = :" + ALLOW_MOTO_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(applePayEnabled)) {
            filters.add(" gae.allowApplePay = :" + ALLOW_APPLE_PAY_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(googlePayEnabled)) {
            filters.add(" gae.allowGooglePay = :" + ALLOW_GOOGLE_PAY_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(requires3ds)) {
            filters.add(" gae.requires3ds = :" + REQUIRES_3DS_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(type)) {
            filters.add(" gae.type = :" + TYPE_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(paymentProvider)) {
            filters.add(" gae.gatewayName = :" + PAYMENT_PROVIDER_SQL_FIELD);
        }
        if (StringUtils.isNotEmpty(providerSwitchEnabled)) {
            filters.add(" gae.providerSwitchEnabled = :" + PROVIDER_SWITCH_ENABLED_SQL_FIELD);
        }

        return List.copyOf(filters);
    }
    
    public Map<String, Object> getQueryMap() {
        HashMap<String, Object> queryMap = new HashMap<>();
        
        if (accountIds != null && accountIds.isNotEmpty()) {
            queryMap.put(ACCOUNT_IDS_SQL_FIELD, accountIds.getParameters());
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
            queryMap.put(TYPE_SQL_FIELD, GatewayAccountType.fromString(type));
        }
        if (StringUtils.isNotEmpty(paymentProvider)) {
            queryMap.put(PAYMENT_PROVIDER_SQL_FIELD, paymentProvider);
        }
        if (StringUtils.isNotEmpty(providerSwitchEnabled)) {
            queryMap.put(PROVIDER_SWITCH_ENABLED_SQL_FIELD, Boolean.valueOf(providerSwitchEnabled));
        }
        
        return queryMap;
    }

    @Override
    public String toString() {
        return "GatewayAccountSearchParams{" +
                "accountIds=" + accountIds +
                ", motoEnabled='" + motoEnabled + '\'' +
                ", applePayEnabled='" + applePayEnabled + '\'' +
                ", googlePayEnabled='" + googlePayEnabled + '\'' +
                ", requires3ds='" + requires3ds + '\'' +
                ", type='" + type + '\'' +
                ", paymentProvider='" + paymentProvider + '\'' +
                ", providerSwitchEnabled='" + providerSwitchEnabled + '\'' +
                '}';
    }
}
