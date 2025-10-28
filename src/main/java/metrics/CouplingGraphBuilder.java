package metrics;

import model.ClassInfo;
import java.util.*;

public final class CouplingGraphBuilder {

    private CouplingGraphBuilder() {}

    // petit graphe non orienté pondéré
    public static final class WeightedGraph<N> {
        private final Set<N> nodes = new LinkedHashSet<>();
        private final Map<N, Map<N, Double>> w = new LinkedHashMap<>();
        public void addNode(N n){ if (nodes.add(n)) w.put(n, new LinkedHashMap<>()); }
        public void addEdgeUndirected(N a, N b, double weight){
            if (a==null || b==null || a.equals(b) || weight<=0) return;
            addNode(a); addNode(b);
            w.get(a).merge(b, weight, Double::sum);
            w.get(b).merge(a, weight, Double::sum);
        }
        public Set<N> nodes(){ return nodes; }
        public Map<N, Map<N, Double>> edges(){ return w; }
    }

    /**
     * Construit le graphe de couplage pondéré en appelant calculateCoupling pour chaque couple (A,B).
     * @param methodGraph
     * @param classes
     * @return
     */
    public static WeightedGraph<String> buildFromCalculator(
            CallGraphBuilder.DiGraph<String> methodGraph,
            List<ClassInfo> classes
    ) {
        Objects.requireNonNull(methodGraph, "methodGraph");
        Objects.requireNonNull(classes, "classes");

        WeightedGraph<String> g = new WeightedGraph<>();

        for (ClassInfo ci : classes) g.addNode(qnOf(ci));

        final int n = classes.size();
        for (int i = 0; i < n; i++) {
            ClassInfo A = classes.get(i);
            for (int j = i+1; j < n; j++) {
                ClassInfo B = classes.get(j);
                float w = MetricsCalculator.calculateCoupling(methodGraph, classes, A, B);
                g.addEdgeUndirected(qnOf(A), qnOf(B), w);
            }
        }
        return g;
    }

    private static String qnOf(ClassInfo ci) {
        if (ci.qualifiedName != null && !ci.qualifiedName.isEmpty()) return ci.qualifiedName;
        if (ci.packageName != null && !ci.packageName.isEmpty()) return ci.packageName + "." + ci.className;
        return ci.className;
    }
}
