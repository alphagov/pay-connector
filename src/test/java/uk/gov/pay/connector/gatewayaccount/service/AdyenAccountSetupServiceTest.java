package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccount.dao.AdyenAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;

@ExtendWith(MockitoExtension.class)
class AdyenAccountSetupServiceTest {

    @Mock
    private AdyenAccountSetupDao mockAdyenAccountSetupDao;

    @Spy
    private GatewayAccountEntity spyGatewayAccountEntity;

    private AdyenAccountSetupService adyenAccountSetupService;

    private static final String EXPECTED_ERROR_MSG = "Gateway account type must be TEST and gateway name must be ADYEN";

    @BeforeEach
    public void setUp() {
        this.adyenAccountSetupService = new AdyenAccountSetupService(mockAdyenAccountSetupDao);
    }

    @Nested
    class completeTestAccountSetup {
        @Test
        void shouldCompleteAllTasks_WhenAccountIsAdyenAndTest() {
            doReturn(TEST.toString()).when(spyGatewayAccountEntity).getType();
            doReturn(ADYEN.getName()).when(spyGatewayAccountEntity).getGatewayName();

            adyenAccountSetupService.completeTestAccountSetup(spyGatewayAccountEntity);

            ArgumentCaptor<AdyenAccountSetupTaskEntity> captor =
                    ArgumentCaptor.forClass(AdyenAccountSetupTaskEntity.class);

            verify(mockAdyenAccountSetupDao, times(AdyenAccountSetupTask.values().length)).persist(captor.capture());

            assertThat(captor.getAllValues())
                    .extracting(AdyenAccountSetupTaskEntity::getStatus)
                    .containsOnly(AdyenAccountSetupStatus.COMPLETED);
        }

        @Test
        void shouldThrowIllegalArgException_WhenAccountIsNotTest() {
            doReturn(LIVE.toString()).when(spyGatewayAccountEntity).getType();
            var ex = assertThrows(IllegalArgumentException.class, () -> adyenAccountSetupService.completeTestAccountSetup(spyGatewayAccountEntity));
            verify(mockAdyenAccountSetupDao, times(0)).persist(any(AdyenAccountSetupTaskEntity.class));
            assertEquals(EXPECTED_ERROR_MSG, ex.getMessage());
        }

        @Test
        void shouldThrowIllegalArgException_WhenAccountIsNotAdyen() {
            doReturn(TEST.toString()).when(spyGatewayAccountEntity).getType();
            doReturn(SANDBOX.getName()).when(spyGatewayAccountEntity).getGatewayName();
            var ex = assertThrows(IllegalArgumentException.class, () -> adyenAccountSetupService.completeTestAccountSetup(spyGatewayAccountEntity));
            verify(mockAdyenAccountSetupDao, times(0)).persist(any(AdyenAccountSetupTaskEntity.class));
            assertEquals(EXPECTED_ERROR_MSG, ex.getMessage());
        }
    }
}
