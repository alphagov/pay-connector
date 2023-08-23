package uk.gov.pay.connector.junit;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import junitparams.JUnitParamsRunner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import uk.gov.service.payments.commons.testing.port.PortFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.collect.Lists.newArrayList;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static java.util.Arrays.stream;
import static uk.gov.pay.connector.junit.DropwizardTestApplications.createIfNotRunning;
import static uk.gov.pay.connector.junit.PostgresTestDocker.getDbPassword;
import static uk.gov.pay.connector.junit.PostgresTestDocker.getDbUri;
import static uk.gov.pay.connector.junit.PostgresTestDocker.getDbUsername;
import static uk.gov.pay.connector.junit.PostgresTestDocker.getOrCreate;
import static uk.gov.pay.connector.junit.SqsTestDocker.getQueueUrl;

/**
 * Runs a Dropwizard application with the given {@link DropwizardConfig} before the Test class if there is not an
 * application started yet (from a previous Test class using the same runner) with the same {@link DropwizardConfig}.
 * <p>
 * Example:
 * <p>
 * <pre>
 *  &#64;RunWith(DropwizardJUnitRunner.class)
 *  &#64;DropwizardConfig(app = MyApp.class, config = "config/test.yaml")
 *  public class MyTest {
 *
 *      &#64;Test
 *      public void shouldGetTestResourceFromARunningApplicationInAnotherClass() throws Exception {
 *          given().get("/test-resource")
 *                 .then()
 *                 .statusCode(200);
 *      }
 *  }
 * </pre>
 * <p>
 * After this test and if more tests are being executed, the application will be kept alive so other tests that needs
 * similar configuration will benefit from the existing running application.
 */
public final class DropwizardJUnitRunner extends JUnitParamsRunner {

    private int wireMockPort = PortFactory.findFreePort();
    private WireMockServer wireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort));

    public DropwizardJUnitRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected Statement classBlock(final RunNotifier notifier) {
        DropwizardConfig dropwizardConfigAnnotation = dropwizardConfigAnnotation();
        List<ConfigOverride> configOverride = newArrayList();

        if (dropwizardConfigAnnotation.withDockerPostgres()) {
            getOrCreate();
            configOverride.add(config("database.url", getDbUri()));
            configOverride.add(config("database.user", getDbUsername()));
            configOverride.add(config("database.password", getDbPassword()));
        }

        if (dropwizardConfigAnnotation.withDockerSQS()) {
            SqsTestDocker.initialise("capture-queue", "event-queue", "tasks-queue");
            configOverride.add(config("sqsConfig.captureQueueUrl", getQueueUrl("capture-queue")));
            configOverride.add(config("sqsConfig.eventQueueUrl", getQueueUrl("event-queue")));
            configOverride.add(config("sqsConfig.taskQueueUrl", getQueueUrl("tasks-queue")));
        }

        if (dropwizardConfigAnnotation.configOverrides().length > 0) {
            Arrays.stream(dropwizardConfigAnnotation.configOverrides()).forEach(c -> configOverride.add(config(c.key(), c.value())));
        }

        configOverride.add(config("worldpay.urls.test", "http://localhost:" + wireMockPort + "/jsp/merchant/xml/paymentService.jsp"));
        configOverride.add(config("worldpay.threeDsFlexDdcUrls.test", "http://localhost:" + wireMockPort + "/shopper/3ds/ddc.html"));
        configOverride.add(config("stripe.url", "http://localhost:" + wireMockPort));
        configOverride.add(config("ledgerBaseURL", "http://localhost:" + wireMockPort));
        configOverride.add(config("cardidBaseURL", "http://localhost:" + wireMockPort));

        try {
            createIfNotRunning(dropwizardConfigAnnotation.app(), dropwizardConfigAnnotation.config(), configOverride.toArray(new ConfigOverride[0]));
        } catch (Exception e) {
            throw new DropwizardJUnitRunnerException(e);
        } finally {
            // DropwizardTestSupport adds the config overrides to the System properties. Clear these up immediately
            // otherwise all subsequent DropWizard apps started by tests will use these until the app is stopped
            DropwizardTestApplications.removeConfigOverridesFromSystemProperties();
        }

        wireMockServer.start();

        return super.classBlock(notifier);
    }

    @Override
    protected Statement withAfterClasses(Statement statement) {
        return new CleanupStatement(statement, wireMockServer);
    }

    @Override
    public Object createTest() throws Exception {
        Object testInstance = super.createTest();
        DropwizardConfig declaredConfiguration = dropwizardConfigAnnotation();
        TestContext testContext = DropwizardTestApplications.getTestContextOf(declaredConfiguration.app(), declaredConfiguration.config(), wireMockServer);
        setTestContextToDeclaredAnnotations(testInstance, testContext);
        return testInstance;
    }

    private void setTestContextToDeclaredAnnotations(Object testInstance, TestContext testContext) {
        List<FrameworkField> annotatedFields = getTestClass().getAnnotatedFields();
        annotatedFields.forEach(frameworkField -> stream(frameworkField.getAnnotations())
                .filter(annotation -> annotation.annotationType().equals(DropwizardTestContext.class))
                .findFirst()
                .ifPresent(testContextAnnotation -> {
                    frameworkField.getField().setAccessible(true);
                    try {
                        frameworkField.getField().set(testInstance, testContext);
                    } catch (IllegalAccessException e) {
                        throw new DropwizardJUnitRunnerException(e);
                    }
                }));
    }

    private DropwizardConfig dropwizardConfigAnnotation() {
        return (DropwizardConfig) stream(getTestClass().getAnnotations())
                .filter(annotation -> annotation.annotationType().equals(DropwizardConfig.class))
                .findFirst()
                .orElseThrow(() -> new DropwizardJUnitRunnerException("DropwizardJUnitRunner requires annotation @DropwizardConfig to be present"));
    }

    class CleanupStatement extends Statement {
        private Statement next;
        private WireMockServer wireMockServer;

        public CleanupStatement(Statement next, WireMockServer wireMockServer) {
            this.next = next;
            this.wireMockServer = wireMockServer;
        }

        @Override
        public void evaluate() throws Throwable {
            List<Throwable> errors = new ArrayList<>();
            try {
                next.evaluate();
            } catch (Throwable e) {
                errors.add(e);
            } finally {
                wireMockServer.stop();
            }
            MultipleFailureException.assertEmpty(errors);
        }
    }
}
