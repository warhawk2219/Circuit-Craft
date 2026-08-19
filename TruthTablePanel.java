import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Side panel that displays a live truth table for the circuit's INPUT and
 * OUTPUT gates. Recomputed and pushed in by CircuitPanel every time the
 * circuit changes; the row matching the circuit's current input values is
 * highlighted.
 */
public class TruthTablePanel extends JPanel {

    private final JLabel titleLabel = new JLabel("Live Truth Table");
    private final JLabel messageLabel = new JLabel();
    private final JTable table = new JTable();
    private final JScrollPane scrollPane = new JScrollPane(table);
    private Palette palette = Palette.LIGHT;
    private int currentRow = -1;
    private CircuitPanel.TruthTableSnapshot lastSnapshot;

    public TruthTablePanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));

        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 0));

        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        messageLabel.setVerticalAlignment(SwingConstants.TOP);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));

        table.setEnabled(false);
        table.setRowHeight(24);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                boolean isCurrent = row == currentRow;
                Color bg = isCurrent ? palette.tableRowCurrent
                        : (row % 2 == 0 ? palette.panelBg : palette.tableRowAlt);
                c.setBackground(bg);
                c.setForeground(isCurrent && palette == Palette.DARK ? Color.WHITE : palette.textPrimary);
                return c;
            }
        });

        add(titleLabel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(messageLabel, BorderLayout.SOUTH);

        showMessage("Add INPUT and OUTPUT gates,\nthen wire them together to see\na live truth table here.");
    }

    public void setPalette(Palette p) {
        this.palette = p;
        setBackground(p.panelBg);
        titleLabel.setForeground(p.textPrimary);
        messageLabel.setForeground(p.textSecondary);
        table.setBackground(p.panelBg);
        table.setGridColor(p.panelBorder);
        table.getTableHeader().setBackground(p.panelHeaderBg);
        table.getTableHeader().setForeground(p.textPrimary);
        scrollPane.getViewport().setBackground(p.panelBg);
        scrollPane.setBorder(BorderFactory.createLineBorder(p.panelBorder));
        repaint();
    }

    public void showMessage(String msg) {
        table.setModel(new DefaultTableModel());
        currentRow = -1;
        messageLabel.setText("<html>" + msg.replace("\n", "<br>") + "</html>");
        messageLabel.setVisible(true);
        scrollPane.setVisible(false);
        revalidate();
        repaint();
    }

    /** Called by CircuitPanel every time the circuit changes. */
    public void update(CircuitPanel.TruthTableSnapshot snap) {
        lastSnapshot = snap;
        if (snap.inputLabels.isEmpty() || snap.outputLabels.isEmpty()) {
            showMessage("Add at least one INPUT gate\nand one OUTPUT gate, then wire\nthem into your circuit.");
            return;
        }
        if (snap.rows.length == 0) {
            showMessage("Too many inputs (max 10)\nto show a live table.");
            return;
        }

        int cols = snap.inputLabels.size() + snap.outputLabels.size();
        String[] columnNames = new String[cols];
        for (int i = 0; i < snap.inputLabels.size(); i++) columnNames[i] = snap.inputLabels.get(i);
        for (int o = 0; o < snap.outputLabels.size(); o++) {
            columnNames[snap.inputLabels.size() + o] = snap.outputLabels.get(o);
        }

        Object[][] data = new Object[snap.rows.length][cols];
        for (int r = 0; r < snap.rows.length; r++) {
            for (int c = 0; c < cols; c++) {
                data[r][c] = snap.rows[r][c] ? "1" : "0";
            }
        }

        currentRow = snap.currentRowIndex;
        table.setModel(new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        });

        messageLabel.setVisible(false);
        scrollPane.setVisible(true);
        revalidate();
        repaint();
    }

    /** Returns the current truth table as CSV text, or null if there's nothing to export. */
    public String toCsv() {
        if (lastSnapshot == null || lastSnapshot.inputLabels.isEmpty()
                || lastSnapshot.outputLabels.isEmpty() || lastSnapshot.rows.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        List<String> headers = new java.util.ArrayList<>();
        headers.addAll(lastSnapshot.inputLabels);
        headers.addAll(lastSnapshot.outputLabels);
        sb.append(String.join(",", headers)).append("\n");
        for (boolean[] row : lastSnapshot.rows) {
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < row.length; c++) {
                if (c > 0) line.append(',');
                line.append(row[c] ? '1' : '0');
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
