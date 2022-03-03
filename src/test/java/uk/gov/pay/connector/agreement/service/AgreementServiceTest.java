package uk.gov.pay.connector.agreement.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import junitparams.JUnitParamsRunner;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;


@RunWith(JUnitParamsRunner.class)
public class AgreementServiceTest {

    private static final String SERVICE_ID = "TestAgreementServiceID";

    private static final long GATEWAY_ACCOUNT_ID = 10L;
    
    private  static final String REFERENCE_ID = "test";

    @Rule
    private MockitoRule rule = MockitoJUnit.rule();

    @Rule
    private ExpectedException thrown = ExpectedException.none();
    
    @Mock
    private GatewayAccountEntity gatewayAccount;
    

    @Mock
    private AgreementDao mockedAgreementDao;

    @Mock
    private GatewayAccountDao mockedGatewayAccountDao;


    private AgreementService service;
    
    @Before
    public void setUp() {
        service = new AgreementService(mockedAgreementDao, mockedGatewayAccountDao, Clock.systemUTC());
    }


    @Test
    public void shouldCreateAnAgreement() { 
        when(gatewayAccount.getServiceId()).thenReturn(SERVICE_ID);
        when(gatewayAccount.isLive()).thenReturn(false);
    	when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

    	AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest(REFERENCE_ID);    	
    	Optional<AgreementResponse> response = service.create(agreementCreateRequest, GATEWAY_ACCOUNT_ID);

    	assertNotNull(response.get());
    	assertEquals(REFERENCE_ID, response.get().getReference());
    	assertEquals(SERVICE_ID, response.get().getServiceId());
    }
}
