package uk.gov.pay.connector.charge.service.motoapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.exception.motoapi.CardNumberRejectedException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.service.CardidService;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;

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
    
    ChargeEntity chargeEntity = aValidChargeEntity().withStatus(ChargeStatus.CREATED).build();
    CardInformation cardInformation = aCardInformation().build();
    
    @Test
    void shouldFailForCardNumberWithInvalidCheckDigit() {
        CardNumberRejectedException exception = assertThrows(CardNumberRejectedException.class, () -> motoApiCardNumberValidationService.validateCardNumber(chargeEntity, "1111111111111111"));
        
        assertThat(exception.getMessage(), is("The card_number is not a valid card number"));
        verify(mockChargeService).transitionChargeState(chargeEntity, AUTHORISATION_REJECTED);
        verify(mockChargeDao).merge(chargeEntity);
    }

    @Test
    void shouldFailForCardNumberNotFoundInCardid() {
        when(mockCardidService.getCardInformation(any())).thenReturn(Optional.empty());

        CardNumberRejectedException exception = assertThrows(CardNumberRejectedException.class, () -> motoApiCardNumberValidationService.validateCardNumber(chargeEntity, VALID_CARD_NUMBER));

        assertThat(exception.getMessage(), is("The card_number is not a valid card number"));
        verify(mockChargeService).transitionChargeState(chargeEntity, AUTHORISATION_REJECTED);
        verify(mockChargeDao).merge(chargeEntity);
    }

    @Test
    void shouldSucceedForValidCardNumber() {
        when(mockCardidService.getCardInformation(any())).thenReturn(Optional.of(cardInformation));
        CardInformation cardInformation = motoApiCardNumberValidationService.validateCardNumber(chargeEntity, VALID_CARD_NUMBER);
        assertThat(cardInformation, is(cardInformation));
        verify(mockChargeService, never()).transitionChargeState(eq(chargeEntity), any());
    }
}
