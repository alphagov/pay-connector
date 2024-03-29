package uk.gov.pay.connector.agreement.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;

@ExtendWith(DropwizardExtensionsSupport.class)
class AgreementsApiResourceTest {

    private final static String RESOURCE_URL = "/v1/api/accounts/%d/agreements";

    private final static long VALID_ACCOUNT_ID = 123l;

    private final static long NOT_VALID_ACCOUNT_ID = 9876l;

    private static final String REFERENCE_ID = "1234";
    private static final String VALID_DESCRIPTION = "a valid description";

    private AgreementService agreementService = mock(AgreementService.class);
    
    private final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new AgreementsApiResource(agreementService))
            .build();

    @Test
    void shouldReturn200_whenPostToAgreement() {
        AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest("1234", VALID_DESCRIPTION, null);
        AgreementResponse agreementResponse = new AgreementResponseBuilder().withAgreementId("aid").withReference(REFERENCE_ID).withServiceId("serviceid").withCreatedDate(Instant.parse("2022-03-03T12:00:00Z")).withDescription(VALID_DESCRIPTION).build();
        when(agreementService.create(agreementCreateRequest, VALID_ACCOUNT_ID)).thenReturn(Optional.ofNullable(agreementResponse));

        Map payloadMap = Map.of("reference", REFERENCE_ID, "description", VALID_DESCRIPTION);

        Response response = resources.client()
                .target(format(RESOURCE_URL, VALID_ACCOUNT_ID))
                .request()
                .post(Entity.json(payloadMap));

        assertThat(response.getStatus(), is(CREATED_201));
    }

    @Test
    void shouldReturn404_whenPostWithUnknownAccountId() {
        Map payloadMap = Map.of("reference", REFERENCE_ID, "description", VALID_DESCRIPTION);

        Response response = resources.client()
                .target(format(RESOURCE_URL, NOT_VALID_ACCOUNT_ID))
                .request()
                .post(Entity.json(payloadMap));

        assertThat(response.getStatus(), is(NOT_FOUND_404));
    }
}
