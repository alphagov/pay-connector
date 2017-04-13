package uk.gov.pay.connector.model.domain;

import io.dropwizard.cli.Command;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import java.io.IOException;
import java.nio.charset.Charset;
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
        String data = DefaultStateTransitions.dumpGraphViz().toString();
        writeToFile(data, "states.dot");
    }

    private void writeToFile(String data, String filename) throws IOException {
        byte[] bytes = data.getBytes(Charset.forName("UTF-8"));
        Files.write(Paths.get(filename), bytes, StandardOpenOption.CREATE);

        System.out.format("Wrote state transition graph to '%s'\n\n", Paths.get(filename));
        System.out.println("Render using: ");
        System.out.println("  $ dot -Tpng -O states.dot");
        System.out.println("");
        System.out.println("or upload to http://www.webgraphviz.com/");
    }
}
