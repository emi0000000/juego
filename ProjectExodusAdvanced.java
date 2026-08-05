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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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

    private static boolean gamepadEnabled = false;

    /**
     * Qué pantalla tiene el mando.
     *
     * Solo una puede leerlo por fotograma. El menú y el juego tienen cada uno su
     * propio temporizador y el del menú nunca se detiene, así que sin esta marca
     * los dos llamaban a Gamepad.poll() en el mismo fotograma: la primera lectura
     * se comía la detección de "recién pulsado" y el juego no veía ningún botón,
     * mientras el menú seguía activando sus botones por detrás y reiniciaba la partida.
     */
    private static boolean menuActive = false;

    private final CardLayout cardLayout;
    private final JPanel mainPanel;
    private final MainMenuPanel menuPanel;
    private final LabyrinthPanel gamePanel;

    /** true si este sistema pudo enlazar con XInput y se puede usar mando. */
    static boolean gamepadEnabled() { return gamepadEnabled; }

    /** true mientras se está en el menú principal; false mientras se juega. */
    static boolean isMenuActive() { return menuActive; }

    /** Modo secreto que se activa con el código del menú. */
    private static boolean goldenMode = false;

    static boolean isGoldenMode() { return goldenMode; }

    static void activateGoldenMode() {
        goldenMode = true;
        Achievements.unlock("konami");
    }

    /**
     * Monta la ventana: tema oscuro, tamaño fijo y las dos pantallas del juego
     * (menú y partida) apiladas en un CardLayout, que permite cambiar de una a
     * otra con una sola línea.
     */
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
            public void windowClosing(WindowEvent e) {
                AudioEngine.shutdown();
                if (gamepadEnabled) Gamepad.shutdown();
            }
        });

        menuActive = true;
        setVisible(true);
        if (Settings.fullscreen()) applyFullscreen();
    }

    /**
     * Cambia entre ventana y pantalla completa.
     *
     * Se usa una ventana sin bordes maximizada en vez del modo exclusivo del
     * sistema: cambia igual de rápido, no altera la resolución del escritorio y
     * si el juego se cerrara mal no deja la pantalla en un estado raro.
     *
     * El dibujo no cambia: sigue siendo de 1024x768 y se escala solo.
     */
    public void toggleFullscreen() {
        boolean queremos = !Settings.fullscreen();
        Settings.setFullscreen(queremos);
        applyFullscreen();
    }

    /**
     * Aplica el modo de pantalla que toque. Hay que cerrar y volver a abrir la
     * ventana porque en Swing los bordes no se pueden quitar con ella visible.
     */
    private void applyFullscreen() {
        boolean full = Settings.fullscreen();
        dispose();
        setUndecorated(full);
        setResizable(full);
        if (full) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            setExtendedState(JFrame.NORMAL);
            setSize(1024, 768);
            setLocationRelativeTo(null);
        }
        setVisible(true);
        (menuActive ? (JPanel) menuPanel : gamePanel).requestFocusInWindow();
    }

    public void goToMenu() {
        menuActive = true;
        cardLayout.show(mainPanel, "MENU");
        menuPanel.refresh();
        AudioEngine.setMusicMode(0);
        AudioEngine.setBossMode(false);
        AudioEngine.setTension(0);
    }

    public void startNewGame(int difficulty) {
        menuActive = false;
        cardLayout.show(mainPanel, "LABYRINTH");
        gamePanel.startNewGame(difficulty);
    }

    public void continueSavedGame() {
        menuActive = false;
        cardLayout.show(mainPanel, "LABYRINTH");
        gamePanel.continueSavedGame();
    }

    /**
     * Punto de entrada. Enlaza el mando, carga los logros y arranca el audio y la
     * ventana dentro del hilo de Swing, que es el único desde el que se puede
     * tocar la interfaz.
     */
    public static void main(String[] args) {
        // El soporte de mando usa java.lang.foreign, que existe desde Java 22. En una
        // versión anterior esto lanza NoClassDefFoundError; se captura y el juego
        // sigue funcionando con el teclado como siempre.
        try {
            gamepadEnabled = Gamepad.init();
        } catch (Throwable t) {
            gamepadEnabled = false;
        }
        System.out.println(gamepadEnabled
            ? "Mando: XInput listo (" + Gamepad.getDeviceInfo() + "). Conecta un mando de Xbox cuando quieras."
            : "Mando: no disponible en este sistema. Se juega con teclado.");

        Achievements.load();

        SwingUtilities.invokeLater(() -> {
            AudioEngine.start();
            Settings.load();          // después de arrancar el audio, para aplicar música y efectos
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
    private final JButton btnEasy;
    private final JButton btnNormal;
    private final JButton btnNightmare;
    private final JButton btnAchievements;
    private final JButton btnOptions;
    private final JLabel saveLabel;
    private final JLabel padHint;
    private final Timer bgTimer;
    private final JPanel content;
    private boolean padSelected = false;
    private int frame = 0;

    static final int OVERLAY_NINGUNO = 0;
    static final int OVERLAY_LOGROS = 1;
    static final int OVERLAY_OPCIONES = 2;
    private int overlay = OVERLAY_NINGUNO;
    private int optionSelection = 0;
    private int achievementScroll = 0;

    /** Botones que se pueden recorrer con el mando, en orden. */
    private final ArrayList<JButton> padTargets = new ArrayList<>();
    private final ArrayList<Color> padColors = new ArrayList<>();
    private int padSelection = 0;
    private int padStickCooldown = 0;

    /** El código de toda la vida. Escribirlo en el menú desbloquea el modo dorado. */
    private static final int[] KONAMI = {
        KeyEvent.VK_UP, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_DOWN,
        KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
        KeyEvent.VK_B, KeyEvent.VK_A,
    };
    private int konamiProgress = 0;

    MainMenuPanel(ProjectExodusAdvanced host) {
        this.host = host;
        setLayout(new GridBagLayout());
        setBackground(new Color(6, 5, 9));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) { handleMenuKey(e.getKeyCode()); }
        });

        for (int i = 0; i < 170; i++) rain.add(new Point(rand.nextInt(1024), rand.nextInt(768)));
        bgTimer = new Timer(30, this);
        bgTimer.start();

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
        btnEasy = difficultyButton("FÁCIL", 0, new Color(60, 190, 90));
        btnNormal = difficultyButton("NORMAL", 1, new Color(255, 60, 60));
        btnNightmare = difficultyButton("PESADILLA", 2, new Color(160, 40, 220));
        btnRow.add(btnEasy);
        btnRow.add(btnNormal);
        btnRow.add(btnNightmare);

        JPanel soundRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 4));
        soundRow.setOpaque(false);
        btnMusic = styledButton("MÚSICA: ON", new Color(200, 200, 200), 160, 36);
        btnSfx = styledButton("EFECTOS: ON", new Color(200, 200, 200), 160, 36);
        btnAchievements = styledButton("LOGROS", new Color(255, 190, 70), 160, 36);
        btnOptions = styledButton("OPCIONES", new Color(150, 190, 255), 160, 36);
        btnMusic.addActionListener(e -> { AudioEngine.toggleMusic(); refreshSoundLabels(); });
        btnSfx.addActionListener(e -> { AudioEngine.toggleSfx(); refreshSoundLabels(); });
        btnAchievements.addActionListener(e -> openOverlay(OVERLAY_LOGROS));
        btnOptions.addActionListener(e -> openOverlay(OVERLAY_OPCIONES));
        soundRow.add(btnMusic);
        soundRow.add(btnSfx);

        JPanel extraRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 4));
        extraRow.setOpaque(false);
        extraRow.add(btnAchievements);
        extraRow.add(btnOptions);

        JLabel hint = new JLabel("En el juego: [WASD] Mover  [ESPACIO] Atacar  [E] Tienda  [M] Mapa  [P] Pausa");
        hint.setFont(new Font("Consolas", Font.ITALIC, 13));
        hint.setForeground(new Color(100, 100, 100));

        padHint = new JLabel(" ");
        padHint.setFont(new Font("Consolas", Font.BOLD, 14));
        padHint.setForeground(new Color(90, 220, 120));

        content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.add(title, gbc);
        content.add(Box.createVerticalStrut(12), gbc);
        content.add(subtitle, gbc);
        content.add(Box.createVerticalStrut(34), gbc);
        content.add(btnContinue, gbc);
        content.add(saveLabel, gbc);
        content.add(Box.createVerticalStrut(22), gbc);
        content.add(diffLabel, gbc);
        content.add(btnRow, gbc);
        content.add(Box.createVerticalStrut(10), gbc);
        content.add(soundRow, gbc);
        content.add(extraRow, gbc);
        content.add(Box.createVerticalStrut(14), gbc);
        content.add(hint, gbc);
        content.add(padHint, gbc);

        setLayout(new java.awt.BorderLayout());
        add(content, java.awt.BorderLayout.CENTER);

        // Orden en el que el mando recorre los botones
        padTargets.add(btnContinue);      padColors.add(new Color(90, 200, 255));
        padTargets.add(btnEasy);          padColors.add(new Color(60, 190, 90));
        padTargets.add(btnNormal);        padColors.add(new Color(255, 60, 60));
        padTargets.add(btnNightmare);     padColors.add(new Color(160, 40, 220));
        padTargets.add(btnMusic);         padColors.add(new Color(200, 200, 200));
        padTargets.add(btnSfx);           padColors.add(new Color(200, 200, 200));
        padTargets.add(btnAchievements);  padColors.add(new Color(255, 190, 70));
        padTargets.add(btnOptions);       padColors.add(new Color(150, 190, 255));

        refresh();
    }

    // ------------------------------------------------------------------
    // Pantallas de logros y opciones
    // ------------------------------------------------------------------

    private void openOverlay(int which) {
        overlay = which;
        optionSelection = 0;
        content.setVisible(false);
        AudioEngine.play("blip");
        requestFocusInWindow();
        repaint();
    }

    private void closeOverlay() {
        overlay = OVERLAY_NINGUNO;
        content.setVisible(true);
        AudioEngine.play("blip", 0.5);
        refresh();
    }

    /** Las opciones que se pueden cambiar, en el orden en que se muestran. */
    private String optionLabel(int i) {
        switch (i) {
            case 0: return "Pantalla completa [F11] " + onOff(Settings.fullscreen());
            case 1: return "Música ................ " + onOff(AudioEngine.isMusicOn());
            case 2: return "Efectos de sonido ..... " + onOff(AudioEngine.isSfxOn());
            case 3: return "Sustos de pantalla .... " + onOff(Settings.jumpscares());
            case 4: return "Destellos de pantalla . " + onOff(Settings.screenFlashes());
            case 5: return "Temblor de cámara ..... " + onOff(Settings.screenShake());
            case 6: return "Números de daño ....... " + onOff(Settings.damageNumbers());
            default: return "Vibración del mando ... " + onOff(Settings.rumble());
        }
    }

    private static final int OPTION_COUNT = 8;

    private static String onOff(boolean b) { return b ? "SÍ" : "NO"; }

    /** Cambia la opción seleccionada de la pantalla de opciones. */
    private void toggleOption(int i) {
        switch (i) {
            case 0: if (host != null) host.toggleFullscreen(); break;
            case 1: AudioEngine.toggleMusic(); break;
            case 2: AudioEngine.toggleSfx(); break;
            case 3: Settings.toggleJumpscares(); break;
            case 4: Settings.toggleScreenFlashes(); break;
            case 5: Settings.toggleScreenShake(); break;
            case 6: Settings.toggleDamageNumbers(); break;
            default: Settings.toggleRumble(); break;
        }
        AudioEngine.play("blip", 0.6);
        repaint();
    }

    /**
     * Recorre los botones del menú con la cruceta o el stick y los activa con (A).
     * Se llama desde el temporizador de la lluvia, así que ya va a 33 fotogramas
     * por segundo sin necesidad de un hilo aparte.
     */
    private void handleGamepad() {
        if (!ProjectExodusAdvanced.gamepadEnabled()) return;

        // Este temporizador nunca se detiene, ni siquiera durante la partida.
        // Si el menú leyera el mando mientras se juega, cada (A) para atacar
        // pulsaría además el botón de dificultad y reiniciaría el barrio.
        if (!ProjectExodusAdvanced.isMenuActive()) {
            if (padSelected) { clearPadHighlight(); padSelected = false; padHint.setText(" "); }
            return;
        }

        Gamepad.poll();

        if (!Gamepad.isConnected()) {
            if (padSelected) { clearPadHighlight(); padSelected = false; }
            return;
        }

        // Dentro de logros u opciones el mando maneja la lista, no los botones
        if (overlay != OVERLAY_NINGUNO) {
            if (padStickCooldown > 0) padStickCooldown--;
            double sy = Gamepad.moveY();
            if (padStickCooldown == 0 && Math.abs(sy) > 0.5) {
                padStickCooldown = 12;
                handleMenuKey(sy < 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN);
            }
            if (Gamepad.pressed(Gamepad.A)) handleMenuKey(KeyEvent.VK_ENTER);
            if (Gamepad.pressed(Gamepad.B) || Gamepad.pressed(Gamepad.START)) closeOverlay();
            return;
        }

        if (!padSelected) {
            padSelected = true;
            padSelection = btnContinue.isVisible() ? 0 : 1;
            padHint.setText("MANDO CONECTADO   -   cruceta para elegir, (A) para aceptar");
        }

        if (padStickCooldown > 0) padStickCooldown--;

        double y = Gamepad.moveY();
        double x = Gamepad.moveX();
        int step = 0;
        if (Math.abs(y) > 0.5) step = y < 0 ? -1 : 1;
        else if (Math.abs(x) > 0.5) step = x < 0 ? -1 : 1;

        if (step != 0 && padStickCooldown == 0) {
            padStickCooldown = 12;
            int guard = 0;
            do {
                padSelection = (padSelection + step + padTargets.size()) % padTargets.size();
                guard++;
            } while (!padTargets.get(padSelection).isVisible() && guard < padTargets.size() * 2);
            AudioEngine.play("blip", 0.4);
        }

        if (Gamepad.pressed(Gamepad.A)) {
            JButton target = padTargets.get(padSelection);
            if (target.isVisible()) {
                AudioEngine.play("blip");
                target.doClick();
            }
        }

        applyPadHighlight();
    }

    /** Marca con su color el botón que tiene ahora el mando y apaga los demás. */
    private void applyPadHighlight() {
        for (int i = 0; i < padTargets.size(); i++) {
            JButton b = padTargets.get(i);
            Color c = padColors.get(i);
            if (i == padSelection) {
                b.setBackground(c);
                b.setForeground(Color.BLACK);
            } else {
                b.setBackground(new Color(18, 18, 22));
                b.setForeground(c);
            }
        }
    }

    private void clearPadHighlight() {
        for (int i = 0; i < padTargets.size(); i++) {
            padTargets.get(i).setBackground(new Color(18, 18, 22));
            padTargets.get(i).setForeground(padColors.get(i));
        }
    }

    /**
     * Teclado del menú: pantalla completa, navegación de las pantallas de logros
     * y opciones, y el código secreto.
     */
    private void handleMenuKey(int key) {
        if (key == KeyEvent.VK_F11) {
            if (host != null) host.toggleFullscreen();
            return;
        }

        if (overlay != OVERLAY_NINGUNO) {
            if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_BACK_SPACE) { closeOverlay(); return; }
            if (overlay == OVERLAY_OPCIONES) {
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    optionSelection = (optionSelection - 1 + OPTION_COUNT) % OPTION_COUNT;
                    repaint();
                } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    optionSelection = (optionSelection + 1) % OPTION_COUNT;
                    repaint();
                } else if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE
                        || key == KeyEvent.VK_LEFT || key == KeyEvent.VK_RIGHT) {
                    toggleOption(optionSelection);
                }
            } else {
                int max = Math.max(0, Achievements.total() - 12);
                if (key == KeyEvent.VK_UP || key == KeyEvent.VK_W) {
                    achievementScroll = Math.max(0, achievementScroll - 1);
                    repaint();
                } else if (key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S) {
                    achievementScroll = Math.min(max, achievementScroll + 1);
                    repaint();
                }
            }
            return;
        }

        if (key == KONAMI[konamiProgress]) {
            konamiProgress++;
            if (konamiProgress >= KONAMI.length) {
                konamiProgress = 0;
                if (!ProjectExodusAdvanced.isGoldenMode()) {
                    ProjectExodusAdvanced.activateGoldenMode();
                    AudioEngine.play("rescue");
                }
                refresh();
            }
        } else {
            // Si falla, se reinicia (pero la primera tecla del código vuelve a contar)
            konamiProgress = key == KONAMI[0] ? 1 : 0;
        }
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
        btnAchievements.setText("LOGROS  " + Achievements.unlockedCount() + "/" + Achievements.total());
        padHint.setText(ProjectExodusAdvanced.isGoldenMode()
            ? "MODO GATO DORADO ACTIVO" : " ");
        requestFocusInWindow();
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

    /** Botón del menú con el estilo del juego: borde de color e inversión al pasar el ratón. */
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

    /**
     * Fondo del menú: siluetas de edificios, lluvia y Nox acechando en la esquina.
     * Si hay abierta una pantalla de logros u opciones, dibuja esa en su lugar.
     */
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

        if (overlay == OVERLAY_LOGROS) { drawAchievements(g2d); return; }
        if (overlay == OVERLAY_OPCIONES) { drawOptions(g2d); return; }

        // Nox acechando en la esquina inferior
        Sprites.drawNox(g2d, 70, getHeight() - 130, 3, frame, false, false, false, 0,
                        ProjectExodusAdvanced.isGoldenMode());
    }

    /** Lista de logros con su barra de progreso. Los secretos no ensenan su nombre hasta que se consiguen. */
    private void drawAchievements(Graphics2D g2d) {
        g2d.setColor(new Color(6, 5, 9, 246));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setFont(new Font("Consolas", Font.BOLD, 30));
        g2d.setColor(new Color(255, 190, 70));
        g2d.drawString("LOGROS", 60, 62);

        String progreso = Achievements.unlockedCount() + " de " + Achievements.total();
        g2d.setFont(new Font("Consolas", Font.BOLD, 18));
        g2d.drawString(progreso, getWidth() - 60 - g2d.getFontMetrics().stringWidth(progreso), 62);

        // Barra de progreso
        int barW = getWidth() - 120;
        g2d.setColor(new Color(30, 26, 20));
        g2d.fillRect(60, 74, barW, 8);
        g2d.setColor(new Color(255, 190, 70));
        g2d.fillRect(60, 74, barW * Achievements.unlockedCount() / Math.max(1, Achievements.total()), 8);

        java.util.List<Achievements.Ach> lista = Achievements.all();
        int visibles = 12;
        int y = 118;

        for (int i = achievementScroll; i < Math.min(lista.size(), achievementScroll + visibles); i++) {
            Achievements.Ach a = lista.get(i);
            boolean oculto = a.secret && !a.unlocked;

            g2d.setColor(a.unlocked ? new Color(24, 20, 14) : new Color(16, 15, 18));
            g2d.fillRect(60, y - 18, getWidth() - 120, 44);
            g2d.setColor(a.unlocked ? new Color(255, 190, 70, 120) : new Color(60, 60, 66));
            g2d.drawRect(60, y - 18, getWidth() - 120, 44);

            g2d.setColor(a.unlocked ? new Color(255, 190, 70) : new Color(52, 52, 58));
            g2d.fillOval(72, y - 10, 26, 26);
            g2d.setColor(a.unlocked ? new Color(30, 22, 10) : new Color(90, 90, 96));
            g2d.setFont(new Font("Consolas", Font.BOLD, 16));
            g2d.drawString(a.unlocked ? "*" : "?", 81, y + 8);

            g2d.setFont(new Font("Consolas", Font.BOLD, 15));
            g2d.setColor(a.unlocked ? Color.WHITE : new Color(110, 110, 118));
            g2d.drawString(oculto ? "LOGRO SECRETO" : a.name, 112, y - 1);

            g2d.setFont(new Font("Consolas", Font.PLAIN, 13));
            g2d.setColor(a.unlocked ? new Color(180, 180, 190) : new Color(80, 80, 88));
            g2d.drawString(oculto ? "Sigue jugando para descubrirlo." : a.description, 112, y + 17);

            y += 50;
        }

        g2d.setFont(new Font("Consolas", Font.ITALIC, 14));
        g2d.setColor(new Color(150, 150, 155));
        g2d.drawString("[W/S o flechas] Desplazar     [ESC] Volver", 60, getHeight() - 26);
    }

    /** Pantalla de opciones, con las de accesibilidad explicadas arriba. */
    private void drawOptions(Graphics2D g2d) {
        g2d.setColor(new Color(6, 5, 9, 246));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setFont(new Font("Consolas", Font.BOLD, 30));
        g2d.setColor(new Color(150, 190, 255));
        g2d.drawString("OPCIONES", 60, 70);

        g2d.setFont(new Font("Consolas", Font.ITALIC, 15));
        g2d.setColor(new Color(140, 140, 150));
        g2d.drawString("Este es un juego de terror. Si los sustos o los destellos te", 60, 108);
        g2d.drawString("molestan, apágalos aquí: el juego se sigue jugando igual.", 60, 130);

        int y = 200;
        for (int i = 0; i < OPTION_COUNT; i++) {
            boolean sel = i == optionSelection;
            if (sel) {
                g2d.setColor(new Color(150, 190, 255, 45));
                g2d.fillRect(56, y - 24, getWidth() - 112, 38);
            }
            g2d.setFont(new Font("Consolas", Font.BOLD, 20));
            g2d.setColor(sel ? new Color(255, 240, 160) : new Color(215, 215, 225));
            g2d.drawString((sel ? "> " : "  ") + optionLabel(i), 68, y);
            y += 48;
        }

        g2d.setFont(new Font("Consolas", Font.ITALIC, 14));
        g2d.setColor(new Color(150, 150, 155));
        g2d.drawString("[W/S] Elegir    [ENTER] Cambiar    [ESC] Volver", 60, getHeight() - 26);
    }

    /**
     * Late 33 veces por segundo: mueve la lluvia del fondo, lee el mando y repinta.
     * Este temporizador no se detiene nunca, ni durante la partida; por eso el mando
     * solo se lee aquí cuando el menú es la pantalla activa.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        frame++;
        handleGamepad();
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
