package uk.gov.pay.connector.gateway.requestfactory;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffCardNumberMotoAuthoriseRequest;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;

public class WorldpayOneOffCardNumberMotoAuthoriseRequestFactory {

    private final WorldpayAuthoriseRequestFactoryHelper helper;

    public WorldpayOneOffCardNumberMotoAuthoriseRequestFactory(WorldpayAuthoriseRequestFactoryHelper helper) {
        this.helper = helper;
    }

    public WorldpayOneOffCardNumberMotoAuthoriseRequest create(CardAuthorisationGatewayRequest request) {
        WorldpayMerchantCodeCredentials credentials = helper.getWorldpayMerchantCodeCredentials(request);
        String orderCode = helper.getOrderCodeOrThrow(request);

        return new WorldpayOneOffCardNumberMotoAuthoriseRequest(
                credentials.getUsername(),
                credentials.getPassword(),
                credentials.getMerchantCode(),
                orderCode,
                helper.getDescription(request),
                request.getAmount(),
                request.getAuthCardDetails().getCardNo(),
                request.getAuthCardDetails().getEndDate().getTwoDigitMonth(),
                request.getAuthCardDetails().getEndDate().getFourDigitYear(),
                request.getAuthCardDetails().getCardHolder(),
                request.getAuthCardDetails().getCvc(),
                helper.getEmailIfEnabledAndAvailable(request).orElse(null)
        );
    }
 
}
