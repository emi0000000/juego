
import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * PROJECT: ÉXODO SIMULADO [CORE: М.Е.Н.Т.О.С. v8.0]
 */

public class ProjectExodusAdvanced extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private MainMenuPanel menuPanel;
    private LabyrinthPanel gamePanel;

    public ProjectExodusAdvanced() {
        // Inicialización del Motor de Diseño usando la librería instalada por Maven
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Error al cargar FlatLaf. Revisa tu pom.xml.");
        }

        setTitle("PROYECTO ÉXODO [SECURE LINK] - NIGHT PROTOCOL");
        setSize(1024, 768);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        // UI Core
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        menuPanel = new MainMenuPanel(this);
        gamePanel = new LabyrinthPanel(this);

        mainPanel.add(menuPanel, "MENU");
        mainPanel.add(gamePanel, "LABYRINTH");

        add(mainPanel);
        setVisible(true);
    }

    public void switchState(String state) {
        cardLayout.show(mainPanel, state);
        if (state.equals("LABYRINTH")) {
            gamePanel.initNewGame();
            gamePanel.requestFocusInWindow();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ProjectExodusAdvanced());
    }
}

// --- MÓDULO 1: MENÚ CIBERNÉTICO ---
class MainMenuPanel extends JPanel implements ActionListener {
    private ProjectExodusAdvanced host;
    private Timer bgTimer;
    private ArrayList<Point> stars;
    private Random rand = new Random();

    public MainMenuPanel(ProjectExodusAdvanced host) {
        this.host = host;
        setLayout(new GridBagLayout());
        setBackground(new Color(5, 8, 12)); 

        stars = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            stars.add(new Point(rand.nextInt(1024), rand.nextInt(768)));
        }
        bgTimer = new Timer(50, this);
        bgTimer.start();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.alignmentX = Component.CENTER_ALIGNMENT;

        JLabel title = new JLabel("М.Е.Н.Т.О.С. v8.0");
        title.setFont(new Font("Consolas", Font.BOLD, 60));
        title.setForeground(new Color(0, 200, 255));
        
        JLabel subtitle = new JLabel(">> SIMULACIÓN ESTRUCTURAL <<");
        subtitle.setFont(new Font("Consolas", Font.PLAIN, 18));
        subtitle.setForeground(new Color(150, 150, 170));

        JButton btnStart = new JButton("INICIALIZAR DESPLIEGUE");
        btnStart.setFont(new Font("Consolas", Font.BOLD, 22));
        btnStart.setFocusPainted(false);
        btnStart.setBackground(new Color(15, 25, 35));
        btnStart.setForeground(new Color(0, 255, 150));
        btnStart.setBorder(BorderFactory.createLineBorder(new Color(0, 255, 150), 2));
        btnStart.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnStart.setPreferredSize(new Dimension(350, 60));

        btnStart.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) {
                btnStart.setBackground(new Color(0, 255, 150));
                btnStart.setForeground(Color.BLACK);
            }
            public void mouseExited(MouseEvent evt) {
                btnStart.setBackground(new Color(15, 25, 35));
                btnStart.setForeground(new Color(0, 255, 150));
            }
        });

        btnStart.addActionListener(e -> host.switchState("LABYRINTH"));

        add(title, gbc);
        add(Box.createVerticalStrut(10), gbc);
        add(subtitle, gbc);
        add(Box.createVerticalStrut(80), gbc);
        add(btnStart, gbc);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(new Color(0, 200, 255, 50));
        for (Point p : stars) {
            g2d.fillRect(p.x, p.y, 2, 2);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        for (Point p : stars) {
            p.y += 2;
            if (p.y > 768) {
                p.y = 0;
                p.x = rand.nextInt(1024);
            }
        }
        repaint();
    }
}

// --- MÓDULO 2: MOTOR DE JUEGO (LABERINTO) ---
class LabyrinthPanel extends JPanel implements ActionListener, KeyListener {
    private ProjectExodusAdvanced host;
    private Timer gameLoop;
    
