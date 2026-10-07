package sealmc.swe3tie.sealcore.core.module;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Dependency ordering for modules. Pure logic, no Bukkit types. */
public final class ModuleGraph {

    private ModuleGraph() {
    }

    /** Thrown when module dependencies form a cycle. */
    public static final class CycleException extends RuntimeException {
        public CycleException(String message) {
            super(message);
        }
    }

    /**
     * Orders ids so every module comes after the modules it depends on. Dependencies on ids that are
     * not in the graph are ignored here (the manager reports them separately). Output is stable:
     * independent modules keep alphabetical order.
     *
     * @param dependencies module id → ids it depends on
     * @throws CycleException if the graph has a cycle
     */
    @NotNull
    public static List<String> order(@NotNull Map<String, ? extends Collection<String>> dependencies) {
        Map<String, Collection<String>> sorted = new TreeMap<>(dependencies);
        List<String> result = new ArrayList<>(sorted.size());
        Set<String> done = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();

        for (String id : sorted.keySet()) {
            visit(id, sorted, done, visiting, result);
        }
        return result;
    }

    private static void visit(String id, Map<String, Collection<String>> graph, Set<String> done,
                              Set<String> visiting, List<String> out) {
        if (done.contains(id)) return;
        if (!visiting.add(id)) {
            List<String> path = new ArrayList<>(visiting);
            path.add(id);
            throw new CycleException("Module dependency cycle: " + String.join(" -> ", path));
        }
        for (String dep : graph.get(id)) {
            if (graph.containsKey(dep)) visit(dep, graph, done, visiting, out);
        }
        visiting.remove(id);
        done.add(id);
        out.add(id);
    }

    /** Convenience for tests and callers holding a plain list of edges. */
    @NotNull
    public static Map<String, Set<String>> graphOf(@NotNull Map<String, ? extends Collection<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((k, v) -> copy.put(k, new LinkedHashSet<>(v)));
        return copy;
    }
}
