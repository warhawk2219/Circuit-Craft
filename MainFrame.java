import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * The application window: grouped toolbar, the circuit canvas, a live
 * truth-table + waveform side panel (each independently floatable into
 * its own resizable window), and a status bar. Supports light/dark theme,
 * undo/redo, save/load, and PNG/CSV export.
 */
public class MainFrame extends JFrame {

    private final CircuitPanel circuitPanel = new CircuitPanel();
    private final TruthTablePanel truthTablePanel = new TruthTablePanel();
    private final WaveformPanel waveformPanel = new WaveformPanel();
    private final JLabel statusLabel = new JLabel();
    private final JPanel statusBar = new JPanel(new BorderLayout());

    private final JPanel truthTableSlot = new JPanel(new BorderLayout());
    private final JPanel waveformSlot = new JPanel(new BorderLayout());
    private JFrame truthTableFrame;
    private JFrame waveformFrame;

    private JToolBar toolBarTop;
    private JToolBar toolBarBottom;
    private JSplitPane sideSplit;

    private PillButton infoBtn;

    private final Map<PillButton, GateType> gateButtonTypes = new LinkedHashMap<>();
    private final List<JLabel> sectionLabels = new java.util.ArrayList<>();
    private PillButton clearBtn, helpBtn, themeBtn;
    private PillButton undoBtn, redoBtn, saveBtn, loadBtn, exportPngBtn, exportCsvBtn;
    private PillButton floatTruthBtn, floatWaveBtn;
    private JLabel gridValueLabel, zoomValueLabel;

    private boolean darkMode = false;
    private Palette palette = Palette.LIGHT;

