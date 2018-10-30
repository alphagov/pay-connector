package uk.gov.pay.connector.gateway.model.request;


import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class AuthorisationGatewayRequestImplTest {
    
    @Test
    public void shouldReturnTotalAmount_whenThereIsACorporateSurcharge() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(2000L)
                .withCorporateSurcharge(250L)
                .build();
        AuthorisationGatewayRequestImpl gatewayRequest = new AuthorisationGatewayRequestImpl(chargeEntity, null);
        assertThat(gatewayRequest.getAmount(), is("2250"));
    }
    
    @Test
    public void shouldReturnBaseAmount_whenThereIsNoCorporateSurcharge() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(2000L)
                .build();
        AuthorisationGatewayRequestImpl gatewayRequest = new AuthorisationGatewayRequestImpl(chargeEntity, null);
        assertThat(gatewayRequest.getAmount(), is("2000"));
    }
}
