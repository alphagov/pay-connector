package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardInformationFixture;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.paymentprocessor.model.AuthoriseRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WorldpayMotoAuthoriseRequestFactoryTest {
    private WorldpayMotoAuthoriseRequestFactory worldpayMotoAuthoriseRequestFactory;
    
    @Mock
    private WorldpayAuthoriseDescriptionHelper mockDescriptionHelper;
    
    @Mock
    private WorldpayAuthoriseCredentialsHelper mockCredentialsHelper;
    
    @BeforeEach
    void setUp(){
        this.worldpayMotoAuthoriseRequestFactory = new WorldpayMotoAuthoriseRequestFactory(mockDescriptionHelper, mockCredentialsHelper);
    }
    
    @Test
    void create(){
        String cardNumber = "4242424242424242";
        String expiryDateMonth = "11";
        String expiryDateYear = "2030";
        String expiryDateOnCard = "11/30";
        String cardholderName = "Hugo";
        String cvc = "123";
        String orderCode = "order_code";
        String descriptionOrReference = "description or reference";
        String username = "username";
        String password = "password";
        String merchantCode = "merchant_code";
        long amountInPence = 2000L;
        
        
        AuthoriseRequest authoriseRequest = new AuthoriseRequest("one-time-token", cardNumber, cvc, expiryDateOnCard, cardholderName);
        CardInformation cardInformation = CardInformationFixture.aCardInformation().build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(amountInPence)
                .withTransactionId(orderCode)
                .build();
        
        AuthCardDetails authCardDetails = AuthCardDetails.of(authoriseRequest, chargeEntity, cardInformation);

        CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
        
        given(mockDescriptionHelper.getDescription(cardAuthorisationGatewayRequest)).willReturn(descriptionOrReference);
        given(mockCredentialsHelper.getOneOffCredentials(cardAuthorisationGatewayRequest)).willReturn(new WorldpayMerchantCodeCredentials(merchantCode, username, password));
        
        WorldpayMotoAuthoriseRequest actual = worldpayMotoAuthoriseRequestFactory.create(cardAuthorisationGatewayRequest);
        WorldpayMotoAuthoriseRequest expected = new WorldpayMotoAuthoriseRequest(
                username, 
                password, 
                merchantCode, 
                orderCode,
                descriptionOrReference,
                String.valueOf(amountInPence),
                cardNumber,
                expiryDateMonth,
                expiryDateYear,
                cardholderName,
                cvc);
        
        assertThat(actual, is(expected));
    }
}
