package ui;

import metrics.HierarchicalClustering;
import metrics.ModuleIdentifier;
import model.ClassInfo;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ModulesPanel extends JPanel {

    private final List<ClassInfo> classes;
    private final HierarchicalClustering.Coupling couplingFn;

    // UI
    private final JComboBox<HierarchicalClustering.Linkage> cbLinkage =
            new JComboBox<>(HierarchicalClustering.Linkage.values());
    private final JSpinner spCP = new JSpinner(new SpinnerNumberModel(0.05, 0.0, 1.0, 0.01));
    private final JButton btnRun = new JButton("Recalculer");
    private final JLabel lblInfo = new JLabel("—");

    private final DendrogramPanel dendro = new DendrogramPanel();
    private final JTable table = new JTable();
    private final ModulesTableModel tableModel = new ModulesTableModel();

    // état courant
    private HierarchicalClustering.Node root;
    private ModuleIdentifier.Result result;

    public ModulesPanel(List<ClassInfo> classes,
                        HierarchicalClustering.Coupling couplingFn) {
        this.classes = Objects.requireNonNull(classes);
        this.couplingFn = Objects.requireNonNull(couplingFn);
        buildUI();
        runClustering(); // premier calcul
    }

    private void buildUI() {
        setLayout(new BorderLayout(8, 8));

        // Toolbar
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        tools.add(new JLabel("Linkage:"));
        tools.add(cbLinkage);
        tools.add(new JLabel("CP (seuil):"));
        JSpinner.NumberEditor ed = new JSpinner.NumberEditor(spCP, "0.000");
        spCP.setEditor(ed);
        tools.add(spCP);
        tools.add(btnRun);
        tools.add(lblInfo);
        add(tools, BorderLayout.NORTH);

        // Centre : dendrogramme (scrollable) + table (à droite)
        JScrollPane scrollD = new JScrollPane(dendro);
        scrollD.getHorizontalScrollBar().setUnitIncrement(16);
        scrollD.getVerticalScrollBar().setUnitIncrement(16);

        table.setModel(tableModel);
        table.setAutoCreateRowSorter(true);
        JScrollPane scrollT = new JScrollPane(table);
        scrollT.setPreferredSize(new Dimension(420, 300));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollD, scrollT);
        split.setResizeWeight(0.75);
        add(split, BorderLayout.CENTER);

        // Actions
        btnRun.addActionListener(e -> runClustering());
    }

    private void runClustering() {
        double CP = ((Number) spCP.getValue()).doubleValue();
        HierarchicalClustering.Linkage linkage = (HierarchicalClustering.Linkage) cbLinkage.getSelectedItem();

        // 1) dendrogramme
        root = HierarchicalClustering.cluster(classes, couplingFn, linkage);
        dendro.setRoot(root);

        // 2) identification modules
        result = ModuleIdentifier.identify(root, couplingFn, CP);

        // 3) couleurs par module (feuilles)
        Map<String, Color> colorByFqn = colorize(result.modules);
        dendro.setLeafColors(colorByFqn);

        // 4) table
        tableModel.setData(result.modules, result.avgCouplings);

        // 5) info
        String feas = result.feasible ? "OK" : "infeasible (borne M/2 atteinte)";
        lblInfo.setText("Modules: " + result.modules.size() + "  |  " + feas);
    }

    private Map<String, Color> colorize(List<Set<ClassInfo>> modules) {
        // palette simple distincte
        Color[] palette = new Color[]{
                new Color(0x4CA3FF), new Color(0xFF7A59), new Color(0x65D46E),
                new Color(0xC792EA), new Color(0xF2C94C), new Color(0x56CCF2),
                new Color(0xEB5757), new Color(0x6FCF97), new Color(0xBB6BD9),
                new Color(0xF2994A)
        };
        Map<String, Color> map = new HashMap<>();
        int k = 0;
        for (Set<ClassInfo> mod : modules) {
            Color c = palette[k % palette.length];
            for (ClassInfo ci : mod) {
                String fqn = qnOf(ci);
                map.put(fqn, c);
            }
            k++;
        }
        return map;
    }

    private static String qnOf(ClassInfo ci) {
        if (ci.qualifiedName != null && !ci.qualifiedName.isEmpty()) return ci.qualifiedName;
        if (ci.packageName != null && !ci.packageName.isEmpty()) return ci.packageName + "." + ci.className;
        return ci.className;
    }

    // ---- Table model ----
    private static final class ModulesTableModel extends AbstractTableModel {
        private final String[] cols = {"#", "Taille", "Moy. couplage", "Classes"};
        private List<Set<ClassInfo>> modules = List.of();
        private List<Double> avgs = List.of();

        public void setData(List<Set<ClassInfo>> modules, List<Double> avgs) {
            this.modules = modules != null ? modules : List.of();
            this.avgs = avgs != null ? avgs : List.of();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return modules.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int col) { return cols[col]; }

        @Override public Object getValueAt(int row, int col) {
            Set<ClassInfo> mod = modules.get(row);
            switch (col) {
                case 0: return row + 1;
                case 1: return mod.size();
                case 2: return String.format("%.3f", avgs.get(row));
                case 3:
                    return mod.stream()
                            .map(ci -> (ci.qualifiedName != null && !ci.qualifiedName.isEmpty())
                                    ? ci.qualifiedName
                                    : (ci.packageName != null && !ci.packageName.isEmpty()
                                    ? ci.packageName + "." + ci.className
                                    : ci.className))
                            .sorted()
                            .collect(Collectors.joining(", "));
                default: return "";
            }
        }
        @Override public Class<?> getColumnClass(int col) {
            return col==0 || col==1 ? Integer.class : String.class;
        }
    }
}
