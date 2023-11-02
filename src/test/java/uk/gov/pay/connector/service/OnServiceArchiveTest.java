package uk.gov.pay.connector.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsHistoryDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.usernotification.model.domain.NotificationCredentials;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.RECURRING_MERCHANT_INITIATED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccount.model.StripeCredentials.STRIPE_ACCOUNT_ID_KEY;
import static uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials.DELETED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

@ExtendWith(MockitoExtension.class)
public class OnServiceArchiveTest {

    private GatewayAccountService gatewayAccountService;

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;
    
    @Mock
    private GatewayAccountCredentialsHistoryDao mockGatewayAccountCredentialsHistoryDao;
    
    @Mock
    private GatewayAccountCredentialsDao mockGatewayAccountCredentialsDao;

    @Captor
    private ArgumentCaptor<GatewayAccountEntity> updatedGatewayAccountEntity;
    
    @Captor
    private ArgumentCaptor<GatewayAccountCredentialsEntity> updatedGatewayAccountCredentialsEntity;

    @Captor
    private ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor;

    @Mock
    private Appender<ILoggingEvent> mockAppender;
    
    @BeforeEach
    void setUp() {
        gatewayAccountService = new GatewayAccountService(mockGatewayAccountDao, mock(CardTypeDao.class),
                mock(GatewayAccountCredentialsService.class), mockGatewayAccountCredentialsHistoryDao, 
                mockGatewayAccountCredentialsDao);
    }

    @Test
    void shouldLogIfNotWorldpayOrEpdqCredentials() {
        Logger root = (Logger) LoggerFactory.getLogger(GatewayAccountService.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);

        String serviceId = "service-to-be-archived";

        GatewayAccountEntity account1 = new GatewayAccountEntity(TEST);
        account1.setExternalId(randomUuid());
        account1.setDisabled(false);
        account1.setGatewayAccountCredentials(List.of(
                new GatewayAccountCredentialsEntity(account1, STRIPE.getName(), Map.of(STRIPE_ACCOUNT_ID_KEY, "acct_123"), ACTIVE)));
        when(mockGatewayAccountDao.findByServiceId(serviceId)).thenReturn(List.of(account1));

        when(mockGatewayAccountCredentialsHistoryDao.delete(serviceId)).thenReturn(1);

        gatewayAccountService.onServiceArchive(serviceId);

        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        assertThat(loggingEventArgumentCaptor.getValue().getFormattedMessage(), containsString("No credentials to redact."));
    }
    
    @Test
    void shouldDisableWorldpayAccount_redactCredentials_deleteCredentialsHistory() {
        String serviceId = "service-to-be-archived";
        Map<String, Object> worldpayCreds = Map.of(
                ONE_OFF_CUSTOMER_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-1", CREDENTIALS_USERNAME, "a-merchant-code-1", CREDENTIALS_PASSWORD, "passw0rd1"),
                RECURRING_MERCHANT_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-3", CREDENTIALS_USERNAME, "a-merchant-code-3", CREDENTIALS_PASSWORD, "passw0rd1"));
        
        GatewayAccountEntity account1 = new GatewayAccountEntity(TEST);
        account1.setExternalId(randomUuid());
        account1.setDisabled(false);
        account1.setNotificationCredentials(new NotificationCredentials(account1));
        account1.setGatewayAccountCredentials(List.of(
                new GatewayAccountCredentialsEntity(account1, WORLDPAY.getName(), worldpayCreds, ACTIVE)));
        when(mockGatewayAccountDao.findByServiceId(serviceId)).thenReturn(List.of(account1));
        
        when(mockGatewayAccountCredentialsHistoryDao.delete(serviceId)).thenReturn(1);
        
        gatewayAccountService.onServiceArchive(serviceId);
        
        verify(mockGatewayAccountDao).findByServiceId(serviceId);
        
        verify(mockGatewayAccountDao).merge(updatedGatewayAccountEntity.capture());
        var capturedGatewayAccountEntity = updatedGatewayAccountEntity.getValue();
        assertTrue(capturedGatewayAccountEntity.isDisabled());
        assertNull(capturedGatewayAccountEntity.getNotificationCredentials());
        
        verify(mockGatewayAccountCredentialsDao).merge(updatedGatewayAccountCredentialsEntity.capture());
        var capturedGatewayAccountCredentialsEntity = updatedGatewayAccountCredentialsEntity.getValue();
        assertThat(capturedGatewayAccountCredentialsEntity.getState(), is(RETIRED));
        Map<String, Object> expectedCredentials = Map.of(
                ONE_OFF_CUSTOMER_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-1", CREDENTIALS_USERNAME, DELETED, CREDENTIALS_PASSWORD, DELETED),
                RECURRING_MERCHANT_INITIATED, Map.of(CREDENTIALS_MERCHANT_CODE, "a-merchant-code-3", CREDENTIALS_USERNAME, DELETED, CREDENTIALS_PASSWORD, DELETED)
        );
        assertThat(capturedGatewayAccountCredentialsEntity.getCredentials(), is(expectedCredentials));

        verify(mockGatewayAccountCredentialsHistoryDao).delete(serviceId);
    }

    @Test
    void shouldDisableEpdqAccount_redactCredentials_deleteCredentialsHistory() {
        String serviceId = "service-to-be-archived";

        GatewayAccountEntity account1 = new GatewayAccountEntity(TEST);
        account1.setExternalId(randomUuid());
        account1.setDisabled(false);
        account1.setNotificationCredentials(new NotificationCredentials(account1));
        Map<String, Object> epdqCreds = Map.of("merchant_id", "a-merchant-id",
                "username", "a-secret-username",
                "password", "a-secret-password",
                "sha_in_passphrase", "123456",
                "sha_out_passphrase", "123456");
        account1.setGatewayAccountCredentials(List.of(
                new GatewayAccountCredentialsEntity(account1, EPDQ.getName(), epdqCreds, ACTIVE)));
        when(mockGatewayAccountDao.findByServiceId(serviceId)).thenReturn(List.of(account1));

        when(mockGatewayAccountCredentialsHistoryDao.delete(serviceId)).thenReturn(1);

        gatewayAccountService.onServiceArchive(serviceId);

        verify(mockGatewayAccountDao).findByServiceId(serviceId);

        verify(mockGatewayAccountDao).merge(updatedGatewayAccountEntity.capture());
        var capturedGatewayAccountEntity = updatedGatewayAccountEntity.getValue();
        assertTrue(capturedGatewayAccountEntity.isDisabled());
        assertNull(capturedGatewayAccountEntity.getNotificationCredentials());

        verify(mockGatewayAccountCredentialsDao).merge(updatedGatewayAccountCredentialsEntity.capture());
        var capturedGatewayAccountCredentialsEntity = updatedGatewayAccountCredentialsEntity.getValue();
        assertThat(capturedGatewayAccountCredentialsEntity.getState(), is(RETIRED));
        Map<String, Object> expectedCredentials = Map.of("merchant_id", "a-merchant-id",
                "username", "<DELETED>",
                "password", "<DELETED>",
                "sha_in_passphrase", "123456",
                "sha_out_passphrase", "123456");
        assertThat(capturedGatewayAccountCredentialsEntity.getCredentials(), is(expectedCredentials));

        verify(mockGatewayAccountCredentialsHistoryDao).delete(serviceId);
    }
}
