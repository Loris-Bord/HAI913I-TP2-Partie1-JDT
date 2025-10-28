package model;

public class FieldInfo {
    public String name;
    public String visibility; // public/protected/private/package-private
    public String type;       // type textuel (ou FQN si souhaité)

    @Override
    public String toString() {
        return visibility + " " + type + " " + name;
    }

}
