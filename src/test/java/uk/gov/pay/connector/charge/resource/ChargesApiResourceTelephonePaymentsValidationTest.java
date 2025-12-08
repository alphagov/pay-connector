package uk.gov.pay.connector.charge.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.util.JsonMappingExceptionMapper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(DropwizardExtensionsSupport.class)
public class ChargesApiResourceTelephonePaymentsValidationTest {

    public static ResourceExtension chargesApiResource = ResourceExtension.builder()
            .addResource(new ChargesApiResource(null, null, null, null))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(ConstraintViolationExceptionMapper.class)
            .addProvider(JsonMappingExceptionMapper.class)
            .build();

    @DisplayName("Should return 400 for an invalid card expiry date")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn400ForInvalidCardExpiryDate(String url) {
        var payload = Map.of("amount", 12000,
                "reference", "MRPC12345",
                "description", "New passport application",
                "processor_id", "183f2j8923j8",
                "provider_id", "17498-8412u9-1273891239",
                "payment_outcome", Map.of("status", "success"),
                "card_expiry", "99/99"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 400, "Not in MM/yy format");
        }
    }

    @DisplayName("Should return 422 for invalid card type")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn422ForInvalidCardType(String url) {
        var payload = Map.of("amount", 12000,
                "reference", "MRPC12345",
                "description", "New passport application",
                "processor_id", "183f2j8923j8",
                "provider_id", "17498-8412u9-1273891239",
                "payment_outcome", Map.of("status", "success"),
                "card_type", "invalid-card"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 422, "Field [card_type] must be either master-card, visa, maestro, diners-club, american-express or jcb");
        }
    }

    @DisplayName("Should return 422 for invalid payment outcome status")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn422ForInvalidPaymentOutcomeStatus(String url) {
        var payload = Map.of("amount", 12000,
                "reference", "MRPC12345",
                "description", "New passport application",
                "processor_id", "183f2j8923j8",
                "provider_id", "17498-8412u9-1273891239",
                "payment_outcome", Map.of(
                        "status", "invalid"
                ));
        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 422, "Field [payment_outcome] must include a valid status and error code");
        }
    }

    @DisplayName("Should return 422 for invalid payment outcome error code")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn422ForInvalidPaymentOutcomeErrorCode(String url) {
        var payload = Map.of("amount", 12000,
                "reference", "MRPC12345",
                "description", "New passport application",
                "processor_id", "183f2j8923j8",
                "provider_id", "17498-8412u9-1273891239",
                "payment_outcome", Map.of(
                        "status", "failed",
                        "code", "error",
                        "supplemental", Map.of(
                                "error_code", "ECKOH01234",
                                "error_message", "textual message describing error code"
                        )
                ));

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 422, "Field [payment_outcome] must include a valid status and error code");
        }
    }

    @DisplayName("Should return 422 for invalid combination of payment outcome status and code")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn422ForInvalidPaymentOutcomeStatusAndCode(String url) {
        var payload = Map.of("amount", 12000,
                "reference", "MRPC12345",
                "description", "New passport application",
                "processor_id", "183f2j8923j8",
                "provider_id", "17498-8412u9-1273891239",
                "payment_outcome", Map.of(
                        "status", "success",
                        "code", "P0010"
                ));
        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 422, "Field [payment_outcome] must include a valid status and error code");
        }
    }

    @DisplayName("Should return 422 for invalid created date")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn422ForInvalidCreatedDate(String url) {
        var payload = Map.of("amount", 12000,
                "reference", "MRPC12345",
                "description", "New passport application",
                "processor_id", "183f2j8923j8",
                "provider_id", "17498-8412u9-1273891239",
                "payment_outcome", Map.of("status", "success"),
                "created_date", "invalid"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 422, "Field [created_date] must be a valid ISO-8601 time and date format");
        }
    }

    @DisplayName("Should return 422 for invalid authorised date")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn422ForInvalidAuthorisedDate(String url) {
        var payload = Map.of("amount", 12000,
                "reference", "MRPC12345",
                "description", "New passport application",
                "processor_id", "183f2j8923j8",
                "provider_id", "17498-8412u9-1273891239",
                "payment_outcome", Map.of("status", "success"),
                "authorised_date", "invalid"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 422, "Field [authorised_date] must be a valid ISO-8601 time and date format");
        }
    }

    @DisplayName("Should return 422 if mandatory fields are missing")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn422ForMissingFields(String url) {
        var payload = Map.of();

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertThat(response.getStatus(), is(422));
            List<String> errorResponseMessages = response.readEntity(ErrorResponse.class).messages();
            assertTrue(errorResponseMessages.contains("Field [amount] cannot be null"));
            assertTrue(errorResponseMessages.contains("Field [description] cannot be null"));
            assertTrue(errorResponseMessages.contains("Field [reference] cannot be null"));
            assertTrue(errorResponseMessages.contains("Field [processor_id] cannot be null"));
            assertTrue(errorResponseMessages.contains("Field [provider_id] cannot be null"));
            assertTrue(errorResponseMessages.contains("Field [payment_outcome] cannot be null"));
        }
    }

    @DisplayName("Should return 422 if request body is empty")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/telephone-charges",
            "/v1/api/service/my-service-id/account/test/telephone-charges"
    })
    public void shouldReturn422ForNullRequestBody(String url) {
        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(""))) {

            assertGenericErrorResponse(response, 422, "must not be null");
        }
    }

    private static void assertGenericErrorResponse(Response response, int status, String errorMessage) {
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(response.getStatus(), is(status));
        assertThat(errorResponse.identifier(), is(ErrorIdentifier.GENERIC));
        var errorIsPresentInMessages = errorResponse
                .messages()
                .stream()
                .anyMatch(message -> message
                        .contains(errorMessage));
        assertThat(errorIsPresentInMessages, is(true));
    }
}
