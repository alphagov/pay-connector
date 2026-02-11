package uk.gov.pay.connector.gateway.requestfactory;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayCardNumberAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffApplePayAuthoriseRequest;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;

public class WorldpayAuthoriseRequestFactory {

    private final WorldpayOneOffCardNumberMotoAuthoriseRequestFactory oneOffCardNumberMotoAuthoriseRequestFactory;
    private final WorldpayOneOffCardNumber3dsFlexEligibleAuthoriseRequestFactory oneOffCardNumber3dsFlexEligibleAuthoriseRequestFactory;
    private final WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory oneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory;
    private final WorldpayOneOffCardNumberNon3dsAuthoriseRequestFactory oneOffCardNumberNon3dsAuthoriseRequestFactory;
    private final WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory recurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory;
    private final WorldpayRecurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequestFactory recurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequestFactory;
    private final WorldpayRecurringCustomerInitiatedNon3dsAuthoriseRequestFactory recurringCustomerInitiatedNon3dsAuthoriseRequestFactory;
    private final WorldpayOneOffApplePayAuthoriseRequestFactory worldpayOneOffApplePayAuthoriseRequestFactory;
    private final WorldpayAuthoriseRequestFactoryHelper helper;

    public WorldpayAuthoriseRequestFactory(
            WorldpayOneOffCardNumberMotoAuthoriseRequestFactory oneOffCardNumberMotoAuthoriseRequestFactory,
            WorldpayOneOffCardNumber3dsFlexEligibleAuthoriseRequestFactory oneOffCardNumber3dsFlexEligibleAuthoriseRequestFactory,
            WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory oneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory,
            WorldpayOneOffCardNumberNon3dsAuthoriseRequestFactory oneOffCardNumberNon3dsAuthoriseRequestFactory,
            WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory recurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory,
            WorldpayRecurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequestFactory recurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequestFactory,
            WorldpayRecurringCustomerInitiatedNon3dsAuthoriseRequestFactory recurringCustomerInitiatedNon3dsAuthoriseRequestFactory,
            WorldpayOneOffApplePayAuthoriseRequestFactory worldpayOneOffApplePayAuthoriseRequestFactory,
            WorldpayAuthoriseRequestFactoryHelper helper) {
        this.oneOffCardNumberMotoAuthoriseRequestFactory = oneOffCardNumberMotoAuthoriseRequestFactory;
        this.oneOffCardNumber3dsFlexEligibleAuthoriseRequestFactory = oneOffCardNumber3dsFlexEligibleAuthoriseRequestFactory;
        this.oneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory = oneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory;
        this.oneOffCardNumberNon3dsAuthoriseRequestFactory = oneOffCardNumberNon3dsAuthoriseRequestFactory;
        this.recurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory = recurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory;
        this.recurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequestFactory = recurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequestFactory;
        this.recurringCustomerInitiatedNon3dsAuthoriseRequestFactory = recurringCustomerInitiatedNon3dsAuthoriseRequestFactory;
        this.worldpayOneOffApplePayAuthoriseRequestFactory = worldpayOneOffApplePayAuthoriseRequestFactory;
        this.helper = helper;
    }

     public WorldpayCardNumberAuthoriseRequest newCardNumberRequest(CardAuthorisationGatewayRequest request) {
         if (request.isMoto()) {
             return oneOffCardNumberMotoAuthoriseRequestFactory.create(request);
         }

         if (request.isSavePaymentInstrumentToAgreement()) {
             if (helper.is3dsFlexRequest(request)) {
                 return recurringCustomerInitiated3dsFlexEligibleAuthoriseRequestFactory.create(request);
             }
             
             if (helper.is3dsRequired(request)) {
                 return recurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequestFactory.create(request);
             }

             return recurringCustomerInitiatedNon3dsAuthoriseRequestFactory.create(request);
         }

         if (helper.is3dsFlexRequest(request)) {
             return oneOffCardNumber3dsFlexEligibleAuthoriseRequestFactory.create(request);
         }
         
         if (helper.is3dsRequired(request)) {
             return oneOffCardNumberLegacy3dsEligibleAuthoriseRequestFactory.create(request);
         }

         return oneOffCardNumberNon3dsAuthoriseRequestFactory.create(request);
     }

     public WorldpayOneOffApplePayAuthoriseRequest newApplePayRequest(WalletAuthorisationRequest request) {
         return worldpayOneOffApplePayAuthoriseRequestFactory.create(request);
     }

}
