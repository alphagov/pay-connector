package uk.gov.pay.connector.it.resources;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.common.model.domain.NumbersInStringsSanitizer.sanitize;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.randomAlphanumeric;

public class ChargesApiResourceCreatePrefilledCardholderDetailsIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    private static final String VALID_SERVICE_ID = "valid-service-id";
    private String gatewayAccountId;

    @BeforeEach
    void setup() {
        gatewayAccountId = app.givenSetup()
                .body(toJson(Map.of(
                        "service_id", VALID_SERVICE_ID,
                        "type", GatewayAccountType.TEST,
                        "payment_provider", PaymentGatewayName.SANDBOX.getName(),
                        "service_name", "my-test-service-name"
                )))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201)
                .extract().path("gateway_account_id");
    }

    @Nested
    class ByGatewayAccountId {

        @Test
        void shouldReturn201_withAllPrefilledCardHolderDetailsFields() {
            String cardholderName = "Joe Bogs";
            String line1 = "Line 1";
            String line2 = "Line 2";
            String city = "City";
            String postcode = "AB1 CD2";
            String country = "GB";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName,
                                    "billing_address", Map.of(
                                            "line1", line1,
                                            "line2", line2,
                                            "city", city,
                                            "postcode", postcode,
                                            "country", country
                                    ))
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address.line1", is(line1))
                    .body("card_details.billing_address.line2", is(line2))
                    .body("card_details.billing_address.city", is(city))
                    .body("card_details.billing_address.postcode", is(postcode))
                    .body("card_details.billing_address.country", is(country));
        }


        @Test
        void shouldReturn201_withPrefilledCardHolderDetailsFieldsAtMaximumLength() {
            String cardholderName = randomAlphanumeric(255);
            String line1 = randomAlphanumeric(255);
            String line2 = randomAlphanumeric(255);
            String city = randomAlphanumeric(255);
            String postcode = randomAlphanumeric(25);
            String country = "GB";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName,
                                    "billing_address", Map.of(
                                            "line1", line1,
                                            "line2", line2,
                                            "city", city,
                                            "postcode", postcode,
                                            "country", country
                                    ))
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address.line1", is(sanitize(line1))) // where there are more than 10 digits in the field, the digits are replaced by asterisks in the response
                    .body("card_details.billing_address.line2", is(sanitize(line2)))
                    .body("card_details.billing_address.city", is(sanitize(city)))
                    .body("card_details.billing_address.postcode", is(sanitize(postcode)))
                    .body("card_details.billing_address.country", is(country));
        }

        @Test
        void shouldReturn201_withSomePrefilledCardHolderDetailsFields() {
            String cardholderName = "Joe Bogs";
            String line1 = "Line 1";
            String postcode = "AB1 CD2";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName,
                                    "billing_address", Map.of(
                                            "line1", line1,
                                            "postcode", postcode
                                    ))
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address.line1", is(line1))
                    .body("card_details.billing_address.line2", is(nullValue()))
                    .body("card_details.billing_address.city", is(nullValue()))
                    .body("card_details.billing_address.postcode", is(postcode))
                    .body("card_details.billing_address.country", is(nullValue()));
        }

        @Test
        void shouldReturn201AndNoCountryWhenSuppliedCountryIsTooLong() {
            String cardholderName = "Joe Bogs";
            String line1 = "Line 1";
            String postcode = "AB1 CD2";
            String countryThatIsTooLong = "GBR";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName,
                                    "billing_address", Map.of(
                                            "line1", line1,
                                            "postcode", postcode,
                                            "country", countryThatIsTooLong
                                    ))
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address.line1", is(line1))
                    .body("card_details.billing_address.line2", is(nullValue()))
                    .body("card_details.billing_address.city", is(nullValue()))
                    .body("card_details.billing_address.postcode", is(postcode))
                    .body("card_details.billing_address.country", is(nullValue()));
        }

        @Test
        void shouldReturn201WithNoBillingAddressWhenPrefilledCardHolderDetailsFieldsContainsCardHolderNameOnly() {
            String cardholderName = "Joe Bogs";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName
                            )
                    )))
                    .post(format("/v1/api/accounts/%s/charges", gatewayAccountId))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address", is(nullValue()));
        }
    }

    @Nested
    class ByServiceIdAndAccountType {

        @Test
        void shouldReturn201_withAllPrefilledCardHolderDetailsFields() {
            String cardholderName = "Joe Bogs";
            String line1 = "Line 1";
            String line2 = "Line 2";
            String city = "City";
            String postcode = "AB1 CD2";
            String country = "GB";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName,
                                    "billing_address", Map.of(
                                            "line1", line1,
                                            "line2", line2,
                                            "city", city,
                                            "postcode", postcode,
                                            "country", country
                                    ))
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address.line1", is(line1))
                    .body("card_details.billing_address.line2", is(line2))
                    .body("card_details.billing_address.city", is(city))
                    .body("card_details.billing_address.postcode", is(postcode))
                    .body("card_details.billing_address.country", is(country));
        }


        @Test
        void shouldReturn201_withPrefilledCardHolderDetailsFieldsAtMaximumLength() {
            String cardholderName = randomAlphanumeric(255);
            String line1 = randomAlphanumeric(255);
            String line2 = randomAlphanumeric(255);
            String city = randomAlphanumeric(255);
            String postcode = randomAlphanumeric(25);
            String country = "GB";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName,
                                    "billing_address", Map.of(
                                            "line1", line1,
                                            "line2", line2,
                                            "city", city,
                                            "postcode", postcode,
                                            "country", country
                                    ))
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address.line1", is(sanitize(line1))) // where there are more than 10 digits in the field, the digits are replaced by asterisks in the response
                    .body("card_details.billing_address.line2", is(sanitize(line2)))
                    .body("card_details.billing_address.city", is(sanitize(city)))
                    .body("card_details.billing_address.postcode", is(sanitize(postcode)))
                    .body("card_details.billing_address.country", is(country));
        }

        @Test
        void shouldReturn201_withSomePrefilledCardHolderDetailsFields() {
            String cardholderName = "Joe Bogs";
            String line1 = "Line 1";
            String postcode = "AB1 CD2";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName,
                                    "billing_address", Map.of(
                                            "line1", line1,
                                            "postcode", postcode
                                    ))
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address.line1", is(line1))
                    .body("card_details.billing_address.line2", is(nullValue()))
                    .body("card_details.billing_address.city", is(nullValue()))
                    .body("card_details.billing_address.postcode", is(postcode))
                    .body("card_details.billing_address.country", is(nullValue()));
        }

        @Test
        void shouldReturn201AndNoCountryWhenSuppliedCountryIsTooLong() {
            String cardholderName = "Joe Bogs";
            String line1 = "Line 1";
            String postcode = "AB1 CD2";
            String countryThatIsTooLong = "GBR";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName,
                                    "billing_address", Map.of(
                                            "line1", line1,
                                            "postcode", postcode,
                                            "country", countryThatIsTooLong
                                    ))
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address.line1", is(line1))
                    .body("card_details.billing_address.line2", is(nullValue()))
                    .body("card_details.billing_address.city", is(nullValue()))
                    .body("card_details.billing_address.postcode", is(postcode))
                    .body("card_details.billing_address.country", is(nullValue()));
        }

        @Test
        void shouldReturn201WithNoBillingAddressWhenPrefilledCardHolderDetailsFieldsContainsCardHolderNameOnly() {
            String cardholderName = "Joe Bogs";

            app.givenSetup()
                    .body(toJson(Map.of(
                            "amount", 6234L,
                            "reference", "Test reference",
                            "description", "Test description",
                            "return_url", "http://service.local/success-page/",
                            "prefilled_cardholder_details", Map.of(
                                    "cardholder_name", cardholderName
                            )
                    )))
                    .post(format("/v1/api/service/%s/account/%s/charges", VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .contentType(JSON)
                    .log()
                    .body()
                    .body("charge_id", is(notNullValue()))
                    .body("reference", is("Test reference"))
                    .body("card_details.cardholder_name", is(cardholderName))
                    .body("card_details.billing_address", is(nullValue()));
        }
    }


}
