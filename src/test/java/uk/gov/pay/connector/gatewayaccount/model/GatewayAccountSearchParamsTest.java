package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;

 class GatewayAccountSearchParamsTest {

    @Test
     void shouldReturnFilterTemplatesWithAllParameters() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds("1,2");
        params.setServiceIds("serviceidone,serviceidtwo,serviceidthree");
        params.setMotoEnabled("false");
        params.setApplePayEnabled("true");
        params.setGooglePayEnabled("true");
        params.setRequires3ds("true");
        params.setType("live");
        params.setPaymentProvider("stripe");
        params.setPaymentProviderAccountId("acc123");
        params.setProviderSwitchEnabled("true");
        params.setRecurringEnabled("true");

        List<String> filterTemplates = params.getFilterTemplates();
        assertThat(filterTemplates, containsInAnyOrder(
                " ga.id IN (#accountIds0,#accountIds1)",
                " ga.service_id IN (#serviceId0,#serviceId1,#serviceId2)",
                " ga.allow_moto = #allowMoto",
                " ga.allow_apple_pay = #allowApplePay",
                " ga.allow_google_pay = #allowGooglePay",
                " ga.requires_3ds = #requires3ds",
                " ga.type = #type",
                " ga.provider_switch_enabled = #providerSwitchEnabled",
                " ga.recurring_enabled = #recurringEnabled",
                " ga.id in ( " +
                        "  select gateway_account_id " +
                        "  from ( " +
                        "    select gateway_account_id, payment_provider, row_number() over(partition by gateway_account_id order by " +
                        "    (case when state='ACTIVE' then 1 when state = 'RETIRED' then 1000 else 2 end) asc, created_date asc ) rn  " +
                        "    from gateway_account_credentials gac " +
                        "    where gac.gateway_account_id = ga.id " +
                        "  ) a " +
                        "  where rn = 1 " +
                        "  and a.payment_provider = #gatewayName" +
                        ")",
                " ga.id in (   select gateway_account_id   from " +
                        "(     select gateway_account_id     from gateway_account_credentials gac     " +
                        "where gac.gateway_account_id = ga.id and gac.credentials->>'stripe_account_id' = #paymentProviderAccountId" +
                        "    or gac.credentials->'one_off_customer_initiated'->>'merchant_code' = #paymentProviderAccountId) a)")
        );
    }

    @Test
     void shouldReturnEmptyFilterTemplatesForNoParamsSet() {
        var params = new GatewayAccountSearchParams();

        List<String> filterTemplates = params.getFilterTemplates();
        assertThat(filterTemplates, hasSize(0));
    }

    @Test
     void shouldNotIncludeAccountIdsInFilterTemplatesForEmptyString() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds("");

        List<String> filterTemplates = params.getFilterTemplates();
        assertThat(filterTemplates, hasSize(0));
    }

    @Test
     void shouldReturnQueryMapWithAllParameters() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds("1,22");
        params.setServiceIds("serviceidone,serviceidtwo,serviceidthree");
        params.setMotoEnabled("false");
        params.setApplePayEnabled("true");
        params.setGooglePayEnabled("true");
        params.setRequires3ds("true");
        params.setType("live");
        params.setPaymentProvider("stripe");
        params.setPaymentProviderAccountId("acc123");
        params.setProviderSwitchEnabled("true");
        params.setRecurringEnabled("true");

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, aMapWithSize(14));
        assertThat(queryMap, hasEntry("accountIds0", 1L));
        assertThat(queryMap, hasEntry("accountIds1", 22L));
        assertThat(queryMap, hasEntry("serviceId0", "serviceidone"));
        assertThat(queryMap, hasEntry("serviceId1", "serviceidtwo"));
        assertThat(queryMap, hasEntry("serviceId2", "serviceidthree"));
        assertThat(queryMap, hasEntry("allowMoto", false));
        assertThat(queryMap, hasEntry("allowApplePay", true));
        assertThat(queryMap, hasEntry("allowGooglePay", true));
        assertThat(queryMap, hasEntry("requires3ds", true));
        assertThat(queryMap, hasEntry("type", "LIVE"));
        assertThat(queryMap, hasEntry("gatewayName", "stripe"));
        assertThat(queryMap, hasEntry("providerSwitchEnabled", true));
        assertThat(queryMap, hasEntry("recurringEnabled", true));
        assertThat(queryMap, hasEntry("paymentProviderAccountId", "acc123"));
    }

    @Test
     void shouldReturnEmptyQueryMapForNoParamsSet() {
        var params = new GatewayAccountSearchParams();

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, anEmptyMap());
    }

    @Test
     void shouldNotIncludeAccountIdsInQueryMapForEmptyString() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds("");

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, anEmptyMap());
    }
}
