import metrics.MetricsCalculator;
import model.ClassInfo;
import model.MethodInfo;
import ui.MetricsUI;
import visitors.*;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ASTAnalyzer {

    public static final String projectPath = "/home/loris/Documents/S9/Evolution et Restructuration des logiciels/org.anonbnr.design_patterns";
    public static final String projectSourcePath = projectPath + "/src";

    private static final String JAVA_HOME = "/home/loris/.jdks/corretto-17.0.16";
    private static final String[] EXTRA_CLASSPATH = new String[]{
            JAVA_HOME + "/jmods/java.base.jmod",
            JAVA_HOME + "/jmods/java.desktop.jmod",
            JAVA_HOME + "/jmods/java.logging.jmod",
            JAVA_HOME + "/jmods/java.xml.jmod",
            JAVA_HOME + "/jmods/java.sql.jmod",
            JAVA_HOME + "/jmods/java.management.jmod",
            JAVA_HOME + "/jmods/java.naming.jmod",
            JAVA_HOME + "/jmods/java.net.http.jmod"
    };

    private static List<ClassInfo> infos = new ArrayList<>();

    private static Map<String, Integer> filesLOC = new HashMap<>();
    private static Set<String> packagesSet = new HashSet<>();

    public static List<ClassInfo> analyze(String unitName, String source,
                                          String[] classpath, String[] sourcepath) {

        CompilationUnit cu = parse(unitName, source.toCharArray(), classpath, sourcepath);

        int fileLOC = source.isEmpty() ? 0 : cu.getLineNumber(source.length() - 1);
        filesLOC.put(source.toString(), fileLOC);

        PackageDeclaration packageDeclaration = cu.getPackage();
        if (packageDeclaration != null) {
            String packages = packageDeclaration.getName().getFullyQualifiedName();
            if (packages != null) packagesSet.add(packages);
        }

        ClassDeclVisitor v1 = new ClassDeclVisitor();
        cu.accept(v1);
        Map<String, ClassInfo> classesByKey = v1.getClassesByKey();

        FieldVisitor v2 = new FieldVisitor(classesByKey);
        cu.accept(v2);

        MethodDeclVisitor v3 = new MethodDeclVisitor(classesByKey, cu);
        cu.accept(v3);
        Map<String, MethodInfo> methodsByKey = v3.getMethodsByKey();

        CallVisitor v4 = new CallVisitor(classesByKey, methodsByKey);
        cu.accept(v4);

        return new ArrayList<>(classesByKey.values());
    }

    private static CompilationUnit parse(String unitName, char[] classSource,
                                         String[] classpath, String[] sourcepath) {

        ASTParser parser = ASTParser.newParser(AST.JLS4);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);

        @SuppressWarnings("rawtypes")
        Map options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options); // ou VERSION_1_8 si Java 8
        parser.setCompilerOptions(options);

        parser.setUnitName((unitName != null && !unitName.isEmpty()) ? unitName : "Unit.java");
        parser.setSource(classSource);

        String[] cp = (classpath != null) ? classpath : new String[0];
        String[] sp = (sourcepath != null && sourcepath.length > 0)
                ? sourcepath
                : new String[]{projectSourcePath};
        String[] enc = (sp.length > 0) ? new String[]{"UTF-8"} : null;

        parser.setEnvironment(cp, sp, enc, true);

        return (CompilationUnit) parser.createAST(null);
    }

    // ===========================
    // Parcours du projet
    // ===========================
    public static void main(String[] args) throws IOException {
        runProject();
    }

    private static void runProject() throws IOException {
        Path root = Paths.get(projectSourcePath);
        if (!Files.isDirectory(root)) {
            System.err.println("Dossier source introuvable: " + projectSourcePath);
            return;
        }

        String[] classpath = EXTRA_CLASSPATH;

        String[] sourcepath = new String[]{projectSourcePath};

        Map<String, ClassInfo> byQualifiedName = new LinkedHashMap<>();

        try (Stream<Path> files = Files.walk(root)) {
            List<Path> javaFiles = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path p : javaFiles) {
                String code = Files.readString(p, StandardCharsets.UTF_8);
                String unitName = root.relativize(p).toString().replace('\\', '/'); // utile pour résolutions relatives

                infos.addAll(analyze(unitName, code, classpath, sourcepath));

                for (ClassInfo ci : infos) {
                    /*String key = (ci.qualifiedName != null && !ci.qualifiedName.isEmpty())
                            ? ci.qualifiedName
                            : (ci.packageName + "." + ci.className);

                    byQualifiedName.putIfAbsent(key, ci);*/
                  //  System.out.println(ci.toString());
                }
            }
        }

        MetricsCalculator.Metrics result = MetricsCalculator.compute(infos, filesLOC, packagesSet);
        MetricsUI.show(result, infos, 5);

        // Affichage simple : classes -> méthodes -> appels
       /* for (Map.Entry<String, ClassInfo> e : byQualifiedName.entrySet()) {
            ClassInfo ci = e.getValue();
            String title = (ci.qualifiedName != null) ? ci.qualifiedName
                    : (ci.packageName + "." + ci.className);
            System.out.println("=== " + title + " ===");
            if (ci.superClass != null) System.out.println("  extends: " + ci.superClass);
            if (!ci.interfaces.isEmpty()) System.out.println("  implements: " + ci.interfaces);

            ci.methods.forEach(m -> {
                System.out.println("  - " + m.visibility + " " + (m.returnType != null ? m.returnType : "void")
                        + " " + m.name + "(" + String.join(", ", m.parameterTypes) + ")");
                if (!m.calls.isEmpty()) {
                    m.calls.forEach(c ->
                            System.out.println("      -> " + c.name
                                    + "  recv=" + c.receiverStaticType
                                    + "  decl=" + c.declaringType
                                    + "  sig=" + c.qualifiedSignature)
                    );
                }
            });
            System.out.println();
        }*/
    }
}
