package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.service.UserCardCancelService;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class UserCardCancelServiceTest extends CardCancelServiceTest {
    private final UserCardCancelService userCardCancelService = new UserCardCancelService(mockedChargeDao, mockedProviders);

    @Test
    public void whenChargeThatHasStatusEnteringCardDetailsIsCancelled_chargeShouldBeCancelledWithoutCallingGatewayProvider() throws Exception {

        Long chargeId = 1234L;
        Long accountId = 1L;

        ChargeEntity charge = createNewChargeWith(chargeId, ENTERING_CARD_DETAILS);
        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(reloadedCharge);

        verifyPaymentProviderNotCalled();

        Either<ErrorResponse, GatewayResponse> response = userCardCancelService.doCancel(charge.getExternalId());

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));

        ArgumentCaptor<ChargeEntity> argumentCaptor = ArgumentCaptor.forClass(ChargeEntity.class);
        verify(mockedChargeDao).mergeAndNotifyStatusHasChanged(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue(), is(reloadedCharge));

        verify(reloadedCharge, times(1)).setStatus(USER_CANCELLED);
    }

    @Test
    public void whenUsersTriesToCancelChargeAndPaymentGatewayFails_chargeStatusShouldBeSetToCancelError() {
        Long chargeId = 1234L;

        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);

        ChargeEntity reloadedCharge = mock(ChargeEntity.class);

        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
        when(mockedChargeDao.merge(charge))
                .thenReturn(charge)
                .thenReturn(reloadedCharge);

        mockUnsuccessfulCancel();

        Either<ErrorResponse, GatewayResponse> response = userCardCancelService.doCancel(charge.getExternalId());

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));

        verify(reloadedCharge, times(1)).setStatus(CANCEL_ERROR);
    }
}
