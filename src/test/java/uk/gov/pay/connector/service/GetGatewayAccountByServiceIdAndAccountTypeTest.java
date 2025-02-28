package uk.gov.pay.connector.service;

import com.google.inject.persist.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.MultipleLiveGatewayAccountsException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsHistoryDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;

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
    
    private GatewayAccountEntity smartpayGatewayAccount;
    
    private static final String SERVICE_ID = "a-service-id";

    @BeforeEach
    void setUp() {
        gatewayAccountService = new GatewayAccountService(mockGatewayAccountDao, mockCardTypeDao,
                mockGatewayAccountCredentialsService, mock(GatewayAccountCredentialsHistoryDao.class), mock(GatewayAccountCredentialsDao.class), mock(UnitOfWork.class));
        
        stripeGatewayAccount = new GatewayAccountEntity(TEST);
        var stripeGatewayAccountCreds = new GatewayAccountCredentialsEntity(stripeGatewayAccount, "stripe", Map.of(), ACTIVE);
        stripeGatewayAccount.setGatewayAccountCredentials(List.of(stripeGatewayAccountCreds));
        
        sandboxGatewayAccount = new GatewayAccountEntity(TEST);
        var sandboxGatewayAccountCreds = new GatewayAccountCredentialsEntity(sandboxGatewayAccount, "sandbox", Map.of(), ACTIVE);
        sandboxGatewayAccount.setGatewayAccountCredentials(List.of(sandboxGatewayAccountCreds));
        
        worldpayGatewayAccount = new GatewayAccountEntity(TEST);
        var worldpayGatewayAccountCreds = new GatewayAccountCredentialsEntity(worldpayGatewayAccount, "worldpay", Map.of(), ACTIVE);
        worldpayGatewayAccount.setGatewayAccountCredentials(List.of(worldpayGatewayAccountCreds));
        
        smartpayGatewayAccount = new GatewayAccountEntity(TEST);
        var smartpayGatewayAccountCreds = new GatewayAccountCredentialsEntity(smartpayGatewayAccount, "smartpay", Map.of(), ACTIVE);
        smartpayGatewayAccount.setGatewayAccountCredentials(List.of(smartpayGatewayAccountCreds));
    }

    @Test
    void shouldReturnStripeGatewayAccountWhenThereAreMultipleGatewayAccounts() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, TEST))
                .thenReturn(List.of(worldpayGatewayAccount, sandboxGatewayAccount, stripeGatewayAccount));

        Optional<GatewayAccountEntity> gatewayAccount = 
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, TEST);
        assertTrue(gatewayAccount.isPresent());
        assertTrue(gatewayAccount.get().isStripeGatewayAccount());
    }
    
    @Test
    void shouldReturnNothingWhenThereAreNoGatewayAccounts() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, TEST))
                .thenReturn(List.of());

        Optional<GatewayAccountEntity> gatewayAccount =
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, TEST);
        assertFalse(gatewayAccount.isPresent());
    }

    @Test
    void shouldReturnSandboxGatewayAccountWhenThereAreSandboxAndWorldpayAccounts() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, TEST))
                .thenReturn(List.of(worldpayGatewayAccount, sandboxGatewayAccount));

        Optional<GatewayAccountEntity> gatewayAccount =
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, TEST);
        assertTrue(gatewayAccount.isPresent());
        assertTrue(gatewayAccount.get().isSandboxGatewayAccount());
    }

    @Test
    void shouldIgnoreDisabledGatewayAccounts() {
        var gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(10L);
        var stripeCreds = new GatewayAccountCredentialsEntity(gatewayAccount, "stripe", Map.of(), RETIRED);
        stripeCreds.setActiveEndDate(Instant.now());
        stripeCreds.setActiveStartDate(Instant.now().minus(Duration.ofDays(1)));
        var worldpayCreds = new GatewayAccountCredentialsEntity(gatewayAccount, "worldpay", Map.of(), ACTIVE);
        worldpayCreds.setActiveStartDate(Instant.now());
        gatewayAccount.setGatewayAccountCredentials(List.of(stripeCreds, worldpayCreds));
        
        var disabledSandboxGatewayAccount = new GatewayAccountEntity(TEST);
        disabledSandboxGatewayAccount.setDisabled(true);
        disabledSandboxGatewayAccount.setDisabledReason("Superseded by newer test account [gateway_account_id: 10]");
        var sandboxCreds = new GatewayAccountCredentialsEntity(disabledSandboxGatewayAccount, "sandbox", Map.of(), ACTIVE);
        sandboxCreds.setActiveStartDate(Instant.now().minus(Duration.ofDays(1)));
        disabledSandboxGatewayAccount.setGatewayAccountCredentials(List.of(sandboxCreds));

        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, TEST))
                .thenReturn(List.of(gatewayAccount, disabledSandboxGatewayAccount));

        var retrievedGatewayAccount = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, TEST);
        assertTrue(retrievedGatewayAccount.isPresent());
        assertTrue(retrievedGatewayAccount.get().isWorldpayGatewayAccount());
    }
    
    @Test
    void shouldReturnWorldpayGatewayAccount() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, TEST))
                .thenReturn(List.of(worldpayGatewayAccount));

        Optional<GatewayAccountEntity> gatewayAccount =
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, TEST);
        assertTrue(gatewayAccount.isPresent());
        assertTrue(gatewayAccount.get().isWorldpayGatewayAccount());
    }
    
    @Test
    void shouldNotReturnObscureGatewayAccount() {
        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, TEST))
                .thenReturn(List.of(smartpayGatewayAccount));

        Optional<GatewayAccountEntity> gatewayAccount =
                gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, TEST);
        assertFalse(gatewayAccount.isPresent());
    }
    
    @Test
    void shouldLogAndThrowErrorIfThereAreMultipleLiveGatewayAccounts() {
        var stripeLiveGatewayAccount = new GatewayAccountEntity(LIVE);
        var stripeGatewayAccountCreds = new GatewayAccountCredentialsEntity(stripeLiveGatewayAccount, "stripe", Map.of(), ACTIVE);
        stripeLiveGatewayAccount.setGatewayAccountCredentials(List.of(stripeGatewayAccountCreds));
        
        var worldpayLiveGatewayAccount = new GatewayAccountEntity(LIVE);
        var worldpayGatewayAccountCreds = new GatewayAccountCredentialsEntity(worldpayLiveGatewayAccount, "worldpay", Map.of(), ACTIVE);
        worldpayLiveGatewayAccount.setGatewayAccountCredentials(List.of(worldpayGatewayAccountCreds));

        when(mockGatewayAccountDao.findByServiceIdAndAccountType(SERVICE_ID, LIVE))
                .thenReturn(List.of(stripeLiveGatewayAccount, worldpayLiveGatewayAccount));

        assertThrows(MultipleLiveGatewayAccountsException.class, 
                () -> gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(SERVICE_ID, LIVE));
    }
}
