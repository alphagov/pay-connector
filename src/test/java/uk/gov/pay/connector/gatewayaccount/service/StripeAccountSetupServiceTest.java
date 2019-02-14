package uk.gov.pay.connector.gatewayaccount.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gatewayaccount.dao.StripeAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTaskEntity;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class StripeAccountSetupServiceTest {

    private static final long GATEWAY_ACCOUNT_ID = 42;
    
    @Mock private StripeAccountSetupDao mockStripeAccountSetupDao;
    
    @Mock private StripeAccountSetupTaskEntity mockBankDetailsCompletedTaskEntity;
    @Mock private StripeAccountSetupTaskEntity mockResponsiblePersonCompletedTaskEntity;
    @Mock private StripeAccountSetupTaskEntity mockOrganisationDetailsCompletedTaskEntity;

    private StripeAccountSetupService stripeAccountSetupService;

    @Before
    public void setUp() {
        given(mockBankDetailsCompletedTaskEntity.getTask()).willReturn(StripeAccountSetupTask.BANK_ACCOUNT);
        given(mockResponsiblePersonCompletedTaskEntity.getTask()).willReturn(StripeAccountSetupTask.RESPONSIBLE_PERSON);
        given(mockOrganisationDetailsCompletedTaskEntity.getTask()).willReturn(StripeAccountSetupTask.ORGANISATION_DETAILS);

        this.stripeAccountSetupService = new StripeAccountSetupService(mockStripeAccountSetupDao);
    }

    @Test
    public void shouldConfigureStripeAccountSetupWithNoTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Collections.emptyList());

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(false));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(false));
    }

    @Test
    public void shouldConfigureStripeAccountSetupWithAllTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockOrganisationDetailsCompletedTaskEntity, mockResponsiblePersonCompletedTaskEntity,
                        mockBankDetailsCompletedTaskEntity));

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(true));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(true));
    }

    @Test
    public void shouldConfigureStripeAccountSetupWithSomeTasksCompleted() {
        given(mockStripeAccountSetupDao.findByGatewayAccountId(GATEWAY_ACCOUNT_ID))
                .willReturn(Arrays.asList(mockOrganisationDetailsCompletedTaskEntity, mockResponsiblePersonCompletedTaskEntity));

        StripeAccountSetup tasks = stripeAccountSetupService.getCompletedTasks(GATEWAY_ACCOUNT_ID);

        assertThat(tasks.isBankAccountCompleted(), is(false));
        assertThat(tasks.isResponsiblePersonCompleted(), is(true));
        assertThat(tasks.isOrganisationDetailsCompleted(), is(true));
    }
    
}
