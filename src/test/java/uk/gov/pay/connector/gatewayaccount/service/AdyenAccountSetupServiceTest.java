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
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.COMPLETED;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus.NOT_STARTED;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.BANK_ACCOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.COMPANY_NUMBER;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.DIRECTOR;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.GOVERNMENT_ENTITY_DOCUMENT;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.ORGANISATION_DETAILS;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.RESPONSIBLE_PERSON;
import static uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask.VAT_NUMBER;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class AdyenAccountSetupServiceTest {

    public static final long GATEWAY_ACCOUNT_ID = 999L;
    private static final String SERVICE_ID = "service-123";
    private static final String CREDENTIAL_EXTERNAL_ID = "credential-123";
    private static final String STATUS_KEY = "status";
    
    @Mock
    private AdyenAccountSetupDao mockAdyenAccountSetupDao;

    @Mock
    private AdyenAccountSetupTaskEntity mockBankDetailsTaskEntity;
    @Mock
    private AdyenAccountSetupTaskEntity mockResponsiblePersonTaskEntity;
    @Mock
    private AdyenAccountSetupTaskEntity mockVatNumberTaskEntity;
    @Mock
    private AdyenAccountSetupTaskEntity mockCompanyNumberTaskEntity;
    @Mock
    private AdyenAccountSetupTaskEntity mockDirectorTaskEntity;
    @Mock
    private AdyenAccountSetupTaskEntity mockGovernmentEntityDocumentTaskEntity;
    @Mock
    private AdyenAccountSetupTaskEntity mockOrganisationDetailsTaskEntity;
    
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
            assertThat(tasksWithStatus.getTasks().get(task.getValue()).get(STATUS_KEY), is(NOT_STARTED)));
    }

    @Test
    void shouldReturnAdyenAccountSetupWithAllTasksCompleted() {
        given(mockAdyenAccountSetupDao.findByGatewayAccountIdAndCredentialId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockResponsiblePersonTaskEntity,
                        mockBankDetailsTaskEntity, mockVatNumberTaskEntity,
                        mockCompanyNumberTaskEntity, mockDirectorTaskEntity,
                        mockGovernmentEntityDocumentTaskEntity, mockOrganisationDetailsTaskEntity));
        
        given(mockBankDetailsTaskEntity.getTask()).willReturn(BANK_ACCOUNT);
        given(mockBankDetailsTaskEntity.getStatus()).willReturn(COMPLETED);

        given(mockResponsiblePersonTaskEntity.getTask()).willReturn(RESPONSIBLE_PERSON);
        given(mockResponsiblePersonTaskEntity.getStatus()).willReturn(COMPLETED);
        
        given(mockVatNumberTaskEntity.getTask()).willReturn(VAT_NUMBER);
        given(mockVatNumberTaskEntity.getStatus()).willReturn(COMPLETED);
        
        given(mockCompanyNumberTaskEntity.getTask()).willReturn(COMPANY_NUMBER);
        given(mockCompanyNumberTaskEntity.getStatus()).willReturn(COMPLETED);
        
        given(mockDirectorTaskEntity.getTask()).willReturn(DIRECTOR);
        given(mockDirectorTaskEntity.getStatus()).willReturn(COMPLETED);
        
        given(mockGovernmentEntityDocumentTaskEntity.getTask()).willReturn(GOVERNMENT_ENTITY_DOCUMENT);
        given(mockGovernmentEntityDocumentTaskEntity.getStatus()).willReturn(COMPLETED);
        
        given(mockOrganisationDetailsTaskEntity.getTask()).willReturn(ORGANISATION_DETAILS);
        given(mockOrganisationDetailsTaskEntity.getStatus()).willReturn(COMPLETED);
        
        AdyenAccountSetupResponse tasksWithStatus = adyenAccountSetupService.buildResponse(SERVICE_ID, GATEWAY_ACCOUNT_ID, CREDENTIAL_EXTERNAL_ID);

        assertThat(tasksWithStatus.getServiceId(), is(SERVICE_ID));
        assertThat(tasksWithStatus.getCredentialExternalId(), is(CREDENTIAL_EXTERNAL_ID));
        assertThat(tasksWithStatus.getGatewayAccountId(), is(GATEWAY_ACCOUNT_ID));

        Arrays.stream(AdyenAccountSetupTask.values()).forEach(task ->
                assertThat(tasksWithStatus.getTasks().get(task.getValue()).get(STATUS_KEY), is(COMPLETED)));
    }

    @Test
    void shouldReturnAdyenAccountSetupWithSomeTasksCompleted() {
        given(mockAdyenAccountSetupDao.findByGatewayAccountIdAndCredentialId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(
                        mockCompanyNumberTaskEntity, mockDirectorTaskEntity,
                        mockGovernmentEntityDocumentTaskEntity, mockOrganisationDetailsTaskEntity));

        given(mockCompanyNumberTaskEntity.getTask()).willReturn(COMPANY_NUMBER);
        given(mockCompanyNumberTaskEntity.getStatus()).willReturn(COMPLETED);

        given(mockDirectorTaskEntity.getTask()).willReturn(DIRECTOR);
        given(mockDirectorTaskEntity.getStatus()).willReturn(COMPLETED);

        given(mockGovernmentEntityDocumentTaskEntity.getTask()).willReturn(GOVERNMENT_ENTITY_DOCUMENT);
        given(mockGovernmentEntityDocumentTaskEntity.getStatus()).willReturn(COMPLETED);

        given(mockOrganisationDetailsTaskEntity.getTask()).willReturn(ORGANISATION_DETAILS);
        given(mockOrganisationDetailsTaskEntity.getStatus()).willReturn(COMPLETED);

        AdyenAccountSetupResponse tasksWithStatus = adyenAccountSetupService.buildResponse(SERVICE_ID, GATEWAY_ACCOUNT_ID, CREDENTIAL_EXTERNAL_ID);

        assertThat(tasksWithStatus.getServiceId(), is(SERVICE_ID));
        assertThat(tasksWithStatus.getCredentialExternalId(), is(CREDENTIAL_EXTERNAL_ID));
        assertThat(tasksWithStatus.getGatewayAccountId(), is(GATEWAY_ACCOUNT_ID));

        assertThat(tasksWithStatus.getTasks().get(BANK_ACCOUNT.getValue()).get(STATUS_KEY), is(NOT_STARTED));
        assertThat(tasksWithStatus.getTasks().get(RESPONSIBLE_PERSON.getValue()).get(STATUS_KEY), is(NOT_STARTED));
        assertThat(tasksWithStatus.getTasks().get(VAT_NUMBER.getValue()).get(STATUS_KEY), is(NOT_STARTED));
        assertThat(tasksWithStatus.getTasks().get(COMPANY_NUMBER.getValue()).get(STATUS_KEY), is(COMPLETED));
        assertThat(tasksWithStatus.getTasks().get(DIRECTOR.getValue()).get(STATUS_KEY), is(COMPLETED));
        assertThat(tasksWithStatus.getTasks().get(GOVERNMENT_ENTITY_DOCUMENT.getValue()).get(STATUS_KEY), is(COMPLETED));
        assertThat(tasksWithStatus.getTasks().get(ORGANISATION_DETAILS.getValue()).get(STATUS_KEY), is(COMPLETED));
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
                    .toList();

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
