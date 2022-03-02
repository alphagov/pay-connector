package uk.gov.pay.connector.app.agreement.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.eclipse.persistence.exceptions.ValidationException;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.pay.connector.agreement.resource.AgreementsApiResource;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;

@ExtendWith(DropwizardExtensionsSupport.class)
class AgreementResourceTest {
	
	private final static long ACCOUNT_ID = 123;
	private final static String RESOURCE_URL = "/v1/api/accounts/123/agreements";
	
    private static final Map<String, String> AGREEMENT_PAYLOAD = Map.of(
            "reference", "1234");
	
    private static AgreementService agreementService = mock(AgreementService.class);

    //private static ChargeDao chargeDao = mock(ChargeDao.class);
    //private static CardTypeDao cardTypeDao = mock(CardTypeDao.class);
    //private static Worldpay3dsFlexJwtService worldpay3dsFlexJwtService = mock(Worldpay3dsFlexJwtService.class);

    private static final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new AgreementsApiResource(agreementService))
            .build();



    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    protected GatewayAccountEntity gatewayAccount;

    @Mock
    protected AgreementDao mockedAgreementDao;

    @Mock
    protected AgreementResponse mockedAgreementResponse;

    @Mock
    protected GatewayAccountDao mockedGatewayAccountDao;
    
    
    @Test
    void shouldReturn201_whenPostToAgreement() {
        AgreementResponse ar = new AgreementResponseBuilder().withAgreementId("aid").withReference("1234").withServiceId("serviceid").withCreatedDate(Instant.now()).build();
        when(agreementService.create(any(AgreementCreateRequest.class), any(Long.class))).thenReturn(Optional.ofNullable(ar));

        AgreementCreateRequest agreementCreateRequest =  new AgreementCreateRequest("1234");
        String bodyPayload = toJson(agreementCreateRequest);
        System.out.println("payload " + bodyPayload);
        
        Map myMap = Map.of("reference", "1234");
        Entity e = Entity.json(agreementCreateRequest);

        Response response = resources.client()
                .target(RESOURCE_URL)
                .request()
                .post(Entity.json(myMap))
                //.accept("application/json")
                //.header("Content-Type", "application/json")
                //.post(e);
        ;
        assertThat(response.getStatus(), is(200));
        
        //List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        //assertThat(listOfErrors.size(), is(1));
        //assertThat(listOfErrors, hasItem("must not be null"));
        int a = 0;
    }

    @Ignore
    @Test
    void debugPost() {
        AgreementCreateRequest agreementCreateRequest =  new AgreementCreateRequest("1234");
        String bodyPayload = toJson(agreementCreateRequest);
        System.out.println("payload " + bodyPayload);

        Map myMap = Map.of("reference", "1234");
        Entity e = Entity.json(agreementCreateRequest);

        Response response = resources.client()
                .target("/v1/api/accounts/1234/agreements")
                .request()
                //.post(Entity.json(myMap))
                //.get()
                //.accept("application/json")
                //.header("Content-Type", "application/json")
                .post(e);
                ;
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
