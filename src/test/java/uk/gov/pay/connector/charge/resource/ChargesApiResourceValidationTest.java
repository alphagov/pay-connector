package uk.gov.pay.connector.charge.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.pay.connector.charge.exception.InvalidAttributeValueExceptionMapper;
import uk.gov.pay.connector.charge.exception.MissingMandatoryAttributeExceptionMapper;
import uk.gov.pay.connector.charge.exception.UnexpectedAttributeExceptionMapper;
import uk.gov.pay.connector.common.exception.ConstraintViolationExceptionMapper;
import uk.gov.pay.connector.common.exception.ValidationExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.util.JsonMappingExceptionMapper;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.randomAlphabetic;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.randomAlphanumeric;

@ExtendWith(DropwizardExtensionsSupport.class)
public class ChargesApiResourceValidationTest {

    public static ResourceExtension chargesApiResource = ResourceExtension.builder()
            .addResource(new ChargesApiResource(null, null, null, null))
            .setRegisterDefaultExceptionMappers(false)
            .addProvider(ConstraintViolationExceptionMapper.class)
            .addProvider(JsonMappingExceptionMapper.class)
            .addProvider(ValidationExceptionMapper.class)
            .addProvider(MissingMandatoryAttributeExceptionMapper.class)
            .addProvider(UnexpectedAttributeExceptionMapper.class)
            .addProvider(InvalidAttributeValueExceptionMapper.class)
            .build();

