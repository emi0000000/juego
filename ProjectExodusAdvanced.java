import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Random;

/**
 * ÉXODO: LA VENGANZA DE NOX
 *
 * Nox, un gato callejero, cruza seis barrios de una ciudad controlada por la pandilla
 * de perros de Don Colmillo para rescatar a su familia. Ver HISTORIA.md.
 */
public class ProjectExodusAdvanced extends JFrame {

    private final CardLayout cardLayout;
    private final JPanel mainPanel;
    private final MainMenuPanel menuPanel;
    private final LabyrinthPanel gamePanel;

    public ProjectExodusAdvanced() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Error al cargar FlatLaf: " + ex.getMessage());
        }

        setTitle("ÉXODO: LA VENGANZA DE NOX");
        setSize(1024, 768);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        menuPanel = new MainMenuPanel(this);
        gamePanel = new LabyrinthPanel(this);

        mainPanel.add(menuPanel, "MENU");
        mainPanel.add(gamePanel, "LABYRINTH");
        add(mainPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { AudioEngine.shutdown(); }
        });

        setVisible(true);
    }

    public void goToMenu() {
        cardLayout.show(mainPanel, "MENU");
        menuPanel.refresh();
        AudioEngine.setMusicMode(0);
        AudioEngine.setBossMode(false);
        AudioEngine.setTension(0);
    }

    public void startNewGame(int difficulty) {
        cardLayout.show(mainPanel, "LABYRINTH");
        gamePanel.startNewGame(difficulty);
    }

    public void continueSavedGame() {
        cardLayout.show(mainPanel, "LABYRINTH");
        gamePanel.continueSavedGame();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AudioEngine.start();
            AudioEngine.setMusicMode(0);
            new ProjectExodusAdvanced();
        });
    }
}

/** Menú principal: lluvia de fondo, Nox acechando y selección de dificultad. */
class MainMenuPanel extends JPanel implements ActionListener {

    private final ProjectExodusAdvanced host;
    private final ArrayList<Point> rain = new ArrayList<>();
    private final Random rand = new Random();
    private final JButton btnContinue;
    private final JButton btnMusic;
    private final JButton btnSfx;
    private final JLabel saveLabel;
    private int frame = 0;

