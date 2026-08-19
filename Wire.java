/**
 * A directed connection from one gate's output pin to another gate's
 * specific input pin.
 */
public class Wire {
    public final Gate from;
    public final Gate to;
    public final int toInputIndex;

    public Wire(Gate from, Gate to, int toInputIndex) {
        this.from = from;
        this.to = to;
        this.toInputIndex = toInputIndex;
    }
}
