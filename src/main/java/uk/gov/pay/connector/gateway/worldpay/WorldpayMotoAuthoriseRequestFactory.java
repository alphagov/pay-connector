package uk.gov.pay.connector.gateway.worldpay;

import jakarta.inject.Inject;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.records.WorldpayMotoAuthorisePayload;
import uk.gov.pay.connector.gateway.worldpay.utils.WorldpayAuthoriseCredentialsHelper;
import uk.gov.pay.connector.gateway.worldpay.utils.WorldpayAuthoriseDescriptionHelper;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;

public class WorldpayMotoAuthoriseRequestFactory {

    private final WorldpayAuthoriseDescriptionHelper descriptionHelper;
    private final WorldpayAuthoriseCredentialsHelper credentialsHelper;

    @Inject
    public WorldpayMotoAuthoriseRequestFactory(WorldpayAuthoriseDescriptionHelper descriptionHelper, WorldpayAuthoriseCredentialsHelper credentialsHelper) {
        this.descriptionHelper = descriptionHelper;
        this.credentialsHelper = credentialsHelper;
    }
    
    public WorldpayMotoAuthorisePayload create(CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest){
        WorldpayMerchantCodeCredentials credentials = credentialsHelper.getOneOffCredentials(cardAuthorisationGatewayRequest);
        return new WorldpayMotoAuthorisePayload(
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
