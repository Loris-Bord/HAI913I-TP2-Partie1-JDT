package ui;

import metrics.CallGraphBuilder;
import metrics.CallGraphBuilder.DiGraph;
import model.ClassInfo;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;
import java.util.function.Function;

public class CallGraphPanel extends JPanel {

    public enum Mode { CLASSES, METHODES }

    private final List<ClassInfo> classes;

    // UI
    private final JComboBox<Mode> modeCombo = new JComboBox<>(Mode.values());
    private final JCheckBox includeExternal = new JCheckBox("Inclure externes", true);
    private final JButton buildBtn = new JButton("Construire");
    private final JLabel status = new JLabel(" ");

    private final JTable edgesTable = new JTable();
    private final GraphCanvas canvas = new GraphCanvas();

    // Données courantes
    private DiGraph<String> graph = new DiGraph<String>();

    public CallGraphPanel(List<ClassInfo> classes) {
        super(new BorderLayout(8,8));
        this.classes = classes;
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));

        // Toolbar
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(new JLabel("Niveau : "));
        tb.add(modeCombo);
        tb.add(Box.createHorizontalStrut(8));
        tb.add(includeExternal);
        tb.add(Box.createHorizontalStrut(8));
        tb.add(buildBtn);
        tb.add(Box.createHorizontalStrut(16));
        tb.add(status);

        // Split : graph (gauche) / table (droite)
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrap(canvas), new JScrollPane(edgesTable));
        split.setResizeWeight(0.6);

        add(tb, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // Listeners
        buildBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { rebuildGraph(); }
        });

        // Construction initiale
        rebuildGraph();
    }

    private void rebuildGraph() {
        Mode mode = (Mode) modeCombo.getSelectedItem();
        boolean ext = includeExternal.isSelected();
        if (mode == Mode.METHODES) {
            this.graph = CallGraphBuilder.buildMethodGraph(classes, ext);
        } else {
            this.graph = CallGraphBuilder.buildClassGraph(classes, ext);
        }
        fillTable();
        canvas.setGraph(graph);
        status.setText("Noeuds: " + graph.nodes().size() + "  |  Arêtes: " + countEdges(graph));
    }

    private void fillTable() {
        DefaultTableModel m = new DefaultTableModel(new Object[]{"From", "To"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Map.Entry<String, Set<String>> e : graph.edges().entrySet()) {
            String from = e.getKey();
            for (String to : e.getValue()) m.addRow(new Object[]{from, to});
        }
        edgesTable.setModel(m);
        edgesTable.setAutoCreateRowSorter(true);
        edgesTable.setRowHeight(22);
        prefWidth(edgesTable, 0, 420);
        prefWidth(edgesTable, 1, 420);
    }

    private int countEdges(DiGraph<String> g) {
        int c = 0;
        for (Set<String> s : g.edges().values()) c += s.size();
        return c;
    }

    private void writeText(File f, String text) {
        try (PrintWriter out = new PrintWriter(f, "UTF-8")) {
            out.print(text);
            JOptionPane.showMessageDialog(this, "Fichier écrit : " + f.getAbsolutePath(),
                    "Export", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur export : " + ex.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void prefWidth(JTable t, int col, int w) {
        t.getColumnModel().getColumn(col).setPreferredWidth(w);
    }

    private static JComponent wrap(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    // -------------------------- Canvas de dessin --------------------------

    private static class GraphCanvas extends JComponent {
        private DiGraph<String> graph = new DiGraph<String>();
        // layout
        private final Map<String, Point2D> pos = new LinkedHashMap<String, Point2D>();
        private double zoom = 1.0;
        private double tx = 0.0, ty = 0.0; // pan
        private Point lastDrag = null;

        public GraphCanvas() {
            setBackground(Color.WHITE);
            setOpaque(true);

            addMouseWheelListener(new MouseWheelListener() {
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    int rot = e.getWheelRotation();
                    double factor = (rot > 0) ? 0.9 : 1.1;
                    zoom *= factor;
                    zoom = Math.max(0.1, Math.min(zoom, 5.0));
                    repaint();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    lastDrag = e.getPoint();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    lastDrag = null;
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    if (lastDrag != null) {
                        Point p = e.getPoint();
                        tx += (p.x - lastDrag.x) / zoom;
                        ty += (p.y - lastDrag.y) / zoom;
                        lastDrag = p;
                        repaint();
                    }
                }
            });
        }

        public void setGraph(DiGraph<String> g) {
            this.graph = g != null ? g : new DiGraph<String>();
            computeCircularLayout();
            zoom = 1.0; tx = ty = 0.0;
            repaint();
        }

        private void computeCircularLayout() {
            pos.clear();
            int n = graph.nodes().size();
            if (n == 0) return;
            // calc rayon en fonction du nbre de noeuds
            double radius = 80 + 18 * Math.sqrt(n);
            double angleStep = (2 * Math.PI) / n;
            int i = 0;
            for (String node : graph.nodes()) {
                double a = i * angleStep;
                double x = Math.cos(a) * radius;
                double y = Math.sin(a) * radius;
                pos.put(node, new Point2D(x, y));
                i++;
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            // Transform (zoom + pan) centré
            AffineTransform at = new AffineTransform();
            at.translate(w / 2.0, h / 2.0);
            at.scale(zoom, zoom);
            at.translate(tx, ty);
            g.transform(at);

            // Edges
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(0x888888));
            for (Map.Entry<String, Set<String>> e : graph.edges().entrySet()) {
                Point2D p1 = pos.get(e.getKey());
                if (p1 == null) continue;
                for (String to : e.getValue()) {
                    Point2D p2 = pos.get(to);
                    if (p2 == null) continue;
                    g.draw(new Line2D.Double(p1.x, p1.y, p2.x, p2.y));
                }
            }

            // Nodes
            FontMetrics fm = g.getFontMetrics();
            for (String n : graph.nodes()) {
                Point2D p = pos.get(n);
                if (p == null) continue;
                String label = n;
                int pad = 6;
                int tw = fm.stringWidth(label) + pad * 2;
                int th = fm.getHeight() + pad * 2;
                int x = (int) Math.round(p.x - tw / 2.0);
                int y = (int) Math.round(p.y - th / 2.0);

                // box
                g.setColor(new Color(0xF2F6FF));
                g.fillRoundRect(x, y, tw, th, 14, 14);
                g.setColor(new Color(0x2F5DA8));
                g.drawRoundRect(x, y, tw, th, 14, 14);

                // text
                g.setColor(Color.DARK_GRAY);
                g.drawString(label, x + pad, y + pad + fm.getAscent());
            }

            g.dispose();
        }

        // petit Point2D interne
        private static class Point2D {
            final double x, y; Point2D(double x, double y) { this.x = x; this.y = y; }
        }
    }
}
