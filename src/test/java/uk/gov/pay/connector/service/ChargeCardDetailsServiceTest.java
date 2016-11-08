package uk.gov.pay.connector.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeCardDetailsDao;
import uk.gov.pay.connector.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.exception.IllegalStateRuntimeException;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeCardDetailsEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static uk.gov.pay.connector.model.domain.CardFixture.aValidCard;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;

@RunWith(MockitoJUnitRunner.class)
public class ChargeCardDetailsServiceTest extends CardServiceTest {
    private ChargeCardDetailsService chargeCardDetailsService;

    @Mock
    private ChargeCardDetailsDao mockedChargeCardDetailsDao;

    @Before
    public void beforeTest() {
        chargeCardDetailsService = new ChargeCardDetailsService(mockedChargeCardDetailsDao, mockedChargeDao);
    }

    @Test
    public void shouldStoreConfirmationDetails_ifChargeStatusIsAuthorisationSuccess() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, AUTHORISATION_SUCCESS);

        Card cardDetails = aValidCard()
                .withCardNo("11111111111111234")
                .withCardBrand("card-brand")
                .build();

        ChargeEntity mockedChargeEntity = mock(ChargeEntity.class);
        when(mockedChargeEntity.getStatus())
                .thenReturn(AUTHORISATION_SUCCESS.getValue());
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(mockedChargeEntity));

        chargeCardDetailsService.doStore(charge.getExternalId(), cardDetails);
        ArgumentCaptor<ChargeCardDetailsEntity> capturedConfirmationDetailsEntity = ArgumentCaptor.forClass(ChargeCardDetailsEntity.class);
        verify(mockedChargeCardDetailsDao, times(1)).persist(capturedConfirmationDetailsEntity.capture());
        assertThat(capturedConfirmationDetailsEntity.getValue().getChargeEntity(), is(mockedChargeEntity));
        assertThat(capturedConfirmationDetailsEntity.getValue().getCardHolderName(), is(cardDetails.getCardHolder()));
        assertThat(capturedConfirmationDetailsEntity.getValue().getLastDigitsCardNumber(), is("1234"));
        assertThat(capturedConfirmationDetailsEntity.getValue().getExpiryDate(), is(cardDetails.getEndDate()));
        assertThat(capturedConfirmationDetailsEntity.getValue().getBillingAddress().toAddress(), is(cardDetails.getAddress()));

        verify(mockedChargeEntity).setCardBrand(cardDetails.getCardBrand());
    }

    @Test(expected = ChargeNotFoundRuntimeException.class)
    public void shouldThrowAnExceptionWhenStoringDetails_ifChargeIsNotFound() throws Exception {
        when(mockedChargeDao.findByExternalId(any()))
                .thenReturn(Optional.empty());

        chargeCardDetailsService.doStore("", aValidCard().build());
    }

    @Test(expected = IllegalStateRuntimeException.class)
    public void shouldThrowAnExceptionWhenStoringDetails_ifChargeStatusIsNotAuthorisationSuccess() throws Exception {
        ChargeEntity charge = createNewChargeWith(1L, CAPTURE_READY);

        Card cardDetails = aValidCard().withCardNo("11111111111111234").build();

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));

        chargeCardDetailsService.doStore(charge.getExternalId(), cardDetails);
    }

}
