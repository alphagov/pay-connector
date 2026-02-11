package uk.gov.pay.connector.gateway.requestfactory;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffCardNumber3dsFlexEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequest;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;

public class WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory {
    
    private final WorldpayAuthoriseRequestFactoryHelper helper;
    
    public WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory(WorldpayAuthoriseRequestFactoryHelper helper) {
        this.helper = helper;
    }
    
    public WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequest create(CardAuthorisationGatewayRequest request) {
        WorldpayMerchantCodeCredentials credentials = helper.getWorldpayMerchantCodeCredentials(request);
        String orderCode = helper.getOrderCodeOrThrow(request);
        String sessionId = request.getGovUkPayPaymentId();

        return new WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequest(
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
                helper.getEmailIfEnabledAndAvailable(request).orElse(null),
                helper.newWorldpayAddress(request).orElse(null),
                helper.getIpAddressIfEnabledAndAvailable(request).orElse(null),
                sessionId,
                request.getAuthCardDetails().getAcceptHeader(),
                request.getAuthCardDetails().getUserAgentHeader(),
                helper.newWorldpayExemptionRequest(request).orElse(null)
        );
    }

}
