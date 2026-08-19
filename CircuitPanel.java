import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Arc2D;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The interactive canvas where gates are placed, wired together, dragged,
 * simulated, and rendered with real IEEE-style logic gate symbols. Also
 * owns zoom, snap-to-grid, undo/redo, copy/paste, and save/load/export.
 */
public class CircuitPanel extends JPanel {

    private static final int PIN_RADIUS = 6;
    private static final double BUBBLE_R = 5.0;
    private static final int WORLD_WIDTH = 1600;
    private static final int WORLD_HEIGHT = 1200;
    private static final int MAX_UNDO = 50;

    private final List<Gate> gates = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();

    /** Set (via setPlacingType) when the user has a gate armed to place. */
    private GateType placingType = null;

    /** Optional callbacks so the UI can react to status/theme/data/tool changes. */
    public Consumer<String> statusUpdater;
    public Consumer<TruthTableSnapshot> truthTableListener;
    public Consumer<LiveSnapshot> waveformListener;
    public Runnable placingChangedListener;

    private Palette palette = Palette.LIGHT;
    private int gridStep = 24;
    private double zoom = 1.0;

    private Gate draggingGate = null;
    private Point dragOffset;
    private CircuitState dragStartState;

    private Gate wireDragSource = null;
    private Point wireDragCurrentPoint = null;

    private Gate selectedGate = null;
    private Wire selectedWire = null;
    private Gate clipboard = null;

    private final Deque<CircuitState> undoStack = new ArrayDeque<>();
    private final Deque<CircuitState> redoStack = new ArrayDeque<>();

