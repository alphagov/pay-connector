package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccount.dao.AdyenAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupResponse;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class AdyenAccountSetupServiceTest {

    public static final long GATEWAY_ACCOUNT_ID = 999L;
    private static final String SERVICE_ID = "service-123";
    private static final String CREDENTIAL_EXTERNAL_ID = "credential-123";
    @Mock
    private AdyenAccountSetupDao mockAdyenAccountSetupDao;

    private AdyenAccountSetupService adyenAccountSetupService;

    private static final String EXPECTED_ERROR_MSG = "Gateway account type must be TEST and gateway name must be ADYEN";

    @BeforeEach
    void setUp() {
        this.adyenAccountSetupService = new AdyenAccountSetupService(mockAdyenAccountSetupDao);
    }

    @Test
    void shouldReturnAdyenAccountSetupWithNoTasksCompleted() {
        given(mockAdyenAccountSetupDao.findByGatewayAccountIdAndCredentialId(GATEWAY_ACCOUNT_ID))
                .willReturn(Collections.emptyList());

        AdyenAccountSetupResponse tasksWithStatus = adyenAccountSetupService.buildResponse(SERVICE_ID, GATEWAY_ACCOUNT_ID, CREDENTIAL_EXTERNAL_ID);
        
        assertThat(tasksWithStatus.getServiceId(), is(SERVICE_ID));
        assertThat(tasksWithStatus.getCredentialExternalId(), is(CREDENTIAL_EXTERNAL_ID));
        assertThat(tasksWithStatus.getGatewayAccountId(), is(GATEWAY_ACCOUNT_ID));

        Arrays.stream(AdyenAccountSetupTask.values()).forEach(task -> 
            assertThat(tasksWithStatus.getTasks().get(task.getValue()).get("status"), is(AdyenAccountSetupStatus.NOT_STARTED)));
    }
    
    @Nested
    class completeTestAccountSetup {
        @Test
        void shouldCompleteAllTasks_WhenAccountIsAdyenAndTest() {
            var gatewayAccountCredentials = aGatewayAccountCredentialsEntity().withPaymentProvider(ADYEN.getName()).build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withType(TEST)
                    .withGatewayAccountCredentials(List.of(gatewayAccountCredentials))
                    .build();

            adyenAccountSetupService.completeTestAccountSetup(gatewayAccountEntity);

            ArgumentCaptor<AdyenAccountSetupTaskEntity> captor =
                    ArgumentCaptor.forClass(AdyenAccountSetupTaskEntity.class);

            verify(mockAdyenAccountSetupDao, times(AdyenAccountSetupTask.values().length)).persist(captor.capture());

            assertEquals(gatewayAccountCredentials.getId(), captor.getValue().getGatewayAccountCredential().getId());
            
            List<AdyenAccountSetupStatus> statuses = captor.getAllValues().stream()
                    .map(AdyenAccountSetupTaskEntity::getStatus)
                    .collect(Collectors.toList());

            assertThat(statuses, everyItem(is(AdyenAccountSetupStatus.COMPLETED)));
        }

        @Test
        void shouldThrowIllegalArgException_WhenAccountIsNotTest() {
            var gatewayAccountCredentials = aGatewayAccountCredentialsEntity().withPaymentProvider(ADYEN.getName()).build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withType(LIVE)
                    .withGatewayAccountCredentials(List.of(gatewayAccountCredentials))
                    .build();
            var ex = assertThrows(IllegalArgumentException.class, () -> adyenAccountSetupService.completeTestAccountSetup(gatewayAccountEntity));
            verify(mockAdyenAccountSetupDao, times(0)).persist(any(AdyenAccountSetupTaskEntity.class));
            assertEquals(EXPECTED_ERROR_MSG, ex.getMessage());
        }

        @Test
        void shouldThrowIllegalArgException_WhenAccountIsNotAdyen() {
            var gatewayAccountCredentials = aGatewayAccountCredentialsEntity().withPaymentProvider(WORLDPAY.getName()).build();
            GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                    .withType(TEST)
                    .withGatewayAccountCredentials(List.of(gatewayAccountCredentials))
                    .build();
            var ex = assertThrows(IllegalArgumentException.class, () -> adyenAccountSetupService.completeTestAccountSetup(gatewayAccountEntity));
            verify(mockAdyenAccountSetupDao, times(0)).persist(any(AdyenAccountSetupTaskEntity.class));
            assertEquals(EXPECTED_ERROR_MSG, ex.getMessage());
        }
    }
}
