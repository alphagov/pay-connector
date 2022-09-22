package uk.gov.pay.connector.gateway.model.request;


import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CardAuthorisationGatewayRequestTest {
    
    @Test
    public void shouldReturnTotalAmount_whenThereIsACorporateSurcharge() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(2000L)
                .withCorporateSurcharge(250L)
                .build();
        CardAuthorisationGatewayRequest gatewayRequest = new CardAuthorisationGatewayRequest(chargeEntity, null);
        assertThat(gatewayRequest.getAmount(), is("2250"));
    }
    
    @Test
    public void shouldReturnBaseAmount_whenThereIsNoCorporateSurcharge() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(2000L)
                .build();
        CardAuthorisationGatewayRequest gatewayRequest = new CardAuthorisationGatewayRequest(chargeEntity, null);
        assertThat(gatewayRequest.getAmount(), is("2000"));
    }
}