    @DisplayName("Should return 400 if fields are missing")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn422_whenFieldsMissing(String url) {
        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(Map.of()))) {

            assertThat(response.getStatus(), is(422));
            List<String> errorResponseMessages = response.readEntity(ErrorResponse.class).messages();
            assertTrue(errorResponseMessages.contains("Field [amount] cannot be null"));
            assertTrue(errorResponseMessages.contains("Field [description] cannot be null"));
            assertTrue(errorResponseMessages.contains("Field [reference] cannot be null"));
        }
    }

    @DisplayName("Should return 422 if fields exceed maximum length")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn422_whenFieldsExceedMaxLength(String url) {
        var payload = Map.of(
                "amount", 6234L,
                "reference", randomAlphabetic(256),
                "description", randomAlphabetic(256),
                "email", randomAlphabetic(256),
                "return_url", "https://service.example/success-page/");

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertThat(response.getStatus(), is(422));
            List<String> errorResponseMessages = response.readEntity(ErrorResponse.class).messages();
            assertTrue(errorResponseMessages.contains("Field [email] can have a size between 0 and 254"));
            assertTrue(errorResponseMessages.contains("Field [description] can have a size between 0 and 255"));
            assertTrue(errorResponseMessages.contains("Field [reference] can have a size between 0 and 255"));
        }
    }

    @DisplayName("Should return 404 if accountId is not numeric")
    @Test
    void shouldReturn404WhenCreatingChargeAccountIdIsNonNumeric() {
        var payload = Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "email", "test@example.com",
                "return_url", "https://service.example/success-page/");

        try (Response response = chargesApiResource
                .target("/v1/api/accounts/invalidAccountId/charges")
                .request()
                .post(Entity.json(payload))) {

            assertThat(response.getStatus(), is(404));
        }
    }


    @DisplayName("Should return 400 if language is not supported")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn400WhenLanguageNotSupported(String url) {
        var payload = Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "return_url", "https://service.example/success-page/",
                "language", "not a supported language");

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 400, "not a supported language");
        }
    }

    @DisplayName("Should return 422 if metadata is correct type but invalid values")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn422ForInvalidMetadata(String url) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", null);
        metadata.put("key2", new HashMap<>());
        metadata.put("", "validValue");
        metadata.put("key3", "");
        metadata.put("key4", IntStream.rangeClosed(1, ExternalMetadata.MAX_VALUE_LENGTH + 1).mapToObj(i -> "v").collect(joining()));
        metadata.put(IntStream.rangeClosed(1, ExternalMetadata.MAX_KEY_LENGTH + 1).mapToObj(i -> "k").collect(joining()), "This is valid");

        var payload = Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "return_url", "https://service.example/success-page/",
                "metadata", metadata
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertThat(response.getStatus(), is(422));

            List<String> errorResponseMessages = response.readEntity(ErrorResponse.class).messages();
            assertTrue(errorResponseMessages.contains("Field [metadata] must not have null values"));
            assertTrue(errorResponseMessages.contains("Field [metadata] values must be of type String, Boolean or Number"));
            assertTrue(errorResponseMessages.contains("Field [metadata] values must be no greater than " + ExternalMetadata.MAX_VALUE_LENGTH + " characters long"));
            assertTrue(errorResponseMessages.contains("Field [metadata] keys must be between " + ExternalMetadata.MIN_KEY_LENGTH + " and " + ExternalMetadata.MAX_KEY_LENGTH
                    + " characters long"));
        }
    }

    @DisplayName("Should return 400 if metadata is a string")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn400_whenMetadataIsAString(String url) {
        var payload = Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "return_url", "https://service.example/success-page/",
                "metadata", "metadata cannot be a string"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 400, "Field [metadata] must be an object of JSON key-value pairs");
        }
    }

    @DisplayName("Should return 400 if metadata is an array")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn400_whenMetadataIsAnArray(String url) {
        var payload = Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "return_url", "https://service.example/success-page/",
                "metadata", new Object[1]
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 400, "Field [metadata] must be an object of JSON key-value pairs");
        }
    }

    @DisplayName("Should return 400 if authorisation mode is invalid")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void invalidAuthorisationMode_shouldReturn400(String url) {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "return_url", "https://service.example/success-page/",
                "authorisation_mode", "foo"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 400, "Cannot deserialize value of type `uk.gov.service.payments.commons.model.AuthorisationMode`");
        }
    }

    @DisplayName("Should return 400 if authorisation_payment_type is invalid for initial payment")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void invalidAuthorisationPaymentTypeForInitialPayment_shouldReturn400(String url) {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "return_url", "https://service.example/success-page/",
                "save_payment_instrument_to_agreement", true,
                "agreement_id", "agreement_id",
                "agreement_payment_type", "fantasy-value"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 400, "Cannot deserialize value of type `uk.gov.service.payments.commons.model.AgreementPaymentType`");
        }
    }

    @DisplayName("Should return 400 if authorisation_payment_type is invalid for subsequent payment")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void invalidAuthorisationPaymentTypeForSubsequentPayment_shouldReturn400(String url) {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "return_url", "https://service.example/success-page/",
                "agreement_id", "agreement_id",
                "agreement_payment_type", "fantasy-value"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 400, "Cannot deserialize value of type `uk.gov.service.payments.commons.model.AgreementPaymentType`");
        }
    }

    @DisplayName("Should return 422 if idempotency key is above maximum length")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void idempotencyKeyAboveMaxLength_shouldReturn422(String url) {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "authorisation_mode", "agreement",
                "agreement_id", "agreement12345677890123456"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .header("Idempotency-Key", "a".repeat(256))
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 422, "Header [Idempotency-Key] can have a size between 1 and 255");
        }
    }

    @DisplayName("Should return 422 if idempotency key is empty")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void idempotencyKeyEmpty_shouldReturn422(String url) {
        var payload = Map.of(
                "amount", 100,
                "reference", "ref",
                "description", "desc",
                "authorisation_mode", "agreement",
                "agreement_id", "agreement12345677890123456"
        );

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .header("Idempotency-Key", "")
                .post(Entity.json(payload))) {

            assertGenericErrorResponse(response, 422, "Header [Idempotency-Key] can have a size between 1 and 255");
        }
    }

    @DisplayName("Should return 422 if return url is missing")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn422WhenReturnUrlIsMissing(String url) {

        String payload = toJson(Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description"
        ));

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            assertThat(response.getStatus(), is(422));
            assertThat(errorResponse.messages(), contains("Missing mandatory attribute: return_url"));
        }
    }

    @DisplayName("Should return 422 if return url is an empty string")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn422WhenReturnUrlIsEmptyString(String url) {

        String payload = toJson(Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "return_url", ""
        ));

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            assertThat(response.getStatus(), is(422));
            assertThat(errorResponse.messages(), contains("Missing mandatory attribute: return_url"));
        }
    }

    @DisplayName("Should return 422 if return url is not a valid format")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn422WhenReturnUrlIsNotValid(String url) {

        String payload = toJson(Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "return_url", "not.a.valid.url"
        ));

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            assertThat(response.getStatus(), is(422));
            assertThat(errorResponse.messages(), contains("Invalid attribute value: return_url. Must be a valid URL format"));
        }
    }

    @DisplayName("Should return 422 if return url is present and authorisation mode is moto api")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn422WhenReturnUrlIsPresentAndAuthorisationModeMotoApi(String url) {

        String payload = toJson(Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "return_url", "https://i.should.not.be.here.co.uk",
                "authorisation_mode", "moto_api"
        ));

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            assertThat(response.getStatus(), is(422));
            assertThat(errorResponse.messages(), contains("Unexpected attribute: return_url"));
        }
    }

    @DisplayName("Should return 400 if source value is invalid")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn400IfSourceValueIsInvalid(String url) {

        String payload = toJson(Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "source", "invalid-source-value"
        ));

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            assertThat(response.getStatus(), is(400));
            assertThat(errorResponse.messages(), contains("Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO"));
        }
    }

    @DisplayName("Should return 400 if source type is invalid")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn400IfSourceTypeIsInvalid(String url) {

        String payload = toJson(Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "source", true
        ));

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            assertThat(response.getStatus(), is(400));
            assertThat(errorResponse.messages(), contains("Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO"));
        }
    }

    @DisplayName("Should return 422 if prefilled cardholder details exceed maximum length]")
    @ParameterizedTest
    @ValueSource(strings = {
            "/v1/api/accounts/1234/charges",
            "/v1/api/service/my-service-id/account/test/charges"
    })
    void shouldReturn422IfPrefilledCardHolderDetailsExceedMaximumLength(String url) {
        String cardholderName = randomAlphanumeric(256);
        String line1 = randomAlphanumeric(256);
        String line2 = randomAlphanumeric(256);
        String city = randomAlphanumeric(256);
        String postcode = randomAlphanumeric(26);
        String country = "GB";

        String payload = toJson(Map.of(
                "amount", 6234L,
                "reference", "Test reference",
                "description", "Test description",
                "return_url", "https://service.example/success-page/",
                "prefilled_cardholder_details", Map.of(
                        "cardholder_name", cardholderName,
                        "billing_address", Map.of(
                                "line1", line1,
                                "line2", line2,
                                "city", city,
                                "postcode", postcode,
                                "country", country
                        ))
        ));

        try (Response response = chargesApiResource
                .target(url)
                .request()
                .post(Entity.json(payload))) {

            ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
            assertThat(response.getStatus(), is(422));
            List<String> errorMessages = errorResponse.messages();
            assertTrue(errorMessages.contains("Field [cardholder_name] can have a size between 0 and 255"));
            assertTrue(errorMessages.contains("Field [line1] can have a size between 0 and 255"));
            assertTrue(errorMessages.contains("Field [line2] can have a size between 0 and 255"));
            assertTrue(errorMessages.contains("Field [city] can have a size between 0 and 255"));
            assertTrue(errorMessages.contains("Field [postcode] can have a size between 0 and 25"));
        }
    }

    private static void assertGenericErrorResponse(Response response, int status, String errorMessage) {
        ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
        assertThat(response.getStatus(), is(status));
        assertThat(errorResponse.identifier(), is(ErrorIdentifier.GENERIC));
        assertThat(errorResponse.messages().stream().anyMatch(message -> message.contains(errorMessage)), is(true));
    }

}
