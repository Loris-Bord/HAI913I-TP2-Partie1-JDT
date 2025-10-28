package ui;

import metrics.CouplingGraphBuilder.WeightedGraph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;

public class CouplingWeightedGraphPanel extends JPanel {
    private WeightedGraph<String> graph;
    private final Map<String, Point2D.Double> pos = new LinkedHashMap<>();

    private double minWeight = 0.0;
    private double zoom = 1.0;
    private boolean showLabels = true;
    private boolean showWeights = true; // <-- nouveau

    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
    private final JSpinner minSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1.0, 0.01));
    private final JButton btnLayout = new JButton("Relayout");
    private final JCheckBox cbLabels = new JCheckBox("Labels", true);
    private final JCheckBox cbWeights = new JCheckBox("Poids", true); // <-- nouveau
    private final JSlider zoomSlider = new JSlider(25, 300, 100);

    public CouplingWeightedGraphPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        zoomSlider.setPreferredSize(new Dimension(120, 24));
        toolbar.add(new JLabel("Min poids:"));
        toolbar.add(minSpinner);
        toolbar.add(btnLayout);
        toolbar.add(cbLabels);
        toolbar.add(cbWeights); // <-- ajouté
        toolbar.add(new JLabel("Zoom:"));
        toolbar.add(zoomSlider);
        add(toolbar, BorderLayout.NORTH);

        minSpinner.addChangeListener(e -> { minWeight = ((Number)minSpinner.getValue()).doubleValue(); repaint(); });
        cbLabels.addActionListener(e -> { showLabels = cbLabels.isSelected(); repaint(); });
        cbWeights.addActionListener(e -> { showWeights = cbWeights.isSelected(); repaint(); }); // <-- nouveau
        btnLayout.addActionListener(e -> { if (graph!=null){ initPositions(); runLayout(600); repaint(); } });
        zoomSlider.addChangeListener(e -> { zoom = zoomSlider.getValue()/100.0; repaint(); });

        MouseAdapter ma = new MouseAdapter() {
            Point last;
            @Override public void mousePressed(MouseEvent e){ last = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e){
                if (last!=null) {
                    double dx=(e.getX()-last.x)/zoom, dy=(e.getY()-last.y)/zoom;
                    for (var p : pos.values()) { p.x += dx; p.y += dy; }
                    last = e.getPoint();
                    repaint();
                }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    public void setGraph(WeightedGraph<String> g) {
        this.graph = g;
        initPositions();
        runLayout(600);
        repaint();
    }

    private void initPositions() {
        pos.clear();
        if (graph == null) return;
        int n = Math.max(1, graph.nodes().size());
        double R = 220 + 10*Math.sqrt(n);
        int i=0;
        for (String v : graph.nodes()) {
            double ang = 2*Math.PI * (i++)/n;
            pos.put(v, new Point2D.Double(R*Math.cos(ang), R*Math.sin(ang)));
        }
    }

    private void runLayout(int iterations) {
        if (graph == null) return;
        double area = 900*600;
        int n = Math.max(1, graph.nodes().size());
        double k = Math.sqrt(area / n);
        Map<String, Point2D.Double> disp = new HashMap<>();

        for (int it=0; it<iterations; it++) {
            for (String v : graph.nodes()) disp.put(v, new Point2D.Double(0,0));

            for (String v : graph.nodes()) {
                Point2D.Double pv = pos.get(v);
                for (String u : graph.nodes()) if (!u.equals(v)) {
                    Point2D.Double pu = pos.get(u);
                    double dx = pv.x - pu.x, dy = pv.y - pu.y;
                    double dist = Math.hypot(dx, dy) + 0.01;
                    double force = (k*k)/dist;
                    disp.get(v).x += (dx/dist)*force;
                    disp.get(v).y += (dy/dist)*force;
                }
            }

            for (var a : graph.edges().entrySet()) {
                String v = a.getKey();
                Point2D.Double pv = pos.get(v);
                for (var b : a.getValue().entrySet()) {
                    String u = b.getKey();
                    if (v.compareTo(u) >= 0) continue;
                    double w = b.getValue();
                    if (w < minWeight) continue;
                    Point2D.Double pu = pos.get(u);
                    double dx = pv.x - pu.x, dy = pv.y - pu.y;
                    double dist = Math.hypot(dx, dy) + 0.01;
                    double force = (dist*dist)/k;
                    double gain = 0.5 + Math.sqrt(Math.max(0,w));
                    double fx = (dx/dist)*force*gain;
                    double fy = (dy/dist)*force*gain;
                    disp.get(v).x -= fx; disp.get(v).y -= fy;
                    disp.get(u).x += fx; disp.get(u).y += fy;
                }
            }

            double t = (1.0 - (double)it/iterations) * 10.0;
            for (String v : graph.nodes()) {
                Point2D.Double p = pos.get(v), d = disp.get(v);
                double len = Math.hypot(d.x, d.y);
                if (len > 0) {
                    p.x += (d.x/len) * Math.min(len, t);
                    p.y += (d.y/len) * Math.min(len, t);
                }
            }
        }
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (graph == null) return;

        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int cx = getWidth()/2, cy = getHeight()/2;
        g.translate(cx, cy);
        g.scale(zoom, zoom);

        double maxW = 0;
        for (var m : graph.edges().values()) for (double w : m.values()) maxW = Math.max(maxW, w);
        double edgeScale = (maxW>0) ? 8.0/Math.sqrt(maxW) : 1.0;

        // === Edges + affichage des poids ===
        g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
        for (var a : graph.edges().entrySet()) {
            String v = a.getKey();
            Point2D.Double pv = pos.get(v);
            for (var b : a.getValue().entrySet()) {
                String u = b.getKey();
                if (v.compareTo(u) >= 0) continue;
                double w = b.getValue();
                if (w < minWeight) continue;
                Point2D.Double pu = pos.get(u);

                float width = (float) Math.max(0.5, Math.sqrt(w)*edgeScale);
                g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0x33,0x33,0x33, 150));
                g.draw(new Line2D.Double(pv.x, pv.y, pu.x, pu.y));

                if (showWeights) {
                    // Affiche la valeur au milieu
                    double mx = (pv.x + pu.x)/2;
                    double my = (pv.y + pu.y)/2;
                    String txt = String.format("%.3f", w); // ex: "0.024"
                    FontMetrics fm = g.getFontMetrics();
                    int tw = fm.stringWidth(txt);
                    g.setColor(new Color(255, 255, 255, 210));
                    g.fillRect((int)(mx - tw/2.0 - 2), (int)(my - fm.getAscent()/2.0 - 2), tw+4, fm.getHeight());
                    g.setColor(Color.BLACK);
                    g.drawString(txt, (float)(mx - tw/2.0), (float)(my + fm.getAscent()/2.5));
                }
            }
        }

        // === Nodes ===
        double r = 14;
        for (String v : graph.nodes()) {
            Point2D.Double p = pos.get(v);
            Shape s = new Ellipse2D.Double(p.x - r, p.y - r, 2*r, 2*r);
            g.setColor(new Color(0x4C,0xA3,0xFF));
            g.fill(s);
            g.setColor(new Color(0x1F,0x5B,0xB5));
            g.draw(s);
        }

        // === Labels des classes ===
        if (showLabels) {
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
            g.setColor(Color.BLACK);
            for (String v : graph.nodes()) {
                Point2D.Double p = pos.get(v);
                FontMetrics fm = g.getFontMetrics();
                int w = fm.stringWidth(v);
                g.drawString(v, (float)(p.x - w/2.0), (float)(p.y - r - 6));
            }
        }

        g.dispose();
    }
}
