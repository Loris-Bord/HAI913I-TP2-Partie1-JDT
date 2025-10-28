package metrics;

import model.ClassInfo;
import model.MethodCallInfo;
import model.MethodInfo;
import org.eclipse.core.runtime.Assert;

import java.util.*;
import java.util.stream.Collectors;

public class MetricsCalculator {
    public static class Metrics {
       public float couplage;
    }

    public static Metrics compute(List<ClassInfo> classes, Map<String, Integer> filePathToLOC, Set<String> packages) {
        Metrics m = new Metrics();



        return m;
    }

    private static String qnOf(ClassInfo ci) {
        if (ci.qualifiedName != null && !ci.qualifiedName.isEmpty()) return ci.qualifiedName;
        return (ci.packageName != null && !ci.packageName.isEmpty())
                ? ci.packageName + "." + ci.className
                : ci.className;
    }

    private static String simpleSig(MethodInfo m) {
        return m.name + "(" + (m.parameterTypes == null ? "" : String.join(",", m.parameterTypes)) + ")";
    }

    /**
     * Calcule une métrique de couplage
     * @param methodGraph
     * @param classes
     * @param A
     * @param B
     * @return
     */
    public static float calculateCoupling(CallGraphBuilder.DiGraph<String> methodGraph,
                                          List<ClassInfo> classes,
                                          ClassInfo A, ClassInfo B) {
        if (methodGraph == null || classes == null || A == null || B == null || A == B) return 0f;

        Map<String,String> simple2fqn = new HashMap<>();
        Set<String> fqns = new HashSet<>();
        for (ClassInfo ci : classes) {
            String fqn = qnOf(ci);
            fqns.add(fqn);
            simple2fqn.put(ci.className, fqn);
        }

        String qnA = qnOf(A);
        String qnB = qnOf(B);

        long numerator = 0, denominator = 0;

        for (Map.Entry<String, Set<String>> e : methodGraph.edges().entrySet()) {
            String fromOwner = canonicalOwner(ownerOfMethodNodeAllowExt(e.getKey()), fqns, simple2fqn);
            for (String toNode : e.getValue()) {
                String toOwner = canonicalOwner(ownerOfMethodNodeAllowExt(toNode), fqns, simple2fqn);

                denominator++;

                if ((fromOwner.equals(qnA) && toOwner.equals(qnB)) ||
                        (fromOwner.equals(qnB) && toOwner.equals(qnA))) {
                    numerator++;
                }
            }
        }
        //System.out.println(numerator + "/" + denominator);
        return denominator == 0 ? 0f : (float) numerator / (float) denominator;
    }

// --- helpers ---

    private static String canonicalOwner(String owner, Set<String> projectFqns, Map<String,String> simple2fqn) {
        if (owner == null) return "";
        if (owner.equals("[EXT]")) return "[EXT]";
        owner = owner.replace('$','.');
        if (projectFqns.contains(owner)) return owner;
        if (owner.indexOf('.') < 0) {
            String fqn = simple2fqn.get(owner);
            if (fqn != null) return fqn;
        }
        return owner;
    }

    private static String ownerOfMethodNodeAllowExt(String nodeLabel) {
        if (nodeLabel == null) return null;
        String s = nodeLabel.trim();
        if (s.startsWith("[EXT]")) s = s.substring(5).trim();
        int paren = s.indexOf('(');
        if (paren <= 0) return "[EXT]";
        int lastDot = s.lastIndexOf('.', paren);
        if (lastDot <= 0) return "[EXT]";
        return s.substring(0, lastDot).replace('$','.');
    }

    private static boolean isCallTo(MethodCallInfo call, String targetQN) {
        if (targetQN == null) return false;
        if (targetQN.equals(call.declaringType)) return true;
        return targetQN.equals(call.receiverStaticType);
    }
}
