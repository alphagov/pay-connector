package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.util.PGobject;
import uk.gov.pay.connector.charge.util.ExternalMetadataConverter;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static io.restassured.http.ContentType.JSON;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.EMAIL;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_CHARGE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_EMAIL_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_MESSAGE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_METADATA_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_RETURN_URL_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.RETURN_URL;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.JsonEncoder.toJsonWithNulls;

public class ChargesApiResourceCreateMetadataIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = AppWithPostgresAndSqsExtension.withPersistence();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    @Test
    void shouldReturnChargeWithNoMetadataField_whenCreatedWithEmptyMetadata() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, Map.of()
        ));

        ValidatableResponse response = testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body("$", not(hasKey("metadata")));

        String chargeExternalId = response.extract().path(JSON_CHARGE_KEY);
        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeExternalId)
                .getCharge()
                .body("$", not(hasKey("metadata")));

        assertNull(app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId).get("metadata"));
    }

    @Test
    void shouldCreateChargeWithExternalMetadata() {
        Map<String, Object> metadata = Map.of(
                "key1", "string",
                "key2", true,
                "key3", 123,
                "key4", 1.23
        );

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, metadata
        ));

        ValidatableResponse response = testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_METADATA_KEY + ".key1", is("string"))
                .body(JSON_METADATA_KEY + ".key2", is(true))
                .body(JSON_METADATA_KEY + ".key3", is(123));

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);
        Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(externalChargeId);
        ExternalMetadataConverter converter = new ExternalMetadataConverter();
        ExternalMetadata externalMetadata = converter.convertToEntityAttribute((PGobject) charge.get("external_metadata"));

        assertThat(externalMetadata.getMetadata(), equalTo(metadata));
    }

    @Test
    void shouldReturn422ForInvalidMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key1", null);
        metadata.put("key2", new HashMap<>());
        metadata.put("", "validValue");
        metadata.put("key3", "");
        metadata.put("key4", IntStream.rangeClosed(1, ExternalMetadata.MAX_VALUE_LENGTH + 1).mapToObj(i -> "v").collect(joining()));
        metadata.put(IntStream.rangeClosed(1, ExternalMetadata.MAX_KEY_LENGTH + 1).mapToObj(i -> "k").collect(joining()), "This is valid");

        String postBody = toJsonWithNulls(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, metadata
        ));

        testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, containsInAnyOrder(
                        "Field [metadata] values must be of type String, Boolean or Number",
                        "Field [metadata] keys must be between " + ExternalMetadata.MIN_KEY_LENGTH + " and " + ExternalMetadata.MAX_KEY_LENGTH
                                + " characters long",
                        "Field [metadata] must not have null values",
                        "Field [metadata] values must be no greater than " + ExternalMetadata.MAX_VALUE_LENGTH + " characters long"));
    }

    @Test
    void shouldReturn201IfMetadataIsNull_BecauseWeDoNotDeserializeNullValues() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(JSON_AMOUNT_KEY, AMOUNT);
        payload.put(JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE);
        payload.put(JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE);
        payload.put(JSON_RETURN_URL_KEY, RETURN_URL);
        payload.put(JSON_EMAIL_KEY, EMAIL);
        payload.put(JSON_METADATA_KEY, null);

        String postBody = toJsonWithNulls(payload);

        testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(201)
                .contentType(JSON);
    }

    @Test
    void shouldFailValidationWhenMetadataIsAString() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, "metadata cannot be a string"
        ));

        testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(400)
                .contentType(JSON)
                .body("message", contains("Field [metadata] must be an object of JSON key-value pairs"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void shouldFailValidationWhenMetadataIsAnArray() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_METADATA_KEY, new Object[1]
        ));

        testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(400)
                .contentType(JSON)
                .body("message", contains("Field [metadata] must be an object of JSON key-value pairs"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

}
