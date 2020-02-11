package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.Test;
import uk.gov.pay.connector.common.model.api.CommaDelimitedSetParameter;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.LIVE;

public class GatewayAccountSearchParamsTest {

    @Test
    public void shouldReturnFilterTemplatesWithAllParameters() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds(new CommaDelimitedSetParameter("1,2"));
        params.setMotoEnabled("false");
        params.setApplePayEnabled("true");
        params.setGooglePayEnabled("true");
        params.setRequires3ds("true");
        params.setType("live");
        params.setPaymentProvider("stripe");

        List<String> filterTemplates = params.getFilterTemplates();
        assertThat(filterTemplates, containsInAnyOrder(
                " gae.id IN :accountIds",
                " gae.allowMoto = :allowMoto",
                " gae.allowApplePay = :allowApplePay",
                " gae.allowGooglePay = :allowGooglePay",
                " gae.requires3ds = :requires3ds",
                " gae.type = :type",
                " gae.gatewayName = :gatewayName"));
    }

    @Test
    public void shouldReturnEmptyFilterTemplatesForNoParamsSet() {
        var params = new GatewayAccountSearchParams();

        List<String> filterTemplates = params.getFilterTemplates();
        assertThat(filterTemplates, hasSize(0));
    }

    @Test
    public void shouldNotIncludeAccountIdsInFilterTemplatesForEmptyString() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds(new CommaDelimitedSetParameter(""));

        List<String> filterTemplates = params.getFilterTemplates();
        assertThat(filterTemplates, hasSize(0));
    }

    @Test
    public void shouldReturnQueryMapWithAllParameters() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds(new CommaDelimitedSetParameter("1,2"));
        params.setMotoEnabled("false");
        params.setApplePayEnabled("true");
        params.setGooglePayEnabled("true");
        params.setRequires3ds("true");
        params.setType("live");
        params.setPaymentProvider("stripe");

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, aMapWithSize(7));
        assertThat(queryMap, hasEntry("accountIds", List.of("1", "2")));
        assertThat(queryMap, hasEntry("allowMoto", false));
        assertThat(queryMap, hasEntry("allowApplePay", true));
        assertThat(queryMap, hasEntry("allowGooglePay", true));
        assertThat(queryMap, hasEntry("requires3ds", true));
        assertThat(queryMap, hasEntry("type", LIVE));
        assertThat(queryMap, hasEntry("gatewayName", "stripe"));
    }

    @Test
    public void shouldReturnEmptyQueryMapForNoParamsSet() {
        var params = new GatewayAccountSearchParams();

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, anEmptyMap());
    }

    @Test
    public void shouldNotIncludeAccountIdsInQueryMapForEmptyString() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds(new CommaDelimitedSetParameter(""));

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, anEmptyMap());
    }
}
