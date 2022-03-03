package uk.gov.pay.connector.agreement.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.when;

import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;

@ExtendWith(DropwizardExtensionsSupport.class)
class AgreementResourceTest {

    private final static String RESOURCE_URL = "/v1/api/accounts/%d/agreements";

    private final static long VALID_ACCOUNT_ID = 123l;

    private final static long NOT_VALID_ACCOUNT_ID = 9876l;

    private static final String REFERENCE_ID = "1234";
    
    private static AgreementService agreementService = mock(AgreementService.class);
    
    private static final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new AgreementsApiResource(agreementService))
            .build();

    @Test
    void shouldReturn200_whenPostToAgreement() {
        AgreementResponse ar = new AgreementResponseBuilder().withAgreementId("aid").withReference(REFERENCE_ID).withServiceId("serviceid").withCreatedDate(Instant.now()).build();
        when(agreementService.create(any(AgreementCreateRequest.class), any(Long.class))).thenReturn(Optional.ofNullable(ar));

        Map myMap = Map.of("reference", REFERENCE_ID);

        Response response = resources.client()
                .target(format(RESOURCE_URL, VALID_ACCOUNT_ID))
                .request()
                .post(Entity.json(myMap));

        assertThat(response.getStatus(), is(OK_200));
    }

    @Test
    void shouldReturn404_whenPostWithUnknownAccountId() {
        NotFoundException nfe = new NotFoundException();
        AgreementResponse ar = new AgreementResponseBuilder().withAgreementId("aid").withReference(REFERENCE_ID).withServiceId("serviceid").withCreatedDate(Instant.now()).build();
        when(agreementService.create(any(AgreementCreateRequest.class), eq(NOT_VALID_ACCOUNT_ID))).thenThrow(nfe);

        Map myMap = Map.of("reference", REFERENCE_ID);

        Response response = resources.client()
                .target(format(RESOURCE_URL, NOT_VALID_ACCOUNT_ID))
                .request()
                .post(Entity.json(myMap));

        assertThat(response.getStatus(), is(NOT_FOUND_404));
    }
}
