package uk.gov.pay.connector.gateway.requestfactory;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayRecurringCustomerInitiatedNon3dsAuthoriseRequest;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;

public class WorldpayRecurringCustomerInitiatedNon3dsAuthoriseRequestFactory {
    
    private final WorldpayAuthoriseRequestFactoryHelper helper;
    
    public WorldpayRecurringCustomerInitiatedNon3dsAuthoriseRequestFactory(WorldpayAuthoriseRequestFactoryHelper helper) {
        this.helper = helper;
    }
    
    public WorldpayRecurringCustomerInitiatedNon3dsAuthoriseRequest create(CardAuthorisationGatewayRequest request) {
        WorldpayMerchantCodeCredentials credentials = helper.getWorldpayMerchantCodeCredentials(request);
        String orderCode = helper.getOrderCodeOrThrow(request);
        String tokenEventReference = request.getGovUkPayPaymentId();
        String authenticatedShopperId = helper.getAgreementIdOrThrow(request);

        return new WorldpayRecurringCustomerInitiatedNon3dsAuthoriseRequest(
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
                authenticatedShopperId,
                tokenEventReference,
                helper.getCustomerInitiatedReason(request).orElse(null)
        );
    }

}