    public CircuitPanel() {
        setBackground(palette.canvasBg);
        setPreferredSize(new Dimension(WORLD_WIDTH, WORLD_HEIGHT));
        setFocusable(true);

        MouseAdapter adapter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { handlePressed(e); }
            @Override public void mouseDragged(MouseEvent e) { handleDragged(e); }
            @Override public void mouseReleased(MouseEvent e) { handleReleased(e); }
            @Override public void mouseClicked(MouseEvent e) { handleClicked(e); }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);

        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                setZoom(zoom + (e.getWheelRotation() < 0 ? 0.1 : -0.1));
                e.consume();
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { handleKey(e); }
        });

        Timer clockTimer = new Timer(50, e -> tickClocks());
        clockTimer.start();
    }

    public void setPalette(Palette p) {
        this.palette = p;
        setBackground(p.canvasBg);
        repaint();
    }

    // ---------------------------------------------------------------
    // Placement tool state
    // ---------------------------------------------------------------

    public GateType getPlacingType() {
        return placingType;
    }

    public void setPlacingType(GateType t) {
        placingType = t;
        if (placingChangedListener != null) placingChangedListener.run();
        repaint();
    }

    // ---------------------------------------------------------------
    // Grid + zoom
    // ---------------------------------------------------------------

    public int getGridStep() {
        return gridStep;
    }

    public void setGridStep(int step) {
        gridStep = Math.max(8, Math.min(80, step));
        repaint();
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double z) {
        zoom = Math.max(0.5, Math.min(2.0, Math.round(z * 100) / 100.0));
        setPreferredSize(new Dimension((int) (WORLD_WIDTH * zoom), (int) (WORLD_HEIGHT * zoom)));
        revalidate();
        repaint();
    }

    private Point toWorld(MouseEvent e) {
        return new Point((int) Math.round(e.getX() / zoom), (int) Math.round(e.getY() / zoom));
    }

    private int snap(int v) {
        return Math.round((float) v / gridStep) * gridStep;
    }

    // ---------------------------------------------------------------
    // Interaction
    // ---------------------------------------------------------------

    private void handlePressed(MouseEvent e) {
        requestFocusInWindow();
        Point p = toWorld(e);

        if (placingType != null) {
            if (SwingUtilities.isRightMouseButton(e)) {
                setPlacingType(null);
                updateStatus("Placement cancelled. Ready.");
                return;
            }
            pushUndo();
            int gx = snap(p.x - Gate.WIDTH / 2);
            int gy = snap(p.y - Gate.HEIGHT / 2);
            Gate g = new Gate(placingType, gx, gy);
            gates.add(g);
            simulate();
            updateStatus("Placed a " + g.type.display + " gate. Click again to place another, or Esc to stop.");
            repaint();
            return;
        }

        if (SwingUtilities.isRightMouseButton(e)) return; // handled on click

        // 1) start dragging a wire from an output pin
        for (Gate g : gates) {
            if (g.type == GateType.OUTPUT) continue;
            Point outPin = g.getOutputPinPos();
            if (p.distance(outPin) <= PIN_RADIUS + 4) {
                wireDragSource = g;
                wireDragCurrentPoint = p;
                selectedGate = null;
                selectedWire = null;
                repaint();
                return;
            }
        }

        // 2) click on a gate body: toggle INPUT/CLOCK, or start moving any other gate
        for (int i = gates.size() - 1; i >= 0; i--) {
            Gate g = gates.get(i);
            if (g.getBounds().contains(p)) {
                if (g.type == GateType.INPUT) {
                    g.inputState = !g.inputState;
                    simulate();
                    repaint();
                    return;
                }
                if (g.type == GateType.CLOCK) {
                    g.clockRunning = !g.clockRunning;
                    updateStatus(g.clockRunning ? "Clock running." : "Clock paused.");
                    simulate();
                    repaint();
                    return;
                }
                selectedGate = g;
                selectedWire = null;
                draggingGate = g;
                dragOffset = new Point(p.x - g.x, p.y - g.y);
                dragStartState = captureState();
                repaint();
                return;
            }
        }

        // 3) otherwise, maybe select a wire
        selectedGate = null;
        selectedWire = findWireNear(p);
        repaint();
    }

    private void handleDragged(MouseEvent e) {
        Point p = toWorld(e);
        if (draggingGate != null) {
            draggingGate.x = snap(p.x - dragOffset.x);
            draggingGate.y = snap(p.y - dragOffset.y);
            repaint();
        } else if (wireDragSource != null) {
            wireDragCurrentPoint = p;
            repaint();
        }
    }

    private void handleReleased(MouseEvent e) {
        if (wireDragSource != null) {
            Point p = toWorld(e);
            boolean connected = false;
            outer:
            for (Gate g : gates) {
                if (g == wireDragSource || g.type == GateType.INPUT || g.type == GateType.CLOCK) continue;
                for (int i = 0; i < g.type.numInputs; i++) {
                    Point inPin = g.getInputPinPos(i);
                    if (p.distance(inPin) <= PIN_RADIUS + 5) {
                        pushUndo();
                        final Gate target = g;
                        final int idx = i;
                        wires.removeIf(w -> w.to == target && w.toInputIndex == idx);
                        wires.add(new Wire(wireDragSource, target, idx));
                        connected = true;
                        break outer;
                    }
                }
            }
            wireDragSource = null;
            wireDragCurrentPoint = null;
            if (connected) simulate();
            updateStatus(connected ? "Wire connected. Ready." : "Ready.");
            repaint();
        }
        if (draggingGate != null && dragStartState != null) {
            boolean moved = draggingGate.x != findOriginalPos(dragStartState, draggingGate.id, true)
                    || draggingGate.y != findOriginalPos(dragStartState, draggingGate.id, false);
            if (moved) {
                undoStack.addLast(dragStartState);
                trimUndo();
                redoStack.clear();
                notifyTruthTableListener();
                notifyWaveformListener();
            }
        }
        dragStartState = null;
        draggingGate = null;
    }

    private int findOriginalPos(CircuitState state, int gateId, boolean wantX) {
        for (GateSnap gs : state.gateSnaps) {
            if (gs.id == gateId) return wantX ? gs.x : gs.y;
        }
        return wantX ? draggingGate.x : draggingGate.y;
    }

    private void handleClicked(MouseEvent e) {
        Point p = toWorld(e);

        if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && placingType == null) {
            for (Gate g : gates) {
                if (!g.getBounds().contains(p)) continue;
                if (g.type == GateType.INPUT || g.type == GateType.OUTPUT) {
                    String current = g.customLabel == null ? "" : g.customLabel;
                    String result = JOptionPane.showInputDialog(this, "Label for this gate:", current);
                    if (result != null) {
                        pushUndo();
                        String trimmed = result.trim();
                        g.customLabel = trimmed.isEmpty() ? null : trimmed;
                        simulate();
                    }
                    return;
                }
                if (g.type == GateType.CLOCK) {
                    String result = JOptionPane.showInputDialog(this, "Clock period in milliseconds:",
                            String.valueOf(g.clockPeriodMs));
                    if (result != null) {
                        try {
                            g.clockPeriodMs = Math.max(100, Integer.parseInt(result.trim()));
                        } catch (NumberFormatException ignored) {
                            // leave period unchanged on bad input
                        }
                    }
                    return;
                }
            }
        }

        if (!SwingUtilities.isRightMouseButton(e) || placingType != null) return;

        for (int i = gates.size() - 1; i >= 0; i--) {
            Gate g = gates.get(i);
            if (g.getBounds().contains(p)) {
                pushUndo();
                deleteGate(g);
                updateStatus("Deleted a " + g.type.display + " gate.");
                repaint();
                return;
            }
        }
        Wire w = findWireNear(p);
        if (w != null) {
            pushUndo();
            wires.remove(w);
            simulate();
            updateStatus("Wire deleted.");
            repaint();
        }
    }

    private void handleKey(KeyEvent e) {
        int code = e.getKeyCode();

        if (e.isControlDown() && code == KeyEvent.VK_Z && !e.isShiftDown()) {
            undo();
            return;
        }
        if (e.isControlDown() && (code == KeyEvent.VK_Y || (code == KeyEvent.VK_Z && e.isShiftDown()))) {
            redo();
            return;
        }
        if (e.isControlDown() && code == KeyEvent.VK_C) {
            if (selectedGate != null) {
                clipboard = selectedGate;
                updateStatus("Copied " + selectedGate.type.display + " gate.");
            }
            return;
        }
        if (e.isControlDown() && code == KeyEvent.VK_V) {
            if (clipboard != null) {
                pushUndo();
                Gate copy = new Gate(clipboard.type, clipboard.x + gridStep, clipboard.y + gridStep);
                copy.inputState = clipboard.inputState;
                copy.customLabel = clipboard.customLabel;
                copy.clockPeriodMs = clipboard.clockPeriodMs;
                gates.add(copy);
                selectedGate = copy;
                simulate();
                updateStatus("Pasted a " + copy.type.display + " gate.");
                repaint();
            }
            return;
        }

        if (code == KeyEvent.VK_DELETE || code == KeyEvent.VK_BACK_SPACE) {
            if (selectedGate != null) {
                pushUndo();
                deleteGate(selectedGate);
                selectedGate = null;
                repaint();
            } else if (selectedWire != null) {
                pushUndo();
                wires.remove(selectedWire);
                selectedWire = null;
                simulate();
                repaint();
            }
        } else if (code == KeyEvent.VK_ESCAPE) {
            setPlacingType(null);
            updateStatus("Ready.");
        }
    }

    private void updateStatus(String msg) {
        if (statusUpdater != null) statusUpdater.accept(msg);
    }

    // ---------------------------------------------------------------
    // Clock ticking
    // ---------------------------------------------------------------

    private void tickClocks() {
        boolean changed = false;
        for (Gate g : gates) {
            if (g.tickClock(50)) changed = true;
        }
        if (changed) simulate();
    }

    // ---------------------------------------------------------------
    // Simulation
    // ---------------------------------------------------------------

    /** Propagates values without touching the UI listeners (used internally). */
    private void simulateInternal() {
        for (Gate g : gates) {
            for (int i = 0; i < g.inputValues.length; i++) {
                boolean wired = false;
                for (Wire w : wires) {
                    if (w.to == g && w.toInputIndex == i) {
                        wired = true;
                        break;
                    }
                }
                if (!wired) g.inputValues[i] = false;
            }
        }
        int passes = gates.size() + 2;
        for (int pass = 0; pass < passes; pass++) {
            for (Gate g : gates) g.evaluate();
            for (Wire w : wires) {
                w.to.inputValues[w.toInputIndex] = w.from.outputValue;
            }
        }
        for (Gate g : gates) g.evaluate();
    }

    /** Propagates values, repaints, and pushes fresh data to the side panels. */
    public void simulate() {
        simulateInternal();
        repaint();
        notifyTruthTableListener();
        notifyWaveformListener();
    }

    private void deleteGate(Gate g) {
        gates.remove(g);
        wires.removeIf(w -> w.from == g || w.to == g);
        simulate();
    }

    public void clearAll() {
        pushUndo();
        gates.clear();
        wires.clear();
        selectedGate = null;
        selectedWire = null;
        simulate();
    }

    private Wire findWireNear(Point p) {
        for (Wire w : wires) {
            Point from = w.from.getOutputPinPos();
            Point to = w.to.getInputPinPos(w.toInputIndex);
            double dist = Line2D.ptSegDist(from.x, from.y, to.x, to.y, p.x, p.y);
            if (dist < 6) return w;
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Undo / redo
    // ---------------------------------------------------------------

    private void pushUndo() {
        undoStack.addLast(captureState());
        trimUndo();
        redoStack.clear();
    }

    private void trimUndo() {
        while (undoStack.size() > MAX_UNDO) undoStack.removeFirst();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            updateStatus("Nothing to undo.");
            return;
        }
        redoStack.addLast(captureState());
        restoreState(undoStack.removeLast());
        updateStatus("Undid last change.");
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            updateStatus("Nothing to redo.");
            return;
        }
        undoStack.addLast(captureState());
        restoreState(redoStack.removeLast());
        updateStatus("Redid last change.");
    }

    private static class GateSnap {
        int id;
        GateType type;
        int x, y;
        boolean inputState;
        String customLabel;
        boolean clockRunning;
        int clockPeriodMs;
    }

    private static class WireSnap {
        int fromId, toId, toIndex;
    }

    private static class CircuitState {
        List<GateSnap> gateSnaps = new ArrayList<>();
        List<WireSnap> wireSnaps = new ArrayList<>();
    }

    private CircuitState captureState() {
        CircuitState s = new CircuitState();
        for (Gate g : gates) {
            GateSnap gs = new GateSnap();
            gs.id = g.id;
            gs.type = g.type;
            gs.x = g.x;
            gs.y = g.y;
            gs.inputState = g.inputState;
            gs.customLabel = g.customLabel;
            gs.clockRunning = g.clockRunning;
            gs.clockPeriodMs = g.clockPeriodMs;
            s.gateSnaps.add(gs);
        }
        for (Wire w : wires) {
            WireSnap ws = new WireSnap();
            ws.fromId = w.from.id;
            ws.toId = w.to.id;
            ws.toIndex = w.toInputIndex;
            s.wireSnaps.add(ws);
        }
        return s;
    }

    private void restoreState(CircuitState s) {
        gates.clear();
        wires.clear();
        Map<Integer, Gate> idMap = new HashMap<>();
        for (GateSnap gs : s.gateSnaps) {
            Gate g = new Gate(gs.type, gs.x, gs.y);
            g.inputState = gs.inputState;
            g.customLabel = gs.customLabel;
            g.clockRunning = gs.clockRunning;
            g.clockPeriodMs = gs.clockPeriodMs;
            gates.add(g);
            idMap.put(gs.id, g);
        }
        for (WireSnap ws : s.wireSnaps) {
            Gate from = idMap.get(ws.fromId);
            Gate to = idMap.get(ws.toId);
            if (from != null && to != null) {
                wires.add(new Wire(from, to, ws.toIndex));
            }
        }
        selectedGate = null;
        selectedWire = null;
        simulate();
    }

    // ---------------------------------------------------------------
    // Save / load (simple human-readable text format)
    // ---------------------------------------------------------------

    public String serializeCircuit() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Logic Gate Explorer circuit file\n");
        Map<Gate, Integer> indexMap = new HashMap<>();
        int idx = 0;
        for (Gate g : gates) {
            indexMap.put(g, idx);
            String label = (g.customLabel == null || g.customLabel.isEmpty())
                    ? "-" : g.customLabel.replace(' ', '_');
            sb.append("GATE ").append(idx).append(' ').append(g.type.name()).append(' ')
                    .append(g.x).append(' ').append(g.y).append(' ')
                    .append(g.inputState).append(' ').append(label).append(' ')
                    .append(g.clockPeriodMs).append('\n');
            idx++;
        }
        for (Wire w : wires) {
            sb.append("WIRE ").append(indexMap.get(w.from)).append(' ')
                    .append(indexMap.get(w.to)).append(' ').append(w.toInputIndex).append('\n');
        }
        return sb.toString();
    }

    public void loadCircuit(String content) {
        List<Gate> newGates = new ArrayList<>();
        List<int[]> wireSpecs = new ArrayList<>();

        for (String rawLine : content.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts[0].equals("GATE") && parts.length >= 7) {
                GateType type = GateType.valueOf(parts[2]);
                int x = Integer.parseInt(parts[3]);
                int y = Integer.parseInt(parts[4]);
                boolean state = Boolean.parseBoolean(parts[5]);
                String label = parts[6].equals("-") ? null : parts[6].replace('_', ' ');
                int period = parts.length > 7 ? Integer.parseInt(parts[7]) : 800;
                Gate g = new Gate(type, x, y);
                g.inputState = state;
                g.customLabel = label;
                g.clockPeriodMs = period;
                newGates.add(g);
            } else if (parts[0].equals("WIRE") && parts.length >= 4) {
                wireSpecs.add(new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])});
            }
        }

        pushUndo();
        gates.clear();
        gates.addAll(newGates);
        wires.clear();
        for (int[] spec : wireSpecs) {
            if (spec[0] >= 0 && spec[0] < gates.size() && spec[1] >= 0 && spec[1] < gates.size()) {
                Gate from = gates.get(spec[0]);
                Gate to = gates.get(spec[1]);
                if (spec[2] >= 0 && spec[2] < to.type.numInputs) {
                    wires.add(new Wire(from, to, spec[2]));
                }
            }
        }
        selectedGate = null;
        selectedWire = null;
        simulate();
    }

    // ---------------------------------------------------------------
    // PNG export
    // ---------------------------------------------------------------

    public BufferedImage renderToImage() {
        int w = 400, h = 300;
        for (Gate g : gates) {
            w = Math.max(w, g.x + Gate.WIDTH + 40);
            h = Math.max(h, g.y + Gate.HEIGHT + 40);
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(palette.canvasBg);
        g2.fillRect(0, 0, w, h);
        for (Wire wire : wires) {
            drawWire(g2, wire.from.getOutputPinPos(), wire.to.getInputPinPos(wire.toInputIndex),
                    wire.from.outputValue, false);
        }
        for (Gate gate : gates) {
            drawGate(g2, gate);
        }
        g2.dispose();
        return img;
    }

    // ---------------------------------------------------------------
    // Live truth table
    // ---------------------------------------------------------------

    /** The short label shown on/above a gate: e.g. "IN1", "OUT2", "CLK1", "DFF1". */
    private String ioLabel(Gate g) {
        boolean labeledType = g.type == GateType.INPUT || g.type == GateType.OUTPUT
                || g.type == GateType.CLOCK || g.type == GateType.DFF;
        if (!labeledType) return g.type.display;

        if ((g.type == GateType.INPUT || g.type == GateType.OUTPUT)
                && g.customLabel != null && !g.customLabel.isEmpty()) {
            return g.customLabel;
        }

        List<Gate> sameType = new ArrayList<>();
        for (Gate other : gates) {
            if (other.type == g.type) sameType.add(other);
        }
        sameType.sort(Comparator.comparingInt(o -> o.id));
        int idx = sameType.indexOf(g) + 1;

        String prefix;
        switch (g.type) {
            case INPUT: prefix = "IN"; break;
            case OUTPUT: prefix = "OUT"; break;
            case CLOCK: prefix = "CLK"; break;
            default: prefix = "DFF";
        }
        return prefix + idx;
    }

    public void notifyTruthTableListener() {
        if (truthTableListener != null) {
            truthTableListener.accept(buildTruthTableSnapshot());
        }
    }

    public void notifyWaveformListener() {
        if (waveformListener == null) return;
        List<Gate> io = new ArrayList<>();
        for (Gate g : gates) {
            if (g.type == GateType.INPUT || g.type == GateType.OUTPUT
                    || g.type == GateType.CLOCK || g.type == GateType.DFF) io.add(g);
        }
        io.sort(Comparator.comparingInt(g -> g.id));
        List<String> labels = new ArrayList<>();
        boolean[] values = new boolean[io.size()];
        for (int i = 0; i < io.size(); i++) {
            labels.add(ioLabel(io.get(i)));
            values[i] = io.get(i).outputValue;
        }
        waveformListener.accept(new LiveSnapshot(labels, values));
    }

    /**
     * Cycles every INPUT gate through all 2^n combinations, simulates the
     * circuit for each, and records every OUTPUT gate's value. Input states
     * are restored to their pre-call values afterward. CLOCK/DFF gates are
     * left out of this static sweep (they belong to the live waveform view).
     */
    private TruthTableSnapshot buildTruthTableSnapshot() {
        List<Gate> inputs = new ArrayList<>();
        List<Gate> outputs = new ArrayList<>();
        for (Gate g : gates) {
            if (g.type == GateType.INPUT) inputs.add(g);
            else if (g.type == GateType.OUTPUT) outputs.add(g);
        }
        inputs.sort(Comparator.comparingInt(g -> g.id));
        outputs.sort(Comparator.comparingInt(g -> g.id));

        List<String> inLabels = new ArrayList<>();
        for (Gate g : inputs) inLabels.add(ioLabel(g));
        List<String> outLabels = new ArrayList<>();
        for (Gate g : outputs) outLabels.add(ioLabel(g));

        if (inputs.isEmpty() || outputs.isEmpty() || inputs.size() > 10) {
            return new TruthTableSnapshot(inLabels, outLabels, new boolean[0][0], -1);
        }

        boolean[] savedStates = new boolean[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) savedStates[i] = inputs.get(i).inputState;

        List<Gate> dffs = new ArrayList<>();
        for (Gate g : gates) if (g.type == GateType.DFF) dffs.add(g);
        boolean[] savedDffStored = new boolean[dffs.size()];
        boolean[] savedDffLastClock = new boolean[dffs.size()];
        for (int i = 0; i < dffs.size(); i++) {
            savedDffStored[i] = dffs.get(i).dffStored;
            savedDffLastClock[i] = dffs.get(i).dffLastClock;
        }

        int rowCount = 1 << inputs.size();
        int cols = inputs.size() + outputs.size();
        boolean[][] table = new boolean[rowCount][cols];

        for (int r = 0; r < rowCount; r++) {
            for (int i = 0; i < inputs.size(); i++) {
                boolean val = ((r >> (inputs.size() - 1 - i)) & 1) == 1;
                inputs.get(i).inputState = val;
                table[r][i] = val;
            }
            simulateInternal();
            for (int o = 0; o < outputs.size(); o++) {
                table[r][inputs.size() + o] = outputs.get(o).outputValue;
            }
        }

        int currentRow = 0;
        for (int i = 0; i < savedStates.length; i++) {
            currentRow = (currentRow << 1) | (savedStates[i] ? 1 : 0);
        }

        for (int i = 0; i < dffs.size(); i++) {
            dffs.get(i).dffStored = savedDffStored[i];
            dffs.get(i).dffLastClock = savedDffLastClock[i];
        }
        for (int i = 0; i < inputs.size(); i++) inputs.get(i).inputState = savedStates[i];
        simulateInternal();

        return new TruthTableSnapshot(inLabels, outLabels, table, currentRow);
    }

    /** Immutable snapshot of the circuit's full truth table, pushed to the side panel. */
    public static class TruthTableSnapshot {
        public final List<String> inputLabels;
        public final List<String> outputLabels;
        public final boolean[][] rows;
        public final int currentRowIndex;

        public TruthTableSnapshot(List<String> inputLabels, List<String> outputLabels,
                                   boolean[][] rows, int currentRowIndex) {
            this.inputLabels = inputLabels;
            this.outputLabels = outputLabels;
            this.rows = rows;
            this.currentRowIndex = currentRowIndex;
        }
    }

    /** Immutable snapshot of the circuit's current live I/O values, pushed to the waveform panel. */
    public static class LiveSnapshot {
        public final List<String> labels;
        public final boolean[] values;

        public LiveSnapshot(List<String> labels, boolean[] values) {
            this.labels = labels;
            this.values = values;
        }
    }

    // ---------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.scale(zoom, zoom);

        drawGrid(g2);

        for (Wire w : wires) {
            drawWire(g2, w.from.getOutputPinPos(), w.to.getInputPinPos(w.toInputIndex),
                    w.from.outputValue, w == selectedWire);
        }
        if (wireDragSource != null && wireDragCurrentPoint != null) {
            drawWire(g2, wireDragSource.getOutputPinPos(), wireDragCurrentPoint,
                    wireDragSource.outputValue, false);
        }
        for (Gate gate : gates) {
            drawGate(g2, gate);
        }
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(palette.gridDot);
        double visibleW = getWidth() / zoom;
        double visibleH = getHeight() / zoom;
        for (int x = gridStep; x < visibleW; x += gridStep) {
            for (int y = gridStep; y < visibleH; y += gridStep) {
                g2.fillRect(x, y, 2, 2);
            }
        }
    }

    private void drawWire(Graphics2D g2, Point from, Point to, boolean value, boolean selected) {
        g2.setColor(selected ? palette.wireSelected : (value ? palette.wireOn : palette.wireOff));
        g2.setStroke(new BasicStroke(selected ? 3.5f : 2.5f));
        int midX = (from.x + to.x) / 2;
        CubicCurve2D curve = new CubicCurve2D.Double(from.x, from.y, midX, from.y, midX, to.y, to.x, to.y);
        g2.draw(curve);
    }

    private void drawGate(Graphics2D g2, Gate gate) {
        if (gate.type == GateType.INPUT) {
            drawInputSwitch(g2, gate);
            return;
        }
        if (gate.type == GateType.OUTPUT) {
            drawOutputBulb(g2, gate);
            return;
        }
        if (gate.type == GateType.CLOCK) {
            drawClockGate(g2, gate);
            return;
        }
        if (gate.type == GateType.DFF) {
            drawDFF(g2, gate);
            return;
        }

        Rectangle b = gate.getBounds();
        boolean hasBubble = (gate.type == GateType.NOT || gate.type == GateType.NAND
                || gate.type == GateType.NOR || gate.type == GateType.XNOR);
        boolean isOrFamily = (gate.type == GateType.OR || gate.type == GateType.NOR
                || gate.type == GateType.XOR || gate.type == GateType.XNOR);
        boolean isAndFamily = (gate.type == GateType.AND || gate.type == GateType.NAND);
        boolean isXFamily = (gate.type == GateType.XOR || gate.type == GateType.XNOR);

        Path2D body;
        if (isAndFamily) body = domeShape(b, hasBubble);
        else if (isOrFamily) body = orShape(b, hasBubble);
        else body = triangleShape(b, hasBubble); // NOT, BUFFER

        g2.setColor(new Color(0, 0, 0, palette == Palette.DARK ? 90 : 25));
        Path2D shadow = (Path2D) body.clone();
        shadow.transform(java.awt.geom.AffineTransform.getTranslateInstance(3, 4));
        g2.fill(shadow);

        if (isXFamily) {
            g2.setColor(gate == selectedGate ? palette.gateSelected : palette.gateBorder);
            g2.setStroke(new BasicStroke(2f));
            drawExtraCurve(g2, b);
        }

        g2.setColor(palette.gateFill);
        g2.fill(body);
        g2.setColor(gate == selectedGate ? palette.gateSelected : palette.gateBorder);
        g2.setStroke(new BasicStroke(gate == selectedGate ? 3f : 2f));
        g2.draw(body);

        if (hasBubble) drawBubble(g2, b);

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        String text = gate.type.display;
        double labelX = b.x + b.width * 0.30;
        int tx = (int) (labelX - fm.stringWidth(text) / 2.0);
        int ty = b.y + b.height / 2 + fm.getAscent() / 2 - 3;
        g2.setColor(palette.textPrimary);
        g2.drawString(text, tx, ty);

        for (int i = 0; i < gate.type.numInputs; i++) {
            Point p = gate.getInputPinPos(i);
            drawPin(g2, p, gate.inputValues[i]);
        }
        drawPin(g2, gate.getOutputPinPos(), gate.outputValue);
    }

    private Path2D domeShape(Rectangle b, boolean bubble) {
        double x = b.x, y = b.y, w = b.width, h = b.height;
        double r = h / 2.0;
        double rightExtent = bubble ? w - 2 * BUBBLE_R : w;
        double straightX = x + rightExtent - r;
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(straightX, y);
        p.append(new Arc2D.Double(straightX - r, y, h, h, 90, -180, Arc2D.OPEN), true);
        p.lineTo(x, y + h);
        p.closePath();
        return p;
    }

    private Path2D orShape(Rectangle b, boolean bubble) {
        double x = b.x, y = b.y, w = b.width, h = b.height;
        double tipX = bubble ? x + w - 2 * BUBBLE_R : x + w;
        double dx = tipX - x;
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.curveTo(x + dx * 0.5, y, x + dx * 0.85, y + h * 0.15, tipX, y + h / 2.0);
        p.curveTo(x + dx * 0.85, y + h * 0.85, x + dx * 0.5, y + h, x, y + h);
        p.lineTo(x, y);
        p.closePath();
        return p;
    }

    private Path2D triangleShape(Rectangle b, boolean bubble) {
        double x = b.x, y = b.y, w = b.width, h = b.height;
        double tipX = bubble ? x + w - 2 * BUBBLE_R : x + w;
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x, y + h);
        p.lineTo(tipX, y + h / 2.0);
        p.closePath();
        return p;
    }

    private void drawExtraCurve(Graphics2D g2, Rectangle b) {
        double x = b.x, y = b.y, h = b.height;
        double off = 7;
        Path2D p = new Path2D.Double();
        p.moveTo(x - off, y);
        p.curveTo(x - off + h * 0.16, y, x - off + h * 0.16, y + h, x - off, y + h);
        g2.draw(p);
    }

    private void drawBubble(Graphics2D g2, Rectangle b) {
        double cx = b.x + b.width - BUBBLE_R;
        double cy = b.y + b.height / 2.0;
        Ellipse2D circle = new Ellipse2D.Double(cx - BUBBLE_R, cy - BUBBLE_R, BUBBLE_R * 2, BUBBLE_R * 2);
        g2.setColor(palette.gateFill);
        g2.fill(circle);
        g2.setColor(palette.gateBorder);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(circle);
    }

    private void drawInputSwitch(Graphics2D g2, Gate gate) {
        Rectangle b = gate.getBounds();
        double arc = b.height;

        g2.setColor(new Color(0, 0, 0, palette == Palette.DARK ? 90 : 25));
        g2.fill(new RoundRectangle2D.Double(b.x + 3, b.y + 4, b.width, b.height, arc, arc));

        Color track = gate.inputState ? palette.inputOnFill : palette.inputOffFill;
        RoundRectangle2D pill = new RoundRectangle2D.Double(b.x, b.y, b.width, b.height, arc, arc);
        g2.setColor(track);
        g2.fill(pill);
        g2.setColor(gate == selectedGate ? palette.gateSelected : palette.gateBorder);
        g2.setStroke(new BasicStroke(gate == selectedGate ? 3f : 2f));
        g2.draw(pill);

        double knobR = b.height / 2.0 - 6;
        double knobCx = gate.inputState ? b.x + b.width - b.height / 2.0 : b.x + b.height / 2.0;
        double knobCy = b.y + b.height / 2.0;
        Ellipse2D knob = new Ellipse2D.Double(knobCx - knobR, knobCy - knobR, knobR * 2, knobR * 2);
        g2.setColor(Color.WHITE);
        g2.fill(knob);
        g2.setColor(palette.gateBorder);
        g2.draw(knob);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        String small = ioLabel(gate);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(palette.textPrimary);
        g2.drawString(small, b.x + (b.width - fm.stringWidth(small)) / 2, b.y - 6);

        drawPin(g2, gate.getOutputPinPos(), gate.outputValue);
    }

    private void drawOutputBulb(Graphics2D g2, Gate gate) {
        Rectangle b = gate.getBounds();
        double d = b.height;
        double cx = b.x + b.width / 2.0;
        double cy = b.y + b.height / 2.0;

        g2.setColor(new Color(0, 0, 0, palette == Palette.DARK ? 90 : 25));
        g2.fill(new Ellipse2D.Double(cx - d / 2 + 3, cy - d / 2 + 4, d, d));

        if (gate.outputValue) {
            Color glowBase = palette.outputOnFill;
            g2.setColor(new Color(glowBase.getRed(), glowBase.getGreen(), glowBase.getBlue(), 70));
            g2.fill(new Ellipse2D.Double(cx - d / 2 - 7, cy - d / 2 - 7, d + 14, d + 14));
        }

        Color fill = gate.outputValue ? palette.outputOnFill : palette.outputOffFill;
        Ellipse2D circle = new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d);
        g2.setColor(fill);
        g2.fill(circle);
        g2.setColor(gate == selectedGate ? palette.gateSelected : palette.gateBorder);
        g2.setStroke(new BasicStroke(gate == selectedGate ? 3f : 2f));
        g2.draw(circle);

        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        String text = gate.outputValue ? "1" : "0";
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(palette.textPrimary);
        g2.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) (cy + fm.getAscent() / 2.0 - 3));

        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        String small = ioLabel(gate);
        FontMetrics sfm = g2.getFontMetrics();
        g2.drawString(small, (float) (cx - sfm.stringWidth(small) / 2.0), (float) (b.y - 6));

        for (int i = 0; i < gate.type.numInputs; i++) {
            drawPin(g2, gate.getInputPinPos(i), gate.inputValues[i]);
        }
    }

    private void drawClockGate(Graphics2D g2, Gate gate) {
        Rectangle b = gate.getBounds();

        g2.setColor(new Color(0, 0, 0, palette == Palette.DARK ? 90 : 25));
        g2.fill(new RoundRectangle2D.Double(b.x + 3, b.y + 4, b.width, b.height, 14, 14));

        Color fill = gate.clockRunning ? palette.inputOnFill : palette.gateFill;
        RoundRectangle2D body = new RoundRectangle2D.Double(b.x, b.y, b.width, b.height, 14, 14);
        g2.setColor(fill);
        g2.fill(body);
        g2.setColor(gate == selectedGate ? palette.gateSelected : palette.gateBorder);
        g2.setStroke(new BasicStroke(gate == selectedGate ? 3f : 2f));
        g2.draw(body);

        g2.setColor(palette.textPrimary);
        g2.setStroke(new BasicStroke(2f));
        Path2D wave = new Path2D.Double();
        double wx = b.x + 18, wy = b.y + b.height / 2.0, seg = 10, amp = 10;
        wave.moveTo(wx, wy + amp / 2);
        wave.lineTo(wx, wy - amp / 2);
        wave.lineTo(wx + seg, wy - amp / 2);
        wave.lineTo(wx + seg, wy + amp / 2);
        wave.lineTo(wx + seg * 2, wy + amp / 2);
        wave.lineTo(wx + seg * 2, wy - amp / 2);
        wave.lineTo(wx + seg * 3, wy - amp / 2);
        g2.draw(wave);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        String small = ioLabel(gate);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(small, b.x + (b.width - fm.stringWidth(small)) / 2, b.y - 6);

        drawPin(g2, gate.getOutputPinPos(), gate.outputValue);
    }

    private void drawDFF(Graphics2D g2, Gate gate) {
        Rectangle b = gate.getBounds();

        g2.setColor(new Color(0, 0, 0, palette == Palette.DARK ? 90 : 25));
        g2.fillRoundRect(b.x + 3, b.y + 4, b.width, b.height, 10, 10);

        g2.setColor(palette.gateFill);
        g2.fillRoundRect(b.x, b.y, b.width, b.height, 10, 10);
        g2.setColor(gate == selectedGate ? palette.gateSelected : palette.gateBorder);
        g2.setStroke(new BasicStroke(gate == selectedGate ? 3f : 2f));
        g2.drawRoundRect(b.x, b.y, b.width, b.height, 10, 10);

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        String text = "DFF";
        g2.setColor(palette.textPrimary);
        g2.drawString(text, b.x + (b.width - fm.stringWidth(text)) / 2, b.y + b.height / 2 - 6);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        Point dPin = gate.getInputPinPos(0);
        Point clkPin = gate.getInputPinPos(1);
        Point qPin = gate.getOutputPinPos();
        g2.drawString("D", b.x + 6, dPin.y - 8);
        g2.drawString("CLK", b.x + 6, clkPin.y + 16);
        FontMetrics sfm = g2.getFontMetrics();
        g2.drawString("Q", b.x + b.width - sfm.stringWidth("Q") - 6, qPin.y - 8);

        Path2D tri = new Path2D.Double();
        tri.moveTo(clkPin.x, clkPin.y - 6);
        tri.lineTo(clkPin.x + 8, clkPin.y);
        tri.lineTo(clkPin.x, clkPin.y + 6);
        g2.setColor(palette.gateBorder);
        g2.draw(tri);

        for (int i = 0; i < gate.type.numInputs; i++) {
            drawPin(g2, gate.getInputPinPos(i), gate.inputValues[i]);
        }
        drawPin(g2, gate.getOutputPinPos(), gate.outputValue);
    }

    private void drawPin(Graphics2D g2, Point p, boolean value) {
        g2.setColor(value ? palette.pinOn : palette.pinOff);
        g2.fillOval(p.x - PIN_RADIUS, p.y - PIN_RADIUS, PIN_RADIUS * 2, PIN_RADIUS * 2);
        g2.setColor(palette.textSecondary);
        g2.drawOval(p.x - PIN_RADIUS, p.y - PIN_RADIUS, PIN_RADIUS * 2, PIN_RADIUS * 2);
    }
}
