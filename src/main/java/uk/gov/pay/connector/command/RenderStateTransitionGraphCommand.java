package uk.gov.pay.connector.command;

import io.dropwizard.core.cli.Command;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class RenderStateTransitionGraphCommand extends Command {
    public RenderStateTransitionGraphCommand() {
        super("render-state-transition-graph",
                "Outputs a representation of the connector state transitions as a graphviz 'dot' file");
    }


    @Override
    public void configure(Subparser subparser) {
    }

    @Override
    public void run(Bootstrap<?> bootstrap, Namespace namespace) throws Exception {
        String data = new StateTransitionGraphVizRenderer(PaymentGatewayStateTransitions.getInstance()).toString();

        Path path = Paths.get("states.dot");
        writeToFile(data, path);

        System.out.format("Wrote state transition graph to '%s'\n\n", path);
        System.out.println("Render using: ");
        System.out.println("  $ dot -Tpng -O states.dot");
        System.out.println();
        System.out.println("or upload to http://www.webgraphviz.com/");
    }

    private void writeToFile(String data, Path path) throws IOException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        Files.write(path, bytes, StandardOpenOption.CREATE);
    }
}
