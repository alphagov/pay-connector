package uk.gov.pay.connector.agreement.service;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

@RunWith(MockitoJUnitRunner.class)
public class AgreementServiceTest {

    private static final String SERVICE_ID = "TestAgreementServiceID";

    private static final long GATEWAY_ACCOUNT_ID = 10L;

    private static final String REFERENCE_ID = "test";

    private GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);

    private AgreementDao mockedAgreementDao = mock(AgreementDao.class);

    private GatewayAccountDao mockedGatewayAccountDao = mock(GatewayAccountDao.class);

    private AgreementService service;

    @Before
    public void setUp() {
        String instantExpected = "2022-03-03T10:15:30Z";
        Clock clock = Clock.fixed(Instant.parse(instantExpected), ZoneOffset.UTC);
        service = new AgreementService(mockedAgreementDao, mockedGatewayAccountDao, clock);
    }

    @Test
    public void shouldCreateAnAgreement() {
        when(gatewayAccount.getServiceId()).thenReturn(SERVICE_ID);
        when(gatewayAccount.isLive()).thenReturn(false);
        when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

        AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID);
        Optional<AgreementResponse> response = service.create(agreementCreateRequest, GATEWAY_ACCOUNT_ID);

        assertThat(response.isPresent(), is(true));
        assertThat(REFERENCE_ID, is(response.get().getReference()));
        assertThat(SERVICE_ID, is(response.get().getServiceId()));
    }
}
