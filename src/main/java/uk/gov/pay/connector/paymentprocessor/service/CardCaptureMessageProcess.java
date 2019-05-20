package uk.gov.pay.connector.paymentprocessor.service;

import uk.gov.pay.connector.gateway.CaptureResponse;

import javax.inject.Inject;

// @TODO(sfount) replace `CardCaptureProcess` when feature flag is switched
public class CardCaptureMessageProcess {

    private CardCaptureService cardCaptureService;
   
    @Inject
    public CardCaptureMessageProcess(CardCaptureService cardCaptureService) { 
        this.cardCaptureService = cardCaptureService;
    }
    
    public CaptureResponse runCapture(String chargeExternalId) {  
        return cardCaptureService.doCapture(chargeExternalId);
    }
}
