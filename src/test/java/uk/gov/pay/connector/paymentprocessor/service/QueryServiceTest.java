package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

@RunWith(MockitoJUnitRunner.class)
public class QueryServiceTest {
    
    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private PaymentProviders paymentProviders;

    @InjectMocks
    private QueryService queryService;

    @Before
    public void setUp() {
        when(paymentProviders.byName(any())).thenReturn(paymentProvider);
    }

    @Test
    public void isTerminableWithGateway_returnsTrueForNotFinishedExternalStatus() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        ChargeQueryResponse response = new ChargeQueryResponse(AUTHORISATION_3DS_REQUIRED, "a-response");
        when(paymentProvider.queryPaymentStatus(chargeEntity)).thenReturn(response);

        assertThat(queryService.isTerminableWithGateway(chargeEntity), is(true));
    }

    @Test
    public void isTerminableWithGateway_returnsFalseForFinishedExternalStatus() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        ChargeQueryResponse response = new ChargeQueryResponse(CAPTURED, "a-response");
        when(paymentProvider.queryPaymentStatus(chargeEntity)).thenReturn(response);

        assertThat(queryService.isTerminableWithGateway(chargeEntity), is(false));
    }
}
