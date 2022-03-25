package uk.gov.pay.connector.paymentinstrument.service;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Instant;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.northamericaregion.NorthAmericaRegion;
import uk.gov.pay.connector.northamericaregion.NorthAmericanRegionMapper;
import uk.gov.pay.connector.paymentinstrument.dao.PaymentInstrumentDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import javax.inject.Inject;

import java.time.Clock;
import java.util.Map;

import static uk.gov.pay.connector.common.model.domain.NumbersInStringsSanitizer.sanitize;

public class PaymentInstrumentService {
    private final PaymentInstrumentDao paymentInstrumentDao;
    private final NorthAmericanRegionMapper northAmericanRegionMapper;
    private final Clock clock;
    
    @Inject
    public PaymentInstrumentService(PaymentInstrumentDao paymentInstrumentDao, NorthAmericanRegionMapper northAmericanRegionMapper, Clock clock) {
        this.paymentInstrumentDao = paymentInstrumentDao;
        this.northAmericanRegionMapper = northAmericanRegionMapper;
        this.clock = clock;
    }
    
    public PaymentInstrumentEntity create(AuthCardDetails authCardDetails, Map<String, String> token) {
        var entity = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                .withCardDetails(buildCardDetailsEntity(authCardDetails))
                .withRecurringAuthToken(token)
                .withCreatedDate(clock.instant())
                .withStatus(PaymentInstrumentStatus.CREATED)
                .build();
        
        // @TODO(sfount): transactional, should also emit event
        paymentInstrumentDao.persist(entity);
        return entity;
    }

    public PaymentInstrumentEntity create(String externalId, AuthCardDetails authCardDetails) {
        var entity = new PaymentInstrumentEntity.PaymentInstrumentEntityBuilder()
                .withExternalId(externalId)
                .withCardDetails(buildCardDetailsEntity(authCardDetails))
                .withCreatedDate(clock.instant())
                .withStatus(PaymentInstrumentStatus.CREATED)
                .build();

        // @TODO(sfount): transactional, should also emit event
        paymentInstrumentDao.persist(entity);
        return entity;
    }
    
    // @TODO(sfount): separate out so it can be shared with `ChargeService`
    private CardDetailsEntity buildCardDetailsEntity(AuthCardDetails authCardDetails) {
        CardDetailsEntity detailsEntity = new CardDetailsEntity();
        detailsEntity.setCardBrand(sanitize(authCardDetails.getCardBrand()));
        detailsEntity.setCardHolderName(sanitize(authCardDetails.getCardHolder()));
        detailsEntity.setExpiryDate(authCardDetails.getEndDate());
        if (authCardDetails.getCardNo().length() > 6) { // Apple Pay etc. don’t give us a full card number, just the last four digits here
            detailsEntity.setFirstDigitsCardNumber(FirstDigitsCardNumber.of(StringUtils.left(authCardDetails.getCardNo(), 6)));
        }

        if (authCardDetails.getCardNo().length() >= 4) {
            detailsEntity.setLastDigitsCardNumber(LastDigitsCardNumber.of(StringUtils.right(authCardDetails.getCardNo(), 4)));
        }

        authCardDetails.getAddress().ifPresent(address -> {
            var addressEntity = new AddressEntity(address);
            northAmericanRegionMapper.getNorthAmericanRegionForCountry(address)
                    .map(NorthAmericaRegion::getAbbreviation)
                    .ifPresent(stateOrProvinceAbbreviation -> {
                        addressEntity.setStateOrProvince(stateOrProvinceAbbreviation);
                    });
            detailsEntity.setBillingAddress(addressEntity);
        });

        detailsEntity.setCardType(PayersCardType.toCardType(authCardDetails.getPayersCardType()));

        return detailsEntity;
    } 
}
