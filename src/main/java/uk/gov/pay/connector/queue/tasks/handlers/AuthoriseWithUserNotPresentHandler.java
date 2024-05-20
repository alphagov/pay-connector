package uk.gov.pay.connector.queue.tasks.handlers;

import com.google.inject.Inject;
import uk.gov.pay.connector.charge.service.ChargeEligibleForCaptureService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.paymentprocessor.service.CardAuthoriseService;

public class AuthoriseWithUserNotPresentHandler {
    private final ChargeService chargeService;
    private final CardAuthoriseService cardAuthoriseService;
    private final ChargeEligibleForCaptureService chargeEligibleForCaptureService;

    @Inject
    public AuthoriseWithUserNotPresentHandler(ChargeService chargeService, CardAuthoriseService cardAuthoriseService, ChargeEligibleForCaptureService chargeEligibleForCaptureService) {
        this.chargeService = chargeService;
        this.cardAuthoriseService = cardAuthoriseService;
        this.chargeEligibleForCaptureService = chargeEligibleForCaptureService;
    }

    public void process(String chargeId) {
        var charge = chargeService.findChargeByExternalId(chargeId);
        var response = cardAuthoriseService.doAuthoriseUserNotPresent(charge);
        response.getAuthoriseStatus()
                .ifPresent(authoriseStatus -> {
                    if (authoriseStatus == BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED) {
                        chargeEligibleForCaptureService.markChargeAsEligibleForCapture(charge.getExternalId()); 
                    }
                });
    }
}
