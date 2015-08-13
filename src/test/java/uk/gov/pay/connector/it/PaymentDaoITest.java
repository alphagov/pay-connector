package uk.gov.pay.connector.it;

import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.dao.PaymentDao;
import uk.gov.pay.connector.util.PostgresDockerRule;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PaymentDaoITest {
    private String configFilePath = resourceFilePath("config/test-it-config.yaml");

    private PostgresDockerRule postgres = new PostgresDockerRule();

    private DropwizardAppRule<ConnectorConfiguration> app = new DropwizardAppRule<>(
            ConnectorApp.class,
            configFilePath,
            config("database.url", postgres.getConnectionUrl()),
            config("database.user", postgres.getUsername()),
            config("database.password", postgres.getPassword()));

    @Rule
    public RuleChain rules = RuleChain.outerRule(postgres).around(app);

    private PaymentDao paymentDao;

    @Before
    public void setUp() throws Exception {
        ConnectorApp connectorApp = app.getApplication();
        connectorApp.run("db", "drop-all", "--confirm-delete-everything", configFilePath);
        connectorApp.run("db", "migrate", configFilePath);
        paymentDao = new PaymentDao(connectorApp.getJdbi());
    }

    @Test
    public void insertANewPaymentAndReturnTheId() throws Exception {
        long amount = 100;
        long payId = paymentDao.insertAmountAndReturnNewId(amount);
        assertThat(payId, is(1L));
    }

    @Test
    public void insertAmountAndThenGetAmountById() throws Exception {
        long expectedAmount = 101;
        long payId = paymentDao.insertAmountAndReturnNewId(expectedAmount);

        long actualAmount = paymentDao.getAmountById(payId);

        assertThat(actualAmount, is((long)expectedAmount));
    }
}
