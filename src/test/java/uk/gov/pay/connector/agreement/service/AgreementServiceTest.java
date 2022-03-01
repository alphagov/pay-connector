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
import uk.gov.pay.connector.charge.model.telephone.TelephoneChargeCreateRequest;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;


@RunWith(JUnitParamsRunner.class)
public class AgreementServiceTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    protected static final long GATEWAY_ACCOUNT_ID = 10L;

    protected TelephoneChargeCreateRequest.Builder telephoneRequestBuilder;

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    protected GatewayAccountEntity gatewayAccount;
    

    @Mock
    protected AgreementDao mockedAgreementDao;

    @Mock
    protected GatewayAccountDao mockedGatewayAccountDao;


    protected AgreementService service;




    @Before
    public void setUp() {
        service = new AgreementService(mockedAgreementDao, mockedGatewayAccountDao, Clock.systemUTC());
    }

    private static String SERVICE_ID = "TestAgreementServiceID";
    

    @Test
    public void shouldCreateAnAgreement() { 
        when(gatewayAccount.getServiceId()).thenReturn(SERVICE_ID);
        when(gatewayAccount.isLive()).thenReturn(false);
    	when(mockedGatewayAccountDao.findById(GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(gatewayAccount));

    	AgreementCreateRequest agreementCreateRequest = new AgreementCreateRequest("test");    	
    	Optional<AgreementResponse> response = service.create(agreementCreateRequest, GATEWAY_ACCOUNT_ID);
    	
    	assertNotNull(response.get());
    	//assertEquals("test", response.get().getReference());
    	//assertEquals(SERVICE_ID, response.get().getServiceId());
    }
    
}
