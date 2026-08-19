import javax.swing.SwingUtilities;

/**
 * Entry point for the Logic Gate Explorer application.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
