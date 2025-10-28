package visitors;

import model.ClassInfo;
import model.FieldInfo;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Map;

public class FieldVisitor extends ASTVisitor {

    private final Map<String, ClassInfo> classesByKey;

    public FieldVisitor(Map<String, ClassInfo> classesByKey) {
        this.classesByKey = classesByKey;
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        ASTNode parent = node.getParent();
        while (parent != null && !(parent instanceof TypeDeclaration)) {
            parent = parent.getParent();
        }
        if (!(parent instanceof TypeDeclaration)) return false;

        ITypeBinding ownerB = ((TypeDeclaration) parent).resolveBinding();
        String key = (ownerB != null) ? ownerB.getKey()
                : "NO_BINDING:" + ((TypeDeclaration) parent).getName().getIdentifier();

        ClassInfo ci = classesByKey.get(key);
        if (ci == null) return false;

        String visibility = visibilityOf(node.modifiers());
        String type = node.getType().toString();

        @SuppressWarnings("unchecked")
        List<VariableDeclarationFragment> frags = node.fragments();
        for (VariableDeclarationFragment f : frags) {
            FieldInfo fi = new FieldInfo();
            fi.name = f.getName().getIdentifier();
            fi.visibility = visibility;
            fi.type = type;
            ci.fields.add(fi);
        }
        return false;
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
}