    private final int TILE_SIZE = 50;
    private final int MAP_WIDTH = 40;
    private final int MAP_HEIGHT = 40;
    private int[][] map; 
    
    private double playerX, playerY;
    private int playerHealth = 100;
    private int playerCredits = 2500;
    private double playerSpeed = 6.0;
    private boolean up, down, left, right;
    
    private ArrayList<RainDrop> rainSystem;
    private ArrayList<Trail> playerTrail;
    private Random rand = new Random();
    private int animationFrame = 0;

    public LabyrinthPanel(ProjectExodusAdvanced host) {
        this.host = host;
        setBackground(new Color(5, 5, 8)); 
        setFocusable(true);
        addKeyListener(this);
        
        rainSystem = new ArrayList<>();
        playerTrail = new ArrayList<>();
        
        gameLoop = new Timer(16, this); 
    }

    public void initNewGame() {
        generateProceduralMap();
        playerX = TILE_SIZE * 2;
        playerY = TILE_SIZE * 2;
        up = down = left = right = false;
        
        rainSystem.clear();
        for(int i = 0; i < 200; i++) {
            rainSystem.add(new RainDrop());
        }
        
        gameLoop.start();
    }

    private void generateProceduralMap() {
        map = new int[MAP_WIDTH][MAP_HEIGHT];
        for (int x = 0; x < MAP_WIDTH; x++) {
            for (int y = 0; y < MAP_HEIGHT; y++) {
                map[x][y] = 1;
            }
        }
        int cx = 2, cy = 2;
        for (int i = 0; i < 800; i++) {
            map[cx][cy] = 0;
            if(cx+1 < MAP_WIDTH) map[cx+1][cy] = 0;
            if(cy+1 < MAP_HEIGHT) map[cx][cy+1] = 0;

            int dir = rand.nextInt(4);
            if (dir == 0 && cx > 2) cx -= 2;
            else if (dir == 1 && cx < MAP_WIDTH - 3) cx += 2;
            else if (dir == 2 && cy > 2) cy -= 2;
            else if (dir == 3 && cy < MAP_HEIGHT - 3) cy += 2;
        }

        for(int i = 0; i < 30; i++) {
            int rx = rand.nextInt(MAP_WIDTH);
            int ry = rand.nextInt(MAP_HEIGHT);
            if(map[rx][ry] == 0) {
                map[rx][ry] = 2;
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        double nextX = playerX;
        double nextY = playerY;

        if (up) nextY -= playerSpeed;
        if (down) nextY += playerSpeed;
        if (left) nextX -= playerSpeed;
        if (right) nextX += playerSpeed;

        int tileX = (int) (nextX / TILE_SIZE);
        int tileY = (int) (nextY / TILE_SIZE);
        int tileRight = (int) ((nextX + 30) / TILE_SIZE);
        int tileBottom = (int) ((nextY + 30) / TILE_SIZE);

        if (tileX >= 0 && tileRight < MAP_WIDTH && tileY >= 0 && tileBottom < MAP_HEIGHT) {
            if (map[tileX][tileY] != 1 && map[tileRight][tileY] != 1 &&
                map[tileX][tileBottom] != 1 && map[tileRight][tileBottom] != 1) {
                playerX = nextX;
                playerY = nextY;
            }
        }

        int centerTileX = (int) ((playerX + 15) / TILE_SIZE);
        int centerTileY = (int) ((playerY + 15) / TILE_SIZE);
        if(map[centerTileX][centerTileY] == 2) {
            map[centerTileX][centerTileY] = 0; 
            playerCredits += 150; 
        }

        if (up || down || left || right) {
            animationFrame++;
            if (animationFrame % 3 == 0) {
                playerTrail.add(new Trail(playerX + 5, playerY + 30));
            }
        }

        Iterator<Trail> it = playerTrail.iterator();
        while (it.hasNext()) {
            Trail t = it.next();
            t.life -= 0.05f;
            if (t.life <= 0) it.remove();
        }
        for(RainDrop r : rainSystem) r.update();

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int camX = (int)playerX - getWidth() / 2 + 15;
        int camY = (int)playerY - getHeight() / 2 + 15;

        g2d.translate(-camX, -camY);

        for (int x = 0; x < MAP_WIDTH; x++) {
            for (int y = 0; y < MAP_HEIGHT; y++) {
                if (map[x][y] == 1) {
                    g2d.setColor(new Color(20, 25, 35)); 
                    g2d.fillRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    g2d.setColor(new Color(40, 45, 60)); 
                    g2d.drawRect(x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                } else if (map[x][y] == 2) {
                    g2d.setColor(new Color(0, 255, 200));
                    g2d.fillOval(x * TILE_SIZE + 15, y * TILE_SIZE + 15, 20, 20);
                }
            }
        }

        for(Trail t : playerTrail) {
            g2d.setColor(new Color(0, 200, 255, (int)(t.life * 255)));
            g2d.fillRect((int)t.x, (int)t.y, 20, 5);
        }

        g2d.setColor(new Color(0, 150, 255));
        g2d.fillRect((int)playerX, (int)playerY, 30, 30);
        g2d.setColor(Color.WHITE);
        
        if ((animationFrame / 10) % 2 == 0 && (up || down || left || right)) {
            g2d.fillRect((int)playerX, (int)playerY + 30, 10, 10);
            g2d.fillRect((int)playerX + 20, (int)playerY + 30, 10, 10);
        } else {
            g2d.fillRect((int)playerX + 5, (int)playerY + 30, 10, 10);
            g2d.fillRect((int)playerX + 15, (int)playerY + 30, 10, 10);
        }

        g2d.translate(camX, camY);

        g2d.setColor(new Color(150, 180, 255, 100));
        for(RainDrop r : rainSystem) {
            g2d.drawLine((int)r.x, (int)r.y, (int)r.x - 2, (int)r.y + 10);
        }

        Point2D center = new Point2D.Float(getWidth() / 2f, getHeight() / 2f);
        float[] dist = {0.0f, 0.7f, 1.0f};
        Color[] colors = {
            new Color(0, 0, 0, 0),       
            new Color(5, 8, 12, 200),    
            new Color(5, 5, 8, 255)      
        };
        RadialGradientPaint p = new RadialGradientPaint(center, 350, dist, colors);
        g2d.setPaint(p);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(10, 10, 350, 40);
        g2d.setColor(new Color(0, 255, 150));
        g2d.setFont(new Font("Consolas", Font.BOLD, 18));
        g2d.drawString("INTEGRIDAD: " + playerHealth + "% | NODOS FINCH: " + playerCredits, 20, 35);
        
        g2d.setColor(Color.GRAY);
        g2d.drawString("[ESC] ABORTAR SIMULACIÓN", getWidth() - 250, 35);
    }

    class Trail {
        double x, y;
        float life = 1.0f;
        public Trail(double x, double y) { this.x = x; this.y = y; }
    }

    class RainDrop {
        double x, y, speed;
        public RainDrop() { reset(); y = rand.nextInt(768); }
        public void reset() {
            x = rand.nextInt(1024 + 200); 
            y = -20;
            speed = 15 + rand.nextDouble() * 10;
        }
        public void update() {
            y += speed;
            x -= speed / 3; 
            if (y > 768 || x < 0) reset();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) up = true;
        if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) down = true;
        if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) left = true;
        if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) right = true;
        
        if (key == KeyEvent.VK_ESCAPE) {
            gameLoop.stop();
            host.switchState("MENU");
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) up = false;
        if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) down = false;
        if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) left = false;
        if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) right = false;
    }

    @Override public void keyTyped(KeyEvent e) {}
}