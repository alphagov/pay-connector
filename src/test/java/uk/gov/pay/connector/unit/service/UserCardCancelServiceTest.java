package uk.gov.pay.connector.unit.service;

import fj.data.Either;
import org.junit.Test;
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
    private final UserCardCancelService userCardCancelService = new UserCardCancelService(mockedChargeDao, mockedProviders, chargeService);

    private final Long chargeId = 1234L;
    private final Long accountId = 1L;

    @Test
    public void whenChargeThatHasStatusEnteringCardDetailsIsCancelled_chargeShouldBeCancelledWithoutCallingGatewayProvider() throws Exception {
        ChargeEntity charge = createNewChargeWith(chargeId, CREATED);

        mockChargeDaoFindByChargeId(charge);
        verifyPaymentProviderNotCalled();

        Either<ErrorResponse, GatewayResponse> response = userCardCancelService.doCancel(charge.getExternalId());

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(aSuccessfulResponse()));
        verifyChargeUpdated(charge, USER_CANCELLED);
    }

    @Test
    public void whenUsersTriesToCancelChargeAndPaymentGatewayFails_chargeStatusShouldBeSetToCancelError() {
        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);

        mockChargeDaoMergeCharge(charge);
        mockChargeDaoFindByChargeId(charge);
        mockUnsuccessfulCancel();

        Either<ErrorResponse, GatewayResponse> response = userCardCancelService.doCancel(charge.getExternalId());

        assertTrue(response.isRight());
        assertThat(response.right().value(), is(anUnSuccessfulResponse()));
        verifyChargeUpdated(charge, CANCEL_ERROR);
    }

    private void mockChargeDaoFindByChargeId(ChargeEntity charge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
    }
}
