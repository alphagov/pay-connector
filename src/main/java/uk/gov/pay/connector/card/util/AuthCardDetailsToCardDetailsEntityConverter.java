package uk.gov.pay.connector.card.util;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.card.model.AddressEntity;
import uk.gov.pay.connector.card.model.CardDetailsEntity;
import uk.gov.pay.connector.card.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.card.model.LastDigitsCardNumber;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;

import javax.inject.Inject;

import java.util.Optional;

import static uk.gov.pay.connector.common.model.domain.NumbersInStringsSanitizer.sanitize;

public class AuthCardDetailsToCardDetailsEntityConverter {

    private final NorthAmericanRegionMapper northAmericanRegionMapper;

    @Inject
    public AuthCardDetailsToCardDetailsEntityConverter(NorthAmericanRegionMapper northAmericanRegionMapper) {
        this.northAmericanRegionMapper = northAmericanRegionMapper;
    }

    public CardDetailsEntity convert(AuthCardDetails authCardDetails) {
        CardDetailsEntity detailsEntity = new CardDetailsEntity();
        detailsEntity.setCardBrand(sanitize(authCardDetails.getCardBrand()));
        detailsEntity.setCardHolderName(sanitize(authCardDetails.getCardHolder()));
        detailsEntity.setExpiryDate(authCardDetails.getEndDate());
        if (hasFullCardNumber(authCardDetails)) { // Apple Pay etc. don’t give us a full card number, just the last four digits here
            detailsEntity.setFirstDigitsCardNumber(FirstDigitsCardNumber.of(StringUtils.left(authCardDetails.getCardNo(), 6)));
        }

        if (hasLastFourCharactersCardNumber(authCardDetails)) {
            detailsEntity.setLastDigitsCardNumber(LastDigitsCardNumber.of(StringUtils.right(authCardDetails.getCardNo(), 4)));
        }

        authCardDetails.getAddress().ifPresent(address -> {
            var addressEntity = new AddressEntity(address);
            northAmericanRegionMapper.getNorthAmericanRegionForCountry(address)
                    .map(NorthAmericaRegion::getAbbreviation)
                    .ifPresent(addressEntity::setStateOrProvince);
            detailsEntity.setBillingAddress(addressEntity);
        });

        detailsEntity.setCardType(PayersCardType.toCardType(authCardDetails.getPayersCardType()));

        return detailsEntity;
    }

    private static boolean hasFullCardNumber(AuthCardDetails authCardDetails) {
        return Optional.ofNullable(authCardDetails.getCardNo())
                .map(cardNumber -> cardNumber.length() > 6)
                .orElse(false);
    }

    private static boolean hasLastFourCharactersCardNumber(AuthCardDetails authCardDetails) {
        return Optional.ofNullable(authCardDetails.getCardNo())
                .map(cardNumber -> cardNumber.length() >= 4)
                .orElse(false);
    }

};
