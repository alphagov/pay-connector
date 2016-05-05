package uk.gov.pay.connector.unit.service;

import org.junit.Test;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.service.UserCardCancelService;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class UserCardCancelServiceTest extends CardCancelServiceTest {
    private final UserCardCancelService userCardCancelService = new UserCardCancelService(mockedChargeDao, mockedProviders, mockChargeService);

    private final Long chargeId = 1234L;
    private final Long accountId = 1L;

    @Test
    public void whenChargeThatHasStatusEnteringCardDetailsIsCancelled_chargeShouldBeCancelledWithoutCallingGatewayProvider() throws Exception {
        ChargeEntity charge = createNewChargeWith(chargeId, CREATED);

        mockChargeDaoFindByChargeId(charge);
        verifyPaymentProviderNotCalled();

        GatewayResponse response = userCardCancelService.doCancel(charge.getExternalId());

        assertThat(response, is(aSuccessfulResponse()));
        verifyChargeUpdated(charge, USER_CANCELLED);
    }

    @Test
    public void whenUsersTriesToCancelChargeAndPaymentGatewayFails_chargeStatusShouldBeSetToUserCancelError() {
        ChargeEntity charge = createNewChargeWith(chargeId, AUTHORISATION_SUCCESS);

        mockChargeDaoMergeCharge(charge);
        mockChargeDaoFindByChargeId(charge);
        mockUnsuccessfulCancel();

        GatewayResponse response = userCardCancelService.doCancel(charge.getExternalId());

        assertThat(response, is(anUnSuccessfulResponse()));
        verifyChargeUpdated(charge, USER_CANCEL_ERROR);
    }

    private void mockChargeDaoFindByChargeId(ChargeEntity charge) {
        when(mockedChargeDao.findByExternalId(charge.getExternalId()))
                .thenReturn(Optional.of(charge));
    }
}
