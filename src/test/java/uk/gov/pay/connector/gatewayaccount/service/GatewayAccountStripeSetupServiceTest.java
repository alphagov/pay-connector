package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountStripeSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountStripeSetup;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountStripeSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountStripeSetupTaskEntity;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class GatewayAccountStripeSetupServiceTest {

    private static final long GATEWAY_ACCOUNT_ID = 42;
    
    @Mock private GatewayAccountStripeSetupDao mockGatewayAccountStripeSetupDao;
    
    @Mock private GatewayAccountStripeSetupTaskEntity mockBankDetailsCompletedTaskEntity;
    @Mock private GatewayAccountStripeSetupTaskEntity mockResponsiblePersonCompletedTaskEntity;
    @Mock private GatewayAccountStripeSetupTaskEntity mockOrganisationDetailsCompletedTaskEntity;

    private GatewayAccountStripeSetupService gatewayAccountStripeSetupService;

    @Before
    public void setUp() {
        given(mockBankDetailsCompletedTaskEntity.getTask()).willReturn(GatewayAccountStripeSetupTask.BANK_ACCOUNT);
        given(mockResponsiblePersonCompletedTaskEntity.getTask()).willReturn(GatewayAccountStripeSetupTask.RESPONSIBLE_PERSON);
        given(mockOrganisationDetailsCompletedTaskEntity.getTask()).willReturn(GatewayAccountStripeSetupTask.ORGANISATION_DETAILS);

        this.gatewayAccountStripeSetupService = new GatewayAccountStripeSetupService(mockGatewayAccountStripeSetupDao);
    }

    @Test
    public void shouldConfigureGatewayAccountStripeSetupWithNoTasksCompleted() {
        given(mockGatewayAccountStripeSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Collections.emptyList());

        GatewayAccountStripeSetup tasks = gatewayAccountStripeSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(false));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(false));
    }

    @Test
    public void shouldConfigureGatewayAccountStripeSetupWithAllTasksCompleted() {
        given(mockGatewayAccountStripeSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockOrganisationDetailsCompletedTaskEntity, mockResponsiblePersonCompletedTaskEntity,
                        mockBankDetailsCompletedTaskEntity));

        GatewayAccountStripeSetup tasks = gatewayAccountStripeSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(true));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(true));
    }

    @Test
    public void shouldConfigureGatewayAccountStripeSetupWithSomeTasksCompleted() {
        given(mockGatewayAccountStripeSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockOrganisationDetailsCompletedTaskEntity, mockResponsiblePersonCompletedTaskEntity));

        GatewayAccountStripeSetup tasks = gatewayAccountStripeSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(true));
    }
    
}
