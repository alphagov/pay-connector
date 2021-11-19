package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.ADDITIONAL_KYC_DATA;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.BANK_ACCOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.COMPANY_NUMBER;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.DIRECTOR;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.RESPONSIBLE_PERSON;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.VAT_NUMBER;

@RunWith(MockitoJUnitRunner.class)
public class StripeAccountSetupServiceTest {

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
    private StripeAccountSetupTaskEntity mockAdditionalKycDataCompletedTaskEntity;

    private StripeAccountSetupService stripeAccountSetupService;

    @Before
    public void setUp() {
        given(mockGatewayAccountEntity.getId()).willReturn(GATEWAY_ACCOUNT_ID);

        given(mockBankDetailsCompletedTaskEntity.getTask()).willReturn(BANK_ACCOUNT);
        given(mockResponsiblePersonCompletedTaskEntity.getTask()).willReturn(RESPONSIBLE_PERSON);
        given(mockVatNumberCompletedTaskEntity.getTask()).willReturn(VAT_NUMBER);
        given(mockCompanyNumberCompletedTaskEntity.getTask()).willReturn(COMPANY_NUMBER);
        given(mockDirectorCompletedTaskEntity.getTask()).willReturn(DIRECTOR);
        given(mockAdditionalKycDataCompletedTaskEntity.getTask()).willReturn(ADDITIONAL_KYC_DATA);

        this.stripeAccountSetupService = new StripeAccountSetupService(mockStripeAccountSetupDao);
    }

    @Test
    public void shouldReturnStripeAccountSetupWithNoTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Collections.emptyList());

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(false));
        assertThat(tasks.isVatNumberCompleted(), is(false));
        assertThat(tasks.isCompanyNumberCompleted(), is(false));
        assertThat(tasks.isDirectorCompleted(), is(false));
        assertThat(tasks.isAdditionalKycDataCompleted(), is(false));
    }

    @Test
    public void shouldReturnStripeAccountSetupWithAllTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockResponsiblePersonCompletedTaskEntity,
                        mockBankDetailsCompletedTaskEntity, mockVatNumberCompletedTaskEntity,
                        mockCompanyNumberCompletedTaskEntity, mockDirectorCompletedTaskEntity,
                        mockAdditionalKycDataCompletedTaskEntity));

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(true));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isVatNumberCompleted(), is(true));
        assertThat(tasks.isCompanyNumberCompleted(), is(true));
        assertThat(tasks.isDirectorCompleted(), is(true));
        assertThat(tasks.isAdditionalKycDataCompleted(), is(true));
    }

    @Test
    public void shouldReturnStripeAccountSetupWithSomeTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockResponsiblePersonCompletedTaskEntity, mockVatNumberCompletedTaskEntity));

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isVatNumberCompleted(), is(true));
        assertThat(tasks.isDirectorCompleted(), is(false));
        assertThat(tasks.isAdditionalKycDataCompleted(), is(false));
    }

    @Test
    public void shouldRecordBankAccountCompleted() {
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
    public void shouldDoNothingWhenAskedToRecordBankAccountCompletedIfAlreadyRecorded() {
        StripeAccountSetupUpdateRequest patchRequest = new StripeAccountSetupUpdateRequest(BANK_ACCOUNT, true);

        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, BANK_ACCOUNT)).willReturn(true);

        stripeAccountSetupService.update(mockGatewayAccountEntity, Collections.singletonList(patchRequest));

        verify(mockStripeAccountSetupDao, never()).persist(any(StripeAccountSetupTaskEntity.class));
    }

    @Test
    public void shouldRecordBankAccountNotCompleted() {
        StripeAccountSetupUpdateRequest patchRequest = new StripeAccountSetupUpdateRequest(BANK_ACCOUNT, false);

        stripeAccountSetupService.update(mockGatewayAccountEntity, Collections.singletonList(patchRequest));

        verify(mockStripeAccountSetupDao).removeCompletedTaskForGatewayAccount(GATEWAY_ACCOUNT_ID, BANK_ACCOUNT);
    }

    @Test
    public void shouldRecordMultipleTasksCompleted() {
        List<StripeAccountSetupUpdateRequest> patchRequests = Arrays.asList(
                new StripeAccountSetupUpdateRequest(BANK_ACCOUNT, true),
                new StripeAccountSetupUpdateRequest(RESPONSIBLE_PERSON, true),
                new StripeAccountSetupUpdateRequest(VAT_NUMBER, true),
                new StripeAccountSetupUpdateRequest(DIRECTOR, true),
                new StripeAccountSetupUpdateRequest(ADDITIONAL_KYC_DATA, true));

        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, BANK_ACCOUNT)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, RESPONSIBLE_PERSON)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, VAT_NUMBER)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, DIRECTOR)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, ADDITIONAL_KYC_DATA)).willReturn(false);

        stripeAccountSetupService.update(mockGatewayAccountEntity, patchRequests);

        ArgumentCaptor<StripeAccountSetupTaskEntity> entityArgumentCaptor = ArgumentCaptor.forClass(StripeAccountSetupTaskEntity.class);
        verify(mockStripeAccountSetupDao, times(5)).persist(entityArgumentCaptor.capture());

        List<StripeAccountSetupTaskEntity> entities = entityArgumentCaptor.getAllValues();

        assertThat(entities.size(), is(5));

        assertThat(entities.get(0).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(0).getTask(), is(BANK_ACCOUNT));

        assertThat(entities.get(1).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(1).getTask(), is(RESPONSIBLE_PERSON));

        assertThat(entities.get(2).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(2).getTask(), is(VAT_NUMBER));

        assertThat(entities.get(3).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(3).getTask(), is(DIRECTOR));

        assertThat(entities.get(4).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(4).getTask(), is(ADDITIONAL_KYC_DATA));
    }
}
