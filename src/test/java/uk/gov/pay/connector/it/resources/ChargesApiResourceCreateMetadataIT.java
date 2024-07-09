package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.util.PGobject;
import uk.gov.pay.connector.charge.util.ExternalMetadataConverter;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.EMAIL;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_CHARGE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_EMAIL_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_METADATA_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_RETURN_URL_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.RETURN_URL;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.JsonEncoder.toJsonWithNulls;

public class ChargesApiResourceCreateMetadataIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());
    
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

}
