package uk.gov.pay.connector.it.dao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.gatewayaccount.dao.StripeAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTaskEntity;

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.BANK_ACCOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.VAT_NUMBER_COMPANY_NUMBER;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.RESPONSIBLE_PERSON;

public class StripeAccountSetupDaoIT extends DaoITestBase {
    private StripeAccountSetupDao stripeAccountSetupDao;

    @Before
    public void setUp() {
        stripeAccountSetupDao = env.getInstance(StripeAccountSetupDao.class);
    }

    @After
    public void truncate() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void shouldFindTasksByGatewayAccountId() {
        long gatewayAccountId = 42;
        databaseTestHelper.addGatewayAccount(gatewayAccountId, "stripe");

        long anotherGatewayAccountId = 1;
        databaseTestHelper.addGatewayAccount(anotherGatewayAccountId, "stripe");

        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, BANK_ACCOUNT);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(anotherGatewayAccountId, BANK_ACCOUNT);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(anotherGatewayAccountId, VAT_NUMBER_COMPANY_NUMBER);

        List<StripeAccountSetupTaskEntity> tasks = stripeAccountSetupDao.findByGatewayAccountId(gatewayAccountId);
        assertThat(tasks, hasSize(2));
        assertThat(tasks, contains(
                allOf(
                        hasProperty("gatewayAccount", hasProperty("id", is(gatewayAccountId))),
                        hasProperty("task", is(BANK_ACCOUNT))),
                allOf(
                        hasProperty("gatewayAccount", hasProperty("id", is(gatewayAccountId))),
                        hasProperty("task", is(RESPONSIBLE_PERSON)))
        ));
    }

    @Test
    public void shouldReturnTrueIfGatewayAccountHasCompletedTask() {
        long gatewayAccountId = 42;
        databaseTestHelper.addGatewayAccount(gatewayAccountId, "stripe");

        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, BANK_ACCOUNT);

        boolean result = stripeAccountSetupDao.isTaskCompletedForGatewayAccount(gatewayAccountId, BANK_ACCOUNT);
        
        assertThat(result, is(true));
    }

    @Test
    public void shouldReturnFalseIfGatewayAccountHasCompletedTask() {
        long gatewayAccountId = 42;
        databaseTestHelper.addGatewayAccount(gatewayAccountId, "stripe");

        long anotherGatewayAccountId = 1;
        databaseTestHelper.addGatewayAccount(anotherGatewayAccountId, "stripe");

        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(anotherGatewayAccountId, BANK_ACCOUNT);

        boolean result = stripeAccountSetupDao.isTaskCompletedForGatewayAccount(gatewayAccountId, BANK_ACCOUNT);

        assertThat(result, is(false));
    }

    @Test
    public void shouldReturnTrueIfGatewayAccountHasCompletedTaskRecordedMoreThanOnce() {
        long gatewayAccountId = 42;
        databaseTestHelper.addGatewayAccount(gatewayAccountId, "stripe");
        
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);

        boolean result = stripeAccountSetupDao.isTaskCompletedForGatewayAccount(gatewayAccountId, RESPONSIBLE_PERSON);

        assertThat(result, is(true));
    }

    @Test
    public void shouldRemoveCompletedTaskForGatewayAccount() {
        long gatewayAccountId = 42;
        databaseTestHelper.addGatewayAccount(gatewayAccountId, "stripe");

        long anotherGatewayAccountId = 1;
        databaseTestHelper.addGatewayAccount(anotherGatewayAccountId, "stripe");

        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, BANK_ACCOUNT);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(anotherGatewayAccountId, RESPONSIBLE_PERSON);
        
        stripeAccountSetupDao.removeCompletedTaskForGatewayAccount(gatewayAccountId, RESPONSIBLE_PERSON);

        List<StripeAccountSetupTaskEntity> gatewayAccountTasks = stripeAccountSetupDao.findByGatewayAccountId(gatewayAccountId);

        assertThat(gatewayAccountTasks, hasSize(1));
        assertThat(gatewayAccountTasks, contains(hasProperty("task", is(BANK_ACCOUNT))));

        List<StripeAccountSetupTaskEntity> otherGatewayAccountTasks = stripeAccountSetupDao.findByGatewayAccountId(anotherGatewayAccountId);
        assertThat(otherGatewayAccountTasks, contains(hasProperty("task", is(RESPONSIBLE_PERSON))));
    }

    @Test
    public void shouldRemoveCompletedTaskForGatewayAccountWhenHasCompletedTaskMoreThanOnce() {
        long gatewayAccountId = 42;
        databaseTestHelper.addGatewayAccount(gatewayAccountId, "stripe");

        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, BANK_ACCOUNT);

        stripeAccountSetupDao.removeCompletedTaskForGatewayAccount(gatewayAccountId, RESPONSIBLE_PERSON);

        List<StripeAccountSetupTaskEntity> gatewayAccountTasks = stripeAccountSetupDao.findByGatewayAccountId(gatewayAccountId);

        assertThat(gatewayAccountTasks, hasSize(1));
        assertThat(gatewayAccountTasks, contains(hasProperty("task", is(BANK_ACCOUNT))));
    }
}
