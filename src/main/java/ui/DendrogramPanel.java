package ui;

import metrics.HierarchicalClustering.Node;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class DendrogramPanel extends JPanel {
    private Node root;
    private int leafGap = 120;   // espacement horizontal entre feuilles
    private int levelGap = 60;  // espacement vertical entre niveaux
    private boolean showHeights = true;

    private final Map<Node, Integer> leafIndex = new HashMap<>();
    private int leavesCount;

    private final Map<String, Color> colorByClass = new HashMap<>();

    public void setRoot(Node root) {
        this.root = root;
        reindexLeaves();
        revalidate(); // <-- important pour JScrollPane
        repaint();
    }

    private void reindexLeaves() {
        leafIndex.clear();
        leavesCount = 0;
        if (root == null) return;
        dfsIndex(root);
    }

    private void dfsIndex(Node n) {
        if (n.isLeaf()) {
            leafIndex.put(n, leavesCount++);
            return;
        }
        dfsIndex(n.left);
        dfsIndex(n.right);
    }

    @Override
    public Dimension getPreferredSize() {
        // largeur = nb de feuilles * leafGap + marges
        int width = Math.max(800, leavesCount * leafGap + 200);
        int height = Math.max(600, depth(root) * levelGap + 100);
        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (root == null) return;

        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.translate(100, 40); // marge gauche et haut
        drawNode(g, root);
        g.dispose();
    }

    private int depth(Node n) {
        return n == null ? 0 : n.isLeaf() ? 1 : 1 + Math.max(depth(n.left), depth(n.right));
    }

    private Point drawNode(Graphics2D g, Node n) {
        if (n.isLeaf()) {
            int idx = leafIndex.get(n);
            int x = idx * leafGap;
            int y = depth(root) * levelGap;

            String fqn = (n.label != null) ? n.label : "<unknown>";
            Color c = colorByClass.getOrDefault(fqn, new Color(0x4CA3FF));
            g.setColor(c);
            g.fillOval(x - 6, y - 6, 12, 12);
            g.setColor(c.darker());
            g.drawOval(x - 6, y - 6, 12, 12);

            String label = n.label != null ? n.label : "leaf";
            g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, y + 18);
            return new Point(x, y);
        }

        Point L = drawNode(g, n.left);
        Point R = drawNode(g, n.right);
        int y = Math.min(L.y, R.y) - levelGap;
        int x = (L.x + R.x) / 2;

        g.setColor(Color.DARK_GRAY);
        g.drawLine(L.x, L.y, L.x, y);
        g.drawLine(R.x, R.y, R.x, y);
        g.drawLine(L.x, y, R.x, y);
        g.fillRect(x - 2, y - 2, 4, 4);

        if (showHeights) {
            String h = String.format("h=%.3f", n.height);
            g.drawString(h, x - g.getFontMetrics().stringWidth(h) / 2, y - 4);
        }
        return new Point(x, y);
    }

    public void setLeafColors(Map<String, Color> colorByFqn) {
        colorByClass.clear();
        if (colorByFqn != null) colorByClass.putAll(colorByFqn);
        repaint();
    }
}
