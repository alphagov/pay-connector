package uk.gov.pay.connector.util;

import io.dropwizard.core.cli.ConfiguredCommand;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import uk.gov.pay.connector.app.ConnectorConfiguration;

public class DependentResourceWaitCommand extends ConfiguredCommand<ConnectorConfiguration> {
    public DependentResourceWaitCommand() {
        super("waitOnDependencies", "Waits for dependent resources to become available");
    }

    @Override
    public void configure(Subparser subparser) {
        super.configure(subparser);
    }

    @Override
    protected void run(Bootstrap<ConnectorConfiguration> bs, Namespace ns, ConnectorConfiguration conf) {
        ApplicationStartupDependentResourceChecker applicationStartupDependentResourceChecker = new ApplicationStartupDependentResourceChecker(new ApplicationStartupDependentResource(conf));
        applicationStartupDependentResourceChecker.checkAndWaitForResources();
    }
}
