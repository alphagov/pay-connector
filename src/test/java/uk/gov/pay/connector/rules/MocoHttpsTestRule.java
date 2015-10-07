package uk.gov.pay.connector.rules;

import com.github.dreamhead.moco.HttpServer;
import com.github.dreamhead.moco.HttpsServer;
import com.github.dreamhead.moco.MocoConfig;
import com.github.dreamhead.moco.Runner;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import static com.github.dreamhead.moco.HttpsCertificate.certificate;
import static com.github.dreamhead.moco.Moco.httpsServer;
import static com.github.dreamhead.moco.Moco.log;
import static com.github.dreamhead.moco.Moco.pathResource;

public class MocoHttpsTestRule implements TestRule {
    protected final HttpServer httpServer;
    protected final Runner runner;

    protected MocoHttpsTestRule(int port) {
        this.httpServer = createServerOn(port);
        this.runner = Runner.runner(httpServer);
    }

    private HttpServer createServerOn(int port) {
        MocoConfig config = new MocoConfig() {
            @Override
            public boolean isFor(String s) {
                return false;
            }

            @Override
            public Object apply(Object o) {
                return null;
            }
        };
        HttpsServer server = httpsServer(port, certificate(pathResource("cert.jks"), "changeit", "changeit"), log());
        return server;
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    runner.start();
                    base.evaluate();
                } finally {
                    runner.stop();
                }
            }
        };
    }

    public void shutdown() {
        runner.stop();
    }
}