package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;

public class WorldpayMotoAuthoriseRequestFactory {

    private final WorldpayAuthoriseDescriptionHelper descriptionHelper;
    private final WorldpayAuthoriseCredentialsHelper credentialsHelper;

    public WorldpayMotoAuthoriseRequestFactory(WorldpayAuthoriseDescriptionHelper descriptionHelper, WorldpayAuthoriseCredentialsHelper credentialsHelper) {
        this.descriptionHelper = descriptionHelper;
        this.credentialsHelper = credentialsHelper;
    }
    
    public WorldpayMotoAuthoriseRequest create(CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest){
        WorldpayMerchantCodeCredentials credentials = credentialsHelper.getOneOffCredentials(cardAuthorisationGatewayRequest);
        return new WorldpayMotoAuthoriseRequest(
                credentials.getUsername(),
                credentials.getPassword(),
                credentials.getMerchantCode(),
                cardAuthorisationGatewayRequest.getTransactionId().orElseThrow(IllegalArgumentException::new),
                descriptionHelper.getDescription(cardAuthorisationGatewayRequest),
                cardAuthorisationGatewayRequest.getAmount(),
                cardAuthorisationGatewayRequest.getAuthCardDetails().getCardNo(),
                cardAuthorisationGatewayRequest.getAuthCardDetails().getEndDate().getTwoDigitMonth(),
                cardAuthorisationGatewayRequest.getAuthCardDetails().getEndDate().getFourDigitYear(),
                cardAuthorisationGatewayRequest.getAuthCardDetails().getCardHolder(),
                cardAuthorisationGatewayRequest.getAuthCardDetails().getCvc()
        );
    }
    
}
