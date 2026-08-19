import java.awt.Point;
import java.awt.Rectangle;

/**
 * A single gate placed on the canvas: its type, position, current input
 * values, and computed output value. Also carries the extra state needed
 * for INPUT/OUTPUT custom labels, a free-running CLOCK, and an
 * edge-triggered D flip-flop (DFF).
 */
public class Gate {
    public static final int WIDTH = 90;
    public static final int HEIGHT = 60;

    private static int nextId = 0;

    public final int id;
    public GateType type;
    public int x, y;
    public boolean[] inputValues;
    public boolean outputValue;
    public boolean inputState; // INPUT: toggled by click. CLOCK: current phase, toggled by the ticker.

    /** Optional user-given name, shown instead of the auto IN1/OUT1 label. Only used by INPUT/OUTPUT. */
    public String customLabel = null;

    /** CLOCK-only: whether it is currently free-running. */
    public boolean clockRunning = false;
    /** CLOCK-only: how long each half-period lasts, in milliseconds. */
    public int clockPeriodMs = 800;
    private int clockElapsedMs = 0;

    /** DFF-only: latched output and the previous clock level, for rising-edge detection. */
    boolean dffStored = false;
    boolean dffLastClock = false;

    public Gate(GateType type, int x, int y) {
        this.id = nextId++;
        this.type = type;
        this.x = x;
        this.y = y;
        this.inputValues = new boolean[type.numInputs];
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    public Point getOutputPinPos() {
        return new Point(x + WIDTH, y + HEIGHT / 2);
    }

    public Point getInputPinPos(int index) {
        int spacing = HEIGHT / (type.numInputs + 1);
        return new Point(x, y + spacing * (index + 1));
    }

    /**
     * Advances the CLOCK by deltaMs; flips its phase and returns true once a
     * half-period has elapsed. No-op for any other gate type or if paused.
     */
    public boolean tickClock(int deltaMs) {
        if (type != GateType.CLOCK || !clockRunning) return false;
        clockElapsedMs += deltaMs;
        if (clockElapsedMs >= clockPeriodMs) {
            clockElapsedMs = 0;
            inputState = !inputState;
            return true;
        }
        return false;
    }

    /** Recomputes outputValue from the current inputValues / inputState. */
    public void evaluate() {
        switch (type) {
            case INPUT:
            case CLOCK:
                outputValue = inputState;
                break;
            case NOT:
                outputValue = !inputValues[0];
                break;
            case BUFFER:
            case OUTPUT:
                outputValue = inputValues[0];
                break;
            case AND:
                outputValue = inputValues[0] && inputValues[1];
                break;
            case OR:
                outputValue = inputValues[0] || inputValues[1];
                break;
            case XOR:
                outputValue = inputValues[0] ^ inputValues[1];
                break;
            case NAND:
                outputValue = !(inputValues[0] && inputValues[1]);
                break;
            case NOR:
                outputValue = !(inputValues[0] || inputValues[1]);
                break;
            case XNOR:
                outputValue = !(inputValues[0] ^ inputValues[1]);
                break;
            case DFF: {
                boolean clk = inputValues[1];
                if (clk && !dffLastClock) {
                    dffStored = inputValues[0]; // latch D on the rising edge of CLK
                }
                dffLastClock = clk;
                outputValue = dffStored;
                break;
            }
        }
    }
}
