package ui;

import metrics.CallGraphBuilder;
import metrics.HierarchicalClustering;
import metrics.MetricsCalculator;
import model.ClassInfo;
import model.MethodInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class MetricsUI extends JFrame {

    private final List<ClassInfo> classes;
    private MetricsCalculator.Metrics result;
    private int thresholdX;

    // Onglet "Projet"
    private final JLabel m1 = new JLabel();  // #classes
    private final JLabel m2 = new JLabel();  // LOC app
    private final JLabel m3 = new JLabel();  // #methods
    private final JLabel m4 = new JLabel();  // #packages
    private final JLabel m5 = new JLabel();  // avg methods/class
    private final JLabel m6 = new JLabel();  // avg LOC/method
    private final JLabel m7 = new JLabel();  // avg attributes/class
    private final JLabel m8 = new JLabel();
    private final JTable topByMethods = new JTable();
    private final JTable topByAttrs   = new JTable();
    private final JTable interBoth    = new JTable();
    private final JTable moreThanX    = new JTable();
    private final JTable topMethodsPerClass = new JTable();
    private final JLabel m13 = new JLabel(); // max params
    private final JSpinner spinnerX = new JSpinner(new SpinnerNumberModel(5, 0, 10_000, 1));

    // Onglet "Classes"
    private final JTable classesTable = new JTable();
    private final JTextField filterClasses = new JTextField();

    // Onglet "Méthodes"
    private final JTable methodsTable = new JTable();
    private final JTextField filterMethods = new JTextField();

    public MetricsUI(MetricsCalculator.Metrics result,
                              List<ClassInfo> classes,
                              int initialX) {
        super("HAI913I – Métriques (Projet / Classes / Méthodes)");
        this.classes = classes;
        this.result = result;
        this.thresholdX = initialX;
        spinnerX.setValue(initialX);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 820));
        setLookAndFeelNimbus();

        var tabs = new JTabbedPane();

        CallGraphPanel callGraphPanel = new CallGraphPanel(classes);
        tabs.addTab("Call Graph", callGraphPanel);

        CouplingPanel couplingPanel = new CouplingPanel(classes, (a, b) -> {
            Optional<ClassInfo> A = classes.stream().filter(c -> c.className.equals(a)).findFirst();
            Optional<ClassInfo> B = classes.stream().filter(c -> c.className.equals(b)).findFirst();
            if (A.isPresent() && B.isPresent())
                return MetricsCalculator.calculateCoupling(CallGraphBuilder.buildMethodGraph(classes, true), classes,A.get(), B.get());
            return 0;
        });

        tabs.addTab("Couplage", couplingPanel);

        CouplingWeightedGraphPanel couplingWeightedGraphPanel = new CouplingWeightedGraphPanel();

        CallGraphBuilder.DiGraph<String> gMethods =
                CallGraphBuilder.buildMethodGraph(classes, /* includeExternal */ true);

        metrics.CouplingGraphBuilder.WeightedGraph<String> gCoupling =
                metrics.CouplingGraphBuilder.buildFromCalculator(gMethods, classes);

        couplingWeightedGraphPanel.setGraph(gCoupling);

        tabs.addTab("Graphe de couplage", couplingWeightedGraphPanel);

        // Coupling provider via ta fonction existante
        HierarchicalClustering.Coupling couplingFn = (A, B) ->
                metrics.MetricsCalculator.calculateCoupling(gMethods, classes, A, B);

        metrics.HierarchicalClustering.Node root =
                metrics.HierarchicalClustering.cluster(classes, couplingFn, metrics.HierarchicalClustering.Linkage.AVERAGE);

        DendrogramPanel dendrogramPanel = new DendrogramPanel();
        dendrogramPanel.setRoot(root);

        JScrollPane scroll = new JScrollPane(dendrogramPanel);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        tabs.addTab("Dendrogram", scroll);

        tabs.addTab("Modules", new ui.ModulesPanel(classes, couplingFn));

        setContentPane(tabs);
       // fillProjectTab();
       // fillClassesTab();
       // fillMethodsTab();
        pack();
        setLocationRelativeTo(null);
    }

    // =============== Panels ===============

    private JPanel buildProjectPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Haut : résumé + X
        JPanel summary = new JPanel(new GridLayout(0, 4, 8, 4));
        summary.add(bold("1. Number of classes"));  summary.add(m1);  summary.add(bold("Seuil X (mét. 11)")); summary.add(spinnerX);
        summary.add(bold("2. Number Total of Line"));   summary.add(m2);  summary.add(new JLabel());              summary.add(btnRecalcX());
        summary.add(bold("2.1. Number Total of Useful Line (Method Line)")); summary.add(m8);summary.add(new JLabel());              summary.add(new JLabel());
        summary.add(bold("3. Number of Methods")); summary.add(m3);  summary.add(new JLabel());              summary.add(new JLabel());
        summary.add(bold("4. Number of Packages")); summary.add(m4);  summary.add(new JLabel());              summary.add(new JLabel());
        summary.add(bold("5. Moy. méthodes/cl.")); summary.add(m5); summary.add(new JLabel());              summary.add(new JLabel());
        summary.add(bold("6. Moy. LOC/méthode"));  summary.add(m6);
        summary.add(bold("7. Moy. attributs/cl."));summary.add(m7);

        // Centre : tabs de listes
        JTabbedPane lists = new JTabbedPane();
        lists.add("8. Top 10% classes (methods)", wrap(new JScrollPane(topByMethods)));
        lists.add("9. Top 10% classes (attributes)", wrap(new JScrollPane(topByAttrs)));
        lists.add("10. Intersection", wrap(new JScrollPane(interBoth)));
        lists.add("11. > X méthodes", wrap(new JScrollPane(moreThanX)));
        lists.add("12. Top 10% méthodes par LOC (par classe)", wrap(new JScrollPane(topMethodsPerClass)));

        // Bas : #13
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(bold("13. Max paramètres (toutes méthodes) : "));
        south.add(m13);

        root.add(summary, BorderLayout.NORTH);
        root.add(lists, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildClassesPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(new JLabel("Filtre (classe/package) : "));
        filterClasses.setColumns(30);
        tb.add(filterClasses);
        JButton clear = new JButton("Effacer");
        clear.addActionListener(e -> filterClasses.setText(""));
        tb.add(Box.createHorizontalStrut(8));
        tb.add(clear);

        filterClasses.getDocument().addDocumentListener(new SimpleDoc(() -> applyRowFilter(classesTable, filterClasses.getText())));

        root.add(tb, BorderLayout.NORTH);
        root.add(new JScrollPane(classesTable), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildMethodsPanel() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(new JLabel("Filtre (classe/méthode) : "));
        filterMethods.setColumns(30);
        tb.add(filterMethods);
        JButton clear = new JButton("Effacer");
        clear.addActionListener(e -> filterMethods.setText(""));
        tb.add(Box.createHorizontalStrut(8));
        tb.add(clear);

        filterMethods.getDocument().addDocumentListener(new SimpleDoc(() -> applyRowFilter(methodsTable, filterMethods.getText())));

        root.add(tb, BorderLayout.NORTH);
        root.add(new JScrollPane(methodsTable), BorderLayout.CENTER);
        return root;
    }

    // =============== Fill tabs ===============

   /* private void fillProjectTab() {
        // Résumé 1..7
        m1.setText(String.valueOf(result.numberOfClasses));
        m2.setText(String.valueOf(result.totalAppLOC));
        m3.setText(String.valueOf(result.totalMethods));
        m4.setText(String.valueOf(result.numberOfPackages));
        m5.setText(fmt2(result.avgMethodsPerClass));
        m6.setText(fmt2(result.avgLOCPerMethod));
        m7.setText(fmt2(result.avgAttributesPerClass));
        m8.setText(String.valueOf(result.totalLOCUtile));
        m13.setText(String.valueOf(result.maxParametersAcrossAllMethods));

        // Tables
        setSingleColumnTable(topByMethods, "Classe", result.top10PercentClassesByMethods);
        setSingleColumnTable(topByAttrs,   "Classe", result.top10PercentClassesByAttributes);
        setSingleColumnTable(interBoth,    "Classe", result.intersectionTopClasses);
        setSingleColumnTable(moreThanX,    "Classe", result.classesWithMoreThanXMethods);

        // 12. Map classe -> top10% méthodes par LOC
        DefaultTableModel m = new DefaultTableModel(new Object[]{"Classe", "Méthode(s)"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        result.top10PercentMethodsByLOCPerClass.forEach((cls, list) -> {
            m.addRow(new Object[]{cls, String.join("  |  ", list)});
        });
        topMethodsPerClass.setModel(m);
        topMethodsPerClass.setRowHeight(22);
        topMethodsPerClass.setAutoCreateRowSorter(true);
        prefWidth(topMethodsPerClass, 0, 320);
        prefWidth(topMethodsPerClass, 1, 800);
    }*/

   /* private void fillClassesTab() {
        // Colonnes : Classe, Package, #Méthodes, #Attributs, InTop10#M, InTop10#A, InBoth, >X
        String[] cols = {"Classe", "Package", "#Méthodes", "#Attributs", "Top10% #M", "Top10% #A", "Dans les 2", "> X méthodes"};
        Set<String> topM = new HashSet<>(result.top10PercentClassesByMethods);
        Set<String> topA = new HashSet<>(result.top10PercentClassesByAttributes);
        Set<String> both = new HashSet<>(result.intersectionTopClasses);
        Set<String> gtX  = new HashSet<>(result.classesWithMoreThanXMethods);

        Object[][] data = new Object[classes.size()][cols.length];
        for (int i = 0; i < classes.size(); i++) {
            ClassInfo ci = classes.get(i);
            String qn = qnOf(ci);
            data[i][0] = qn;
            data[i][1] = ci.packageName;
            data[i][2] = ci.methods.size();
            data[i][3] = ci.fields.size();
            data[i][4] = topM.contains(qn);
            data[i][5] = topA.contains(qn);
            data[i][6] = both.contains(qn);
            data[i][7] = gtX.contains(qn);
        }
        DefaultTableModel model = new DefaultTableModel(data, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return switch (c) {
                    case 2,3 -> Integer.class;
                    case 4,5,6,7 -> Boolean.class;
                    default -> String.class;
                };
            }
        };
        classesTable.setModel(model);
        classesTable.setAutoCreateRowSorter(true);
        classesTable.setRowHeight(22);
        prefWidth(classesTable, 0, 380);
        prefWidth(classesTable, 1, 220);
    }

    private void fillMethodsTab() {
        // Colonnes : Classe, Méthode (sig simple), LOC (corps), #params
        String[] cols = {"Classe", "Méthode", "LOC", "#params"};
        List<Object[]> rows = new ArrayList<>();
        for (ClassInfo ci : classes) {
            String qn = qnOf(ci);
            for (MethodInfo mi : ci.methods) {
                String sig = mi.name + "(" + String.join(",", mi.parameterTypes) + ")";
                rows.add(new Object[]{ qn, sig, mi.loc, mi.parametersCount });
            }
        }
        DefaultTableModel model = new DefaultTableModel(rows.toArray(new Object[0][]), cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {
                return (c == 2 || c == 3) ? Integer.class : String.class;
            }
        };
        methodsTable.setModel(model);
        methodsTable.setAutoCreateRowSorter(true);
        methodsTable.setRowHeight(22);
        prefWidth(methodsTable, 0, 380);
        prefWidth(methodsTable, 1, 420);
    }*/

    // =============== Actions ===============

    private JButton btnRecalcX() {
        JButton b = new JButton("Recalculer (11)");
        b.addActionListener(e -> {
            thresholdX = (Integer) spinnerX.getValue();
            // Recalcule juste la métrique 11 localement, le reste reste identique
            var classesWithMoreThanX = classes.stream()
                    .filter(ci -> ci.methods.size() > thresholdX)
                    .map(this::qnOf)
                    .sorted()
                    .collect(Collectors.toList());
            setSingleColumnTable(moreThanX, "Classe", classesWithMoreThanX);
        });
        return b;
    }

    // =============== Helpers ===============

    private static String fmt2(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    private static void setSingleColumnTable(JTable t, String header, List<String> rows) {
        DefaultTableModel m = new DefaultTableModel(new Object[]{header}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        if (rows != null) rows.forEach(s -> m.addRow(new Object[]{s}));
        t.setModel(m);
        t.setRowHeight(22);
        t.setAutoCreateRowSorter(true);
    }

    private static JPanel wrap(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static void prefWidth(JTable t, int col, int width) {
        TableColumn tc = t.getColumnModel().getColumn(col);
        tc.setPreferredWidth(width);
    }

    private void applyRowFilter(JTable table, String query) {
        var sorter = (javax.swing.table.TableRowSorter<?>) table.getRowSorter();
        if (sorter == null) {
            table.setAutoCreateRowSorter(true);
            sorter = (javax.swing.table.TableRowSorter<?>) table.getRowSorter();
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) { sorter.setRowFilter(null); return; }
        sorter.setRowFilter(new javax.swing.RowFilter<TableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends TableModel, ? extends Integer> e) {
                for (int i = 0; i < e.getValueCount(); i++) {
                    Object v = e.getValue(i);
                    if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(q)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private JLabel bold(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private void setLookAndFeelNimbus() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    SwingUtilities.updateComponentTreeUI(this);
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    private String qnOf(ClassInfo ci) {
        if (ci.qualifiedName != null && !ci.qualifiedName.isEmpty()) return ci.qualifiedName;
        return (ci.packageName != null && !ci.packageName.isEmpty())
                ? ci.packageName + "." + ci.className
                : ci.className;
    }

    // Petit listener de texte
    private static class SimpleDoc implements javax.swing.event.DocumentListener {
        private final Runnable on;
        SimpleDoc(Runnable on) { this.on = on; }
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { on.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { on.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { on.run(); }
    }

    // API statique
    public static void show(MetricsCalculator.Metrics res,
                            List<ClassInfo> classes,
                            int initialX) {
        SwingUtilities.invokeLater(() -> new MetricsUI(res, classes, initialX).setVisible(true));
    }
}
