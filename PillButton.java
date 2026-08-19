import javax.swing.*;
import java.awt.*;

/**
 * A JButton with a custom rounded "pill" background that highlights on
 * hover, and an optional colored "armed" border to show it's the active
 * tool (e.g. a gate type currently loaded for placement).
 */
public class PillButton extends JButton {

    private Color bgColor = new Color(0xEC, 0xEF, 0xF7);
    private Color hoverColor = new Color(0xDC, 0xE4, 0xF7);
    private Color armedBorderColor = null;

    public PillButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setOpaque(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setFont(new Font("SansSerif", Font.BOLD, 12));
        setForeground(new Color(0x2A, 0x2C, 0x3A));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        getModel().addChangeListener(e -> repaint());
    }

    public void applyColors(Color bg, Color hover, Color fg) {
        this.bgColor = bg;
        this.hoverColor = hover;
        setForeground(fg);
        repaint();
    }

    /** Pass a color to show this button as the active tool, or null to clear it. */
    public void setArmed(Color borderColor) {
        this.armedBorderColor = borderColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color c = (getModel().isRollover() || getModel().isPressed()) ? hoverColor : bgColor;
        g2.setColor(c);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
        if (armedBorderColor != null) {
            g2.setColor(armedBorderColor);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
