import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A live signal-graph (like a mini logic analyzer) that traces every
 * INPUT and OUTPUT gate's value over time. A new sample is appended every
 * time the circuit is simulated (a toggle, a new wire, a placed or deleted
 * gate). Acts as a strip chart: it always shows the most recent samples
 * that fit its current width, scrolling forward automatically as new data
 * arrives \u2014 no manual horizontal scrolling needed.
 */
public class WaveformPanel extends JPanel {

    private static final int MAX_SAMPLES = 300;
    private static final int ROW_HEIGHT = 34;
    private static final int LABEL_WIDTH = 46;
    private static final int STEP_WIDTH = 16;
    private static final int TOP_PAD = 6;

    private final JLabel titleLabel = new JLabel("Live Signal Waveform");
    private final GraphCanvas canvas = new GraphCanvas();
    private final JScrollPane scrollPane;

    public WaveformPanel() {
        setLayout(new BorderLayout());

        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        add(titleLabel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(canvas);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setPalette(Palette p) {
        setBackground(p.panelBg);
        titleLabel.setForeground(p.textPrimary);
        scrollPane.getViewport().setBackground(p.panelBg);
        canvas.setPalette(p);
    }

    /** Called by CircuitPanel every time the circuit is simulated. */
    public void update(CircuitPanel.LiveSnapshot snap) {
        canvas.pushSample(snap);
    }

    /** The drawing surface. Tracks the viewport's width so it never needs horizontal scrolling. */
    private static class GraphCanvas extends JPanel implements Scrollable {

        private List<String> labels = new ArrayList<>();
        private final LinkedList<boolean[]> history = new LinkedList<>();
        private Palette palette = Palette.LIGHT;

        GraphCanvas() {
            setPreferredSize(new Dimension(200, 160));
        }

        void setPalette(Palette p) {
            this.palette = p;
            setBackground(p.panelBg);
            repaint();
        }

        void pushSample(CircuitPanel.LiveSnapshot snap) {
            if (!snap.labels.equals(labels)) {
                labels = new ArrayList<>(snap.labels);
                history.clear();
            }
            history.addLast(snap.values.clone());
            while (history.size() > MAX_SAMPLES) history.removeFirst();

            setPreferredSize(new Dimension(200, Math.max(160, TOP_PAD + labels.size() * ROW_HEIGHT + 10)));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (labels.isEmpty() || history.isEmpty()) {
                g2.setColor(palette.textSecondary);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.drawString("Waveform will appear once you add", 10, 30);
                g2.drawString("INPUT/OUTPUT gates and interact.", 10, 48);
                return;
            }

            int maxVisible = Math.max(1, (getWidth() - LABEL_WIDTH) / STEP_WIDTH);
            List<boolean[]> visible = history.size() <= maxVisible
                    ? history
                    : new ArrayList<>(history).subList(history.size() - maxVisible, history.size());

            for (int i = 0; i < labels.size(); i++) {
                int rowY = TOP_PAD + i * ROW_HEIGHT;

                if (i % 2 == 0) {
                    g2.setColor(palette.tableRowAlt);
                    g2.fillRect(0, rowY, getWidth(), ROW_HEIGHT);
                }

                g2.setColor(palette.textPrimary);
                g2.setFont(new Font("Monospaced", Font.BOLD, 11));
                g2.drawString(labels.get(i), 4, rowY + ROW_HEIGHT / 2 + 4);

                drawSignalRow(g2, visible, i, rowY);
            }

            g2.setColor(palette.panelBorder);
            for (int i = 0; i <= labels.size(); i++) {
                int y = TOP_PAD + i * ROW_HEIGHT;
                g2.drawLine(0, y, getWidth(), y);
            }
        }

        private void drawSignalRow(Graphics2D g2, List<boolean[]> visible, int rowIndex, int rowY) {
            int highY = rowY + 7;
            int lowY = rowY + ROW_HEIGHT - 9;

            g2.setColor(palette.wireOn);
            g2.setStroke(new BasicStroke(2.2f));

            Path2D path = new Path2D.Double();
            boolean firstVal = visible.get(0)[rowIndex];
            path.moveTo(LABEL_WIDTH, firstVal ? highY : lowY);

            int prevX = LABEL_WIDTH;
            boolean prevVal = firstVal;
            for (int idx = 1; idx < visible.size(); idx++) {
                int x = LABEL_WIDTH + idx * STEP_WIDTH;
                boolean val = visible.get(idx)[rowIndex];
                path.lineTo(x, prevVal ? highY : lowY);
                path.lineTo(x, val ? highY : lowY);
                prevX = x;
                prevVal = val;
            }
            path.lineTo(prevX + STEP_WIDTH, prevVal ? highY : lowY);
            g2.draw(path);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ROW_HEIGHT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return ROW_HEIGHT * 3;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
