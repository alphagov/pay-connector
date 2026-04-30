package uk.gov.pay.connector.gateway.model.request.records;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class WorldpayAuthoriseDescriptionHelperTest {

    public static final String DESCRIPTION = "abcdef";
    public static final String REFERENCE = "reference";
    private WorldpayAuthoriseDescriptionHelper descriptionHelper = new WorldpayAuthoriseDescriptionHelper();
    
    @Test
    void returnsDescriptionWhenSendReferenceToGatewayIsFalse(){
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity().withSendReferenceToGateway(false).build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withDescription(DESCRIPTION)
                .withReference(ServicePaymentReference.of(REFERENCE))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        
        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = new CardAuthorisationGatewayRequest(chargeEntity, null);

        String actual = descriptionHelper.getDescription(cardAuthorisationGatewayRequest);
        assertThat(actual, is(DESCRIPTION));
    }

    @Test
    void returnsReferenceWhenSendReferenceToGatewayIsTrue(){
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity().withSendReferenceToGateway(true).build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withDescription(DESCRIPTION)
                .withReference(ServicePaymentReference.of(REFERENCE))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();

        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = new CardAuthorisationGatewayRequest(chargeEntity, null);

        String actual = descriptionHelper.getDescription(cardAuthorisationGatewayRequest);
        assertThat(actual, is(REFERENCE));
    }
}