    MainMenuPanel(ProjectExodusAdvanced host) {
        this.host = host;
        setLayout(new GridBagLayout());
        setBackground(new Color(6, 5, 9));

        for (int i = 0; i < 170; i++) rain.add(new Point(rand.nextInt(1024), rand.nextInt(768)));
        new Timer(30, this).start();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.CENTER;

        JLabel title = new JLabel("ÉXODO: LA VENGANZA DE NOX");
        title.setFont(new Font("Consolas", Font.BOLD, 48));
        title.setForeground(new Color(255, 40, 40));

        JLabel subtitle = new JLabel(">> RESCATA A TU FAMILIA DE LA PANDILLA DE COLMILLO <<");
        subtitle.setFont(new Font("Consolas", Font.PLAIN, 17));
        subtitle.setForeground(new Color(160, 160, 165));

        btnContinue = styledButton("CONTINUAR PARTIDA", new Color(90, 200, 255), 330, 50);
        btnContinue.addActionListener(e -> host.continueSavedGame());

        saveLabel = new JLabel(" ");
        saveLabel.setFont(new Font("Consolas", Font.ITALIC, 13));
        saveLabel.setForeground(new Color(110, 160, 190));

        JLabel diffLabel = new JLabel("ELIGE TU NIVEL DE PESADILLA:");
        diffLabel.setFont(new Font("Consolas", Font.BOLD, 16));
        diffLabel.setForeground(new Color(205, 205, 205));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        btnRow.setOpaque(false);
        btnRow.add(difficultyButton("FÁCIL", 0, new Color(60, 190, 90)));
        btnRow.add(difficultyButton("NORMAL", 1, new Color(255, 60, 60)));
        btnRow.add(difficultyButton("PESADILLA", 2, new Color(160, 40, 220)));

        JPanel soundRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 4));
        soundRow.setOpaque(false);
        btnMusic = styledButton("MÚSICA: ON", new Color(200, 200, 200), 170, 36);
        btnSfx = styledButton("EFECTOS: ON", new Color(200, 200, 200), 170, 36);
        btnMusic.addActionListener(e -> { AudioEngine.toggleMusic(); refreshSoundLabels(); });
        btnSfx.addActionListener(e -> { AudioEngine.toggleSfx(); refreshSoundLabels(); });
        soundRow.add(btnMusic);
        soundRow.add(btnSfx);

        JLabel hint = new JLabel("En el juego: [WASD] Mover  [ESPACIO] Atacar  [E] Tienda  [M] Mapa  [P] Pausa");
        hint.setFont(new Font("Consolas", Font.ITALIC, 13));
        hint.setForeground(new Color(100, 100, 100));

        add(title, gbc);
        add(Box.createVerticalStrut(12), gbc);
        add(subtitle, gbc);
        add(Box.createVerticalStrut(40), gbc);
        add(btnContinue, gbc);
        add(saveLabel, gbc);
        add(Box.createVerticalStrut(26), gbc);
        add(diffLabel, gbc);
        add(btnRow, gbc);
        add(Box.createVerticalStrut(14), gbc);
        add(soundRow, gbc);
        add(Box.createVerticalStrut(18), gbc);
        add(hint, gbc);

        refresh();
    }

    /** Se llama cada vez que se vuelve al menú, para reflejar la partida guardada. */
    void refresh() {
        boolean has = SaveManager.hasSave();
        btnContinue.setVisible(has);
        if (has) {
            SaveManager.Progress p = SaveManager.load();
            int rescued = 0;
            for (boolean b : p.family) if (b) rescued++;
            saveLabel.setText("Barrio " + p.level + "   |   $" + p.money
                            + "   |   " + rescued + "/3 rescatados");
        } else {
            saveLabel.setText(" ");
        }
        refreshSoundLabels();
        revalidate();
        repaint();
    }

    private void refreshSoundLabels() {
        btnMusic.setText("MÚSICA: " + (AudioEngine.isMusicOn() ? "ON" : "OFF"));
        btnSfx.setText("EFECTOS: " + (AudioEngine.isSfxOn() ? "ON" : "OFF"));
    }

    private JButton difficultyButton(String text, int difficulty, Color color) {
        JButton b = styledButton(text, color, 180, 55);
        b.addActionListener(e -> host.startNewGame(difficulty));
        return b;
    }

    private JButton styledButton(String text, Color color, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(new Font("Consolas", Font.BOLD, h > 40 ? 18 : 14));
        b.setFocusPainted(false);
        b.setBackground(new Color(18, 18, 22));
        b.setForeground(color);
        b.setBorder(BorderFactory.createLineBorder(color, 2));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(w, h));
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent evt) {
                b.setBackground(color);
                b.setForeground(Color.BLACK);
                AudioEngine.play("blip", 0.35);
            }

            @Override
            public void mouseExited(MouseEvent evt) {
                b.setBackground(new Color(18, 18, 22));
                b.setForeground(color);
            }
        });
        return b;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Siluetas de edificios al fondo
        g2d.setColor(new Color(13, 12, 18));
        for (int i = 0; i < 14; i++) {
            int h = 120 + Sprites.hash(i, 7) % 260;
            g2d.fillRect(i * 78, getHeight() - h, 70, h);
        }

        g2d.setColor(new Color(110, 115, 135, 60));
        for (Point p : rain) g2d.drawLine(p.x, p.y, p.x - 2, p.y + 10);

        // Nox acechando en la esquina inferior
        Sprites.drawNox(g2d, 70, getHeight() - 130, 3, frame, false, false, false, 0);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        frame++;
        for (Point p : rain) {
            p.y += 20;
            p.x -= 4;
            if (p.y > getHeight()) {
                p.y = 0;
                p.x = rand.nextInt(1024 + 200);
            }
        }
        repaint();
    }
}
