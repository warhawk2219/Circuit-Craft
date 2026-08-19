import java.awt.Color;

/**
 * A full set of colors for one theme (light or dark), used by every panel
 * so the whole app can be re-skinned by swapping one object.
 */
public class Palette {

    public Color appBg;
    public Color toolbarBg;
    public Color toolbarBorder;
    public Color buttonBg;
    public Color buttonHoverBg;
    public Color buttonText;
    public Color canvasBg;
    public Color gridDot;
    public Color gateFill;
    public Color gateBorder;
    public Color gateSelected;
    public Color inputOnFill;
    public Color inputOffFill;
    public Color outputOnFill;
    public Color outputOffFill;
    public Color wireOn;
    public Color wireOff;
    public Color wireSelected;
    public Color pinOn;
    public Color pinOff;
    public Color textPrimary;
    public Color textSecondary;
    public Color panelBg;
    public Color panelHeaderBg;
    public Color panelBorder;
    public Color tableRowAlt;
    public Color tableRowCurrent;
    public Color statusBarBg;
    public Color statusBarText;

    private Palette() {
    }

    public static final Palette LIGHT = buildLight();
    public static final Palette DARK = buildDark();

    private static Palette buildLight() {
        Palette p = new Palette();
        p.appBg = new Color(0xF4, 0xF6, 0xFB);
        p.toolbarBg = Color.WHITE;
        p.toolbarBorder = new Color(0xE1, 0xE4, 0xEC);
        p.buttonBg = new Color(0xEC, 0xEF, 0xF7);
        p.buttonHoverBg = new Color(0xDC, 0xE4, 0xF7);
        p.buttonText = new Color(0x2A, 0x2C, 0x3A);
        p.canvasBg = Color.WHITE;
        p.gridDot = new Color(0, 0, 0, 45);
        p.gateFill = new Color(0xEB, 0xF0, 0xFA);
        p.gateBorder = new Color(0x46, 0x46, 0x5A);
        p.gateSelected = new Color(0xE0, 0x2F, 0x44);
        p.inputOnFill = new Color(0x9B, 0xE3, 0xAE);
        p.inputOffFill = new Color(0xE0, 0xE0, 0xE0);
        p.outputOnFill = new Color(0xFF, 0xD8, 0x54);
        p.outputOffFill = new Color(0xE8, 0xE8, 0xE8);
        p.wireOn = new Color(0x27, 0xAE, 0x60);
        p.wireOff = new Color(0x82, 0x82, 0x82);
        p.wireSelected = new Color(0xE0, 0x2F, 0x44);
        p.pinOn = new Color(0x2E, 0xCC, 0x71);
        p.pinOff = new Color(0x96, 0x96, 0x96);
        p.textPrimary = new Color(0x1C, 0x1D, 0x26);
        p.textSecondary = new Color(0x6B, 0x6E, 0x7B);
        p.panelBg = Color.WHITE;
        p.panelHeaderBg = new Color(0xE9, 0xEE, 0xFA);
        p.panelBorder = new Color(0xDD, 0xE2, 0xEC);
        p.tableRowAlt = new Color(0xF6, 0xF8, 0xFC);
        p.tableRowCurrent = new Color(0xFF, 0xF0, 0xB3);
        p.statusBarBg = Color.WHITE;
        p.statusBarText = new Color(0x40, 0x42, 0x50);
        return p;
    }

    private static Palette buildDark() {
        Palette p = new Palette();
        p.appBg = new Color(0x1B, 0x1C, 0x25);
        p.toolbarBg = new Color(0x23, 0x25, 0x30);
        p.toolbarBorder = new Color(0x30, 0x32, 0x40);
        p.buttonBg = new Color(0x2E, 0x30, 0x3E);
        p.buttonHoverBg = new Color(0x3B, 0x3E, 0x50);
        p.buttonText = new Color(0xE8, 0xE9, 0xF0);
        p.canvasBg = new Color(0x14, 0x15, 0x1C);
        p.gridDot = new Color(255, 255, 255, 55);
        p.gateFill = new Color(0x2C, 0x2F, 0x3E);
        p.gateBorder = new Color(0x8A, 0x8D, 0xA8);
        p.gateSelected = new Color(0xFF, 0x5C, 0x5C);
        p.inputOnFill = new Color(0x2E, 0x7D, 0x50);
        p.inputOffFill = new Color(0x3A, 0x3D, 0x4D);
        p.outputOnFill = new Color(0xC9, 0xA2, 0x27);
        p.outputOffFill = new Color(0x3A, 0x3D, 0x4D);
        p.wireOn = new Color(0x2E, 0xCC, 0x71);
        p.wireOff = new Color(0x6B, 0x6E, 0x85);
        p.wireSelected = new Color(0xFF, 0x5C, 0x5C);
        p.pinOn = new Color(0x2E, 0xCC, 0x71);
        p.pinOff = new Color(0x6B, 0x6E, 0x85);
        p.textPrimary = new Color(0xEC, 0xED, 0xF4);
        p.textSecondary = new Color(0x9A, 0x9D, 0xB0);
        p.panelBg = new Color(0x23, 0x25, 0x30);
        p.panelHeaderBg = new Color(0x2C, 0x2F, 0x3E);
        p.panelBorder = new Color(0x35, 0x38, 0x48);
        p.tableRowAlt = new Color(0x27, 0x29, 0x34);
        p.tableRowCurrent = new Color(0x3B, 0x5B, 0xDB);
        p.statusBarBg = new Color(0x23, 0x25, 0x30);
        p.statusBarText = new Color(0xC8, 0xCA, 0xD8);
        return p;
    }
}
