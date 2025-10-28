package model;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {
    public String packageName;
    public String className;
    public String qualifiedName;      // si binding résolu
    public String typeKey;
    public boolean isInterface;
    public boolean isEnum;

    public String superClass;
    public List<String> superClassesChain = new ArrayList<>(); // jusqu’à Object
    public List<String> interfaces = new ArrayList<>();

    public List<FieldInfo> fields = new ArrayList<>();
    public List<MethodInfo> methods = new ArrayList<>();

    @Override
    public String toString() {
        return "ClassInfo{" + '\n' +
                "package='" + packageName + '\'' + '\n' +
                ", class='" + className + '\'' + '\n' +
                (qualifiedName != null ? ", qn='" + qualifiedName + '\'' : "") + '\n' +
                ", isInterface=" + isInterface + '\n' +
                ", isEnum=" + isEnum + '\n' +
                ", superClass='" + superClass + '\'' + '\n' +
                ", interfaces=" + interfaces + '\n' +
                ", fields=" + fields.toString() + '\n' +
                ", methods=" + methods.toString() + '\n' +
                '}';
    }

}
