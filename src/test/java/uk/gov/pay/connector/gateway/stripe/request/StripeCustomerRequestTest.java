package uk.gov.pay.connector.gateway.stripe.request;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;

import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

class StripeCustomerRequestTest {
    
    private final static String CARD_HOLDER = "a-card-holder-name";
    private final static String AGREEMENT_DESCRIPTION = "an-agreement-description";
    
    private final static String AGREEMENT_EXTERNAL_ID = "an-agreement-id";
    
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    
    @Test
    void shouldHaveCorrectParameters() {
        StripeCustomerRequest stripeCustomerRequest = createStripeCustomerRequest();

        String payload = stripeCustomerRequest.getGatewayOrder().getPayload();
        assertThat(payload, containsString("name=" + CARD_HOLDER));
        assertThat(payload, containsString("description=" + AGREEMENT_DESCRIPTION));
        assertThat(payload, containsString("metadata%5Bgovuk_pay_agreement_external_id%5D=" + AGREEMENT_EXTERNAL_ID));
    }

    private StripeCustomerRequest createStripeCustomerRequest() {
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripeConnectAccountId"))
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        
        var authCardDetails = new AuthCardDetails();
        authCardDetails.setCardHolder(CARD_HOLDER);
        
        var charge = aValidChargeEntity()
                .withGatewayAccountCredentialsEntity(gatewayAccountCredentialsEntity)
                .build();
        
        var authorisationGatewayRequest = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        var agreementEntity = anAgreementEntity()
                .withExternalId(AGREEMENT_EXTERNAL_ID)
                .withDescription(AGREEMENT_DESCRIPTION)
                .build();
        
        return StripeCustomerRequest.of(authorisationGatewayRequest, stripeGatewayConfig, agreementEntity);
    }
    
}
