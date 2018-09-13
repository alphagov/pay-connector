package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.EmailNotificationsDao;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
//todo PP-4111 remove this class after EmailNotificationsDao is removed 
public class EmailNotificationsDaoITest extends DaoITestBase {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private EmailNotificationsDao emailNotificationsDao;

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestEmailNotification defaultEmailNotification;

    @Before
    public void setUp() {
        emailNotificationsDao = env.getInstance(EmailNotificationsDao.class);

        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();

        this.defaultEmailNotification = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .anEmailNotification()
                .withTestAccount(defaultTestAccount)
                .insert();
    }

    @Test
    public void findByAccountId_shouldFindEmailNotification() {

        Optional<EmailNotificationEntity> emailNotificationOptional = emailNotificationsDao.findByAccountId(defaultEmailNotification.getTestAccount().getAccountId());

        assertThat(emailNotificationOptional.isPresent(), is(true));

        EmailNotificationEntity notification = emailNotificationOptional.get();

        assertThat(notification.getId(), is(notNullValue()));
        assertThat(notification.getTemplateBody(), is(defaultEmailNotification.getTemplate()));
        assertThat(notification.getAccountEntity().getId(), is(defaultEmailNotification.getTestAccount().accountId));
        assertThat(notification.isEnabled(), is(true));
    }

    @Test
    public void findByAccountId_shouldNotFindEmailNotification() {

        Long accountId = 9876512L;

        assertThat(emailNotificationsDao.findByAccountId(accountId), is(Optional.empty()));
    }
}
