package uk.gov.pay.connector.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsHistoryDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;

@ExtendWith(MockitoExtension.class)
public class GetGatewayAccountByServiceIdAndAccountTypeTest {

    private GatewayAccountService gatewayAccountService;

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;

    @Mock
    private CardTypeDao mockCardTypeDao;

    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;
    
    private GatewayAccountEntity stripeGatewayAccount;
    
    private GatewayAccountEntity sandboxGatewayAccount;
    
    private GatewayAccountEntity worldpayGatewayAccount;
    
    private static final String SERVICE_ID = "a-service-id";

    @BeforeEach
    void setUp() {
        gatewayAccountService = new GatewayAccountService(mockGatewayAccountDao, mockCardTypeDao,
                mockGatewayAccountCredentialsService, mock(GatewayAccountCredentialsHistoryDao.class), mock(GatewayAccountCredentialsDao.class));
        
        stripeGatewayAccount = new GatewayAccountEntity(GatewayAccountType.TEST);
        var stripeGatewayAccountCreds = new GatewayAccountCredentialsEntity(stripeGatewayAccount, "stripe", Map.of(), ACTIVE);
        stripeGatewayAccount.setGatewayAccountCredentials(List.of(stripeGatewayAccountCreds));
        
        sandboxGatewayAccount = new GatewayAccountEntity(GatewayAccountType.TEST);
        var sandboxGatewayAccountCreds = new GatewayAccountCredentialsEntity(sandboxGatewayAccount, "sandbox", Map.of(), ACTIVE);
        sandboxGatewayAccount.setGatewayAccountCredentials(List.of(sandboxGatewayAccountCreds));
        
        worldpayGatewayAccount = new GatewayAccountEntity(GatewayAccountType.TEST);
        var worldpayGatewayAccountCreds = new GatewayAccountCredentialsEntity(worldpayGatewayAccount, "worldpay", Map.of(), ACTIVE);
        worldpayGatewayAccount.setGatewayAccountCredentials(List.of(worldpayGatewayAccountCreds));
    }

    @Test
    void shouldReturnStripeGatewayAccountWhenThereAreMultipleGatewayAccounts() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST))
                .thenReturn(List.of(worldpayGatewayAccount, sandboxGatewayAccount, stripeGatewayAccount));

        Optional<GatewayAccountEntity> gatewayAccount = 
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST);
        assertTrue(gatewayAccount.isPresent());
        assertTrue(gatewayAccount.get().isStripeGatewayAccount());
    }
    
    @Test
    void shouldReturnNothingWhenThereAreNoGatewayAccounts() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST))
                .thenReturn(List.of());

        Optional<GatewayAccountEntity> gatewayAccount =
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST);
        assertFalse(gatewayAccount.isPresent());
    }

    @Test
    void shouldReturnSandboxGatewayAccountWhenThereAreSandboxAndWorldpayAccounts() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST))
                .thenReturn(List.of(worldpayGatewayAccount, sandboxGatewayAccount));

        Optional<GatewayAccountEntity> gatewayAccount =
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST);
        assertTrue(gatewayAccount.isPresent());
        assertTrue(gatewayAccount.get().isSandboxGatewayAccount());
    }
    
    @Test
    void shouldReturnWorldpayGatewayAccount() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST))
                .thenReturn(List.of(worldpayGatewayAccount));

        Optional<GatewayAccountEntity> gatewayAccount =
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, GatewayAccountType.TEST);
        assertTrue(gatewayAccount.isPresent());
        assertTrue(gatewayAccount.get().isWorldpayGatewayAccount());
    }
    
    @Test
    void shouldLogAndThrowErrorIfThereAreMultipleLiveGatewayAccounts() {
        //TODO
    }
}
