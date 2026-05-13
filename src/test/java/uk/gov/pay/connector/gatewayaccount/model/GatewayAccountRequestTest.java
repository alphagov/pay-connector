package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static io.dropwizard.jackson.Jackson.newObjectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentialsFixture.anAdyenCredentials;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenGatewayAccountRequestFixture.anAdyenGatewayAccountRequest;

class GatewayAccountRequestTest {

    private static final ObjectMapper MAPPER = newObjectMapper();

    @Test
    void should_deserialize_from_JSON() throws JsonProcessingException {
        // language=JSON
        String requestJson = """
                {
                  "provider_account_type": "test",
                  "payment_provider": "payment-provider",
                  "service_name": "service-name",
                  "service_id": "service-id",
                  "description": "description",
                  "analytics_id": "analytics-id",
                  "requires_3ds": true,
                  "allow_apple_pay": true,
                  "allow_google_pay": true,
                  "send_payer_email_to_gateway": true,
                  "send_payer_ip_address_to_gateway": true
                }""";

        var deserializedRequest = MAPPER.readValue(requestJson, GatewayAccountRequest.class);

        var expectedRequest = new GatewayAccountRequest(
                "test",
                "payment-provider",
                "service-name",
                "service-id",
                "description",
                "analytics-id",
                true,
                true,
                true,
                true,
                true
        );

        assertDeserializedRequest(deserializedRequest, expectedRequest);
    }

    @Test
    void should_deserialize_from_JSON_with_Stripe_credentials() throws JsonProcessingException {
        // language=JSON
        String requestJson = """
                {
                  "payment_provider": "stripe",
                  "credentials": {
                    "stripe_account_id": "stripe-account-id"
                  }
                }""";

        var deserializedRequest = MAPPER.readValue(requestJson, StripeGatewayAccountRequest.class);

        var credentials = new StripeCredentials();
        credentials.setStripeAccountId("stripe-account-id");
        var expectedRequest = new StripeGatewayAccountRequest(
                null,
                "stripe",
                null,
                null,
                null,
                null,
                credentials,
                false,
                false,
                false,
                false,
                false
        );
        assertDeserializedRequest(deserializedRequest, expectedRequest);
    }

    @Test
    void should_deserialize_from_JSON_with_Adyen_credentials() throws JsonProcessingException {
        // language=JSON
        String requestJson = """
                {
                  "payment_provider": "adyen",
                  "credentials": {
                    "legal_entity_id": "LEM0000000000000001",
                    "store_id": "ST00000000000000000000001",
                    "account_holder_id": "AH3227C223222H5J4DCLW9VBV",
                    "balance_account_id": "BA0000000000000000000001"
                  }
                }""";

        var deserializedRequest = MAPPER.readValue(requestJson, AdyenGatewayAccountRequest.class);

        var credentials = anAdyenCredentials()
                .withLegalEntityId("LEM0000000000000001")
                .withStoreId("ST00000000000000000000001")
                .withAccountHolderId("AH3227C223222H5J4DCLW9VBV")
                .withBalanceAccountId("BA0000000000000000000001")
                .build();
        var expectedRequest = anAdyenGatewayAccountRequest()
                .withPaymentProvider("adyen")
                .withCredentials(credentials)
                .build();
        assertDeserializedRequest(deserializedRequest, expectedRequest);
    }

    private static void assertDeserializedRequest(GatewayAccountRequest deserializedRequest, GatewayAccountRequest expectedRequest) {
        assertThat(deserializedRequest.getProviderAccountType(), is(expectedRequest.getProviderAccountType()));
        assertThat(deserializedRequest.getPaymentProvider(), is(expectedRequest.getPaymentProvider()));
        assertThat(deserializedRequest.getServiceName(), is(expectedRequest.getServiceName()));
        assertThat(deserializedRequest.getServiceId(), is(expectedRequest.getServiceId()));
        assertThat(deserializedRequest.getDescription(), is(expectedRequest.getDescription()));
        assertThat(deserializedRequest.getAnalyticsId(), is(expectedRequest.getAnalyticsId()));
        assertThat(deserializedRequest.isRequires3ds(), is(expectedRequest.isRequires3ds()));
        assertThat(deserializedRequest.isAllowApplePay(), is(expectedRequest.isAllowApplePay()));
        assertThat(deserializedRequest.isAllowGooglePay(), is(expectedRequest.isAllowGooglePay()));
        assertThat(deserializedRequest.isSendPayerEmailToGateway(), is(expectedRequest.isSendPayerEmailToGateway()));
        assertThat(deserializedRequest.isSendPayerIpAddressToGateway(), is(expectedRequest.isSendPayerIpAddressToGateway()));
        assertThat(deserializedRequest.getCredentialsAsMap(), is(expectedRequest.getCredentialsAsMap()));
    }
}
