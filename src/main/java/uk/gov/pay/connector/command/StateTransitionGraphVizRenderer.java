package uk.gov.pay.connector.command;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Render a graph - either install graphviz or use http://www.webgraphviz.com/
 */
public class StateTransitionGraphVizRenderer {
    private final Map<ChargeStatus, List<ChargeStatus>> table;

    public StateTransitionGraphVizRenderer(Map<ChargeStatus, List<ChargeStatus>> table) {
        this.table = table;
    }

    public String toString() {
        StringJoiner s = new StringJoiner("\n", "", "\n");
        s.add("digraph DefaultStateTransitions {");

        s.add("rankdir=TB overlap=false splines=true");
        s.add("node [style=filled shape=box color=\"0.650 0.200 1.000\"]");

        table.forEach((c, l) -> s.add(printEntry(c, l)));

        nodeColours(s);

        s.add("}");
        return s.toString();
    }

    private void nodeColours(StringJoiner s) {
        allNodes().forEach(c -> {
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

    private Set<ChargeStatus> allNodes() {
        return Stream.concat(
                table.keySet().stream(),
                table.values().stream().flatMap(l -> l.stream())
        ).collect(Collectors.toSet());
    }

    private String printEntry(ChargeStatus from, List<ChargeStatus> toList) {
        return toList.stream()
                .map(to -> String.format("%s -> %s", nameFor(from), nameFor(to)))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private String nameFor(ChargeStatus to) {
        return String.format("\"%s (%s)\"", to.name(), to.toExternal().getStatus());
    }
}
