package uk.gov.pay.connector.it.dao;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.EmailNotificationsDao;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class EmailNotificationsDaoITest extends DaoITestBase {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private EmailNotificationsDao emailNotificationsDao;
    private ChargeDao chargeDao;

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestEmailNotification defaultEmailNotification;

    @Before
    public void setUp() throws Exception {
        emailNotificationsDao = env.getInstance(EmailNotificationsDao.class);
        chargeDao = env.getInstance(ChargeDao.class);

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

        String template = "lorem ipsum";
        databaseTestHelper.addEmailNotification(defaultEmailNotification.getTestAccount().getAccountId(), template);

        Optional<EmailNotificationEntity> emailNotificationOptional = emailNotificationsDao.findByAccountId(defaultEmailNotification.getTestAccount().getAccountId());

        assertThat(emailNotificationOptional.isPresent(), is(true));

        EmailNotificationEntity notification = emailNotificationOptional.get();

        assertThat(notification.getId(), is(notNullValue()));
        assertThat(notification.getTemplateBody(), is(template));
        assertThat(notification.getAccountEntity().getId(), is(defaultEmailNotification.getTestAccount().accountId));
        assertThat(notification.isEnabled(), is(true));
    }

    @Test
    public void findByAccountId_shouldNotFindEmailNotification() {

        Long accountId = 9876512L;

        assertThat(emailNotificationsDao.findByAccountId(accountId), is(Optional.empty()));
    }
}
