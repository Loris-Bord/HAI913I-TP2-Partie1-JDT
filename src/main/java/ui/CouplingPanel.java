package ui;

import model.ClassInfo;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.ToDoubleBiFunction;

public class CouplingPanel extends JPanel {

    private final JComboBox<String> comboA = new JComboBox<>();
    private final JComboBox<String> comboB = new JComboBox<>();
    private final JButton calcBtn = new JButton("Calculer");
    private final JButton swapBtn = new JButton("↔");
    private final JLabel resultLabel = new JLabel("—");

    // fourni par toi : (classA, classB) -> valeur du couplage
    private final ToDoubleBiFunction<String, String> couplingFunction;

    public CouplingPanel(List<ClassInfo> classes,
                         ToDoubleBiFunction<String, String> couplingFunction) {
        super(new BorderLayout(8, 8));
        this.couplingFunction = Objects.requireNonNull(couplingFunction, "couplingFunction");

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buildUI();
        loadClasses(classes);
        wireActions();
    }

    private void buildUI() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Couplage entre deux classes");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JPanel north = new JPanel(new BorderLayout());
        north.add(title, BorderLayout.WEST);

        // Ligne A
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        form.add(new JLabel("Classe A:"), c);
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        form.add(comboA, c);

        // swap
        c.gridx = 2;
        c.gridy = 0;
        c.weightx = 0;
        form.add(swapBtn, c);

        // Ligne B
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        form.add(new JLabel("Classe B:"), c);
        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1;
        form.add(comboB, c);

        // Bouton calcul
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.weightx = 0;
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(calcBtn);
        form.add(actions, c);
        c.gridwidth = 1;

        // Résultat
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel resTitle = new JLabel("Couplage(A,B) : ");
        resTitle.setFont(resTitle.getFont().deriveFont(Font.BOLD));
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 16f));
        south.add(resTitle);
        south.add(resultLabel);

        add(north, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        // tailles
        comboA.setPrototypeDisplayValue("com.example.very.long.package.AndAQuiteLongClassName");
        comboB.setPrototypeDisplayValue("com.example.very.long.package.AndAQuiteLongClassName");
    }

    private void loadClasses(List<ClassInfo> classes) {
        java.util.List<String> fqn = new ArrayList<>();
        for (ClassInfo ci : classes) {
            fqn.add(ci.className);
        }
        Collections.sort(fqn);
        DefaultComboBoxModel<String> mA = new DefaultComboBoxModel<>(fqn.toArray(new String[0]));
        DefaultComboBoxModel<String> mB = new DefaultComboBoxModel<>(fqn.toArray(new String[0]));
        comboA.setModel(mA);
        comboB.setModel(mB);
        if (fqn.size() >= 2) {
            comboA.setSelectedIndex(0);
            comboB.setSelectedIndex(1);
        }
    }

    private void wireActions() {
        calcBtn.addActionListener(e -> computeAndShow());
        swapBtn.addActionListener(e -> {
            Object a = comboA.getSelectedItem();
            Object b = comboB.getSelectedItem();
            comboA.setSelectedItem(b);
            comboB.setSelectedItem(a);
        });

        // recalcul rapide quand on change une sélection (optionnel)
        comboA.addActionListener(e -> computeAndShow());
        comboB.addActionListener(e -> computeAndShow());
    }

    private void computeAndShow() {
        String a = (String) comboA.getSelectedItem();
        String b = (String) comboB.getSelectedItem();
        if (a == null || b == null) {
            resultLabel.setText("—");
            return;
        }
        if (a.equals(b)) {
            resultLabel.setText("0.0 (même classe)");
            return;
        }
        try {
            double v = couplingFunction.applyAsDouble(a, b);
            resultLabel.setText(String.format(java.util.Locale.ROOT, "%.6f", v));
        } catch (Exception ex) {
            resultLabel.setText("Erreur: " + ex.getMessage());
        }
    }

    private static String qnOf(ClassInfo ci) {
        if (ci.qualifiedName != null && !ci.qualifiedName.isEmpty()) return ci.qualifiedName;
        if (ci.packageName != null && !ci.packageName.isEmpty()) return ci.packageName + "." + ci.className;
        return ci.className;
    }

    // Helper: pour l’ouvrir vite fait dans un JFrame
    public static void showInFrame(List<ClassInfo> classes, ToDoubleBiFunction<String, String> fn) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Couplage entre classes");
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setContentPane(new CouplingPanel(classes, fn));
            f.setSize(720, 220);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}

