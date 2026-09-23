package pe.com.perubilling;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureDependencyTest {
    private static final Pattern IMPORT = Pattern.compile("import\\s+pe\\.com\\.perubilling\\.([A-Za-z0-9_]+)\\.");

    @Test
    void topLevelModulesMustRemainAcyclic() throws IOException {
        Path root = Path.of("src/main/java/pe/com/perubilling");
        Map<String, Set<String>> graph = new HashMap<>();

        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String module = root.relativize(file).getName(0).toString();
                graph.computeIfAbsent(module, ignored -> new HashSet<>());
                Matcher matcher = IMPORT.matcher(Files.readString(file));
                while (matcher.find()) {
                    String dependency = matcher.group(1);
                    if (!module.equals(dependency)) {
                        graph.get(module).add(dependency);
                    }
                }
            }
        }

        List<String> cycles = findCycles(graph);
        assertTrue(cycles.isEmpty(), "Se detectaron ciclos entre modulos: " + cycles);
    }

    private static List<String> findCycles(Map<String, Set<String>> graph) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        List<String> cycles = new ArrayList<>();
        for (String node : graph.keySet()) {
            dfs(node, graph, visiting, visited, stack, cycles);
        }
        return cycles;
    }

    private static void dfs(
            String node,
            Map<String, Set<String>> graph,
            Set<String> visiting,
            Set<String> visited,
            ArrayDeque<String> stack,
            List<String> cycles) {
        if (visited.contains(node)) return;
        if (!visiting.add(node)) {
            cycles.add(String.join(" -> ", stack) + " -> " + node);
            return;
        }
        stack.addLast(node);
        for (String dependency : graph.getOrDefault(node, Set.of())) {
            if (graph.containsKey(dependency)) {
                dfs(dependency, graph, visiting, visited, stack, cycles);
            }
        }
        stack.removeLast();
        visiting.remove(node);
        visited.add(node);
    }
}
