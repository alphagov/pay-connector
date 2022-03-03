package uk.gov.pay.connector.agreement.resource;

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

    private final static String RESOURCE_URL = "/v1/api/accounts/123/agreements";

    private static final String REFERENCE_ID = "1234";

    private static AgreementService agreementService = mock(AgreementService.class);
    
    private static final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new AgreementsApiResource(agreementService))
            .build();

    @Mock
    private GatewayAccountEntity gatewayAccount;

    @Rule
    private MockitoRule rule = MockitoJUnit.rule();

    @Rule
    private ExpectedException thrown = ExpectedException.none();

    @Test
    void shouldReturn200_whenPostToAgreement() {
        AgreementResponse ar = new AgreementResponseBuilder().withAgreementId("aid").withReference(REFERENCE_ID).withServiceId("serviceid").withCreatedDate(Instant.now()).build();
        when(agreementService.create(any(AgreementCreateRequest.class), any(Long.class))).thenReturn(Optional.ofNullable(ar));

        AgreementCreateRequest agreementCreateRequest =  new AgreementCreateRequest(REFERENCE_ID);
        String bodyPayload = toJson(agreementCreateRequest);
        System.out.println("payload " + bodyPayload);
        
        Map myMap = Map.of("reference", REFERENCE_ID);

        Response response = resources.client()
                .target(RESOURCE_URL)
                .request()
                .post(Entity.json(myMap));
        
        assertThat(response.getStatus(), is(200));
    }
}
