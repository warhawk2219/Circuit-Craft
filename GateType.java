/**
 * Defines every gate type available in the explorer, along with how many
 * input pins it has and the short label drawn on its body.
 */
public enum GateType {
    INPUT(0, "IN"),
    OUTPUT(1, "OUT"),
    NOT(1, "NOT"),
    BUFFER(1, "BUF"),
    AND(2, "AND"),
    OR(2, "OR"),
    XOR(2, "XOR"),
    NAND(2, "NAND"),
    NOR(2, "NOR"),
    XNOR(2, "XNOR"),
    CLOCK(0, "CLK"),
    DFF(2, "DFF");

    public final int numInputs;
    public final String display;

    GateType(int numInputs, String display) {
        this.numInputs = numInputs;
        this.display = display;
    }
}
