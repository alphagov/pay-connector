package uk.gov.pay.connector.agreement.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.model.builder.AgreementResponseBuilder;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
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

    private static final String RESOURCE_URL = "/v1/api/accounts/%d/agreements";
    private static final String RESOURCE_BY_SERVICE_ID_URL = "/v1/api/service/%s/account/%s/agreements";
    private static final long VALID_ACCOUNT_ID = 123l;
    private static final long NOT_VALID_ACCOUNT_ID = 9876l;
    private static final String REFERENCE_ID = "1234";
    private static final String VALID_DESCRIPTION = "a valid description";
    public static final String VALID_SERVICE_ID = "a-valid-service-id";

    private AgreementService agreementService = mock(AgreementService.class);

    private final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new AgreementsApiResource(agreementService))
            .build();


    @Nested
    class CreateAgreementByGatewayAccountId {
        @Test
        void returnsSuccessfulResponse_forValidRequest() {
            when(agreementService.createByGatewayAccountId(aValidAgreementCreateRequest(), VALID_ACCOUNT_ID))
                    .thenReturn(Optional.ofNullable(aSuccessfulAgreementResponse()));

            Map payloadMap = Map.of("reference", REFERENCE_ID, "description", VALID_DESCRIPTION);
    
            Response response = resources.client()
                    .target(format(RESOURCE_URL, VALID_ACCOUNT_ID))
                    .request()
                    .post(Entity.json(payloadMap));
    
            assertThat(response.getStatus(), is(CREATED_201));
        }
    
        @Test
        void returnsNotFoundResponse_whenGatewayAccountNotFound() {
            when(agreementService.createByGatewayAccountId(aValidAgreementCreateRequest(), NOT_VALID_ACCOUNT_ID))
                    .thenThrow(new GatewayAccountNotFoundException("Gateway account [9876] not found"));
            Map payloadMap = Map.of("reference", REFERENCE_ID, "description", VALID_DESCRIPTION);
            
            Response response = resources.client()
                    .target(format(RESOURCE_URL, NOT_VALID_ACCOUNT_ID))
                    .request()
                    .post(Entity.json(payloadMap));
    
            assertThat(response.getStatus(), is(NOT_FOUND_404));
        }
    }

    @Nested
    class CreateAgreementByServiceIdAndAccountType {
        @Test
        void returnsSuccessfulResponse_forValidRequest() {
            when(agreementService.createByServiceIdAndAccountType(aValidAgreementCreateRequest(), VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .thenReturn(Optional.ofNullable(aSuccessfulAgreementResponse()));

            Map payloadMap = Map.of("reference", REFERENCE_ID, "description", VALID_DESCRIPTION);

            Response response = resources.client()
                    .target(format(RESOURCE_BY_SERVICE_ID_URL, VALID_SERVICE_ID, "test"))
                    .request()
                    .post(Entity.json(payloadMap));

            assertThat(response.getStatus(), is(CREATED_201));
        }

        @Test
        void returnsNotFoundResponse_whenGatewayAccountNotFound() {
            when(agreementService.createByServiceIdAndAccountType(aValidAgreementCreateRequest(), VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .thenThrow(new GatewayAccountNotFoundException((String.format("Gateway account not found for service external id [%s] and account type [%s]", VALID_SERVICE_ID, GatewayAccountType.TEST))));
            Map payloadMap = Map.of("reference", REFERENCE_ID, "description", VALID_DESCRIPTION);

            Response response = resources.client()
                    .target(format(RESOURCE_BY_SERVICE_ID_URL, VALID_SERVICE_ID, "test"))
                    .request()
                    .post(Entity.json(payloadMap));

            assertThat(response.getStatus(), is(NOT_FOUND_404));
        }
    }
    
    private AgreementCreateRequest aValidAgreementCreateRequest() {
        return new AgreementCreateRequest(REFERENCE_ID, VALID_DESCRIPTION, null);
    }
    
    private AgreementResponse aSuccessfulAgreementResponse() {
        return new AgreementResponseBuilder()
                .withAgreementId("an-agreement-id")
                .withReference(REFERENCE_ID)
                .withServiceId(VALID_SERVICE_ID)
                .withCreatedDate(Instant.parse("2022-03-03T12:00:00Z"))
                .withDescription(VALID_DESCRIPTION)
                .build();
    }
}

