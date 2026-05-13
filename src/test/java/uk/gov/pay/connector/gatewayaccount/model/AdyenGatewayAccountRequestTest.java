package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials.ADYEN_ACCOUNT_HOLDER_ID;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials.ADYEN_BALANCE_ACCOUNT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials.ADYEN_LEGAL_ENTITY_ID;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials.ADYEN_STORE_ID;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenCredentialsFixture.anAdyenCredentials;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenGatewayAccountRequestFixture.anAdyenGatewayAccountRequest;

class AdyenGatewayAccountRequestTest {

    @Test
    void should_get_credentials_as_map() {
        var request = anAdyenGatewayAccountRequest()
                .withCredentials(
                        anAdyenCredentials()
                                .withLegalEntityId("LEM0000000000000001")
                                .withStoreId("ST00000000000000000000001")
                                .withAccountHolderId("AH3227C223222H5J4DCLW9VBV")
                                .withBalanceAccountId("BA0000000000000000000001")
                                .build())
                .build();

        assertThat(request.getCredentialsAsMap().get(ADYEN_LEGAL_ENTITY_ID), is("LEM0000000000000001"));
        assertThat(request.getCredentialsAsMap().get(ADYEN_STORE_ID), is("ST00000000000000000000001"));
        assertThat(request.getCredentialsAsMap().get(ADYEN_ACCOUNT_HOLDER_ID), is("AH3227C223222H5J4DCLW9VBV"));
        assertThat(request.getCredentialsAsMap().get(ADYEN_BALANCE_ACCOUNT_ID), is("BA0000000000000000000001"));
    }
}
