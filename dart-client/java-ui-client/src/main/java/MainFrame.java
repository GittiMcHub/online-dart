import javax.swing.*;

public class MainFrame extends JFrame {
    private GameConfigPanel gameconfig;

    public MainFrame() {
        setTitle("Dartboard Java-UI anthrax-v0.1.1");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);

        // Tabbed Pane erstellen
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tabs hinzufügen
        this.gameconfig = GameConfigPanel.getInstance();
        tabbedPane.add("Config", this.gameconfig);
        // TAB aktueller Spieler
        tabbedPane.add("Aktueller Spieler", new CurrentPlayerPanel());
        // TAB Trefferverteilung
        tabbedPane.add("Trefferverteilung", new DistributionPanel());
        // TAB Statisttik
        JScrollPane scrollPane = new JScrollPane(new StatistikPanel());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        tabbedPane.add("Statistik", scrollPane);
        // Ohne Scrollbalken
        //tabbedPane.add("Statistik", new StatistikPanel());
        tabbedPane.add("Game Server", new GameServerPanel());
        tabbedPane.add("Board Connector", new DartboardConnectorPanel());

        add(tabbedPane);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
