package visitors;

import model.ClassInfo;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class ClassDeclVisitor extends ASTVisitor {

    // Map pivot : typeKey -> ClassInfo
    private final Map<String, ClassInfo> classesByKey = new LinkedHashMap<>();

    public Map<String, ClassInfo> getClassesByKey() {
        return classesByKey;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        ITypeBinding binding = node.resolveBinding();

        ClassInfo ci = new ClassInfo();
        ci.className = node.getName().getIdentifier();

        CompilationUnit cu = (CompilationUnit) node.getRoot();
        ci.packageName = (cu.getPackage() != null)
                ? cu.getPackage().getName().getFullyQualifiedName()
                : "";

        if (binding != null) {
            ci.qualifiedName = binding.getQualifiedName();
            ci.typeKey = binding.getKey();
            ci.isInterface = binding.isInterface();
            ci.isEnum = binding.isEnum();

            ITypeBinding superB = binding.getSuperclass();
            ci.superClass = (superB != null) ? superB.getQualifiedName() : null;

            for (ITypeBinding t = superB; t != null; t = t.getSuperclass()) {
                ci.superClassesChain.add(t.getQualifiedName());
            }

            for (ITypeBinding itf : binding.getInterfaces()) {
                ci.interfaces.add(itf.getQualifiedName());
            }
        } else {
            Type superT = node.getSuperclassType();
            ci.superClass = (superT != null) ? superT.toString() : null;
            ci.typeKey = "NO_BINDING:" + ci.packageName + "." + ci.className;
        }

        classesByKey.put(ci.typeKey, ci);
        return true;
    }
}
