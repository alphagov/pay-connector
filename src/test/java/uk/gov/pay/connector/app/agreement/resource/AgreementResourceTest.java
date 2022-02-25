package uk.gov.pay.connector.app.agreement.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import uk.gov.pay.connector.agreement.resource.AgreementsApiResource;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

import static uk.gov.pay.connector.util.JsonEncoder.toJson;

import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;

@ExtendWith(DropwizardExtensionsSupport.class)
class AgreementResourceTest {
	
	private final static long ACCOUNT_ID = 123;
	private final static String RESOURCE_URL = "/v1/api/accounts/123/agreements";
	
    private static final Map<String, String> AGREEMENT_PAYLOAD = Map.of(
            "reference", "1234");
	
    private static AgreementService agreementService = mock(AgreementService.class);

    private static ChargeService chargeService = mock(ChargeService.class);
    //private static ChargeDao chargeDao = mock(ChargeDao.class);
    //private static CardTypeDao cardTypeDao = mock(CardTypeDao.class);
    //private static Worldpay3dsFlexJwtService worldpay3dsFlexJwtService = mock(Worldpay3dsFlexJwtService.class);

    private static final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new AgreementsApiResource(agreementService))
            .build();

    @Test
    void shouldReturn201_whenPostToAgreement() {
        AgreementCreateRequest agreementCreateRequest =  new AgreementCreateRequest("1234");
        String bodyPayload = toJson(agreementCreateRequest);
        Map myMap = Map.of("reference", "1234");
        Entity e = Entity.json(agreementCreateRequest);
        System.out.println("**** "+bodyPayload);
        Response response = resources.client()
                .target(RESOURCE_URL)
                .request()
//                .post(Entity.json(agreementCreateRequest));
                .accept("application/json")
                //.header("Content-Type", "application/json")
                .post(e);

        assertThat(response.getStatus(), is(201));
        
        //List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        //assertThat(listOfErrors.size(), is(1));
        //assertThat(listOfErrors, hasItem("must not be null"));
        int a = 0;
    }
    
    /*
    @Test
    void shouldReturn400_whenPutToChargeStatus_emptyPayload() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(""));

        assertThat(response.getStatus(), is(422));
        
        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(1));
        assertThat(listOfErrors, hasItem("must not be null"));
    }

    @Test
    void shouldReturn422_whenPutToChargeStatus_emptyNewStatus() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(Collections.singletonMap("new_status", "")));

        assertThat(response.getStatus(), is(422));
        
        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(2));
        assertThat(listOfErrors, hasItem("invalid new status"));
        assertThat(listOfErrors, hasItem("may not be empty"));

    }

    @Test
    void shouldReturn422_whenPutToChargeStatus_invalidNewStatus() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(Collections.singletonMap("new_status", "invalid_status")));

        assertThat(response.getStatus(), is(422));

        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(1));
        assertThat(listOfErrors, hasItem("invalid new status"));
    }
    */
}
