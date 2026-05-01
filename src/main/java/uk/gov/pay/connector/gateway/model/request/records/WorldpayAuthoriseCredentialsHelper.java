package uk.gov.pay.connector.gateway.model.request.records;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.service.payments.commons.model.AuthorisationMode;

public class WorldpayAuthoriseCredentialsHelper {
    public WorldpayMerchantCodeCredentials getOneOffCredentials(CardAuthorisationGatewayRequest cardAuthorisationGatewayRequest){
        return AuthUtil.getWorldpayMerchantCodeCredentials(cardAuthorisationGatewayRequest.getGatewayCredentials(), AuthorisationMode.WEB, false);
    }
}
