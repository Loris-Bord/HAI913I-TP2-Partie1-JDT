package metrics;

import model.ClassInfo;
import java.util.*;

/** Clustering hiérarchique agglomératif basé sur une fonction de couplage [0..1]. */
public final class HierarchicalClustering {

    /** Interface pour fournir le couplage entre deux classes (symétrique, [0..1]). */
    public interface Coupling {
        double between(ClassInfo a, ClassInfo b);
    }

    /** Noeud de dendrogramme. */
    public static final class Node {
        public final Set<ClassInfo> members;  // toutes les classes contenues dans ce cluster
        public final Node left, right;        // enfants si cluster interne, sinon null
        public final double height;           // distance “fusion” (1 - similarité)
        public final String label;

        private Node(Set<ClassInfo> members, Node left, Node right, double height, String label) {
            this.members = members; this.left = left; this.right = right; this.height = height; this.label = label;
        }
        public static Node leaf(ClassInfo c) {
            return new Node(Collections.singleton(c), null, null, 0.0, qnOf(c));
        }
        public static Node merge(Node a, Node b, double height) {
            Set<ClassInfo> m = new LinkedHashSet<>(a.members);
            m.addAll(b.members);
            return new Node(Collections.unmodifiableSet(m), a, b, height, null);
        }
        public boolean isLeaf() { return left == null && right == null; }
    }

    /** Stratégie de liaison inter-clusters (comment on agrège les similarités lors des fusions). */
    public enum Linkage { SINGLE, COMPLETE, AVERAGE }

    /**
     * Lance le clustering. Renvoie la racine du dendrogramme.
     * */
    public static Node cluster(List<ClassInfo> classes,
                               Coupling couplingFn,
                               Linkage linkage) {
        Objects.requireNonNull(classes); Objects.requireNonNull(couplingFn); Objects.requireNonNull(linkage);
        if (classes.isEmpty()) return null;
        if (classes.size() == 1) return Node.leaf(classes.get(0));

        List<Node> clusters = new ArrayList<>();
        for (ClassInfo c : classes) clusters.add(Node.leaf(c));

        Map<Key, Double> leafSim = new HashMap<>();
        for (int i=0;i<classes.size();i++) for (int j=i+1;j<classes.size();j++) {
            double sim = clamp01(couplingFn.between(classes.get(i), classes.get(j)));
            leafSim.put(new Key(classes.get(i), classes.get(j)), sim);
        }

        while (clusters.size() > 1) {
            double bestSim = -1;
            int bi = -1, bj = -1;

            for (int i=0;i<clusters.size();i++) {
                for (int j=i+1;j<clusters.size();j++) {
                    double sim = interClusterSim(clusters.get(i), clusters.get(j), leafSim, linkage);
                    if (sim > bestSim) { bestSim = sim; bi = i; bj = j; }
                }
            }

            Node a = clusters.get(bi), b = clusters.get(bj);
            double height = 1.0 - clamp01(bestSim);
            Node merged = Node.merge(a, b, height);

            // Remplace les deux par le cluster fusionné
            if (bi > bj) { int t=bi; bi=bj; bj=t; }
            clusters.remove(bj);
            clusters.remove(bi);
            clusters.add(merged);
        }

        return clusters.get(0);
    }

    // --- Helpers ---

    /** Similarité entre deux clusters selon la liaison choisie. */
    private static double interClusterSim(Node A, Node B, Map<Key, Double> leafSim, Linkage linkage) {
        switch (linkage) {
            case SINGLE:   return agg(A, B, leafSim, Math::max, -1.0);
            case COMPLETE: return agg(A, B, leafSim, Math::min,  1.0);
            case AVERAGE:
            default:       return average(A, B, leafSim);
        }
    }
    private static double agg(Node A, Node B,
                              Map<Key, Double> leafSim,
                              java.util.function.DoubleBinaryOperator op,
                              double init) {
        double acc = init;
        boolean first = true;

        for (ClassInfo a : A.members) {
            for (ClassInfo b : B.members) {
                double s = leafSim.getOrDefault(new Key(a, b), 0.0);

                if (first) {
                    acc = s;
                    first = false;
                } else {
                    acc = op.applyAsDouble(acc, s);
                }
            }
        }

        return clamp01(acc);
    }

    private static double average(Node A, Node B, Map<Key,Double> leafSim) {
        double sum = 0; int n = 0;
        for (ClassInfo a : A.members) for (ClassInfo b : B.members) {
            sum += leafSim.getOrDefault(new Key(a,b), 0.0);
            n++;
        }
        return (n==0) ? 0.0 : clamp01(sum / n);
    }

    private static double clamp01(double v){ return v<0?0: (v>1?1:v); }

    private static final class Key {
        final ClassInfo a,b; Key(ClassInfo x, ClassInfo y){
            if (qnOf(x).compareTo(qnOf(y)) <= 0) { a=x; b=y; } else { a=y; b=x; }
        }
        @Override public boolean equals(Object o){ if(!(o instanceof Key)) return false; Key k=(Key)o; return a==k.a && b==k.b; }
        @Override public int hashCode(){ return System.identityHashCode(a)*31 + System.identityHashCode(b); }
    }
    private static String qnOf(ClassInfo ci){
        if (ci.qualifiedName != null && !ci.qualifiedName.isEmpty()) return ci.qualifiedName;
        if (ci.packageName != null && !ci.packageName.isEmpty()) return ci.packageName + "." + ci.className;
        return ci.className;
    }
}
