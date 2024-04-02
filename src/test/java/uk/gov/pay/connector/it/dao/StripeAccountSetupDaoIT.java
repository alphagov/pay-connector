package uk.gov.pay.connector.it.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.gatewayaccount.dao.StripeAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTaskEntity;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.BANK_ACCOUNT;
import static uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask.RESPONSIBLE_PERSON;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

public class StripeAccountSetupDaoIT {
    @RegisterExtension
    static ChargingITestBaseExtension app = new ChargingITestBaseExtension("sandbox");
    private StripeAccountSetupDao stripeAccountSetupDao;

    @BeforeEach
    void setUp() {
        stripeAccountSetupDao = app.getInstanceFromGuiceContainer(StripeAccountSetupDao.class);
    }

    @Test
    void shouldFindTasksByGatewayAccountId() {
        long gatewayAccountId = 42;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());

        long anotherGatewayAccountId = 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(anotherGatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());

        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, BANK_ACCOUNT);
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(anotherGatewayAccountId, BANK_ACCOUNT);

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
    void shouldReturnTrueIfGatewayAccountHasCompletedTask() {
        long gatewayAccountId = 42;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());

        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, BANK_ACCOUNT);

        boolean result = stripeAccountSetupDao.isTaskCompletedForGatewayAccount(gatewayAccountId, BANK_ACCOUNT);
        
        assertThat(result, is(true));
    }

    @Test
    void shouldReturnFalseIfGatewayAccountHasCompletedTask() {
        long gatewayAccountId = 42;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());

        long anotherGatewayAccountId = 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(anotherGatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());

        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(anotherGatewayAccountId, BANK_ACCOUNT);

        boolean result = stripeAccountSetupDao.isTaskCompletedForGatewayAccount(gatewayAccountId, BANK_ACCOUNT);

        assertThat(result, is(false));
    }

    @Test
    void shouldReturnTrueIfGatewayAccountHasCompletedTaskRecordedMoreThanOnce() {
        long gatewayAccountId = 42;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());
        
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);

        boolean result = stripeAccountSetupDao.isTaskCompletedForGatewayAccount(gatewayAccountId, RESPONSIBLE_PERSON);

        assertThat(result, is(true));
    }

    @Test
    void shouldRemoveCompletedTaskForGatewayAccount() {
        long gatewayAccountId = 42;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());

        long anotherGatewayAccountId = 1;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(anotherGatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());

        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, BANK_ACCOUNT);
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(anotherGatewayAccountId, RESPONSIBLE_PERSON);
        
        stripeAccountSetupDao.removeCompletedTaskForGatewayAccount(gatewayAccountId, RESPONSIBLE_PERSON);

        List<StripeAccountSetupTaskEntity> gatewayAccountTasks = stripeAccountSetupDao.findByGatewayAccountId(gatewayAccountId);

        assertThat(gatewayAccountTasks, hasSize(1));
        assertThat(gatewayAccountTasks, contains(hasProperty("task", is(BANK_ACCOUNT))));

        List<StripeAccountSetupTaskEntity> otherGatewayAccountTasks = stripeAccountSetupDao.findByGatewayAccountId(anotherGatewayAccountId);
        assertThat(otherGatewayAccountTasks, contains(hasProperty("task", is(RESPONSIBLE_PERSON))));
    }

    @Test
    void shouldRemoveCompletedTaskForGatewayAccountWhenHasCompletedTaskMoreThanOnce() {
        long gatewayAccountId = 42;
        app.getDatabaseTestHelper().addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(gatewayAccountId))
                .withPaymentGateway("stripe")
                .withServiceName("a cool service")
                .build());

        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, RESPONSIBLE_PERSON);
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, BANK_ACCOUNT);

        stripeAccountSetupDao.removeCompletedTaskForGatewayAccount(gatewayAccountId, RESPONSIBLE_PERSON);

        List<StripeAccountSetupTaskEntity> gatewayAccountTasks = stripeAccountSetupDao.findByGatewayAccountId(gatewayAccountId);

        assertThat(gatewayAccountTasks, hasSize(1));
        assertThat(gatewayAccountTasks, contains(hasProperty("task", is(BANK_ACCOUNT))));
    }
}
