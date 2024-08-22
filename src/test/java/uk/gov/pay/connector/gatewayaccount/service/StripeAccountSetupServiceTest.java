package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gatewayaccount.dao.StripeAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupUpdateRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.BANK_ACCOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.COMPANY_NUMBER;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.DIRECTOR;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.GOVERNMENT_ENTITY_DOCUMENT;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.ORGANISATION_DETAILS;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.RESPONSIBLE_PERSON;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.VAT_NUMBER;

@ExtendWith(MockitoExtension.class)
class StripeAccountSetupServiceTest {

    private static final long GATEWAY_ACCOUNT_ID = 42;

    @Mock
    private StripeAccountSetupDao mockStripeAccountSetupDao;

    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;

    @Mock
    private StripeAccountSetupTaskEntity mockBankDetailsCompletedTaskEntity;
    @Mock
    private StripeAccountSetupTaskEntity mockResponsiblePersonCompletedTaskEntity;
    @Mock
    private StripeAccountSetupTaskEntity mockVatNumberCompletedTaskEntity;
    @Mock
    private StripeAccountSetupTaskEntity mockCompanyNumberCompletedTaskEntity;
    @Mock
    private StripeAccountSetupTaskEntity mockDirectorCompletedTaskEntity;
    @Mock
    private StripeAccountSetupTaskEntity mockGovernmentEntityDocumentTaskEntity;
    @Mock
    private StripeAccountSetupTaskEntity mockOrganisationDetailsCompletedTaskEntity;

    private StripeAccountSetupService stripeAccountSetupService;
    
    private static final String EXPECTED_ERROR_MSG = "Gateway account type must be TEST and gateway name must be STRIPE";

    @BeforeEach
    public void setUp() {
        

        this.stripeAccountSetupService = new StripeAccountSetupService(mockStripeAccountSetupDao);
    }

