package metrics;

import model.ClassInfo;
import metrics.HierarchicalClustering.Node;
import metrics.HierarchicalClustering.Coupling;

import java.util.*;

/**
 * Identification de modules (groupes de classes couplées) à partir d'un dendrogramme.
 * Contraintes :
 *  - ≤ M/2 modules (M = nb total de classes)
 *  - chaque module est une branche (un Node du dendrogramme)
 *  - couplage moyen intra-module ≥ CP
 */
public final class ModuleIdentifier {

    private ModuleIdentifier() {}

    public static final class Result {
        /** Liste des modules (ensemble de classes). */
        public final List<Set<ClassInfo>> modules;
        /** Moyenne de couplage de chaque module (même ordre que modules). */
        public final List<Double> avgCouplings;
        /** true si toutes les contraintes ont été satisfaites ; false sinon (infeasible). */
        public final boolean feasible;

        public Result(List<Set<ClassInfo>> modules, List<Double> avgs, boolean feasible) {
            this.modules = modules;
            this.avgCouplings = avgs;
            this.feasible = feasible;
        }
    }

    /** Identifie des modules à partir du dendrogramme et de la fonction de couplage. */
    public static Result identify(Node root, Coupling couplingFn, double CP) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(couplingFn, "couplingFn");

        List<ClassInfo> all = new ArrayList<>(root.members);
        final int M = all.size();
        final int maxModules = Math.max(1, M / 2);

        List<ClusterMod> modules = new ArrayList<>();
        modules.add(new ClusterMod(root, avgPairwise(root.members, couplingFn)));

        while (true) {
            int idxWorst = -1;
            double worstAvg = Double.MAX_VALUE;

            for (int i = 0; i < modules.size(); i++) {
                ClusterMod cm = modules.get(i);
                if (cm.avg >= CP) continue;         // déjà OK
                if (cm.node.isLeaf()) continue;     // pas splittable
                if (cm.avg < worstAvg) { worstAvg = cm.avg; idxWorst = i; }
            }

            if (idxWorst < 0) break;

            if (modules.size() >= maxModules) {
                return toResult(modules, false);
            }

            ClusterMod victim = modules.remove(idxWorst);
            Node L = victim.node.left;
            Node R = victim.node.right;
            modules.add(new ClusterMod(L, avgPairwise(L.members, couplingFn)));
            modules.add(new ClusterMod(R, avgPairwise(R.members, couplingFn)));
        }

        return toResult(modules, true);
    }

    // ---- interne ----

    /** Wrapper pour suivre un cluster courant + sa moyenne. */
    private static final class ClusterMod {
        final Node node;
        final Set<ClassInfo> members;
        final double avg;
        ClusterMod(Node n, double avg) {
            this.node = n;
            this.members = n.members;
            this.avg = avg;
        }
    }

    private static Result toResult(List<ClusterMod> list, boolean feasible) {
        List<Set<ClassInfo>> mods = new ArrayList<>();
        List<Double> avgs = new ArrayList<>();
        for (ClusterMod cm : list) {
            mods.add(Collections.unmodifiableSet(new LinkedHashSet<>(cm.members)));
            avgs.add(cm.avg);
        }
        return new Result(Collections.unmodifiableList(mods),
                Collections.unmodifiableList(avgs),
                feasible);
    }

    /** Couplage moyen de toutes les paires {i<j} au sein d’un ensemble de classes. */
    private static double avgPairwise(Set<ClassInfo> members, Coupling cpl) {
        int n = members.size();
        if (n <= 1) return 1.0; // convention : singleton = parfaitement "cohérent"
        List<ClassInfo> list = new ArrayList<>(members);
        double sum = 0.0;
        int pairs = 0;
        for (int i = 0; i < n; i++) {
            ClassInfo a = list.get(i);
            for (int j = i+1; j < n; j++) {
                ClassInfo b = list.get(j);
                sum += clamp01(cpl.between(a, b));
                pairs++;
            }
        }
        return (pairs == 0) ? 1.0 : (sum / pairs);
    }

    private static double clamp01(double v) { return (v < 0) ? 0 : (v > 1 ? 1 : v); }
}
