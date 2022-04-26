package uk.gov.pay.connector.charge.service.motoapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.motoapi.CardNumberRejectedException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.client.cardid.service.CardidService;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.cardtype.dao.CardTypeEntityBuilder.aCardTypeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@ExtendWith(MockitoExtension.class)
class MotoApiCardNumberValidationServiceTest {
    
    @Mock
    ChargeService mockChargeService;
    
    @Mock
    ChargeDao mockChargeDao;
    
    @Mock
    CardidService mockCardidService;
    
    @InjectMocks
    MotoApiCardNumberValidationService motoApiCardNumberValidationService;

    public static final String VALID_CARD_NUMBER = "4242424242424242";
    public static final String VISA = "visa";

    CardTypeEntity visaCredit = aCardTypeEntity().withType(CardType.CREDIT).withBrand(VISA).build();
    GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().withCardTypes(List.of(visaCredit)).build();
    ChargeEntity chargeEntity = aValidChargeEntity()
            .withStatus(ChargeStatus.CREATED)
            .withGatewayAccountEntity(gatewayAccountEntity)
            .build();
    CardInformation acceptedCardTypeCardInformation = aCardInformation()
            .withType(CardidCardType.CREDIT)
            .withBrand(VISA)
            .build();

    GatewayAccountEntity gatewayAccountWithBlockPrepaid = aGatewayAccountEntity()
            .withBlockPrepaidCards(true)
            .withCardTypes(List.of(visaCredit))
            .build();
    ChargeEntity chargeWithBlockPrepaid = aValidChargeEntity()
            .withStatus(CREATED)
            .withGatewayAccountEntity(gatewayAccountWithBlockPrepaid)
            .build();
    
    @Test
    void shouldFailForCardNumberWithInvalidCheckDigit() {
        CardNumberRejectedException exception = assertThrows(CardNumberRejectedException.class, () -> motoApiCardNumberValidationService.validateCardNumber(chargeEntity, "1111111111111111"));
        
        assertThat(exception.getMessage(), is("The card_number is not a valid card number"));
        verify(mockChargeService).transitionChargeState(chargeEntity, AUTHORISATION_REJECTED);
        verify(mockChargeDao).merge(chargeEntity);
    }

    @Test
    void shouldFailForCardNumberNotFoundInCardid() {
        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.empty());

        CardNumberRejectedException exception = assertThrows(CardNumberRejectedException.class, () -> motoApiCardNumberValidationService.validateCardNumber(chargeEntity, VALID_CARD_NUMBER));

        assertThat(exception.getMessage(), is("The card_number is not a valid card number"));
        verify(mockChargeService).transitionChargeState(chargeEntity, AUTHORISATION_REJECTED);
        verify(mockChargeDao).merge(chargeEntity);
    }

    @Test
    void shouldFailForPrepaidCardWhenPrepaidCardsBlocked() {
        CardInformation cardInformation = aCardInformation().withPrepaidStatus(PayersCardPrepaidStatus.PREPAID).build();

        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.of(cardInformation));
        CardNumberRejectedException exception = assertThrows(CardNumberRejectedException.class, () -> 
                motoApiCardNumberValidationService.validateCardNumber(chargeWithBlockPrepaid, VALID_CARD_NUMBER));

        assertThat(exception.getMessage(), is("Prepaid cards are not accepted"));
        verify(mockChargeService).transitionChargeState(chargeWithBlockPrepaid, AUTHORISATION_REJECTED);
        verify(mockChargeDao).merge(chargeWithBlockPrepaid);
    }

    @Test
    void shouldSucceedForNotPrepayWhenPrepaidCardsBlocked() {
        CardInformation cardInformation = aCardInformation()
                .withPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .withType(CardidCardType.CREDIT)
                .withBrand(VISA)
                .build();

        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.of(cardInformation));

        CardInformation result = motoApiCardNumberValidationService.validateCardNumber(chargeWithBlockPrepaid, VALID_CARD_NUMBER);
        assertThat(result, is(cardInformation));
    }
    
    @Test
    void shouldSucceedForUnknownPrepayWhenPrepaidCardsBlocked() {
        CardInformation cardInformation = aCardInformation()
                .withPrepaidStatus(PayersCardPrepaidStatus.UNKNOWN)
                .withType(CardidCardType.CREDIT)
                .withBrand(VISA)
                .build();

        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.of(cardInformation));

        CardInformation result = motoApiCardNumberValidationService.validateCardNumber(chargeWithBlockPrepaid, VALID_CARD_NUMBER);
        assertThat(result, is(cardInformation));
    }

    @Test
    void shouldSucceedForPrepaidWhenPrepaidCardsNotBlocked() {
        CardInformation cardInformation = aCardInformation()
                .withPrepaidStatus(PayersCardPrepaidStatus.PREPAID)
                .withType(CardidCardType.CREDIT)
                .withBrand(VISA)
                .build();

        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.of(cardInformation));

        CardInformation result = motoApiCardNumberValidationService.validateCardNumber(chargeEntity, VALID_CARD_NUMBER);
        assertThat(result, is(cardInformation));
    }

    @Test
    void shouldFailForCardTypeForBrandNotEnabled() {
        CardInformation cardInformation = aCardInformation()
                .withType(CardidCardType.DEBIT)
                .withBrand(VISA)
                .build();

        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.of(cardInformation));
        CardNumberRejectedException exception = assertThrows(CardNumberRejectedException.class, () ->
                motoApiCardNumberValidationService.validateCardNumber(chargeEntity, VALID_CARD_NUMBER));

        assertThat(exception.getMessage(), is("The card type is not enabled: visa debit"));
        verify(mockChargeService).transitionChargeState(chargeEntity, AUTHORISATION_REJECTED);
        verify(mockChargeDao).merge(chargeEntity);
    }

    @Test
    void shouldFailForCardBrandNotEnabledWhenCardTypeIsCreditOrDebit() {
        CardInformation cardInformation = aCardInformation()
                .withType(CardidCardType.CREDIT_OR_DEBIT)
                .withBrand("master-card")
                .build();
        
        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.of(cardInformation));
        CardNumberRejectedException exception = assertThrows(CardNumberRejectedException.class, () ->
                motoApiCardNumberValidationService.validateCardNumber(chargeEntity, VALID_CARD_NUMBER));

        assertThat(exception.getMessage(), is("The card type is not enabled: master-card"));
        verify(mockChargeService).transitionChargeState(chargeEntity, AUTHORISATION_REJECTED);
        verify(mockChargeDao).merge(chargeEntity);
    }

    @Test
    void shouldSucceedForBrandEnabledWhenCardTypeIsCreditOrDebit() {
        CardInformation cardInformation = aCardInformation()
                .withType(CardidCardType.CREDIT_OR_DEBIT)
                .withBrand(VISA)
                .build();

        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.of(cardInformation));
        
        CardInformation result = motoApiCardNumberValidationService.validateCardNumber(chargeEntity, VALID_CARD_NUMBER);
        assertThat(result, is(cardInformation));
    }

    @Test
    void shouldSucceedForValidCardNumber() {
        when(mockCardidService.getCardInformation(VALID_CARD_NUMBER)).thenReturn(Optional.of(acceptedCardTypeCardInformation));
        CardInformation result = motoApiCardNumberValidationService.validateCardNumber(chargeEntity, VALID_CARD_NUMBER);
        assertThat(result, is(acceptedCardTypeCardInformation));
        verify(mockChargeService, never()).transitionChargeState(eq(chargeEntity), any());
    }
}
