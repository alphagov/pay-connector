package uk.gov.pay.connector.charge.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.util.JsonMappingExceptionMapper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.mock;

@ExtendWith(DropwizardExtensionsSupport.class)
public class ChargesApiResourceTest {

    private static final ChargeService chargeService = mock(ChargeService.class);
    private static final ChargeExpiryService chargeExpiryService = mock(ChargeExpiryService.class);
    private static final GatewayAccountService gatewayAccountService = mock(GatewayAccountService.class);

    public static ResourceExtension resources = ResourceExtension.builder()
            .addResource(new ChargesApiResource(chargeService, chargeExpiryService, gatewayAccountService))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(ConstraintViolationExceptionMapper.class)
            .addProvider(JsonMappingExceptionMapper.class)
            .addProvider(ValidationExceptionMapper.class)
            .build();

    @Test
    void createCharge_invalidAuthorisationMode_shouldReturn400() {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "return_url", "http://service.url/success-page/",
                "authorisation_mode", "foo"
        );

        Response response = resources
                .target("/v1/api/accounts/1/charges")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(400));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages().get(0), startsWith("Cannot deserialize value of type `uk.gov.service.payments.commons.model.AuthorisationMode`"));
        assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
    }

    @Test
    void createCharge_idempotencyKeyAboveMaxLength_shouldReturn422() {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "authorisation_mode", "agreement",
                "agreement_id", "agreement12345677890123456"
        );

        Response response = resources
                .target("/v1/api/accounts/1/charges")
                .request()
                .header("Idempotency-Key", "a".repeat(256))
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), contains("Header [Idempotency-Key] can have a size between 1 and 255"));
        assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
    }

    @Test
    void createCharge_idempotencyKeyEmpty_shouldReturn422() {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "authorisation_mode", "agreement",
                "agreement_id", "agreement12345677890123456"
        );

        Response response = resources
                .target("/v1/api/accounts/1/charges")
                .request()
                .header("Idempotency-Key", "")
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(errorResponse.getMessages(), contains("Header [Idempotency-Key] can have a size between 1 and 255"));
        assertThat(errorResponse.getIdentifier(), is(ErrorIdentifier.GENERIC));
    }
}
