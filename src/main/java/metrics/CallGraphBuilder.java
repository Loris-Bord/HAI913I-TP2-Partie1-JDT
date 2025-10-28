package metrics;

import model.ClassInfo;
import model.MethodInfo;
import model.MethodCallInfo;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Construit des graphes d'appel (méthode->méthode et classe->classe)
 * à partir du modèle collecté par des visiteurs.
 */
public class CallGraphBuilder {

    public static class DiGraph<N> {
        private final Set<N> nodes = new LinkedHashSet<>();
        private final Map<N, Set<N>> adj = new LinkedHashMap<>();

        public void addNode(N n) {
            if (nodes.add(n)) {
                adj.put(n, new LinkedHashSet<>());
            }
        }
        public void addEdge(N from, N to) {
            addNode(from);
            addNode(to);
            adj.get(from).add(to);
        }
        public Set<N> nodes() { return nodes; }
        public Map<N, Set<N>> edges() { return adj; }

        // helpers
        private String id(N n) { return "n" + Integer.toHexString(System.identityHashCode(n)); }
        private static String safe(String s) { return s.replaceAll("\\W+", "_"); }
        private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    }

    /**
     * Construit le graphe d'appel au niveau méthode en utilisant les classes de données en paramètres
     * @param classes
     * @param includeExternal
     * @return
     */
    public static DiGraph<String> buildMethodGraph(List<ClassInfo> classes, boolean includeExternal) {
        // Index des méthodes du projet par signature qualifiée si dispo, sinon par "FQN#name(params)"
        Map<String, String> projectMethodKeys = new LinkedHashMap<>(); // key -> display
        Map<String, String> displayByKey      = new LinkedHashMap<>();

        for (ClassInfo ci : classes) {
            String owner = qnOf(ci);
            for (MethodInfo mi : ci.methods) {
                String key = methodKey(mi, owner);
                String display = displaySig(mi, owner);
                projectMethodKeys.put(key, display);
                displayByKey.put(key, display);
            }
        }

        DiGraph<String> g = new DiGraph<>();

        // Ajouter tous les noeuds "projet"
        for (String disp : projectMethodKeys.values()) g.addNode(disp);

        // Arêtes
        for (ClassInfo ci : classes) {
            String owner = qnOf(ci);
            for (MethodInfo src : ci.methods) {
                String srcKey = methodKey(src, owner);
                String srcDisp = displayByKey.get(srcKey);
                if (srcDisp == null) continue; // sécurité

                if (src.calls == null) continue;
                for (MethodCallInfo call : src.calls) {
                    // Résoudre le callee
                    String calleeKey = calledMethodKey(call);
                    String calleeDisp;

                    if (calleeKey != null && projectMethodKeys.containsKey(calleeKey)) {
                        // Méthode cible appartient au projet (binding/clé reconnue)
                        calleeDisp = projectMethodKeys.get(calleeKey);
                    } else {
                        if (!includeExternal) continue;
                        // External: construire un libellé raisonnable
                        String targetOwner = (call.declaringType != null ? call.declaringType : call.receiverStaticType);
                        String sig = (call.qualifiedSignature != null)
                                ? call.qualifiedSignature
                                : (targetOwner != null ? targetOwner + "." + call.name + "(...)" : call.name + "(...)");
                        calleeDisp = "[EXT] " + sig;
                    }

                    g.addEdge(srcDisp, calleeDisp);
                }
            }
        }

        return g;
    }

    /**
     * Construit le graphe d'appel au niveau classe
     * @param classes
     * @param includeExternal
     * @return
     */
    public static DiGraph<String> buildClassGraph(List<ClassInfo> classes, boolean includeExternal) {
        Set<String> projectClasses = classes.stream().map(CallGraphBuilder::qnOf).collect(Collectors.toCollection(LinkedHashSet::new));
        DiGraph<String> g = new DiGraph<>();
        for (String c : projectClasses) g.addNode(c);

        for (ClassInfo ci : classes) {
            String from = qnOf(ci);
            for (MethodInfo mi : ci.methods) {
                if (mi.calls == null) continue;
                for (MethodCallInfo call : mi.calls) {
                    String target = (call.declaringType != null) ? call.declaringType : call.receiverStaticType;
                    if (target == null) continue;
                    if (!projectClasses.contains(target)) {
                        if (!includeExternal) continue;
                        target = "[EXT] " + target;
                    }
                    if (!from.equals(target)) {
                        g.addEdge(from, target);
                    }
                }
            }
        }
        return g;
    }

    // -------------------- helpers --------------------

    /**
     * Retourne le qualified Name de la classe
     * @param ci
     * @return
     */
    private static String qnOf(ClassInfo ci) {
        if (ci.qualifiedName != null && !ci.qualifiedName.isEmpty()) return ci.qualifiedName;
        if (ci.packageName != null && !ci.packageName.isEmpty()) return ci.packageName + "." + ci.className;
        return ci.className;
    }

    /**
     * Clé "globale" d’une méthode du projet (préférence au methodKey/binding)
     * @param mi
     * @param ownerQN
     * @return
     */
    private static String methodKey(MethodInfo mi, String ownerQN) {
        if (mi.methodKey != null && !mi.methodKey.startsWith("NO_BINDING:")) return mi.methodKey;
        // fallback stable
        return ownerQN + "#" + simpleSig(mi);
    }

    /**
     * Tente de reconstituer une clé de méthode appelée (via binding s’il existe)
     * @param call
     * @return
     */
    private static String calledMethodKey(MethodCallInfo call) {
        if (call.methodKey != null && !call.methodKey.startsWith("NO_BINDING:")) return call.methodKey;
        if (call.qualifiedSignature != null) return call.qualifiedSignature; // Owner.m(T1,T2)->R
        if (call.declaringType != null && call.name != null) {
            return call.declaringType + "." + call.name + "(?)";
        }
        return null;
    }

    private static String simpleSig(MethodInfo m) {
        String params = (m.parameterTypes == null) ? "" : String.join(",", m.parameterTypes);
        return m.name + "(" + params + ")";
    }

    private static String displaySig(MethodInfo m, String ownerQN) {
        return ownerQN + "." + simpleSig(m);
    }
}