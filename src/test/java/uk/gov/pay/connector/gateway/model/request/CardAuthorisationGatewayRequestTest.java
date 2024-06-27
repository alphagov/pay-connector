package uk.gov.pay.connector.gateway.model.request;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

 class CardAuthorisationGatewayRequestTest {
    
    @Test
     void shouldReturnTotalAmount_whenThereIsACorporateSurcharge() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(2000L)
                .withCorporateSurcharge(250L)
                .build();
        CardAuthorisationGatewayRequest gatewayRequest = new CardAuthorisationGatewayRequest(chargeEntity, null);
        assertThat(gatewayRequest.amount(), is("2250"));
    }
    
    @Test
     void shouldReturnBaseAmount_whenThereIsNoCorporateSurcharge() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(2000L)
                .build();
        CardAuthorisationGatewayRequest gatewayRequest = new CardAuthorisationGatewayRequest(chargeEntity, null);
        assertThat(gatewayRequest.amount(), is("2000"));
    }
}
