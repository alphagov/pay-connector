package uk.gov.pay.connector.gateway.stripe;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCancelHandler;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(MockitoJUnitRunner.class)
public class StripeCancelHandlerTest {

    private StripeCancelHandler stripeCancelHandler;
    @Mock 
    private StripeGatewayClient client;
    @Mock 
    private StripeGatewayConfig stripeGatewayConfig;

    @Before
    public void setup() {
        stripeCancelHandler = new StripeCancelHandler(client, stripeGatewayConfig);
    }
    
    @Test
    public void shouldCancelPaymentSuccessfully() {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();
        CancelGatewayRequest request = CancelGatewayRequest.valueOf(charge);
        final GatewayResponse<BaseCancelResponse> response = stripeCancelHandler.cancel(request);
        assertThat(response.isSuccessful(), is(true));
    } 
}
