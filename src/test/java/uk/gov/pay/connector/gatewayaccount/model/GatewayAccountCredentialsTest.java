package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.dropwizard.jackson.Jackson.newObjectMapper;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials.ADYEN_LEGAL_ENTITY_ID;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials.ADYEN_STORE_ID;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

class GatewayAccountCredentialsTest {

    private static final ObjectMapper MAPPER = newObjectMapper();

    @Test
    void should_serialise_to_JSON_with_Adyen_credentials() throws JsonProcessingException {
        var legalEntityId = "LEM0000000000000001";
        var storeId = "ST00000000000000000000001";
        var adyenGatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider("adyen")
                .withGatewayAccountEntity(GatewayAccountEntityFixture.aGatewayAccountEntity().build())
                .withCredentials(Map.of(
                        ADYEN_LEGAL_ENTITY_ID, legalEntityId,
                        ADYEN_STORE_ID, storeId
                )).build();
        var credentials = new GatewayAccountCredentials(adyenGatewayAccountCredentialsEntity);

        var json = MAPPER.writeValueAsString(credentials);

        JsonAssert.with(json)
                .assertThat("$.credentials.legal_entity_id", is(legalEntityId))
                .assertThat("$.credentials.store_id", is(storeId));
    }
}
