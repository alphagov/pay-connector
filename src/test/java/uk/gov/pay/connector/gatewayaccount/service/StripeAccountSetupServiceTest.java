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
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupUpdateRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.BANK_ACCOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.RESPONSIBLE_PERSON;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.VAT_NUMBER_COMPANY_NUMBER;

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
    private StripeAccountSetupTaskEntity mockOrganisationDetailsCompletedTaskEntity;

    private StripeAccountSetupService stripeAccountSetupService;

    @Before
    public void setUp() {
        given(mockGatewayAccountEntity.getId()).willReturn(GATEWAY_ACCOUNT_ID);

        given(mockBankDetailsCompletedTaskEntity.getTask()).willReturn(BANK_ACCOUNT);
        given(mockResponsiblePersonCompletedTaskEntity.getTask()).willReturn(StripeAccountSetupTask.RESPONSIBLE_PERSON);
        given(mockOrganisationDetailsCompletedTaskEntity.getTask()).willReturn(StripeAccountSetupTask.VAT_NUMBER_COMPANY_NUMBER);

        this.stripeAccountSetupService = new StripeAccountSetupService(mockStripeAccountSetupDao);
    }

    @Test
    public void shouldReturnStripeAccountSetupWithNoTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Collections.emptyList());

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(false));
        assertThat(tasks.isVatNumberCompanyNumberCompleted(), is(false));
    }

    @Test
    public void shouldReturnStripeAccountSetupWithAllTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockOrganisationDetailsCompletedTaskEntity, mockResponsiblePersonCompletedTaskEntity,
                        mockBankDetailsCompletedTaskEntity));

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(true));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isVatNumberCompanyNumberCompleted(), is(true));
    }

    @Test
    public void shouldReturnStripeAccountSetupWithSomeTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockOrganisationDetailsCompletedTaskEntity, mockResponsiblePersonCompletedTaskEntity));

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isVatNumberCompanyNumberCompleted(), is(true));
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
                new StripeAccountSetupUpdateRequest(VAT_NUMBER_COMPANY_NUMBER, true));

        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, BANK_ACCOUNT)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, RESPONSIBLE_PERSON)).willReturn(false);
        given(mockStripeAccountSetupDao.isTaskCompletedForGatewayAccount(GATEWAY_ACCOUNT_ID, VAT_NUMBER_COMPANY_NUMBER)).willReturn(false);

        stripeAccountSetupService.update(mockGatewayAccountEntity, patchRequests);

        ArgumentCaptor<StripeAccountSetupTaskEntity> entityArgumentCaptor = ArgumentCaptor.forClass(StripeAccountSetupTaskEntity.class);
        verify(mockStripeAccountSetupDao, times(3)).persist(entityArgumentCaptor.capture());

        List<StripeAccountSetupTaskEntity> entities = entityArgumentCaptor.getAllValues();

        assertThat(entities.size(), is(3));

        assertThat(entities.get(0).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(0).getTask(), is(BANK_ACCOUNT));

        assertThat(entities.get(1).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(1).getTask(), is(RESPONSIBLE_PERSON));

        assertThat(entities.get(2).getGatewayAccount(), is(mockGatewayAccountEntity));
        assertThat(entities.get(2).getTask(), is(VAT_NUMBER_COMPANY_NUMBER));
    }
}
