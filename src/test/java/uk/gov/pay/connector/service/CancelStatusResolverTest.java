package uk.gov.pay.connector.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.InternalExternalStatus;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.is;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CancelStatusResolverTest {

    CancelStatusResolver cancelStatusResolver;

    @Test
    public void shouldUserCancelSubmittedStateBeResolvedToUserCancelledState() {

        cancelStatusResolver = new CancelStatusResolver();
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);
        when(mockChargeEntity.getStatus()).thenReturn(ChargeStatus.USER_CANCEL_SUBMITTED.getValue());

        Optional<InternalExternalStatus> result = cancelStatusResolver.resolve(mockChargeEntity);
        assertTrue(result.isPresent());

        ChargeStatus chargeStatus = (ChargeStatus) result.get();
        assertThat(chargeStatus.getValue(), is(ChargeStatus.USER_CANCELLED.getValue()));
    }

    @Test
    public void shouldSystemCancelSubmittedStateBeResolvedToSystemCancelledState() {

        cancelStatusResolver = new CancelStatusResolver();
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);
        when(mockChargeEntity.getStatus()).thenReturn(ChargeStatus.SYSTEM_CANCEL_SUBMITTED.getValue());

        Optional<InternalExternalStatus> result = cancelStatusResolver.resolve(mockChargeEntity);
        assertTrue(result.isPresent());

        ChargeStatus chargeStatus = (ChargeStatus) result.get();
        assertThat(chargeStatus.getValue(), is(ChargeStatus.SYSTEM_CANCELLED.getValue()));

    }

    @Test
    public void shouldExpiredCancelSubmittedStateBeResolvedToExpiredState() {

        cancelStatusResolver = new CancelStatusResolver();
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);
        when(mockChargeEntity.getStatus()).thenReturn(ChargeStatus.EXPIRE_CANCEL_SUBMITTED.getValue());

        Optional<InternalExternalStatus> result = cancelStatusResolver.resolve(mockChargeEntity);
        assertTrue(result.isPresent());

        ChargeStatus chargeStatus = (ChargeStatus) result.get();
        assertThat(chargeStatus.getValue(), is(ChargeStatus.EXPIRED.getValue()));

    }

    @Test
    public void shouldTheResolverDefaultToSystemCancelState() {

        cancelStatusResolver = new CancelStatusResolver();
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);
        when(mockChargeEntity.getStatus()).thenReturn(ChargeStatus.CREATED.getValue());

        Optional<InternalExternalStatus> result = cancelStatusResolver.resolve(mockChargeEntity);
        assertTrue(result.isPresent());

        ChargeStatus chargeStatus = (ChargeStatus) result.get();
        assertThat(chargeStatus.getValue(), is(ChargeStatus.SYSTEM_CANCELLED.getValue()));

    }

}
