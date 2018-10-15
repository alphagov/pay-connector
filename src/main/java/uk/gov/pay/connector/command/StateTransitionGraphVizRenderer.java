package uk.gov.pay.connector.command;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.Triple;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PaymentGatewayStateTransitions;

import java.util.StringJoiner;

/**
 * Render a graph - either install graphviz or use http://www.webgraphviz.com/
 */
public class StateTransitionGraphVizRenderer {
    private final PaymentGatewayStateTransitions transitions;

    public StateTransitionGraphVizRenderer(PaymentGatewayStateTransitions transitions) {
        this.transitions = transitions;
    }

    public String toString() {
        StringJoiner s = new StringJoiner("\n", "", "\n");
        s.add("digraph PaymentGatewayStateTransitions {");

        s.add("rankdir=TB overlap=false splines=true");
        s.add("node [style=filled shape=box color=\"0.650 0.200 1.000\"]");

        transitions.allTransitions().forEach(edge ->
            s.add(formatEdge(edge))
        );

        nodeColours(s);

        s.add("}");
        return s.toString();
    }

    private CharSequence formatEdge(Triple<ChargeStatus, ChargeStatus, String> edge) {
        return String.format("%s -> %s [label=\"%s\"]",
                nameFor(edge.getLeft()),
                nameFor(edge.getMiddle()),
                edge.getRight());
    }

    private void nodeColours(StringJoiner s) {
        transitions.allStatuses().forEach(c -> {
            s.add(String.format("%s [color=\"%s\" shape=%s] ;", nameFor(c), colourFor(c), shapeFor(c)));
        });
    }

    private String shapeFor(ChargeStatus c) {
        return c.toExternal().isFinished() ? "doubleoctagon" : "box";
    }

    private String colourFor(ChargeStatus c) {
        ImmutableMap<String, String> colours = ImmutableMap.<String, String>builder()
                .put("created", "0.305 0.625 1.000")
                .put("started", "0.201 0.753 1.000")
                .put("submitted", "0.408 0.498 1.000")
                .put("success", "0.449 0.447 1.000")
                .put("failed", "0.628 0.227 1.000")
                .put("cancelled", "0.578 0.289 1.000")
                .put("error", "0.650 0.200 1.000").build();

        return colours.getOrDefault(c.toExternal().getStatus(), "1.000 0.000 0.000");
    }

    private String nameFor(ChargeStatus to) {
        return String.format("\"%s (%s)\"", to.name(), to.toExternal().getStatus());
    }
}
