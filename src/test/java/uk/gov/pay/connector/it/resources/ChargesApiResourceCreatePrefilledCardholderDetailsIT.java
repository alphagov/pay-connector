package uk.gov.pay.connector.it.resources;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
@Ignore
public class ChargesApiResourceCreatePrefilledCardholderDetailsIT extends ChargingITestBase {

    private static final String JSON_PREFILLED_CARDHOLDER_DETAILS_KEY = "prefilled_cardholder_details";
    private static final String JSON_BILLING_ADDRESS_KEY = "billing_address";
    private static final String JSON_ADDRESS_LINE_1_KEY = "line1";
    private static final String JSON_ADDRESS_LINE_2_KEY = "line2";
    private static final String JSON_ADDRESS_POST_CODE_KEY = "postcode";
    private static final String JSON_CARDHOLDER_NAME_KEY = "cardholder_name";
    private static final String JSON_ADDRESS_LINE_CITY = "city";
    private static final String JSON_ADDRESS_LINE_COUNTRY_CODE = "country";

    public ChargesApiResourceCreatePrefilledCardholderDetailsIT() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldReturn201WhenPrefilledCardHolderDetailsFieldsAreMaximum() {
        String line1 = randomAlphanumeric(255);
        String city = randomAlphanumeric(255);
        String postCode = randomAlphanumeric(25);
        String countryCode = "GB";

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, randomAlphanumeric(255),
                        JSON_BILLING_ADDRESS_KEY, Map.of(
                                JSON_ADDRESS_LINE_1_KEY, line1,
                                JSON_ADDRESS_LINE_CITY, city,
                                JSON_ADDRESS_POST_CODE_KEY, postCode,
                                JSON_ADDRESS_LINE_COUNTRY_CODE, countryCode
                        )
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL));
    }

    @Test
    public void shouldReturn201WithAllPrefilledCardHolderDetailsFields() {
        String cardholderName = "Joe Bogs";
        String line1 = "Line 1";
        String city = "City";
        String postCode = "AB1 CD2";
        String countryCode = "GB";

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, cardholderName,
                        JSON_BILLING_ADDRESS_KEY, Map.of(
                                JSON_ADDRESS_LINE_1_KEY, line1,
                                JSON_ADDRESS_LINE_CITY, city,
                                JSON_ADDRESS_POST_CODE_KEY, postCode,
                                JSON_ADDRESS_LINE_COUNTRY_CODE, countryCode
                        )
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("card_details." + JSON_CARDHOLDER_NAME_KEY, is(cardholderName))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_1_KEY, is(line1))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_2_KEY, is(nullValue()))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_CITY, is(city))
                .body("card_details.billing_address." + JSON_ADDRESS_POST_CODE_KEY, is(postCode))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_COUNTRY_CODE, is(countryCode));
    }

    @Test
    public void shouldReturn201WithSomePrefilledCardHolderDetailsFields() {
        String cardholderName = "Joe Bogs";
        String line1 = "Line 1";
        String postCode = "AB1 CD2";

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, cardholderName,
                        JSON_BILLING_ADDRESS_KEY, Map.of(
                                JSON_ADDRESS_LINE_1_KEY, line1,
                                JSON_ADDRESS_POST_CODE_KEY, postCode
                        )
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("card_details." + JSON_CARDHOLDER_NAME_KEY, is(cardholderName))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_1_KEY, is(line1))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_2_KEY, is(nullValue()))
                .body("card_details.billing_address." + JSON_ADDRESS_POST_CODE_KEY, is(postCode))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_CITY, is(nullValue()))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_COUNTRY_CODE, is(nullValue()));
    }

    @Test
    public void shouldReturn201AndNoCountryWhenSuppliedCountryIsTooLong() {
        String cardholderName = "Joe Bogs";
        String line1 = "Line 1";
        String postCode = "AB1 CD2";
        String countryThatIsTooLong = "GBR";

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, cardholderName,
                        JSON_BILLING_ADDRESS_KEY, Map.of(
                                JSON_ADDRESS_LINE_1_KEY, line1,
                                JSON_ADDRESS_POST_CODE_KEY, postCode,
                                JSON_ADDRESS_LINE_COUNTRY_CODE, countryThatIsTooLong
                        )
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("card_details." + JSON_CARDHOLDER_NAME_KEY, is(cardholderName))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_1_KEY, is(line1))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_2_KEY, is(nullValue()))
                .body("card_details.billing_address." + JSON_ADDRESS_POST_CODE_KEY, is(postCode))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_CITY, is(nullValue()))
                .body("card_details.billing_address." + JSON_ADDRESS_LINE_COUNTRY_CODE, is(nullValue()));
    }

    @Test
    public void shouldReturn201WithNoCardDetailsWhenPrefilledCardHolderDetailsFieldsAreNotPresent() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("containsKey('card_details')", is(false));
    }

    @Test
    public void shouldReturn201WithBillingAddresssWhenPrefilledCardHolderDetailsFieldsContainsCardHolderNameOnly() {
        String cardholderName = "Joe Bogs";
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_PREFILLED_CARDHOLDER_DETAILS_KEY, Map.of(
                        JSON_CARDHOLDER_NAME_KEY, cardholderName
                )
        ));

        connectorRestApiClient.postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(AMOUNT))
                .body(JSON_REFERENCE_KEY, is(JSON_REFERENCE_VALUE))
                .body(JSON_DESCRIPTION_KEY, is(JSON_DESCRIPTION_VALUE))
                .body(JSON_PROVIDER_KEY, is(PROVIDER_NAME))
                .body(JSON_RETURN_URL_KEY, is(RETURN_URL))
                .body("card_details." + JSON_CARDHOLDER_NAME_KEY, is(cardholderName))
                .body("containsKey('billing_address')", is(false));
    }

}
