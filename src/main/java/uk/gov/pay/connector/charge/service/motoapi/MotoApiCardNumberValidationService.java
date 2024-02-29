package uk.gov.pay.connector.charge.service.motoapi;

import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.motoapi.CardNumberRejectedException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.client.cardid.service.CardidService;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;

import javax.inject.Inject;
import java.util.Locale;

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
        try {
            checkHasValidLuhnCheckDigit(cardNumber);
            return cardidService.getCardInformation(cardNumber).map(cardInformation -> {
                checkCardIsAllowedForGatewayAccount(charge, cardInformation);
                return cardInformation;
            }).orElseThrow(() -> {
                logger.info("Card number rejected: BIN range for card number not found in card id");
                throw new CardNumberRejectedException("The card_number is not a valid card number");
            });
        } catch (CardNumberRejectedException e) {
            transitionChargeToRejected(charge);
            throw e;
        }
    }

    private void checkHasValidLuhnCheckDigit(String cardNumber) {
        if (!(new LuhnCheckDigit().isValid(cardNumber))) {
            logger.info("Card number rejected: Invalid Luhn check digit");
            throw new CardNumberRejectedException("The card_number is not a valid card number");
        }
    }

    private void checkCardIsAllowedForGatewayAccount(ChargeEntity charge, CardInformation cardInformation) {
        if (charge.getGatewayAccount().getCardConfigurationEntity().isBlockPrepaidCards() && cardInformation.getPrepaidStatus() == PayersCardPrepaidStatus.PREPAID) {
            logger.info("Card number rejected: Card is prepaid and the gateway account has prepaid cards blocked");
            throw new CardNumberRejectedException("Prepaid cards are not accepted");
        }

        CardType cardType = CardidCardType.toCardType(cardInformation.getType());
        charge.getGatewayAccount().getCardTypes()
                .stream()
                .filter(cardTypeEntity -> cardTypeEntity.getBrand().equals(cardInformation.getBrand())
                        && (cardType == null || cardTypeEntity.getType() == cardType))
                .findFirst()
                .orElseThrow(() -> {
                    String formattedCardType = formatCardTypeForErrorMessage(cardType, cardInformation.getBrand());
                    logger.info("Card number rejected: The card type is not enabled: {}", formattedCardType);
                    throw new CardNumberRejectedException("The card type is not enabled: " + formattedCardType);
                });
    }

    private String formatCardTypeForErrorMessage(CardType cardType, String brand) {
        String formattedCardType = brand;
        if (cardType != null) {
            formattedCardType = formattedCardType + " " + cardType.toString().toLowerCase(Locale.ROOT);
        }
        return formattedCardType;
    }

    private void transitionChargeToRejected(ChargeEntity charge) {
        chargeService.transitionChargeState(charge, ChargeStatus.AUTHORISATION_REJECTED);
        chargeDao.merge(charge);
    }
}
