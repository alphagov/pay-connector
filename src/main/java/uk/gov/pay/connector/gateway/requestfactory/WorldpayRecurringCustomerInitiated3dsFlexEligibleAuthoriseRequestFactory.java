package uk.gov.pay.connector.gateway.requestfactory;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequest;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;

public class WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory {
    
    private final WorldpayAuthoriseRequestFactoryHelper helper;
    
    public WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory(WorldpayAuthoriseRequestFactoryHelper helper) {
        this.helper = helper;
    }

    public WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequest create(CardAuthorisationGatewayRequest request) {
        WorldpayMerchantCodeCredentials credentials = helper.getWorldpayMerchantCodeCredentials(request);
        String orderCode = helper.getOrderCodeOrThrow(request);
        String sessionId = request.getGovUkPayPaymentId();
        String tokenEventReference = request.getGovUkPayPaymentId();
        String authenticatedShopperId = helper.getAgreementIdOrThrow(request);

        return new WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequest(
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
                helper.getPreferredLanguageTagIfRequired(request).orElse(null),
                request.getAuthCardDetails().getWorldpay3dsFlexDdcResult().orElse(null),
                helper.newWorldpayExemptionRequest(request).orElse(null),
                authenticatedShopperId,
                tokenEventReference,
                helper.getCustomerInitiatedReason(request).orElse(null)
        );
    }

}