    @Test
    void shouldReturnStripeAccountSetupWithNoTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Collections.emptyList());

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(false));
        assertThat(tasks.isVatNumberCompleted(), is(false));
        assertThat(tasks.isCompanyNumberCompleted(), is(false));
        assertThat(tasks.isDirectorCompleted(), is(false));
        assertThat(tasks.isGovernmentEntityDocument(), is(false));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(false));
    }

    @Test
    void shouldReturnStripeAccountSetupWithAllTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockResponsiblePersonCompletedTaskEntity,
                        mockBankDetailsCompletedTaskEntity, mockVatNumberCompletedTaskEntity,
                        mockCompanyNumberCompletedTaskEntity, mockDirectorCompletedTaskEntity,
                        mockGovernmentEntityDocumentTaskEntity, mockOrganisationDetailsCompletedTaskEntity));

        given(mockBankDetailsCompletedTaskEntity.getTask()).willReturn(BANK_ACCOUNT);
        given(mockResponsiblePersonCompletedTaskEntity.getTask()).willReturn(RESPONSIBLE_PERSON);
        given(mockVatNumberCompletedTaskEntity.getTask()).willReturn(VAT_NUMBER);
        given(mockCompanyNumberCompletedTaskEntity.getTask()).willReturn(COMPANY_NUMBER);
        given(mockDirectorCompletedTaskEntity.getTask()).willReturn(DIRECTOR);
        given(mockGovernmentEntityDocumentTaskEntity.getTask()).willReturn(GOVERNMENT_ENTITY_DOCUMENT);
        given(mockOrganisationDetailsCompletedTaskEntity.getTask()).willReturn(ORGANISATION_DETAILS);

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(true));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isVatNumberCompleted(), is(true));
        assertThat(tasks.isCompanyNumberCompleted(), is(true));
        assertThat(tasks.isDirectorCompleted(), is(true));
        assertThat(tasks.isGovernmentEntityDocument(), is(true));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(true));
    }

    @Test
    void shouldReturnStripeAccountSetupWithSomeTasksCompleted() {
        
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockResponsiblePersonCompletedTaskEntity, mockVatNumberCompletedTaskEntity));

        given(mockResponsiblePersonCompletedTaskEntity.getTask()).willReturn(RESPONSIBLE_PERSON);
        given(mockVatNumberCompletedTaskEntity.getTask()).willReturn(VAT_NUMBER);

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isVatNumberCompleted(), is(true));
        assertThat(tasks.isDirectorCompleted(), is(false));
        assertThat(tasks.isGovernmentEntityDocument(), is(false));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(false));
    }

    @Test
    void shouldRecordBankAccountCompleted() {
        given(mockGatewayAccountEntity.getId()).willReturn(GATEWAY_ACCOUNT_ID);
        StripeAccountSetupUpdateRequest patchRequest = new StripeAccountSetupUpdateRequest(BANK_ACCOUNT, true);

        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, BANK_ACCOUNT)).willReturn(false);

        stripeAccountSetupService.update(mockGatewayAccountEntity, Collections.singletonList(patchRequest));

        ArgumentCaptor<StripeAccountSetupTaskEntity> entityArgumentCaptor = ArgumentCaptor.forClass(StripeAccountSetupTaskEntity.class);
        verify(mockStripeAccountSetupDao).persist(entityArgumentCaptor.capture());

        StripeAccountSetupTaskEntity entity = entityArgumentCaptor.getValue();
        assertThat(entity.getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entity.getTask(), is(BANK_ACCOUNT));
    }

    @Test
    void shouldDoNothingWhenAskedToRecordBankAccountCompletedIfAlreadyRecorded() {
        given(mockGatewayAccountEntity.getId()).willReturn(GATEWAY_ACCOUNT_ID);
        StripeAccountSetupUpdateRequest patchRequest = new StripeAccountSetupUpdateRequest(BANK_ACCOUNT, true);

        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, BANK_ACCOUNT)).willReturn(true);

        stripeAccountSetupService.update(mockGatewayAccountEntity, Collections.singletonList(patchRequest));

        verify(mockStripeAccountSetupDao, never()).persist(any(StripeAccountSetupTaskEntity.class));
    }

    @Test
    void shouldRecordBankAccountNotCompleted() {
        given(mockGatewayAccountEntity.getId()).willReturn(GATEWAY_ACCOUNT_ID);
        StripeAccountSetupUpdateRequest patchRequest = new StripeAccountSetupUpdateRequest(BANK_ACCOUNT, false);

        stripeAccountSetupService.update(mockGatewayAccountEntity, Collections.singletonList(patchRequest));

        verify(mockStripeAccountSetupDao).removeCompletedTaskForGatewayAccount(GATEWAY_ACCOUNT_ID, BANK_ACCOUNT);
    }
    
    @Test
    void completeTestAccountSetupShouldCompleteAllTasksForStripeTestAccounts() {
        given(mockGatewayAccountEntity.getType()).willReturn(TEST.toString());
        given(mockGatewayAccountEntity.getGatewayName()).willReturn(STRIPE.getName());

        stripeAccountSetupService.completeTestAccountSetup(mockGatewayAccountEntity);

        verify(mockStripeAccountSetupDao, times(7)).persist(any(StripeAccountSetupTaskEntity.class));
    }

    @Test
    void completeTestAccountSetupShouldThrowIllegalArgExceptionIfAccountEntityIsNotTest() {
        given(mockGatewayAccountEntity.getType()).willReturn(LIVE.toString());
        var ex = assertThrows(IllegalArgumentException.class, () -> stripeAccountSetupService.completeTestAccountSetup(mockGatewayAccountEntity));
        verify(mockStripeAccountSetupDao, times(0)).persist(any(StripeAccountSetupTaskEntity.class));
        assertEquals(EXPECTED_ERROR_MSG, ex.getMessage());
    }

    @Test
    void completeTestAccountSetupShouldThrowIllegalArgExceptionIfAccountEntityIsNotStripe() {
        given(mockGatewayAccountEntity.getType()).willReturn(TEST.toString());
        given(mockGatewayAccountEntity.getGatewayName()).willReturn(SANDBOX.getName());
        var ex = assertThrows(IllegalArgumentException.class, () -> stripeAccountSetupService.completeTestAccountSetup(mockGatewayAccountEntity));
        verify(mockStripeAccountSetupDao, times(0)).persist(any(StripeAccountSetupTaskEntity.class));
        assertEquals(EXPECTED_ERROR_MSG, ex.getMessage());
    }

    @Test
    void shouldRecordMultipleTasksCompleted() {
        given(mockGatewayAccountEntity.getId()).willReturn(GATEWAY_ACCOUNT_ID);
        List<StripeAccountSetupUpdateRequest> patchRequests = Arrays.asList(
                new StripeAccountSetupUpdateRequest(BANK_ACCOUNT, true),
                new StripeAccountSetupUpdateRequest(RESPONSIBLE_PERSON, true),
                new StripeAccountSetupUpdateRequest(VAT_NUMBER, true),
                new StripeAccountSetupUpdateRequest(DIRECTOR, true),
                new StripeAccountSetupUpdateRequest(GOVERNMENT_ENTITY_DOCUMENT, true),
                new StripeAccountSetupUpdateRequest(ORGANISATION_DETAILS, true));

        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, BANK_ACCOUNT)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, RESPONSIBLE_PERSON)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, VAT_NUMBER)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, DIRECTOR)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, GOVERNMENT_ENTITY_DOCUMENT)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, ORGANISATION_DETAILS)).willReturn(false);

        stripeAccountSetupService.update(mockGatewayAccountEntity, patchRequests);

        ArgumentCaptor<StripeAccountSetupTaskEntity> entityArgumentCaptor = ArgumentCaptor.forClass(StripeAccountSetupTaskEntity.class);
        verify(mockStripeAccountSetupDao, times(6)).persist(entityArgumentCaptor.capture());

        List<StripeAccountSetupTaskEntity> entities = entityArgumentCaptor.getAllValues();

        assertThat(entities.size(), is(6));

        assertThat(entities.get(0).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(0).getTask(), is(BANK_ACCOUNT));

        assertThat(entities.get(1).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(1).getTask(), is(RESPONSIBLE_PERSON));

        assertThat(entities.get(2).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(2).getTask(), is(VAT_NUMBER));

        assertThat(entities.get(3).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(3).getTask(), is(DIRECTOR));

        assertThat(entities.get(4).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(4).getTask(), is(GOVERNMENT_ENTITY_DOCUMENT));

        assertThat(entities.get(5).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(5).getTask(), is(ORGANISATION_DETAILS));
    }
}
