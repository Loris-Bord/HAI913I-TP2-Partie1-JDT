package model;

public class MethodCallInfo {
    public String name;                 // nom simple ou "<init>"
    public String declaringType;        // FQN du type déclarant la méthode appelée
    public String qualifiedSignature;   // Owner.m(T1,T2)->R (vide R pour ctor)
    public String receiverStaticType;
    public String methodKey;

    @Override
    public String toString() {
        return "call " + name +
                (receiverStaticType != null ? " recv=" + receiverStaticType : "") +
                (declaringType != null ? " decl=" + declaringType : "") +
                (qualifiedSignature != null ? " sig=" + qualifiedSignature : "") + '\n' +
                (methodKey != null ? " key=" + methodKey : "");
    }

}