    public MainFrame() {
        super("Logic Gate Explorer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1360, 800);
        setLocationRelativeTo(null);

        circuitPanel.truthTableListener = truthTablePanel::update;
        circuitPanel.waveformListener = waveformPanel::update;
        circuitPanel.placingChangedListener = this::refreshArmedButtons;

        statusLabel.setText("Ready \u2014 pick a gate below, click the canvas to place it.");
        circuitPanel.statusUpdater = statusLabel::setText;

        toolBarTop = buildTopToolbar();
        toolBarBottom = buildBottomToolbar();
        JPanel toolbarStack = new JPanel();
        toolbarStack.setLayout(new BoxLayout(toolbarStack, BoxLayout.Y_AXIS));
        toolbarStack.add(toolBarTop);
        toolbarStack.add(toolBarBottom);

        truthTableSlot.add(truthTablePanel, BorderLayout.CENTER);
        waveformSlot.add(waveformPanel, BorderLayout.CENTER);

        sideSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, truthTableSlot, waveformSlot);
        sideSplit.setResizeWeight(0.55);
        sideSplit.setDividerSize(6);
        sideSplit.setBorder(null);
        sideSplit.setPreferredSize(new Dimension(320, 0));

        buildInfoButton();
        statusBar.add(statusLabel, BorderLayout.CENTER);
        JPanel infoWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        infoWrap.setOpaque(false);
        infoWrap.add(infoBtn);
        statusBar.add(infoWrap, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(toolbarStack, BorderLayout.NORTH);
        add(new JScrollPane(circuitPanel), BorderLayout.CENTER);
        add(sideSplit, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        applyTheme();
        circuitPanel.notifyTruthTableListener();
        circuitPanel.notifyWaveformListener();
    }

    // ---------------------------------------------------------------
    // Toolbars
    // ---------------------------------------------------------------

    private JToolBar buildTopToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        bar.add(sectionLabel("I/O"));
        addGateButton(bar, GateType.INPUT);
        addGateButton(bar, GateType.OUTPUT);
        bar.addSeparator(new Dimension(16, 0));

        bar.add(sectionLabel("GATES"));
        addGateButton(bar, GateType.NOT);
        addGateButton(bar, GateType.BUFFER);
        addGateButton(bar, GateType.AND);
        addGateButton(bar, GateType.OR);
        addGateButton(bar, GateType.XOR);
        bar.addSeparator(new Dimension(16, 0));

        bar.add(sectionLabel("COMPOUND"));
        addGateButton(bar, GateType.NAND);
        addGateButton(bar, GateType.NOR);
        addGateButton(bar, GateType.XNOR);
        bar.addSeparator(new Dimension(16, 0));

        bar.add(sectionLabel("SEQUENTIAL"));
        addGateButton(bar, GateType.CLOCK);
        addGateButton(bar, GateType.DFF);
        bar.addSeparator(new Dimension(24, 0));

        clearBtn = new PillButton("Clear All");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Clear the entire circuit?",
                    "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                circuitPanel.clearAll();
                statusLabel.setText("Circuit cleared. Ready.");
            }
        });
        bar.add(clearBtn);

        helpBtn = new PillButton("? Help");
        helpBtn.addActionListener(e -> showHelpDialog());
        bar.add(helpBtn);

        bar.add(Box.createHorizontalGlue());

        themeBtn = new PillButton("\uD83C\uDF19  Dark Mode");
        themeBtn.addActionListener(e -> toggleTheme());
        bar.add(themeBtn);

        return bar;
    }

    private JToolBar buildBottomToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        undoBtn = new PillButton("\u21B6 Undo");
        undoBtn.addActionListener(e -> circuitPanel.undo());
        bar.add(undoBtn);

        redoBtn = new PillButton("\u21B7 Redo");
        redoBtn.addActionListener(e -> circuitPanel.redo());
        bar.add(redoBtn);

        bar.addSeparator(new Dimension(16, 0));

        saveBtn = new PillButton("\uD83D\uDCBE Save");
        saveBtn.addActionListener(e -> saveCircuit());
        bar.add(saveBtn);

        loadBtn = new PillButton("\uD83D\uDCC2 Load");
        loadBtn.addActionListener(e -> loadCircuit());
        bar.add(loadBtn);

        bar.addSeparator(new Dimension(16, 0));

        exportPngBtn = new PillButton("Export PNG");
        exportPngBtn.addActionListener(e -> exportPng());
        bar.add(exportPngBtn);

        exportCsvBtn = new PillButton("Export CSV");
        exportCsvBtn.addActionListener(e -> exportCsv());
        bar.add(exportCsvBtn);

        bar.addSeparator(new Dimension(16, 0));

        bar.add(sectionLabel("GRID"));
        PillButton gridMinus = new PillButton("\u2212");
        gridMinus.addActionListener(e -> adjustGrid(-4));
        bar.add(gridMinus);
        gridValueLabel = new JLabel(circuitPanel.getGridStep() + "px");
        gridValueLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        gridValueLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        bar.add(gridValueLabel);
        PillButton gridPlus = new PillButton("+");
        gridPlus.addActionListener(e -> adjustGrid(4));
        bar.add(gridPlus);

        bar.addSeparator(new Dimension(16, 0));

        bar.add(sectionLabel("ZOOM"));
        PillButton zoomMinus = new PillButton("\u2212");
        zoomMinus.addActionListener(e -> adjustZoom(-0.1));
        bar.add(zoomMinus);
        zoomValueLabel = new JLabel(Math.round(circuitPanel.getZoom() * 100) + "%");
        zoomValueLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        zoomValueLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        bar.add(zoomValueLabel);
        PillButton zoomPlus = new PillButton("+");
        zoomPlus.addActionListener(e -> adjustZoom(0.1));
        bar.add(zoomPlus);

        bar.add(Box.createHorizontalGlue());

        floatTruthBtn = new PillButton("\u2B1A Float Truth Table");
        floatTruthBtn.addActionListener(e -> toggleFloatTruthTable());
        bar.add(floatTruthBtn);

        floatWaveBtn = new PillButton("\u2B1A Float Waveform");
        floatWaveBtn.addActionListener(e -> toggleFloatWaveform());
        bar.add(floatWaveBtn);

        return bar;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        l.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
        sectionLabels.add(l);
        return l;
    }

    private void addGateButton(JToolBar bar, GateType type) {
        PillButton btn = new PillButton(type.display);
        btn.setToolTipText("Place a " + type.name() + " gate (click again to stop)");
        btn.addActionListener(e -> {
            if (circuitPanel.getPlacingType() == type) {
                circuitPanel.setPlacingType(null);
                statusLabel.setText("Ready.");
            } else {
                circuitPanel.setPlacingType(type);
                statusLabel.setText("Placing " + type.display
                        + " \u2014 click the canvas to drop as many as you like (click the button again, right-click, or Esc to stop).");
            }
            circuitPanel.requestFocusInWindow();
        });
        gateButtonTypes.put(btn, type);
        bar.add(btn);
    }

    private void refreshArmedButtons() {
        GateType active = circuitPanel.getPlacingType();
        Color armColor = darkMode ? Color.WHITE : Color.BLACK;
        for (Map.Entry<PillButton, GateType> entry : gateButtonTypes.entrySet()) {
            entry.getKey().setArmed(entry.getValue() == active ? armColor : null);
        }
    }

    private void adjustGrid(int delta) {
        circuitPanel.setGridStep(circuitPanel.getGridStep() + delta);
        gridValueLabel.setText(circuitPanel.getGridStep() + "px");
    }

    private void adjustZoom(double delta) {
        circuitPanel.setZoom(circuitPanel.getZoom() + delta);
        zoomValueLabel.setText(Math.round(circuitPanel.getZoom() * 100) + "%");
    }

    // ---------------------------------------------------------------
    // Sidebar
    // ---------------------------------------------------------------

    private void buildInfoButton() {
        infoBtn = new PillButton("!");
        infoBtn.setToolTipText("About this project");
        infoBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        infoBtn.setPreferredSize(new Dimension(30, 30));
        infoBtn.setBorder(BorderFactory.createEmptyBorder());
        infoBtn.addActionListener(e -> showAboutDialog());
    }

    private void showAboutDialog() {
        JDialog dialog = new JDialog(this, "About", true);
        dialog.setSize(520, 580);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        content.setBackground(palette.appBg);

        JLabel title = new JLabel("CircuitCraft");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(palette.textPrimary);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Logic Gate Explorer \u2014 an interactive digital logic circuit simulator");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(palette.textSecondary);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(BorderFactory.createEmptyBorder(2, 0, 16, 0));

        JTextArea aboutText = new JTextArea(
                "CircuitCraft is a desktop application for building and simulating digital logic "
                + "circuits in real time. Place gates on a canvas, wire them together, and watch "
                + "signals propagate instantly \u2014 with a live truth table, a logic-analyzer-style "
                + "waveform view, undo/redo, save/load, and full light/dark theming, all built from "
                + "scratch in Java Swing.");
        aboutText.setWrapStyleWord(true);
        aboutText.setLineWrap(true);
        aboutText.setEditable(false);
        aboutText.setFocusable(false);
        aboutText.setOpaque(false);
        aboutText.setFont(new Font("SansSerif", Font.PLAIN, 13));
        aboutText.setForeground(palette.textPrimary);
        aboutText.setAlignmentX(Component.LEFT_ALIGNMENT);
        aboutText.setMaximumSize(new Dimension(460, 140));

        JLabel teamTitle = new JLabel("TEAM");
        teamTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        teamTitle.setForeground(palette.textPrimary);
        teamTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        teamTitle.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));

        content.add(title);
        content.add(subtitle);
        content.add(aboutText);
        content.add(teamTitle);

        String[][] members = {
                {"Gokul Sai K", "2503811710621031"},
                {"Gokul Babu S", "2503811710621032"},
                {"Hari Mukessh K B", "2503811710621033"},
                {"Hariharasudhan R B", "2503811710621034"}
        };
        for (String[] m : members) {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(460, 22));
            JLabel nameLabel = new JLabel(m[0]);
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            nameLabel.setForeground(palette.textPrimary);
            JLabel regLabel = new JLabel(m[1]);
            regLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
            regLabel.setForeground(palette.textSecondary);
            row.add(nameLabel, BorderLayout.WEST);
            row.add(regLabel, BorderLayout.EAST);
            content.add(Box.createVerticalStrut(6));
            content.add(row);
        }

        content.add(Box.createVerticalStrut(24));
        content.add(Box.createVerticalGlue());

        JLabel footer = new JLabel("EGB1201 \u2013 JAVA PROGRAMMING | LOGIC GATE EXPLORER(CIRCUITCRAFT)");
        footer.setFont(new Font("SansSerif", Font.PLAIN, 11));
        footer.setForeground(palette.textSecondary);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(footer);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(palette.appBg);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(palette.appBg);
        dialog.add(scroll, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    // ---------------------------------------------------------------
    // Save / load / export
    // ---------------------------------------------------------------

    private void saveCircuit() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save circuit");
        chooser.setFileFilter(new FileNameExtensionFilter("Logic Gate Circuit (*.lgc)", "lgc"));
        chooser.setSelectedFile(new File("circuit.lgc"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".lgc")) {
                file = new File(file.getParentFile(), file.getName() + ".lgc");
            }
            try {
                Files.write(file.toPath(), circuitPanel.serializeCircuit().getBytes(StandardCharsets.UTF_8));
                statusLabel.setText("Saved to " + file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Couldn't save the file:\n" + ex.getMessage(),
                        "Save failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void loadCircuit() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load circuit");
        chooser.setFileFilter(new FileNameExtensionFilter("Logic Gate Circuit (*.lgc)", "lgc"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                circuitPanel.loadCircuit(content);
                statusLabel.setText("Loaded " + file.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Couldn't load that file:\n" + ex.getMessage(),
                        "Load failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportPng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export circuit as PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image (*.png)", "png"));
        chooser.setSelectedFile(new File("circuit.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }
            try {
                BufferedImage img = circuitPanel.renderToImage();
                ImageIO.write(img, "png", file);
                statusLabel.setText("Exported image to " + file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Couldn't export the image:\n" + ex.getMessage(),
                        "Export failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportCsv() {
        String csv = truthTablePanel.toCsv();
        if (csv == null) {
            JOptionPane.showMessageDialog(this,
                    "There's no truth table to export yet.\nAdd INPUT and OUTPUT gates and wire them up first.",
                    "Nothing to export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export truth table as CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV file (*.csv)", "csv"));
        chooser.setSelectedFile(new File("truth_table.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new File(file.getParentFile(), file.getName() + ".csv");
            }
            try {
                Files.write(file.toPath(), csv.getBytes(StandardCharsets.UTF_8));
                statusLabel.setText("Exported truth table to " + file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Couldn't export the CSV:\n" + ex.getMessage(),
                        "Export failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---------------------------------------------------------------
    // Floating panels
    // ---------------------------------------------------------------

    private void toggleFloatTruthTable() {
        if (truthTableFrame == null) {
            truthTableSlot.removeAll();
            truthTableSlot.add(floatingPlaceholder("Truth table is floating in its own window.\nClose that window to dock it back here."),
                    BorderLayout.CENTER);
            truthTableSlot.revalidate();
            truthTableSlot.repaint();

            truthTableFrame = new JFrame("Live Truth Table");
            truthTableFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            truthTableFrame.setSize(480, 420);
            truthTableFrame.setLocationRelativeTo(this);
            truthTableFrame.add(truthTablePanel);
            truthTableFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    truthTableFrame = null;
                    truthTableSlot.removeAll();
                    truthTableSlot.add(truthTablePanel, BorderLayout.CENTER);
                    truthTableSlot.revalidate();
                    truthTableSlot.repaint();
                    floatTruthBtn.setText("\u2B1A Float Truth Table");
                }
            });
            truthTableFrame.setVisible(true);
            floatTruthBtn.setText("\u2B1A Dock Truth Table");
        } else {
            truthTableFrame.dispose();
        }
    }

    private void toggleFloatWaveform() {
        if (waveformFrame == null) {
            waveformSlot.removeAll();
            waveformSlot.add(floatingPlaceholder("Waveform is floating in its own window.\nClose that window to dock it back here."),
                    BorderLayout.CENTER);
            waveformSlot.revalidate();
            waveformSlot.repaint();

            waveformFrame = new JFrame("Live Signal Waveform");
            waveformFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            waveformFrame.setSize(700, 420);
            waveformFrame.setLocationRelativeTo(this);
            waveformFrame.add(waveformPanel);
            waveformFrame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    waveformFrame = null;
                    waveformSlot.removeAll();
                    waveformSlot.add(waveformPanel, BorderLayout.CENTER);
                    waveformSlot.revalidate();
                    waveformSlot.repaint();
                    floatWaveBtn.setText("\u2B1A Float Waveform");
                }
            });
            waveformFrame.setVisible(true);
            floatWaveBtn.setText("\u2B1A Dock Waveform");
        } else {
            waveformFrame.dispose();
        }
    }

    private JComponent floatingPlaceholder(String msg) {
        JLabel l = new JLabel("<html><body style='width:180px; text-align:center;'>"
                + msg.replace("\n", "<br>") + "</body></html>");
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setForeground(palette.textSecondary);
        l.setBorder(BorderFactory.createEmptyBorder(20, 16, 20, 16));
        return l;
    }

    // ---------------------------------------------------------------
    // Theme
    // ---------------------------------------------------------------

    private void toggleTheme() {
        darkMode = !darkMode;
        palette = darkMode ? Palette.DARK : Palette.LIGHT;
        themeBtn.setText(darkMode ? "\u2600  Light Mode" : "\uD83C\uDF19  Dark Mode");
        applyTheme();
    }

    private void applyTheme() {
        getContentPane().setBackground(palette.appBg);

        for (JToolBar bar : new JToolBar[]{toolBarTop, toolBarBottom}) {
            bar.setBackground(palette.toolbarBg);
        }
        toolBarTop.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        toolBarBottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, palette.toolbarBorder),
                BorderFactory.createEmptyBorder(6, 12, 10, 12)));

        for (PillButton b : gateButtonTypes.keySet()) b.applyColors(palette.buttonBg, palette.buttonHoverBg, palette.buttonText);
        for (PillButton b : new PillButton[]{clearBtn, helpBtn, themeBtn, undoBtn, redoBtn, saveBtn, loadBtn,
                exportPngBtn, exportCsvBtn, floatTruthBtn, floatWaveBtn}) {
            b.applyColors(palette.buttonBg, palette.buttonHoverBg, palette.buttonText);
        }
        for (JLabel l : sectionLabels) l.setForeground(palette.textSecondary);
        gridValueLabel.setForeground(palette.textPrimary);
        zoomValueLabel.setForeground(palette.textPrimary);

        statusBar.setBackground(palette.statusBarBg);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, palette.toolbarBorder),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        statusLabel.setOpaque(false);
        statusLabel.setForeground(palette.statusBarText);
        infoBtn.applyColors(palette.buttonBg, palette.buttonHoverBg, palette.buttonText);

        circuitPanel.setPalette(palette);
        truthTablePanel.setPalette(palette);
        waveformPanel.setPalette(palette);
        truthTableSlot.setBackground(palette.appBg);
        waveformSlot.setBackground(palette.appBg);
        sideSplit.setBackground(palette.appBg);

        refreshArmedButtons();
        circuitPanel.notifyTruthTableListener();
        repaint();
    }

    // ---------------------------------------------------------------
    // Help
    // ---------------------------------------------------------------

    private void showHelpDialog() {
        JLabel content = new JLabel(
                "<html><body style='width:300px; font-size:12px'>"
                + "<b>Place a gate</b><br>Click a toolbar button (it gets a bordered outline while armed), "
                + "then click the canvas as many times as you like. Click the button again, right-click, or "
                + "press Esc to stop.<br><br>"
                + "<b>Wire two gates</b><br>Drag from a gate's right-side pin to another gate's left-side pin.<br><br>"
                + "<b>Toggle a value</b><br>Click an INPUT gate to flip its switch, or a CLOCK gate to start/stop it.<br><br>"
                + "<b>Rename / set clock period</b><br>Double-click an INPUT, OUTPUT, or CLOCK gate.<br><br>"
                + "<b>Move a gate</b><br>Drag its body \u2014 it snaps to the grid.<br><br>"
                + "<b>Delete</b><br>Right-click a gate or wire, or select it and press Delete.<br><br>"
                + "<b>Undo / Redo</b><br>Ctrl+Z / Ctrl+Y, or the toolbar buttons.<br><br>"
                + "<b>Copy / Paste</b><br>Select a gate, Ctrl+C, then Ctrl+V.<br><br>"
                + "<b>Save / Load</b><br>Saves circuits as a simple, readable .lgc text file.<br><br>"
                + "<b>Zoom</b><br>Toolbar +/\u2212, or Ctrl+scroll on the canvas.<br><br>"
                + "<b>Live truth table &amp; waveform</b><br>Update automatically as you build. Use "
                + "\"Float\" to pop either one into its own resizable window \u2014 maximize it for a "
                + "full-screen view, and close the window to dock it back.<br><br>"
                + "<b>Colors</b><br>Green = logic 1, gray = logic 0."
                + "</body></html>");
        JOptionPane.showMessageDialog(this, content, "How to use Logic Gate Explorer", JOptionPane.INFORMATION_MESSAGE);
    }
}
