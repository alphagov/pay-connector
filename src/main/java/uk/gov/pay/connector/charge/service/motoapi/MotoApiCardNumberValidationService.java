package uk.gov.pay.connector.charge.service.motoapi;

import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.motoapi.CardNumberRejectedException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.service.CardidService;

import javax.inject.Inject;

public class MotoApiCardNumberValidationService {

    private final Logger logger = LoggerFactory.getLogger(MotoApiCardNumberValidationService.class);

    private final ChargeService chargeService;
    private final ChargeDao chargeDao;
    private final CardidService cardidService;

    @Inject
    public MotoApiCardNumberValidationService(
            ChargeService chargeService,
            ChargeDao chargeDao,
            CardidService cardidService) {
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
        this.cardidService = cardidService;
    }

    public CardInformation validateCardNumber(ChargeEntity charge, String cardNumber) {
        if (!hasValidLuhnCheckDigit(cardNumber)) {
            logger.info("Card number rejected: Invalid Luhn check digit");
            transitionChargeToRejected(charge);
            throw new CardNumberRejectedException("The card_number is not a valid card number");
        }
        return cardidService.getCardInformation(cardNumber).orElseGet(() -> {
            logger.info("Card number rejected: BIN range for card number not found in card id");
            transitionChargeToRejected(charge);
            throw new CardNumberRejectedException("The card_number is not a valid card number");
        });
    }

    private boolean hasValidLuhnCheckDigit(String cardNumber) {
        return new LuhnCheckDigit().isValid(cardNumber);
    }

    private void transitionChargeToRejected(ChargeEntity charge) {
        chargeService.transitionChargeState(charge, ChargeStatus.AUTHORISATION_REJECTED);
        chargeDao.merge(charge);
    }
}
