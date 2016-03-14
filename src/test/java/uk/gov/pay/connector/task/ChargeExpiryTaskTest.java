package uk.gov.pay.connector.task;

import com.google.common.collect.ImmutableMultimap;
import fj.data.Either;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.CancelResponse;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.GatewayErrorType;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.service.CardService;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ChargeExpiryTaskTest {

    @Mock
    ChargeDao mockChargeDao;
    @Mock
    CardService mockCardService;
    @Mock
    GatewayAccountEntity mockGatewayAccountEntity;
    @Mock
    ChargeEntity mockChargeEntity;
    @InjectMocks
    private ChargeExpiryTask chargeExpiryTask;

    @Before
    public void setup() {
        when(mockChargeEntity.getId()).thenReturn(10L);
        when(mockChargeEntity.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockGatewayAccountEntity.getId()).thenReturn(1L);
        when(mockChargeDao.findBeforeDateWithStatusIn(anyObject(), eq(ChargeExpiryTask.NON_TERMINAL_STATUSES))).thenReturn(asList(mockChargeEntity));
    }

    @Test
    public void shouldExpireChargeForCreatedState() throws Exception {
        when(mockChargeEntity.getStatus()).thenReturn(ChargeStatus.CREATED.getValue());

        chargeExpiryTask.execute(ImmutableMultimap.of(), null);

        verifyZeroInteractions(mockCardService);
        verify(mockChargeEntity).setStatus(ChargeStatus.EXPIRED);
    }

    @Test
    public void shouldExpireAndCancelChargeWhenAuthSuccess() throws Exception {
        mockCancelResponse(Either.right(new CancelResponse(true, null)));
        when(mockChargeEntity.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());

        chargeExpiryTask.execute(ImmutableMultimap.of(), null);

        verify(mockCardService, times(1)).doCancel(anyString(), anyLong());
        verify(mockChargeEntity, times(1)).setStatus(ChargeStatus.EXPIRED);
    }

    @Test
    public void shouldNotExpireChargeWhenCancelResponseUnsuccessful() throws Exception {
        mockCancelResponse(Either.right(new CancelResponse(false, null)));
        when(mockChargeEntity.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());

        chargeExpiryTask.execute(ImmutableMultimap.of(), null);

        verify(mockCardService, times(1)).doCancel(anyString(), anyLong());
        verify(mockChargeEntity, times(0)).setStatus(ChargeStatus.EXPIRED);
    }

    @Test
    public void shouldNotExpireChargeWhenCancelFailed() throws Exception {
        GatewayError gatewayError = new GatewayError("error-message", GatewayErrorType.GATEWAY_CONNECTION_TIMEOUT_ERROR);
        mockCancelResponse(Either.left(gatewayError));
        when(mockChargeEntity.getStatus()).thenReturn(ChargeStatus.AUTHORISATION_SUCCESS.getValue());

        chargeExpiryTask.execute(ImmutableMultimap.of(), null);

        verify(mockCardService, times(1)).doCancel(anyString(), anyLong());
        verify(mockChargeEntity, times(0)).setStatus(ChargeStatus.EXPIRED);
    }

    private void mockCancelResponse(Either<GatewayError, GatewayResponse> either) {
        when(mockCardService.doCancel(anyString(), anyLong())).thenReturn(either);
    }
}
