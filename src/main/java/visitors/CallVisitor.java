package visitors;

import model.ClassInfo;
import model.MethodCallInfo;
import model.MethodInfo;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class CallVisitor extends ASTVisitor {

    private final Map<String, ClassInfo> classesByKey;
    private final Map<String, MethodInfo> methodsByKey;

    private final Deque<String> currentMethodKey = new ArrayDeque<>();
    private final Deque<String> currentClassKey  = new ArrayDeque<>();

    public CallVisitor(Map<String, ClassInfo> classesByKey, Map<String, MethodInfo> methodsByKey) {
        this.classesByKey = classesByKey;
        this.methodsByKey = methodsByKey;
    }


    @Override
    public boolean visit(TypeDeclaration node) {
        ITypeBinding tb = node.resolveBinding();
        String key = (tb != null) ? tb.getKey()
                : "NO_BINDING:" + node.getName().getIdentifier();
        currentClassKey.push(key);
        return true;
    }

    @Override
    public void endVisit(TypeDeclaration node) {
        if (!currentClassKey.isEmpty()) currentClassKey.pop();
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        IMethodBinding mb = node.resolveBinding();
        String mKey = (mb != null) ? mb.getKey() : null;

        if (mKey == null) {
            String classKey = currentClassKey.peek();
            String name = node.getName().getIdentifier();
            mKey = "NO_BINDING:" + classKey + "#" + name + "(" + node.parameters().size() + ")";
        }

        currentMethodKey.push(mKey);
        return true;
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        if (!currentMethodKey.isEmpty()) currentMethodKey.pop();
    }

    // --- Collecte des appels ---

    @Override
    public boolean visit(MethodInvocation node) {
        if (currentMethodKey.isEmpty()) return false;

        MethodCallInfo call = new MethodCallInfo();
        call.name = node.getName().getIdentifier();

        IMethodBinding mb = node.resolveMethodBinding();
        if (mb != null) {
            IMethodBinding d = mb.getMethodDeclaration();
            call.declaringType = (d.getDeclaringClass() != null) ? d.getDeclaringClass().getQualifiedName() : null;
            call.qualifiedSignature = qualifiedSignatureOf(d);
            call.methodKey = d.getKey();
        }


        // récepteur statique
        String recv = resolveReceiverType(node.getExpression());
        if (recv == null) {
            // appel implicite: this
            ClassInfo ci = currentClassKey.isEmpty() ? null : classesByKey.get(currentClassKey.peek());
            recv = (ci != null && ci.qualifiedName != null) ? ci.qualifiedName :
                    (ci != null ? ci.className : null);
        }
        call.receiverStaticType = recv;

        MethodInfo where = methodsByKey.get(currentMethodKey.peek());
        if (where != null) where.calls.add(call);

        return true;
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        if (currentMethodKey.isEmpty()) return false;

        MethodCallInfo call = new MethodCallInfo();
        call.name = node.getName().getIdentifier();

        IMethodBinding mb = node.resolveMethodBinding();
        if (mb != null) {
            call.declaringType = (mb.getDeclaringClass() != null) ? mb.getDeclaringClass().getQualifiedName() : null;
            call.qualifiedSignature = qualifiedSignatureOf(mb);
        }

        // récepteur = super-classe courante
        ClassInfo ci = currentClassKey.isEmpty() ? null : classesByKey.get(currentClassKey.peek());
        call.receiverStaticType = (ci != null && ci.superClass != null) ? ci.superClass : "java.lang.Object";

        MethodInfo where = methodsByKey.get(currentMethodKey.peek());
        if (where != null) where.calls.add(call);
        return false;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        if (currentMethodKey.isEmpty()) return false;

        MethodCallInfo call = new MethodCallInfo();
        call.name = "<init>";

        IMethodBinding mb = node.resolveConstructorBinding();
        if (mb != null) {
            call.declaringType = (mb.getDeclaringClass() != null) ? mb.getDeclaringClass().getQualifiedName() : null;
            call.qualifiedSignature = qualifiedSignatureOf(mb);
        }

        ITypeBinding tb = (node.getType() != null) ? node.getType().resolveBinding() : null;
        call.receiverStaticType = (tb != null) ? tb.getQualifiedName() : (node.getType() != null ? node.getType().toString() : null);

        MethodInfo where = methodsByKey.get(currentMethodKey.peek());
        if (where != null) where.calls.add(call);
        return false;
    }

    @Override
    public boolean visit(ConstructorInvocation node) { // this(...)
        if (currentMethodKey.isEmpty()) return false;

        MethodCallInfo call = new MethodCallInfo();
        call.name = "<init>";
        IMethodBinding mb = node.resolveConstructorBinding();
        if (mb != null) {
            call.declaringType = (mb.getDeclaringClass() != null) ? mb.getDeclaringClass().getQualifiedName() : null;
            call.qualifiedSignature = qualifiedSignatureOf(mb);
            call.receiverStaticType = call.declaringType;
        } else if (!currentClassKey.isEmpty()) {
            ClassInfo ci = classesByKey.get(currentClassKey.peek());
            call.receiverStaticType = (ci != null) ? ci.qualifiedName : null;
        }

        MethodInfo where = methodsByKey.get(currentMethodKey.peek());
        if (where != null) where.calls.add(call);
        return false;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) { // super(...)
        if (currentMethodKey.isEmpty()) return false;

        MethodCallInfo call = new MethodCallInfo();
        call.name = "<init>";
        IMethodBinding mb = node.resolveConstructorBinding();
        if (mb != null) {
            call.declaringType = (mb.getDeclaringClass() != null) ? mb.getDeclaringClass().getQualifiedName() : null;
            call.qualifiedSignature = qualifiedSignatureOf(mb);
            call.receiverStaticType = call.declaringType;
        } else if (!currentClassKey.isEmpty()) {
            ClassInfo ci = classesByKey.get(currentClassKey.peek());
            call.receiverStaticType = (ci != null) ? ci.superClass : null;
        }

        MethodInfo where = methodsByKey.get(currentMethodKey.peek());
        if (where != null) where.calls.add(call);
        return false;
    }

    // ---- helpers ----

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
                .reduce((a,b) -> a + "," + b).orElse("");
        String ret = mb.isConstructor() ? "" : "->" + mb.getReturnType().getErasure().getQualifiedName();
        return owner + "." + name + "(" + params + ")" + ret;
    }

    /**
     * Méthode utile pour résoudre le type du receveur lorsque celui-ci peut etre un appel statique ou this par exemple
     * @param expr
     * @return
     */
    private static String resolveReceiverType(Expression expr) {
        if (expr == null) return null;

        // expr.m(...) -> type de expr
        ITypeBinding tb = expr.resolveTypeBinding();
        if (tb != null) return tb.getQualifiedName();

        // cas TypeName.staticMethod()
        if (expr instanceof Name) {
            IBinding b = ((Name) expr).resolveBinding();
            if (b instanceof ITypeBinding) {
                return ((ITypeBinding) b).getQualifiedName();
            }
        }
        return null;
    }
}
