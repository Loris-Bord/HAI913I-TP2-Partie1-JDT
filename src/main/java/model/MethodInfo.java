package model;

import java.util.ArrayList;
import java.util.List;

public class MethodInfo {
    public String name;
    public String visibility;
    public String returnType;
    public List<String> parameterTypes = new ArrayList<>();

    public String declaringType;        // FQN du type déclarant (si résolu)
    public String methodKey;
    public String qualifiedSignature;   // Owner.m(T1,T2)->R

    public List<MethodCallInfo> calls = new ArrayList<>();

    public int loc;
    public int parametersCount;

    @Override
    public String toString() {
        return visibility + " " +
                (returnType != null ? returnType : "void") +
                " " + name + "(" + String.join(", ", parameterTypes) + ")" +
                (qualifiedSignature != null ? " [" + qualifiedSignature + "]" : "") +
                " calls=" + calls.toString() + '\n';
    }

}
