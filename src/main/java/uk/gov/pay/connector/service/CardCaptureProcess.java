package uk.gov.pay.connector.service;

import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeSearchParams;

import javax.inject.Inject;
import java.util.Collections;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;

public class CardCaptureProcess {

    static final long NUMBER_OF_CHARGES_TO_CAPTURE = 10;
    private final ChargeDao chargeDao;
    private final CardCaptureService captureService;

    @Inject
    public CardCaptureProcess(ChargeDao chargeDao, CardCaptureService cardCaptureService) {
        this.chargeDao = chargeDao;
        this.captureService = cardCaptureService;
    }

    public void runCapture() {
        chargeDao
                .findAllBy(chargeSearchCriteriaForCapture())
                .forEach((charge) -> captureService.doCapture(charge.getExternalId()));
    }

    private ChargeSearchParams chargeSearchCriteriaForCapture() {
        ChargeSearchParams chargeSearchParams = new ChargeSearchParams();
        chargeSearchParams.withInternalChargeStatuses(Collections.singletonList(CAPTURE_APPROVED));
        chargeSearchParams.withDisplaySize(CardCaptureProcess.NUMBER_OF_CHARGES_TO_CAPTURE);
        chargeSearchParams.withPage(1L);
        return chargeSearchParams;
    }
}
