package visitors;

import model.ClassInfo;
import model.MethodInfo;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.stream.Collectors;

public class MethodDeclVisitor extends ASTVisitor {

    private final Map<String, ClassInfo> classesByKey;
    private final Map<String, MethodInfo> methodsByKey = new LinkedHashMap<>();
    private final CompilationUnit cu;

    public MethodDeclVisitor(Map<String, ClassInfo> classesByKey, CompilationUnit cu) {
        this.classesByKey = classesByKey;
        this.cu = cu;
    }

    public Map<String, MethodInfo> getMethodsByKey() {
        return methodsByKey;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        // classe parente
        ASTNode parent = node.getParent();
        while (parent != null && !(parent instanceof TypeDeclaration)) {
            parent = parent.getParent();
        }
        if (!(parent instanceof TypeDeclaration)) return false;

        ITypeBinding ownerB = ((TypeDeclaration) parent).resolveBinding();
        String classKey = (ownerB != null) ? ownerB.getKey()
                : "NO_BINDING:" + ((TypeDeclaration) parent).getName().getIdentifier();

        ClassInfo ci = classesByKey.get(classKey);
        if (ci == null) return false;

        MethodInfo mi = new MethodInfo();
        mi.name = node.getName().getIdentifier();
        mi.visibility = visibilityOf(node.modifiers());
        if (node.getReturnType2() != null) mi.returnType = node.getReturnType2().toString();

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = node.parameters();
        mi.parameterTypes = params.stream()
                .map(p -> p.getType().toString())
                .collect(Collectors.toList());

        mi.parametersCount = params.size();

        IMethodBinding mb = node.resolveBinding();
        if (mb != null) {
            IMethodBinding d = mb.getMethodDeclaration();
            mi.methodKey = d.getKey();
            mi.declaringType = (d.getDeclaringClass() != null) ? d.getDeclaringClass().getQualifiedName() : null;
            mi.qualifiedSignature = qualifiedSignatureOf(d);
            methodsByKey.put(mi.methodKey, mi);
        } else {
            mi.methodKey = "NO_BINDING:" + ci.qualifiedName + "#" + mi.name + "(" +
                    String.join(",", mi.parameterTypes) + ")";
            methodsByKey.put(mi.methodKey, mi);
        }

        Block body = node.getBody();
        mi.loc = (body != null) ? methodLOC(cu, body) : 0;

        ci.methods.add(mi);
        return true;
    }

    /**
     * Map la visibilité d'un attribut avec une chaine relative associée
     * @param modifiers
     * @return
     */
    @SuppressWarnings("unchecked")
    private static String visibilityOf(List<?> modifiers) {
        for (Object m : modifiers) {
            if (m instanceof Modifier) {
                Modifier mod = (Modifier) m;
                if (mod.isPublic()) return "public";
                if (mod.isProtected()) return "protected";
                if (mod.isPrivate()) return "private";
            }
        }
        return "package-private";
    }

    /**
     * Construit la signature qualifiée complète d'une méthode à partir de son binding JDT.
     * <p>
     * Cette méthode permet d'obtenir une représentation textuelle normalisée d'une méthode
     * Java en incluant le nom complet de la classe déclarante, le nom de la méthode,
     * la liste des types de paramètres (érodés) et le type de retour.
     * Elle est utilisée pour identifier de manière unique une méthode au sein du projet,
     * notamment lors de la construction du graphe d’appel.
     * </p>
     *
     * <p><b>Format retourné :</b><br>
     * <code>owner.methodName(T1,T2,...)->ReturnType</code><br>
     * ou pour un constructeur : <code>owner.&lt;init&gt;(T1,T2,...)</code>
     * </p>
     *
     * <p><b>Exemples :</b><br>
     * <code>com.example.MyClass.doSomething(java.lang.String,int)->void</code><br>
     * <code>com.example.MyClass.&lt;init&gt;(int)</code>
     * </p>
     *
     * @param mb l’instance de {@link IMethodBinding} représentant la méthode analysée.
     * @return une chaîne de caractères correspondant à la signature qualifiée de la méthode.
     *         Si la classe déclarante est inconnue, la chaîne commencera par "<unknown>".
     */
    private static String qualifiedSignatureOf(IMethodBinding mb) {
        String owner = (mb.getDeclaringClass() != null) ? mb.getDeclaringClass().getQualifiedName() : "<unknown>";
        String name  = mb.isConstructor() ? "<init>" : mb.getName();
        String params = Arrays.stream(mb.getParameterTypes())
                .map(t -> t.getErasure().getQualifiedName())
                .collect(Collectors.joining(","));
        String ret = mb.isConstructor() ? "" : "->" + mb.getReturnType().getErasure().getQualifiedName();
        return owner + "." + name + "(" + params + ")" + ret;
    }

    /**
     * Compte le nombre de ligne de code du corps d'une méthode
     * @param cu
     * @param body
     * @return
     */
    private static int methodLOC(CompilationUnit cu, Block body) {
        int start = body.getStartPosition();
        int end   = start + body.getLength() - 1;
        int startLine = cu.getLineNumber(start); // 1-based
        int endLine   = cu.getLineNumber(end);   // 1-based
        return Math.max(0, endLine - startLine + 1);
    }
}
