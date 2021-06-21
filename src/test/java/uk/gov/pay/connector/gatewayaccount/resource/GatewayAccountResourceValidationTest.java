package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Test;
import uk.gov.pay.connector.common.validator.RequestValidator;
import uk.gov.pay.connector.gatewayaccountcredentials.resource.GatewayAccountCredentialsRequestValidator;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GatewayAccountResourceValidationTest {

    private static ObjectMapper objectMapper = new ObjectMapper();
    private static GatewayAccountCredentialsRequestValidator mockGatewayAccountCredentialsRequestValidator = mock(GatewayAccountCredentialsRequestValidator.class);

    static {
        when(mockGatewayAccountCredentialsRequestValidator.getMissingCredentialsFields(any(), any())).thenReturn(List.of());
    }

    @ClassRule
    public static ResourceTestRule resources = ResourceTestRuleWithCustomExceptionMappersBuilder.getBuilder()
            .addResource(new GatewayAccountResource(null, null,
                    null, null,
                    new GatewayAccountRequestValidator(new RequestValidator()), null,
                    null, mockGatewayAccountCredentialsRequestValidator))
            .build();

    @Test
    public void shouldReturn422_whenEverythingMissing() {
        Response response = resources.client()
                .target("/v1/api/accounts")
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(422));
    }

    @Test
    public void shouldReturn422_whenProviderAccountTypeIsInvalid() {

        Map<String, Object> payload = Map.of("type", "invalid");

        Response response = resources.client()
                .target("/v1/api/accounts")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Unsupported payment provider account type, should be one of (test, live)"));
    }

    @Test
    public void shouldReturn422_whenPaymentProviderIsInvalid() {

        Map<String, Object> payload = Map.of("payment_provider", "blockchain");

        Response response = resources.client()
                .target("/v1/api/accounts")
                .request()
                .post(Entity.json(payload));

        assertThat(response.getStatus(), is(422));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Unsupported payment provider value."));
    }

    @Test
    public void shouldReturn400_whenCorporateDebitCardSurchargeOperationIsInvalid() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of("op", "add",
                        "path", "corporate_debit_card_surcharge_amount",
                        "value", 250));
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Operation [add] is not valid for path [corporate_debit_card_surcharge_amount]", ErrorIdentifier.GENERIC);
    }

    @Test
    public void shouldReturn400_whenCorporatePrepaidCreditCardSurchargeOperationIsMissing() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of("path", "corporate_prepaid_credit_card_surcharge_amount", "value", 250));
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Field [op] is required", ErrorIdentifier.GENERIC);
    }

    @Test
    public void shouldReturn400_whenCorporateCreditCardSurchargeAmountIsNegativeValue() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of("op", "replace",
                        "path", "corporate_credit_card_surcharge_amount",
                        "value", -100));
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Value [-100] is not valid for path [corporate_credit_card_surcharge_amount]",
                ErrorIdentifier.GENERIC);
    }

    @Test
    public void shouldReturn400_whenCorporateDebditCardSurchargeAmountIsInvalidValue() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of("op", "replace",
                        "path", "corporate_debit_card_surcharge_amount",
                        "value", "not zero or a positive number that can be represented as a long"));
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Value [not zero or a positive number that can be represented as a long] is not valid for path [corporate_debit_card_surcharge_amount]",
                ErrorIdentifier.GENERIC);
    }

    @Test
    public void shouldReturn400_whenCorporatePrepaidCreditCardSurchargeAmountValueIsMissing() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of("op", "replace", "path", "corporate_prepaid_credit_card_surcharge_amount"));
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Field [value] is required", ErrorIdentifier.GENERIC);
    }

    @Test
    public void shouldReturn400_whenCorporateCreditCardSurchargeAmountValueIsEmpty() {
        JsonNode jsonNode = objectMapper.valueToTree(
                Map.of("op", "replace",
                        "path", "corporate_credit_card_surcharge_amount",
                        "value", ""));
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Value [] is not valid for path [corporate_credit_card_surcharge_amount]",
                ErrorIdentifier.GENERIC);
    }

    @Test
    public void shouldReturn400_whenCorporateDebitCardSurchargeAmountValueIsNull() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("op", "replace");
        valueMap.put("path", "corporate_prepaid_debit_card_surcharge_amount");
        valueMap.put("value", null);
        JsonNode jsonNode = objectMapper.valueToTree(valueMap);
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Field [value] is required", ErrorIdentifier.GENERIC);
    }

    @Test
    public void shouldReturn400_whenAllowZeroAmountValueIsNull() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("op", "replace");
        valueMap.put("path", "allow_zero_amount");
        valueMap.put("value", null);
        JsonNode jsonNode = objectMapper.valueToTree(valueMap);
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Field [value] is required", ErrorIdentifier.GENERIC);
    }

    @Test
    public void shouldReturn400_whenAllowZeroAmountValueIsNotBooleanValue() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("op", "replace");
        valueMap.put("path", "allow_zero_amount");
        valueMap.put("value", "false");
        JsonNode jsonNode = objectMapper.valueToTree(valueMap);
        Response response = resources.client()
                .target("/v1/api/accounts/12")
                .request()
                .method("PATCH", Entity.json(jsonNode));
        assertThat(response.getStatus(), is(400));
        JsonNode body = response.readEntity(JsonNode.class);
        assertErrorBodyMatches(body, "Value [false] must be of type boolean for path [allow_zero_amount]",
                ErrorIdentifier.GENERIC);
    }

    private void assertErrorBodyMatches(JsonNode body, String expectedMessage, ErrorIdentifier identifier) {
        JsonNode message = body.get("message");
        assertThat(message.isArray(), is(true));
        assertThat(message.size(), is(1));
        assertThat(message.get(0).textValue(),
                is(expectedMessage));
        assertThat(body.get("error_identifier").textValue(), is(identifier.toString()));
    }
}
