package uk.gov.pay.connector.util;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

import uk.gov.pay.commons.utils.startup.ApplicationStartupDependentResourceChecker;
import uk.gov.pay.commons.utils.startup.DatabaseStartupResource;
import uk.gov.pay.connector.app.ConnectorConfiguration;

public class DependentResourceWaitCommand extends ConfiguredCommand<ConnectorConfiguration> {
    public DependentResourceWaitCommand() {
        super("waitOnDependencies", "Waits for dependent resources to become available");
    }

    @Override
    protected void run(Bootstrap<ConnectorConfiguration> bs, Namespace ns, ConnectorConfiguration conf) {
        new ApplicationStartupDependentResourceChecker(new DatabaseStartupResource(conf.getDataSourceFactory()), duration -> {
            try {
                Thread.sleep(duration.toNanos() / 1000);
            } catch (InterruptedException ignored) {
            }
        })
                .checkAndWaitForResource();
    }
}
