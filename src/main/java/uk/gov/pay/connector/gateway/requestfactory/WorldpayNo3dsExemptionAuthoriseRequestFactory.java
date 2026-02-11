package uk.gov.pay.connector.gateway.requestfactory;

import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.Worldpay3dsEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayAuthoriseRequestWithOptional3dsExemption;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffCardNumber3dsFlexEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequest;
import uk.gov.pay.connector.gateway.model.request.gateway.worldpay.authorise.WorldpayRecurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequest;

public class WorldpayNo3dsExemptionAuthoriseRequestFactory {
    
    public Worldpay3dsEligibleAuthoriseRequest create(WorldpayAuthoriseRequestWithOptional3dsExemption request, String orderCode) {
        switch (request) {
            case WorldpayOneOffCardNumber3dsFlexEligibleAuthoriseRequest req -> {
                return new WorldpayOneOffCardNumber3dsFlexEligibleAuthoriseRequest(
                        req.username(),
                        req.password(),
                        req.merchantCode(),
                        orderCode,
                        req.description(),
                        req.amountInPence(),
                        req.cardNumber(),
                        req.expiryMonthTwoDigits(),
                        req.expiryYearFourDigits(),
                        req.cardholderName(),
                        req.cvc(),
                        req.email(),
                        req.address(),
                        req.ipAddress(),
                        req.sessionId(),
                        req.acceptHeader(),
                        req.userAgentHeader(),
                        req.browserLanguageTag(),
                        req.dfReferenceId(),
                        null
                );
            }

            case WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequest req -> {
                return new WorldpayOneOffCardNumberLegacy3dsEligibleAuthoriseRequest(
                        req.username(),
                        req.password(),
                        req.merchantCode(),
                        orderCode,
                        req.description(),
                        req.amountInPence(),
                        req.cardNumber(),
                        req.expiryMonthTwoDigits(),
                        req.expiryYearFourDigits(),
                        req.cardholderName(),
                        req.cvc(),
                        req.email(),
                        req.address(),
                        req.ipAddress(),
                        req.sessionId(),
                        req.acceptHeader(),
                        req.userAgentHeader(),
                        null
                );
            }

            case WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequest req -> {
                return new WorldpayRecurringCustomerInitiated3dsFlexEligibleAuthoriseRequest(
                        req.username(),
                        req.password(),
                        req.merchantCode(),
                        orderCode,
                        req.description(),
                        req.amountInPence(),
                        req.cardNumber(),
                        req.expiryMonthTwoDigits(),
                        req.expiryYearFourDigits(),
                        req.cardholderName(),
                        req.cvc(),
                        req.email(),
                        req.address(),
                        req.ipAddress(),
                        req.sessionId(),
                        req.acceptHeader(),
                        req.userAgentHeader(),
                        req.browserLanguageTag(),
                        req.dfReferenceId(),
                        null,
                        req.authenticatedShopperId(),
                        req.tokenEventReference(),
                        req.customerInitiatedReason()
                );
            }

            case WorldpayRecurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequest req -> {
                return new WorldpayRecurringCustomerInitiatedLegacy3dsEligibleAuthoriseRequest(
                        req.username(),
                        req.password(),
                        req.merchantCode(),
                        orderCode,
                        req.description(),
                        req.amountInPence(),
                        req.cardNumber(),
                        req.expiryMonthTwoDigits(),
                        req.expiryYearFourDigits(),
                        req.cardholderName(),
                        req.cvc(),
                        req.email(),
                        req.address(),
                        req.ipAddress(),
                        req.sessionId(),
                        req.acceptHeader(),
                        req.userAgentHeader(),
                        null,
                        req.authenticatedShopperId(),
                        req.tokenEventReference(),
                        req.customerInitiatedReason()
                );
            }
        }
    }
}
