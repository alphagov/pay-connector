package uk.gov.pay.connector.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.ChargeSearchParams;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;

@RunWith(MockitoJUnitRunner.class)
public class CardCaptureProcessTest {

    @InjectMocks
    CardCaptureProcess cardCaptureProcess;

    @Mock
    ChargeDao mockChargeDao;

    @Mock
    CardCaptureService mockCardCaptureService;


    @Test
    public void shouldRetrieveASpecifiedNumberOfChargesApprovedForCapture() {
        ArgumentCaptor<ChargeSearchParams> searchParamsArgumentCaptor = ArgumentCaptor.forClass(ChargeSearchParams.class);

        cardCaptureProcess.runCapture();

        verify(mockChargeDao).findAllBy(searchParamsArgumentCaptor.capture());

        assertThat(searchParamsArgumentCaptor.getValue().getDisplaySize(),
                is(CardCaptureProcess.BATCH_SIZE));
        assertThat(searchParamsArgumentCaptor.getValue().getChargeStatuses(), hasItem(CAPTURE_APPROVED));
        assertThat(searchParamsArgumentCaptor.getValue().getPage(), is(1L));
    }

    @Test
    public void shouldRunCaptureForAllCharges() {
        ChargeEntity mockCharge1 = mock(ChargeEntity.class);
        ChargeEntity mockCharge2 = mock(ChargeEntity.class);

        when(mockChargeDao.findAllBy(any(ChargeSearchParams.class))).thenReturn(asList(mockCharge1, mockCharge2));
        when(mockCharge1.getExternalId()).thenReturn("my-charge-1");
        when(mockCharge2.getExternalId()).thenReturn("my-charge-2");

        cardCaptureProcess.runCapture();

        verify(mockCardCaptureService).doCapture("my-charge-1");
        verify(mockCardCaptureService).doCapture("my-charge-2");

    }
}
