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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;

@ExtendWith(MockitoExtension.class)
class MotoApiCardNumberValidationServiceTest {
    
    @Mock
    ChargeService mockChargeService;
    
    @Mock
    ChargeDao mockChargeDao;
    
    @InjectMocks
    MotoApiCardNumberValidationService motoApiCardNumberValidationService;
    
    ChargeEntity chargeEntity = aValidChargeEntity().withStatus(ChargeStatus.CREATED).build();
    
    @Test
    void shouldFailForCardNumberWithInvalidCheckDigit() {
        CardNumberRejectedException exception = assertThrows(CardNumberRejectedException.class, () -> motoApiCardNumberValidationService.validateCardNumber(chargeEntity, "1111111111111111"));
        
        assertThat(exception.getMessage(), is("The card_number is not a valid card number"));
        verify(mockChargeService).transitionChargeState(chargeEntity, AUTHORISATION_REJECTED);
        verify(mockChargeDao).merge(chargeEntity);
    }

    @Test
    void shouldSucceedForValidCardNumber() {
        assertDoesNotThrow(() -> motoApiCardNumberValidationService.validateCardNumber(chargeEntity, "4242424242424242"));
        verify(mockChargeService, never()).transitionChargeState(eq(chargeEntity), any());
    }
}
