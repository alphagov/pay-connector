package uk.gov.pay.connector.charge.service.motoapi;

import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.motoapi.CardNumberRejectedException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;

import javax.inject.Inject;
import java.util.Optional;

public class MotoApiCardNumberValidationService {

    private final Logger logger = LoggerFactory.getLogger(MotoApiCardNumberValidationService.class);

    private final ChargeService chargeService;
    private final ChargeDao chargeDao;

    @Inject
    public MotoApiCardNumberValidationService(ChargeService chargeService, ChargeDao chargeDao) {
        this.chargeService = chargeService;
        this.chargeDao = chargeDao;
    }

    public void validateCardNumber(ChargeEntity charge, String cardNumber) {
        checkValidAllowedCardNumber(charge, cardNumber).ifPresent(errorMessage -> {
            transitionChargeToRejected(charge);
            throw new CardNumberRejectedException(errorMessage);
        });
    }

    private Optional<String> checkValidAllowedCardNumber(ChargeEntity charge, String cardNumber) {
        return checkHasValidLuhnCheckDigit(cardNumber);
        // TODO other checks on card number
    }

    private Optional<String> checkHasValidLuhnCheckDigit(String cardNumber) {
        boolean valid = new LuhnCheckDigit().isValid(cardNumber);
        if (!valid) {
            logger.info("Card number rejected due to invalid Luhn check digit");
            return Optional.of("The card_number is not a valid card number");
        }
        return Optional.empty();
    }

    private void transitionChargeToRejected(ChargeEntity charge) {
        chargeService.transitionChargeState(charge, ChargeStatus.AUTHORISATION_REJECTED);
        chargeDao.merge(charge);
    }
}
