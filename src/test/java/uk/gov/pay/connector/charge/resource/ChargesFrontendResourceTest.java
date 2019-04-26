package uk.gov.pay.connector.charge.resource;

import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class ChargesFrontendResourceTest {
    @Mock
    private static ChargeService chargeService;
    @Mock
    private static  ChargeDao chargeDao;
    @Mock
    private static CardTypeDao cardTypeDao;
    
    @ClassRule
    public static ResourceTestRule resources = ResourceTestRuleWithCustomExceptionMappersBuilder.getBuilder()
            .addResource(new ChargesFrontendResource(chargeDao, chargeService, cardTypeDao))
            .build();

    @Test
    public void shouldReturn400_whenPutToChargeStatus_emptyPayload() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(""));

        assertThat(response.getStatus(), is(422));
        
        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(1));
        assertThat(listOfErrors, hasItem("may not be null"));
    }

    @Test
    public void shouldReturn422_whenPutToChargeStatus_emptyNewStatus() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(Collections.singletonMap("new_status", "")));

        assertEquals( 422, response.getStatus());
        
        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(2));
        assertThat(listOfErrors, hasItem("invalid new status"));
        assertThat(listOfErrors, hasItem("may not be empty"));

    }

    @Test
    public void shouldReturn422_whenPutToChargeStatus_invalidNewStatus() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(Collections.singletonMap("new_status", "invalid_status")));

        assertEquals(422, response.getStatus());

        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(1));
        assertThat(listOfErrors, hasItem("invalid new status"));
    }
}
