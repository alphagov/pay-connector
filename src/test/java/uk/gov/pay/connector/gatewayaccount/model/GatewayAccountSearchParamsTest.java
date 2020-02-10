package uk.gov.pay.connector.gatewayaccount.model;

import org.hamcrest.collection.IsMapWithSize;
import org.junit.Test;
import uk.gov.pay.connector.common.model.api.CommaDelimitedSetParameter;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;

public class GatewayAccountSearchParamsTest {
    
    @Test
    public void shouldReturnFilterTemplatesWithAllParameters() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds(new CommaDelimitedSetParameter("1,2"));
        params.setMotoEnabled(false);

        List<String> filterTemplates = params.getFilterTemplates();
        assertThat(filterTemplates, hasSize(2));
        assertThat(filterTemplates, containsInAnyOrder(
                " gae.id IN :accountIds",
                " gae.allowMoto = :allowMoto"));
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
        params.setMotoEnabled(false);

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, hasEntry("accountIds", List.of("1", "2")));
        assertThat(queryMap, hasEntry("allowMoto", false));
    }

    @Test
    public void shouldReturnEmptyQueryMapForNoParamsSet() {
        var params = new GatewayAccountSearchParams();

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, IsMapWithSize.anEmptyMap());
    }

    @Test
    public void shouldNotIncludeAccountIdsInQueryMapForEmptyString() {
        var params = new GatewayAccountSearchParams();
        params.setAccountIds(new CommaDelimitedSetParameter(""));

        Map<String, Object> queryMap = params.getQueryMap();
        assertThat(queryMap, IsMapWithSize.anEmptyMap());
    }
}
