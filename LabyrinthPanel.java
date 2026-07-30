import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/** Motor del juego: la ciudad, los perros, los jefes y todo lo que ocurre dentro del barrio. */
public class LabyrinthPanel extends JPanel implements ActionListener, KeyListener {

    // Tipos de casilla del mapa
    private static final int T_CALLE = 0;
    private static final int T_MONEDA = 2;
    private static final int T_EDIFICIO = 3;
    private static final int T_TIENDA = 4;
    private static final int T_SALIDA = 5;

    // Tipos de perro
    private static final int E_CALLEJERO = 0;
    private static final int E_ARMADO = 1;
    private static final int E_CORREDOR = 2;
    private static final int E_BRUTO = 3;

    private static final int PLAYER_SIZE = 30;

    private final ProjectExodusAdvanced host;
    private final Timer gameLoop;

    private final int TILE_SIZE = 60;
    private final int MAP_WIDTH = 50;
    private final int MAP_HEIGHT = 50;
    private int[][] map;

    private double playerX, playerY;
    private int playerHealth = 100;
    private int playerMoney = 0;
    private final double playerSpeed = 7.0;
    private boolean up, down, left, right;
    private int facing = 1;
    private int currentLevel = 1;
    private static final int MAX_LEVEL = 6;

    // Dificultad
    private int pendingDifficulty = 1;
    private int difficulty = 1;
    private double diffEnemyMult = 1.0, diffHpMult = 1.0, diffDmgMult = 1.0;

    private LevelInfo[] levels;

    // Estados de pantalla
    private boolean showingPrologue = true;
    private boolean levelIntroActive = false;
    private int levelIntroTimer = 0;
    private boolean paused = false;
    private boolean showMinimap = false;
    private boolean isDead = false;
    private boolean gameWon = false;
    private boolean isTransitioning = false;
    private int transitionTimer = 0;
    private boolean continueRequested = false;

    // Familia de Nox
    private final String[] familyNames = {"WILL", "NINA", "MITSY"};
    private boolean[] familyRescued = new boolean[3];
    private boolean rescueMessageActive = false;
    private int rescueMessageTimer = 0;
    private String rescueMessageText = "";

    // Jefes
    private Enemy currentBoss;
    private boolean bossDefeated = false;
    private boolean bossEncountered = false;
    private boolean bossIntroActive = false;
    private int bossIntroTimer = 0;
    private boolean nearExitLocked = false;

    // Terror
    private double sanity = 100;
    private boolean flickerActive = false;
    private int flickerTimer = 0;
    private int flickerAlpha = 0;
    private int shakeFrames = 0;
    private int shakeMagnitude = 0;
    private boolean isJumpscareActive = false;
    private int jumpscareTimer = 0;
    private int jumpscareType = 0;

    // Tienda
    private boolean nearStore = false;
    private boolean isShopOpen = false;
    private int selectedShopItem = 0;
    private String shopMessage = "";
    private int shopMessageTimer = 0;
    private static final int SHOP_ITEMS = 5;

    // Arsenal
    private boolean hasSteelClaws = false;
    private boolean hasCrossbow = false;
    private boolean hasPlasmaLauncher = false;
    private int activeWeapon = 0;
    private boolean isAttacking = false;
    private int attackTimer = 0;
    private int weaponCooldown = 0;

    // Daño recibido
    private int damageCooldown = 0;
    private int hurtFlash = 0;

    // Estadísticas
    private int kills = 0;
    private int score = 0;
    private long levelStartMs = 0;
    private long accumulatedMs = 0;

    private final ArrayList<RainDrop> rainSystem = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<Projectile> bullets = new ArrayList<>();
    private final Random rand = new Random();
    private int animationFrame = 0;

    public LabyrinthPanel(ProjectExodusAdvanced host) {
        this.host = host;
        setBackground(new Color(5, 5, 8));
        setFocusable(true);
        addKeyListener(this);
        gameLoop = new Timer(16, this);
        levels = LevelInfo.buildCampaign();
    }

    // ------------------------------------------------------------------
    // Arranque de partida
    // ------------------------------------------------------------------

    public void startNewGame(int diff) {
        pendingDifficulty = diff;
        continueRequested = false;
        initGame();
    }

    public void continueSavedGame() {
        pendingDifficulty = SaveManager.load().difficulty;
        continueRequested = true;
        initGame();
    }

    private void initGame() {
        SaveManager.Progress p = continueRequested ? SaveManager.load() : new SaveManager.Progress();

        difficulty = continueRequested ? p.difficulty : pendingDifficulty;
        applyDifficulty();

        if (continueRequested) {
            currentLevel = Math.max(1, Math.min(MAX_LEVEL, p.level));
            playerHealth = Math.max(30, p.health);
            playerMoney = p.money;
            sanity = p.sanity;
            hasSteelClaws = p.claws;
            hasCrossbow = p.crossbow;
            hasPlasmaLauncher = p.plasma;
            familyRescued = p.family.clone();
            score = p.score;
            accumulatedMs = p.playTimeMs;
            activeWeapon = hasPlasmaLauncher ? 3 : hasCrossbow ? 2 : hasSteelClaws ? 1 : 0;
            showingPrologue = false;
        } else {
            currentLevel = 1;
            playerHealth = 100;
            playerMoney = 0;
            sanity = 100;
            hasSteelClaws = hasCrossbow = hasPlasmaLauncher = false;
            familyRescued = new boolean[3];
            score = 0;
            accumulatedMs = 0;
            activeWeapon = 0;
            showingPrologue = true;
        }

        kills = 0;
        isDead = false;
        gameWon = false;
        paused = false;
        showMinimap = false;
        flickerActive = false;
        shakeFrames = 0;
        damageCooldown = 0;
        hurtFlash = 0;
        weaponCooldown = 0;
        levelStartMs = System.currentTimeMillis();

        loadLevel();

        rainSystem.clear();
        for (int i = 0; i < 350; i++) rainSystem.add(new RainDrop());

        gameLoop.start();
        requestFocusInWindow();
    }

    private void applyDifficulty() {
        if (difficulty == 0) { diffEnemyMult = 0.65; diffHpMult = 0.75; diffDmgMult = 0.70; }
        else if (difficulty == 2) { diffEnemyMult = 1.50; diffHpMult = 1.45; diffDmgMult = 1.50; }
        else { diffEnemyMult = 1.0; diffHpMult = 1.0; diffDmgMult = 1.0; }
    }

    private void loadLevel() {
        generateCityMap();
        playerX = TILE_SIZE * 2;
        playerY = TILE_SIZE * 2;
        up = down = left = right = false;
        bullets.clear();
        isTransitioning = false;
        isJumpscareActive = false;
        bossDefeated = false;
        bossEncountered = false;
        bossIntroActive = false;
        rescueMessageActive = false;
        nearExitLocked = false;
        levelIntroActive = !showingPrologue;
        levelIntroTimer = 110;
        sanity = Math.min(100, sanity + 25);
        setBackground(levels[currentLevel - 1].bg);

        AudioEngine.setMusicMode(currentLevel);
        AudioEngine.setBossMode(false);

        saveCheckpoint();
    }

    private void saveCheckpoint() {
        SaveManager.Progress p = new SaveManager.Progress();
        p.level = currentLevel;
        p.health = playerHealth;
        p.money = playerMoney;
        p.difficulty = difficulty;
        p.sanity = sanity;
        p.claws = hasSteelClaws;
        p.crossbow = hasCrossbow;
        p.plasma = hasPlasmaLauncher;
        p.family = familyRescued.clone();
        p.score = score;
        p.playTimeMs = totalPlayTime();
        SaveManager.save(p);
    }

    private long totalPlayTime() {
        return accumulatedMs + (System.currentTimeMillis() - levelStartMs);
    }

    // ------------------------------------------------------------------
    // Generación del barrio
    // ------------------------------------------------------------------

    private void generateCityMap() {
        map = new int[MAP_WIDTH][MAP_HEIGHT];
        enemies.clear();
        currentBoss = null;

        for (int x = 0; x < MAP_WIDTH; x++) {
            for (int y = 0; y < MAP_HEIGHT; y++) {
                if (x == 0 || y == 0 || x == MAP_WIDTH - 1 || y == MAP_HEIGHT - 1) map[x][y] = T_EDIFICIO;
                else if (x % 7 < 3 && y % 7 < 3) map[x][y] = T_EDIFICIO;
                else map[x][y] = T_CALLE;
            }
        }

        // Plaza inicial despejada
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) map[i][j] = T_CALLE;
        }

        // Callejones aleatorios que rompen la cuadrícula: cada partida se siente distinta
        for (int i = 0; i < 60; i++) {
            int ax = rand.nextInt(MAP_WIDTH - 8) + 4;
            int ay = rand.nextInt(MAP_HEIGHT - 8) + 4;
            boolean horizontal = rand.nextBoolean();
            int len = 3 + rand.nextInt(5);
            for (int k = 0; k < len; k++) {
                int cx = horizontal ? ax + k : ax;
                int cy = horizontal ? ay : ay + k;
                if (cx > 0 && cy > 0 && cx < MAP_WIDTH - 1 && cy < MAP_HEIGHT - 1) map[cx][cy] = T_CALLE;
            }
        }

        // Mercado negro: uno junto al inicio y otro a media ciudad
        map[5][2] = T_TIENDA; map[5][3] = T_TIENDA;
        map[6][2] = T_TIENDA; map[6][3] = T_TIENDA;
        map[MAP_WIDTH / 2][MAP_HEIGHT / 2] = T_TIENDA;
        map[MAP_WIDTH / 2 + 1][MAP_HEIGHT / 2] = T_TIENDA;

        // Salida del barrio
        map[MAP_WIDTH - 3][MAP_HEIGHT - 3] = T_SALIDA;
        map[MAP_WIDTH - 2][MAP_HEIGHT - 3] = T_SALIDA;
        map[MAP_WIDTH - 3][MAP_HEIGHT - 2] = T_SALIDA;
        map[MAP_WIDTH - 2][MAP_HEIGHT - 2] = T_SALIDA;

        // Monedas
        for (int i = 0; i < 220; i++) {
            int rx = rand.nextInt(MAP_WIDTH - 5) + 1;
            int ry = rand.nextInt(MAP_HEIGHT - 5) + 1;
            if (map[rx][ry] == T_CALLE && rx > 4) map[rx][ry] = T_MONEDA;
        }

        // Pandilleros
        int numEnemies = (int) Math.round((20 + currentLevel * 5) * diffEnemyMult);
        int placed = 0, tries = 0;
        while (placed < numEnemies && tries < numEnemies * 12) {
            tries++;
            int rx = rand.nextInt(MAP_WIDTH - 12) + 10;
            int ry = rand.nextInt(MAP_HEIGHT - 12) + 10;
            if (map[rx][ry] != T_CALLE && map[rx][ry] != T_MONEDA) continue;
            enemies.add(new Enemy(rx * TILE_SIZE, ry * TILE_SIZE, pickEnemyType(), currentLevel));
            placed++;
        }

        // Jefe del barrio, custodiando la salida
        int bx = MAP_WIDTH - 7, by = MAP_HEIGHT - 7;
        map[bx][by] = T_CALLE;
        LevelInfo info = levels[currentLevel - 1];
        Enemy boss = new Enemy(bx * TILE_SIZE, by * TILE_SIZE, currentLevel, info.bossName, info.bossBreed);
        enemies.add(boss);
        currentBoss = boss;
    }

    private int pickEnemyType() {
        int roll = rand.nextInt(100);
        if (currentLevel <= 1) return roll < 75 ? E_CALLEJERO : E_CORREDOR;
        if (roll < 38) return E_CALLEJERO;
        if (roll < 68) return E_ARMADO;
        if (roll < 88) return E_CORREDOR;
        return E_BRUTO;
    }

    // ------------------------------------------------------------------
    // Colisiones
    // ------------------------------------------------------------------

    private boolean blocked(double nx, double ny, int size) {
        int x1 = (int) (nx / TILE_SIZE);
        int y1 = (int) (ny / TILE_SIZE);
        int x2 = (int) ((nx + size - 1) / TILE_SIZE);
        int y2 = (int) ((ny + size - 1) / TILE_SIZE);
        if (x1 < 0 || y1 < 0 || x2 >= MAP_WIDTH || y2 >= MAP_HEIGHT) return true;
        return map[x1][y1] == T_EDIFICIO || map[x2][y1] == T_EDIFICIO
            || map[x1][y2] == T_EDIFICIO || map[x2][y2] == T_EDIFICIO;
    }

    private int clampTile(int v, int max) { return Math.max(0, Math.min(max - 1, v)); }

    // ------------------------------------------------------------------
    // Bucle principal
    // ------------------------------------------------------------------

    @Override
    public void actionPerformed(ActionEvent e) {
        animationFrame++;

        if (showingPrologue || gameWon || isDead || paused) { repaint(); return; }

        if (levelIntroActive) {
            if (--levelIntroTimer <= 0) levelIntroActive = false;
            repaint();
            return;
        }
        if (isTransitioning) {
            if (--transitionTimer <= 0) { currentLevel++; loadLevel(); }
            repaint();
            return;
        }
        if (isShopOpen) {
            if (shopMessageTimer > 0) shopMessageTimer--;
            repaint();
            return;
        }
        if (isJumpscareActive) {
            if (--jumpscareTimer <= 0) isJumpscareActive = false;
            if (shakeFrames > 0) shakeFrames--;
            repaint();
            return;
        }
        if (bossIntroActive) {
            if (--bossIntroTimer <= 0) bossIntroActive = false;
            repaint();
            return;
        }
        if (rescueMessageActive) {
            if (--rescueMessageTimer <= 0) rescueMessageActive = false;
            repaint();
            return;
        }

        updatePlayer();
        updateBullets();
        updateEnemies();
        updateAtmosphere();

        if (playerHealth <= 0) triggerDeath();

        repaint();
    }

    private void updatePlayer() {
        double dx = 0, dy = 0;
        if (up) { dy -= 1; facing = 0; }
        if (down) { dy += 1; facing = 1; }
        if (left) { dx -= 1; facing = 2; }
        if (right) { dx += 1; facing = 3; }

        if (dx != 0 && dy != 0) { dx *= 0.7071; dy *= 0.7071; }
        dx *= playerSpeed;
        dy *= playerSpeed;

        // Eje por eje: así Nox se desliza por las paredes en vez de quedarse trabado
        if (dx != 0 && !blocked(playerX + dx, playerY, PLAYER_SIZE)) playerX += dx;
        if (dy != 0 && !blocked(playerX, playerY + dy, PLAYER_SIZE)) playerY += dy;

        int cx = clampTile((int) ((playerX + 15) / TILE_SIZE), MAP_WIDTH);
        int cy = clampTile((int) ((playerY + 15) / TILE_SIZE), MAP_HEIGHT);

        if (map[cx][cy] == T_MONEDA) {
            map[cx][cy] = T_CALLE;
            playerMoney += 50;
            score += 25;
            AudioEngine.play("coin", 0.7);
        }

        nearExitLocked = false;
        if (map[cx][cy] == T_SALIDA) {
            if (bossDefeated) {
                if (currentLevel >= MAX_LEVEL) {
                    triggerVictory();
                } else {
                    isTransitioning = true;
                    transitionTimer = 130;
                    playerHealth = Math.min(100, playerHealth + 25);
                    score += 1000;
                    accumulatedMs = totalPlayTime();
                    levelStartMs = System.currentTimeMillis();
                    AudioEngine.play("levelclear");
                }
                return;
            }
            nearExitLocked = true;
        }

        nearStore = false;
        for (int i = -1; i <= 1 && !nearStore; i++) {
            for (int j = -1; j <= 1; j++) {
                if (map[clampTile(cx + i, MAP_WIDTH)][clampTile(cy + j, MAP_HEIGHT)] == T_TIENDA) {
                    nearStore = true;
                    break;
                }
            }
        }

        if (isAttacking && --attackTimer <= 0) isAttacking = false;
        if (weaponCooldown > 0) weaponCooldown--;
        if (damageCooldown > 0) damageCooldown--;
        if (hurtFlash > 0) hurtFlash--;
    }

    private void updateBullets() {
        Iterator<Projectile> it = bullets.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.x += p.dx;
            p.y += p.dy;
            p.life--;

            int tx = clampTile((int) (p.x / TILE_SIZE), MAP_WIDTH);
            int ty = clampTile((int) (p.y / TILE_SIZE), MAP_HEIGHT);
            if (p.life <= 0 || map[tx][ty] == T_EDIFICIO) { it.remove(); continue; }

            boolean hit = false;
            if (p.isEnemyBullet) {
                if (Point2D.distance(p.x, p.y, playerX + 15, playerY + 15) < 20) {
                    damagePlayer((int) Math.round(p.damage * diffDmgMult), false);
                    hit = true;
                }
            } else {
                for (Enemy en : enemies) {
                    double reach = en.size * 0.75;
                    if (Point2D.distance(p.x, p.y, en.x + en.size / 2.0, en.y + en.size / 2.0) < reach) {
                        en.hp -= p.damage;
                        AudioEngine.play("hit", 0.5);
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) it.remove();
        }
    }

    private void updateEnemies() {
        if (currentBoss != null && !bossEncountered
            && Point2D.distance(playerX, playerY, currentBoss.x, currentBoss.y) < 420) {
            bossEncountered = true;
            bossIntroActive = true;
            bossIntroTimer = 110;
            sanity = Math.max(0, sanity - 10);
            AudioEngine.setBossMode(true);
            AudioEngine.play("growl");
        }

        boolean enemyNearby = false;

        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy en = it.next();

            if (en.hp <= 0) {
                kills++;
                if (en.isBoss) {
                    playerMoney += 500;
                    score += 2000;
                    bossDefeated = true;
                    currentBoss = null;
                    sanity = Math.min(100, sanity + 25);
                    AudioEngine.setBossMode(false);
                    AudioEngine.play("door");
                    String target = levels[currentLevel - 1].rescueTarget;
                    if (target != null) {
                        for (int k = 0; k < familyNames.length; k++) {
                            if (familyNames[k].equals(target)) familyRescued[k] = true;
                        }
                        rescueMessageActive = true;
                        rescueMessageTimer = 160;
                        rescueMessageText = "¡HAS RESCATADO A " + target + "!";
                        score += 1500;
                        AudioEngine.play("rescue");
                    }
                    saveCheckpoint();
                } else {
                    playerMoney += en.reward;
                    score += en.reward;
                    AudioEngine.play("hit", 0.8);
                }
                it.remove();
                continue;
            }

            double dist = Point2D.distance(playerX, playerY, en.x, en.y);
            if (dist < 280) enemyNearby = true;

            // Susto: un perro aparece de golpe entre las sombras
            if (!en.isBoss && dist < 130 && !isJumpscareActive && rand.nextInt(420) == 3) {
                triggerJumpscare();
                damagePlayer((int) Math.round(10 * diffDmgMult), true);
            }

            if (en.barkCooldown > 0) en.barkCooldown--;
            else if (dist < 300 && rand.nextInt(300) == 0) {
                en.barkCooldown = 180;
                AudioEngine.play("bark", 0.45);
            }

            moveEnemy(en, dist);
            enemyAttacks(en, dist);
        }

        sanity += enemyNearby ? -0.05 : 0.03;
        sanity = Math.max(0, Math.min(100, sanity));
        AudioEngine.setTension(1.0 - sanity / 100.0);
    }

    private void moveEnemy(Enemy en, double dist) {
        double aggro = en.isBoss ? 700 : 460;
        if (dist >= aggro) return;

        double speed = en.speed;

        if (en.isBoss) {
            if (en.chargeTimer > 0) {
                en.chargeTimer--;
                speed = en.speed * 2.6;
            } else if (--en.chargeCooldown <= 0 && dist > 90 && dist < 460) {
                en.chargeTimer = 55;
                en.chargeCooldown = 300;
                shakeFrames = 10;
                shakeMagnitude = 5;
                AudioEngine.play("growl", 0.5);
            }

            // Ráfaga circular de balas
            if (--en.specialCooldown <= 0 && dist < 520) {
                en.specialCooldown = 240;
                double bx = en.x + en.size / 2.0, by = en.y + en.size / 2.0;
                for (int i = 0; i < 12; i++) {
                    double angle = Math.PI * 2 * i / 12.0;
                    bullets.add(new Projectile(bx, by, angle, 6.0, 14, true));
                }
                AudioEngine.play("plasma", 0.5);
            }
        }

        double vx = 0, vy = 0;
        if (en.x + 6 < playerX) vx = speed;
        else if (en.x - 6 > playerX) vx = -speed;
        if (en.y + 6 < playerY) vy = speed;
        else if (en.y - 6 > playerY) vy = -speed;

        if (vx != 0 && !blocked(en.x + vx, en.y, en.size)) en.x += vx;
        if (vy != 0 && !blocked(en.x, en.y + vy, en.size)) en.y += vy;

        if (vx > 0) en.facing = 3;
        else if (vx < 0) en.facing = 2;
        else if (vy < 0) en.facing = 0;
        else if (vy > 0) en.facing = 1;
    }

    private void enemyAttacks(Enemy en, double dist) {
        int fireInterval = en.isBoss ? 42 : 85;
        if (en.hasGun && dist < 430 && animationFrame % fireInterval == 0) {
            int dmg = en.isBoss ? 20 : 13;
            bullets.add(new Projectile(en.x + en.size / 2.0, en.y + en.size / 2.0,
                                       playerX + 15, playerY + 15, en.isBoss ? 9.0 : 7.5, dmg, true));
            AudioEngine.play("shoot", 0.30);
        }

        double contact = en.isBoss ? en.size * 0.85 : en.size * 0.9;
        if (dist < contact) {
            int dmg = (int) Math.round(en.contactDamage * diffDmgMult);
            if (damagePlayer(dmg, true) && en.isBoss) {
                shakeFrames = 12;
                shakeMagnitude = 7;
            }
        }
    }

    /** @return true si el golpe realmente se aplicó (no estaba en invulnerabilidad). */
    private boolean damagePlayer(int amount, boolean respectCooldown) {
        if (respectCooldown && damageCooldown > 0) return false;
        playerHealth -= amount;
        damageCooldown = 26;
        hurtFlash = 12;
        sanity = Math.max(0, sanity - 1.5);
        AudioEngine.play("hurt", 0.6);
        return true;
    }

    private void updateAtmosphere() {
        if (!flickerActive) {
            if (rand.nextInt(2200) < (100 - sanity) / 4 + 1) {
                flickerActive = true;
                flickerTimer = 4 + rand.nextInt(6);
                flickerAlpha = 60 + rand.nextInt(100);
            }
        } else if (--flickerTimer <= 0) {
            flickerActive = false;
        }

        if (shakeFrames > 0) shakeFrames--;
        for (RainDrop r : rainSystem) r.update();
    }

    private void triggerJumpscare() {
        isJumpscareActive = true;
        jumpscareTimer = 48;
        jumpscareType = rand.nextInt(3);
        shakeFrames = 22;
        shakeMagnitude = 11;
        sanity = Math.max(0, sanity - 15);
        AudioEngine.play("scare");
    }

    private void triggerDeath() {
        isDead = true;
        playerHealth = 0;
        AudioEngine.setBossMode(false);
        AudioEngine.play("death");
    }

    private void triggerVictory() {
        gameWon = true;
        score += 5000 + (int) (sanity * 20);
        accumulatedMs = totalPlayTime();
        AudioEngine.setBossMode(false);
        AudioEngine.play("victory");
        SaveManager.submitScoreAsync("NOX", score, currentLevel, difficulty);
        SaveManager.deleteSave();
    }

    private void exitToMenu() {
        gameLoop.stop();
        AudioEngine.setBossMode(false);
        host.goToMenu();
    }

    // ==================================================================
    //  DIBUJO
    // ==================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (showingPrologue) { drawPrologue(g2d); return; }

        LevelInfo info = levels[Math.min(currentLevel, MAX_LEVEL) - 1];

        int shakeX = 0, shakeY = 0;
        if (shakeFrames > 0) {
            shakeX = rand.nextInt(shakeMagnitude * 2 + 1) - shakeMagnitude;
            shakeY = rand.nextInt(shakeMagnitude * 2 + 1) - shakeMagnitude;
        }

        int camX = (int) playerX - getWidth() / 2 + 15 + shakeX;
        int camY = (int) playerY - getHeight() / 2 + 15 + shakeY;
        camX = Math.max(0, Math.min(camX, MAP_WIDTH * TILE_SIZE - getWidth()));
        camY = Math.max(0, Math.min(camY, MAP_HEIGHT * TILE_SIZE - getHeight()));

        g2d.translate(-camX, -camY);
        drawWorld(g2d, info, camX, camY);
        drawActors(g2d, info);
        g2d.translate(camX, camY);

        // La linterna sigue a Nox: en los bordes del mapa la cámara se detiene
        // pero el gato se sigue moviendo, así que la luz no puede quedarse fija en el centro.
        drawWeather(g2d, info, (int) playerX - camX + 15, (int) playerY - camY + 15);
        drawHud(g2d, info);

        if (showMinimap) drawMinimap(g2d);
        if (isShopOpen) drawShop(g2d);
        if (isJumpscareActive) drawJumpscare(g2d);
        if (bossIntroActive) drawBossIntro(g2d, info);
        if (rescueMessageActive) drawRescueMessage(g2d);
        if (isTransitioning) drawTransition(g2d);
        if (levelIntroActive) drawLevelIntro(g2d, info);
        if (isDead) drawDeath(g2d);
        if (gameWon) drawVictory(g2d);
        if (paused) drawPause(g2d);
    }

    private void drawWorld(Graphics2D g2d, LevelInfo info, int camX, int camY) {
        int x0 = Math.max(0, camX / TILE_SIZE - 1);
        int y0 = Math.max(0, camY / TILE_SIZE - 1);
        int x1 = Math.min(MAP_WIDTH, (camX + getWidth()) / TILE_SIZE + 2);
        int y1 = Math.min(MAP_HEIGHT, (camY + getHeight()) / TILE_SIZE + 2);

        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                switch (map[x][y]) {
                    case T_EDIFICIO:
                        Sprites.drawBuilding(g2d, x, y, TILE_SIZE, info.bldg1, info.bldg2, animationFrame);
                        break;
                    case T_TIENDA:
                        Sprites.drawStore(g2d, x, y, TILE_SIZE, animationFrame);
                        break;
                    case T_SALIDA:
                        Sprites.drawExit(g2d, x, y, TILE_SIZE, bossDefeated, animationFrame);
                        break;
                    case T_MONEDA:
                        Sprites.drawGround(g2d, x, y, TILE_SIZE, info.street, animationFrame);
                        Sprites.drawCoin(g2d, x * TILE_SIZE, y * TILE_SIZE, TILE_SIZE, animationFrame);
                        break;
                    default:
                        Sprites.drawGround(g2d, x, y, TILE_SIZE, info.street, animationFrame);
                }
            }
        }
    }

    private void drawActors(Graphics2D g2d, LevelInfo info) {
        for (Enemy en : enemies) {
            Color body = en.isBoss ? info.bossColor : en.bodyColor;
            Sprites.drawDog(g2d, (int) en.x, (int) en.y, en.size, body, en.breed,
                            en.hasGun, en.isBoss, en.facing, animationFrame);

            if (en.isBoss) {
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Consolas", Font.BOLD, 13));
                int w = g2d.getFontMetrics().stringWidth(en.name);
                g2d.drawString(en.name, (int) en.x + en.size / 2 - w / 2, (int) en.y - 26);
            }

            // Barra de vida
            int barW = en.size;
            g2d.setColor(new Color(0, 0, 0, 200));
            g2d.fillRect((int) en.x, (int) en.y - 14, barW, 6);
            g2d.setColor(en.isBoss ? new Color(255, 60, 200) : new Color(220, 50, 50));
            g2d.fillRect((int) en.x, (int) en.y - 14,
                         (int) (barW * Math.max(0, en.hp) / (double) en.maxHp), 6);
        }

        for (Projectile p : bullets) {
            Color c = p.isEnemyBullet ? new Color(255, 70, 50) : new Color(120, 230, 255);
            g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
            g2d.fillOval((int) p.x - 5, (int) p.y - 5, 20, 20);
            g2d.setColor(c);
            g2d.fillOval((int) p.x, (int) p.y, 10, 10);
        }

        boolean moving = up || down || left || right;
        Sprites.drawNox(g2d, (int) playerX, (int) playerY, facing, animationFrame,
                        moving, isAttacking, hurtFlash > 0);

        if (isAttacking && activeWeapon < 2) {
            Sprites.drawClawSlash(g2d, (int) playerX + 15, (int) playerY + 15,
                                  facing, activeWeapon, animationFrame);
        }
    }

    private void drawWeather(Graphics2D g2d, LevelInfo info, int lightX, int lightY) {
        g2d.setColor(new Color(180, 200, 220, 95));
        for (RainDrop r : rainSystem) {
            g2d.drawLine((int) r.x, (int) r.y, (int) r.x - 3, (int) r.y + 18);
        }

        // Viñeta: la oscuridad se cierra cuando baja la cordura
        Point2D center = new Point2D.Float(lightX, lightY);
        float radius = (float) (330 + sanity * 1.9);
        try {
            g2d.setPaint(new RadialGradientPaint(center, radius,
                new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(0, 0, 0, 0), info.fog1, info.fog2}));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        } catch (Exception ignored) { }

        if (flickerActive) {
            g2d.setColor(new Color(20, 0, 0, flickerAlpha));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
        if (hurtFlash > 0) {
            g2d.setColor(new Color(180, 0, 0, hurtFlash * 8));
            g2d.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    private void drawHud(Graphics2D g2d, LevelInfo info) {
        g2d.setColor(new Color(0, 0, 0, 215));
        g2d.fillRect(10, 10, getWidth() - 20, 44);
        g2d.setColor(new Color(70, 70, 80));
        g2d.drawRect(10, 10, getWidth() - 20, 44);

        // Salud
        g2d.setColor(new Color(60, 15, 15));
        g2d.fillRect(20, 20, 200, 22);
        g2d.setColor(playerHealth > 35 ? new Color(0, 220, 100) : new Color(255, 60, 60));
        g2d.fillRect(20, 20, Math.max(0, Math.min(100, playerHealth)) * 2, 22);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(20, 20, 200, 22);
        g2d.setFont(new Font("Consolas", Font.BOLD, 13));
        g2d.drawString(Math.max(0, playerHealth) + " HP", 88, 37);

        // Datos del barrio
        g2d.setColor(new Color(255, 190, 70));
        g2d.setFont(new Font("Consolas", Font.BOLD, 15));
        g2d.drawString(info.name + "   $" + playerMoney + "   " + weaponLabel(), 235, 37);

        // Familia rescatada
        g2d.setFont(new Font("Consolas", Font.BOLD, 12));
        int fx = getWidth() - 250;
        for (int i = 0; i < familyNames.length; i++) {
            g2d.setColor(familyRescued[i] ? new Color(0, 230, 110) : new Color(90, 20, 20));
            g2d.fillRoundRect(fx + i * 78, 18, 68, 26, 8, 8);
            g2d.setColor(familyRescued[i] ? Color.BLACK : new Color(160, 120, 120));
            int w = g2d.getFontMetrics().stringWidth(familyNames[i]);
            g2d.drawString(familyNames[i], fx + i * 78 + 34 - w / 2, 36);
        }

        // Cordura
        g2d.setColor(new Color(0, 0, 0, 215));
        g2d.fillRect(10, 60, 230, 20);
        g2d.setColor(new Color(140, 30, 210));
        g2d.fillRect(14, 64, (int) (202 * (sanity / 100.0)), 12);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2d.drawString("CORDURA", 18, 74);

        // Barra del jefe
        if (currentBoss != null && bossEncountered && !bossDefeated) {
            int bw = 520, bx = getWidth() / 2 - bw / 2, by = 92;
            g2d.setColor(new Color(0, 0, 0, 220));
            g2d.fillRect(bx - 4, by - 22, bw + 8, 44);
            g2d.setColor(new Color(255, 40, 40));
            g2d.setFont(new Font("Consolas", Font.BOLD, 15));
            int w = g2d.getFontMetrics().stringWidth(currentBoss.name);
            g2d.drawString(currentBoss.name, getWidth() / 2 - w / 2, by - 6);
            g2d.setColor(new Color(50, 10, 10));
            g2d.fillRect(bx, by, bw, 14);
            g2d.setColor(new Color(255, 60, 200));
            g2d.fillRect(bx, by, (int) (bw * Math.max(0, currentBoss.hp) / (double) currentBoss.maxHp), 14);
            g2d.setColor(Color.WHITE);
            g2d.drawRect(bx, by, bw, 14);
        }

        if (nearStore && !isShopOpen) {
            centeredText(g2d, ">>> [E] MERCADO NEGRO <<<", getHeight() - 55,
                         new Font("Consolas", Font.BOLD, 17), new Color(255, 0, 150));
        }
        if (nearExitLocked) {
            centeredText(g2d, "¡DERROTA A " + info.bossName + " PARA AVANZAR!", getHeight() - 55,
                         new Font("Consolas", Font.BOLD, 17), new Color(255, 60, 60));
        }

        g2d.setColor(new Color(120, 120, 130));
        g2d.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2d.drawString("[ESPACIO] Atacar  [1-3] Arma  [M] Mapa  [P/ESC] Pausa", 14, getHeight() - 14);
        String cloud = SaveManager.getCloudStatus();
        int cw = g2d.getFontMetrics().stringWidth(cloud);
        g2d.drawString(cloud, getWidth() - cw - 14, getHeight() - 14);
    }

    private String weaponLabel() {
        switch (activeWeapon) {
            case 1: return "[1] GARRAS DE ACERO";
            case 2: return "[2] BALLESTA";
            case 3: return "[3] PLASMA";
            default: return "[1] GARRAS";
        }
    }

    private void centeredText(Graphics2D g2d, String text, int y, Font font, Color color) {
        g2d.setFont(font);
        g2d.setColor(color);
        int w = g2d.getFontMetrics().stringWidth(text);
        g2d.drawString(text, getWidth() / 2 - w / 2, y);
    }

    private void drawMinimap(Graphics2D g2d) {
        int mx = getWidth() - 235, my = 100, size = 210;
        double scale = size / (double) (MAP_WIDTH * TILE_SIZE);

        g2d.setColor(new Color(0, 0, 0, 225));
        g2d.fillRect(mx - 6, my - 6, size + 12, size + 12);
        g2d.setColor(new Color(255, 0, 150));
        g2d.drawRect(mx - 6, my - 6, size + 12, size + 12);

        for (int x = 0; x < MAP_WIDTH; x++) {
            for (int y = 0; y < MAP_HEIGHT; y++) {
                int px = mx + (int) (x * TILE_SIZE * scale);
                int py = my + (int) (y * TILE_SIZE * scale);
                if (map[x][y] == T_EDIFICIO) {
                    g2d.setColor(new Color(70, 70, 82));
                    g2d.fillRect(px, py, 4, 4);
                } else if (map[x][y] == T_SALIDA) {
                    g2d.setColor(bossDefeated ? new Color(0, 255, 120) : new Color(255, 50, 50));
                    g2d.fillRect(px, py, 5, 5);
                } else if (map[x][y] == T_TIENDA) {
                    g2d.setColor(new Color(255, 0, 150));
                    g2d.fillRect(px, py, 4, 4);
                }
            }
        }

        for (Enemy en : enemies) {
            g2d.setColor(en.isBoss ? Color.MAGENTA : new Color(210, 70, 70));
            int s = en.isBoss ? 7 : 3;
            g2d.fillOval(mx + (int) (en.x * scale) - s / 2, my + (int) (en.y * scale) - s / 2, s, s);
        }

        g2d.setColor(new Color(150, 255, 60));
        g2d.fillOval(mx + (int) (playerX * scale) - 4, my + (int) (playerY * scale) - 4, 8, 8);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2d.drawString("MAPA DEL BARRIO", mx, my + size + 18);
    }

    // ------------------------------------------------------------------
    // Pantallas
    // ------------------------------------------------------------------

    private void drawPrologue(Graphics2D g2d) {
        g2d.setColor(new Color(4, 4, 6));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setColor(new Color(255, 30, 30));
        g2d.setFont(new Font("Consolas", Font.BOLD, 34));
        g2d.drawString("PRÓLOGO", 60, 80);

        String[] lines = {
            "Nox, un gato negro criado entre el hambre y el peligro de la calle,",
            "encontró por fin una vida tranquila junto a su esposa Mitsy",
            "y sus dos gatitos, Will y Nina, en un viejo callejón.",
            "",
            "Una pandilla de perros liderada por el bulldog Colmillo",
            "controla la ciudad y exige 'protección' a todos los animales.",
            "",
            "Nox se negó a pagar.",
            "",
            "Esa misma noche, los perros incendiaron su hogar",
            "y secuestraron a su familia.",
            "",
            "Ahora Nox debe cruzar barrio por barrio, enfrentar a cada",
            "banda de Colmillo y rescatar a los suyos, uno por uno,",
            "hasta llegar al jefe final.",
        };

        g2d.setFont(new Font("Consolas", Font.PLAIN, 18));
        g2d.setColor(new Color(220, 220, 220));
        int ly = 140;
        for (String line : lines) { g2d.drawString(line, 60, ly); ly += 27; }

        g2d.setColor(new Color(255, 180, 50));
        g2d.setFont(new Font("Consolas", Font.BOLD, 15));
        ly += 14;
        for (int i = 0; i < levels.length; i++) {
            g2d.drawString((i + 1) + ". " + levels[i].name + " — " + levels[i].bossName, 60, ly);
            ly += 22;
        }

        // Nox observando desde la esquina
        Sprites.drawNox(g2d, getWidth() - 150, getHeight() - 190, 2, animationFrame, false, false, false);

        centeredText(g2d, "Presiona ESPACIO o ENTER para comenzar...", getHeight() - 32,
                     new Font("Consolas", Font.ITALIC, 16), new Color(160, 160, 160));
    }

    private void drawLevelIntro(Graphics2D g2d, LevelInfo info) {
        g2d.setColor(new Color(0, 0, 0, 215));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        centeredText(g2d, info.name, getHeight() / 2 - 20, new Font("Consolas", Font.BOLD, 36), new Color(255, 60, 60));
        String sub = (currentLevel == MAX_LEVEL ? "JEFE FINAL: " : "JEFE DEL BARRIO: ") + info.bossName;
        centeredText(g2d, sub, getHeight() / 2 + 25, new Font("Consolas", Font.PLAIN, 20), Color.WHITE);
        String rescue = info.rescueTarget != null ? "Aquí retienen a " + info.rescueTarget : "";
        if (!rescue.isEmpty()) {
            centeredText(g2d, rescue, getHeight() / 2 + 60,
                         new Font("Consolas", Font.ITALIC, 17), new Color(120, 255, 160));
        }
    }

    private void drawBossIntro(Graphics2D g2d, LevelInfo info) {
        g2d.setColor(new Color(20, 0, 0, 195));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        centeredText(g2d, "¡" + info.bossName + " APARECE!", getHeight() / 2 - 10,
                     new Font("Consolas", Font.BOLD, 38), new Color(255, 30, 30));
        centeredText(g2d, info.bossTaunt, getHeight() / 2 + 32,
                     new Font("Consolas", Font.ITALIC, 18), new Color(230, 230, 230));
    }

    private void drawRescueMessage(Graphics2D g2d) {
        g2d.setColor(new Color(0, 40, 20, 205));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        centeredText(g2d, rescueMessageText, getHeight() / 2,
                     new Font("Consolas", Font.BOLD, 36), new Color(0, 255, 120));
        centeredText(g2d, "La salida del barrio se ha abierto.", getHeight() / 2 + 42,
                     new Font("Consolas", Font.PLAIN, 18), Color.WHITE);
    }

    private void drawTransition(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 245));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        centeredText(g2d, "BARRIO CONQUISTADO", getHeight() / 2 - 20,
                     new Font("Consolas", Font.BOLD, 44), new Color(255, 50, 50));
        centeredText(g2d, "Entrando a una zona más peligrosa...", getHeight() / 2 + 28,
                     new Font("Consolas", Font.PLAIN, 21), Color.WHITE);
        centeredText(g2d, "Progreso guardado", getHeight() / 2 + 66,
                     new Font("Consolas", Font.ITALIC, 15), new Color(140, 140, 140));
    }

    private void drawDeath(Graphics2D g2d) {
        g2d.setColor(new Color(30, 0, 0, 240));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        centeredText(g2d, "NOX HA CAÍDO", getHeight() / 2 - 70,
                     new Font("Consolas", Font.BOLD, 50), new Color(255, 40, 40));
        centeredText(g2d, "La pandilla de Colmillo sigue al mando de la ciudad.", getHeight() / 2 - 20,
                     new Font("Consolas", Font.PLAIN, 19), new Color(200, 200, 200));
        centeredText(g2d, "Barrio " + currentLevel + "   |   " + kills + " perros derrotados   |   "
                          + score + " puntos", getHeight() / 2 + 20,
                     new Font("Consolas", Font.PLAIN, 18), new Color(255, 190, 70));
        centeredText(g2d, "[ENTER] Reintentar desde el barrio " + currentLevel, getHeight() / 2 + 70,
                     new Font("Consolas", Font.BOLD, 19), new Color(120, 255, 160));
        centeredText(g2d, "[ESC] Volver al menú", getHeight() / 2 + 102,
                     new Font("Consolas", Font.PLAIN, 17), new Color(160, 160, 160));
    }

    private void drawVictory(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 250));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        centeredText(g2d, "¡HAS DERROTADO A DON COLMILLO!", getHeight() / 2 - 110,
                     new Font("Consolas", Font.BOLD, 40), new Color(0, 255, 120));
        centeredText(g2d, "Mitsy, Will y Nina están a salvo.", getHeight() / 2 - 60,
                     new Font("Consolas", Font.PLAIN, 21), Color.WHITE);
        centeredText(g2d, "Nox regresa a casa con su familia, en paz.", getHeight() / 2 - 28,
                     new Font("Consolas", Font.PLAIN, 21), Color.WHITE);

        long secs = accumulatedMs / 1000;
        centeredText(g2d, "PUNTUACIÓN: " + score, getHeight() / 2 + 20,
                     new Font("Consolas", Font.BOLD, 26), new Color(255, 190, 70));
        centeredText(g2d, kills + " perros derrotados   |   tiempo " + (secs / 60) + "m " + (secs % 60) + "s"
                          + "   |   dificultad " + difficultyName(), getHeight() / 2 + 55,
                     new Font("Consolas", Font.PLAIN, 17), new Color(190, 190, 190));

        Sprites.drawNox(g2d, getWidth() / 2 - 15, getHeight() / 2 + 90, 1, animationFrame, false, false, false);
        centeredText(g2d, "Presiona ESC para volver al menú", getHeight() - 40,
                     new Font("Consolas", Font.ITALIC, 16), new Color(150, 150, 150));
    }

    private String difficultyName() {
        return difficulty == 0 ? "FÁCIL" : difficulty == 2 ? "PESADILLA" : "NORMAL";
    }

    private void drawPause(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 210));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        centeredText(g2d, "PAUSA", getHeight() / 2 - 60, new Font("Consolas", Font.BOLD, 46), Color.WHITE);
        centeredText(g2d, "[P] Continuar     [ESC] Salir al menú", getHeight() / 2,
                     new Font("Consolas", Font.PLAIN, 19), new Color(210, 210, 210));
        centeredText(g2d, "[N] Música: " + (AudioEngine.isMusicOn() ? "ON" : "OFF")
                          + "     [B] Efectos: " + (AudioEngine.isSfxOn() ? "ON" : "OFF"),
                     getHeight() / 2 + 40, new Font("Consolas", Font.PLAIN, 18), new Color(255, 190, 70));
        centeredText(g2d, "Tu progreso se guarda al empezar cada barrio.", getHeight() / 2 + 90,
                     new Font("Consolas", Font.ITALIC, 15), new Color(140, 140, 140));
    }

    private void drawShop(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 235));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        int w = 780, h = 470;
        int x = getWidth() / 2 - w / 2, y = getHeight() / 2 - h / 2;
        g2d.setColor(new Color(22, 8, 24));
        g2d.fillRect(x, y, w, h);
        g2d.setColor(new Color(255, 0, 150));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRect(x, y, w, h);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setFont(new Font("Consolas", Font.BOLD, 26));
        g2d.drawString("MERCADO NEGRO", x + 30, y + 45);
        g2d.setColor(new Color(255, 190, 70));
        g2d.setFont(new Font("Consolas", Font.BOLD, 22));
        g2d.drawString("FONDOS: $" + playerMoney, x + w - 250, y + 45);

        String[] items = {
            "Garras de Acero  (cuerpo a cuerpo devastador)   $300 " + (hasSteelClaws ? "[COMPRADO]" : ""),
            "Ballesta de Chatarra  (ataque a distancia)      $500 " + (hasCrossbow ? "[COMPRADO]" : ""),
            "Lanzador de Plasma  (ráfaga de 5 disparos)      $900 " + (hasPlasmaLauncher ? "[COMPRADO]" : ""),
            "Lata de Atún  (+50 de salud)                    $100",
            "Amuleto de Cordura  (+40 de cordura)            $150",
        };

        g2d.setFont(new Font("Consolas", Font.PLAIN, 17));
        for (int i = 0; i < items.length; i++) {
            int iy = y + 120 + i * 52;
            if (i == selectedShopItem) {
                g2d.setColor(new Color(255, 0, 150, 60));
                g2d.fillRect(x + 22, iy - 22, w - 44, 38);
                g2d.setColor(Color.YELLOW);
                g2d.drawString("> " + items[i], x + 34, iy);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.drawString("  " + items[i], x + 34, iy);
            }
        }

        if (shopMessageTimer > 0) {
            g2d.setColor(shopMessage.startsWith("¡COMPRA") ? new Color(0, 255, 120) : new Color(255, 60, 60));
            g2d.setFont(new Font("Consolas", Font.BOLD, 20));
            g2d.drawString(shopMessage, x + 30, y + 88);
        }

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Consolas", Font.PLAIN, 15));
        g2d.drawString("[W/S] Navegar    [ENTER] Comprar    [ESC/E] Cerrar", x + 30, y + h - 25);
    }

    private void drawJumpscare(Graphics2D g2d) {
        if (jumpscareType == 0) {
            g2d.setColor(new Color(190, 0, 0, 235));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            Sprites.drawDog(g2d, getWidth() / 2 - 160, getHeight() / 2 - 120, 320,
                            new Color(30, 20, 20), 1, false, true, 1, animationFrame);
            centeredText(g2d, "¡¡ TE ENCONTRÓ !!", 90, new Font("Consolas", Font.BOLD, 48), Color.WHITE);
        } else if (jumpscareType == 1) {
            g2d.setColor(new Color(0, 0, 0, 240));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(new Color(8, 8, 10));
            g2d.fillOval(getWidth() / 2 - 260, getHeight() / 2 - 250, 520, 520);
            g2d.setColor(new Color(255, 230, 40));
            g2d.fillOval(getWidth() / 2 - 95, getHeight() / 2 - 45, 45, 16);
            g2d.fillOval(getWidth() / 2 + 50, getHeight() / 2 - 45, 45, 16);
            centeredText(g2d, "ALGO TE OBSERVA...", 100, new Font("Consolas", Font.BOLD, 44), new Color(255, 40, 40));
        } else {
            g2d.setColor(new Color(5, 5, 5, 238));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            for (int i = 0; i < 45; i++) {
                g2d.setColor(new Color(255, 255, 255, 20 + rand.nextInt(60)));
                g2d.fillRect(rand.nextInt(getWidth()), rand.nextInt(getHeight()),
                             20 + rand.nextInt(130), 2 + rand.nextInt(6));
            }
            centeredText(g2d, "NO ESTÁS SOLO", getHeight() / 2,
                         new Font("Consolas", Font.BOLD, 50), new Color(255, 0, 0, 200));
        }
    }

    // ==================================================================
    //  CLASES INTERNAS
    // ==================================================================

    class Enemy {
        double x, y, speed;
        int hp, maxHp, size, breed, contactDamage, reward, type;
        boolean hasGun, isBoss;
        String name;
        Color bodyColor;
        int facing = 1;
        int barkCooldown = 0;
        int specialCooldown = 200;
        int chargeCooldown = 260;
        int chargeTimer = 0;

        /** Pandillero normal. */
        Enemy(double x, double y, int type, int level) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.isBoss = false;
            int base = 110 + level * 28;

            switch (type) {
                case E_ARMADO:
                    maxHp = (int) Math.round(base * 0.85 * diffHpMult);
                    speed = 2.2; size = 42; breed = 2; hasGun = true;
                    contactDamage = 11; reward = 180;
                    bodyColor = new Color(105, 45, 35);
                    break;
                case E_CORREDOR:
                    maxHp = (int) Math.round(base * 0.55 * diffHpMult);
                    speed = 4.5; size = 34; breed = 4; hasGun = false;
                    contactDamage = 9; reward = 130;
                    bodyColor = new Color(150, 110, 60);
                    break;
                case E_BRUTO:
                    maxHp = (int) Math.round(base * 1.9 * diffHpMult);
                    speed = 1.7; size = 54; breed = 0; hasGun = false;
                    contactDamage = 22; reward = 260;
                    bodyColor = new Color(70, 65, 70);
                    break;
                default:
                    maxHp = (int) Math.round(base * diffHpMult);
                    speed = 2.7; size = 40; breed = 1; hasGun = false;
                    contactDamage = 14; reward = 150;
                    bodyColor = new Color(120, 78, 40);
            }
            hp = maxHp;
        }

        /** Jefe del barrio. */
        Enemy(double x, double y, int level, String name, int breed) {
            this.x = x;
            this.y = y;
            this.name = name;
            this.breed = breed;
            this.isBoss = true;
            this.hasGun = true;
            this.type = E_ARMADO;
            this.size = 78;
            this.speed = 2.9 + level * 0.14;
            this.contactDamage = 24 + level * 3;
            this.reward = 500;
            this.maxHp = (int) Math.round((520 + level * 190) * diffHpMult);
            this.hp = maxHp;
            this.bodyColor = new Color(90, 40, 40);
        }
    }

    class Projectile {
        double x, y, dx, dy;
        int damage;
        boolean isEnemyBullet;
        int life = 150;

        /** Dirigido hacia un punto. */
        Projectile(double x, double y, double targetX, double targetY,
                   double speed, int damage, boolean isEnemy) {
            this(x, y, Math.atan2(targetY - y, targetX - x), speed, damage, isEnemy);
        }

        /** Con ángulo explícito: lo usan la ráfaga del jefe y la dispersión del plasma. */
        Projectile(double x, double y, double angle, double speed, int damage, boolean isEnemy) {
            this.x = x;
            this.y = y;
            this.damage = damage;
            this.isEnemyBullet = isEnemy;
            this.dx = Math.cos(angle) * speed;
            this.dy = Math.sin(angle) * speed;
        }
    }

    class RainDrop {
        double x, y, speed;
        RainDrop() { reset(); y = rand.nextInt(768); }
        void reset() { x = rand.nextInt(1024 + 300); y = -20; speed = 20 + rand.nextDouble() * 15; }
        void update() { y += speed; x -= speed / 3; if (y > 768 || x < 0) reset(); }
    }

    // ==================================================================
    //  TECLADO
    // ==================================================================

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (showingPrologue) {
            if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
                showingPrologue = false;
                levelIntroActive = true;
                levelIntroTimer = 110;
                AudioEngine.play("blip");
            } else if (key == KeyEvent.VK_ESCAPE) {
                exitToMenu();
            }
            return;
        }

        if (isDead) {
            if (key == KeyEvent.VK_ENTER) {
                gameLoop.stop();
                continueSavedGame();
            } else if (key == KeyEvent.VK_ESCAPE) {
                exitToMenu();
            }
            return;
        }

        if (gameWon) {
            if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_ENTER) exitToMenu();
            return;
        }

        if (paused) {
            if (key == KeyEvent.VK_P) paused = false;
            else if (key == KeyEvent.VK_ESCAPE) exitToMenu();
            else if (key == KeyEvent.VK_N) AudioEngine.toggleMusic();
            else if (key == KeyEvent.VK_B) AudioEngine.toggleSfx();
            return;
        }

        if (levelIntroActive || isJumpscareActive || bossIntroActive || rescueMessageActive || isTransitioning) return;

        if (isShopOpen) { handleShopKey(key); return; }

        if (key == KeyEvent.VK_P || key == KeyEvent.VK_ESCAPE) { paused = true; return; }
        if (key == KeyEvent.VK_M) { showMinimap = !showMinimap; AudioEngine.play("blip", 0.5); return; }

        if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) up = true;
        if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) down = true;
        if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) left = true;
        if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) right = true;

        if (key == KeyEvent.VK_1) activeWeapon = hasSteelClaws ? 1 : 0;
        if (key == KeyEvent.VK_2 && hasCrossbow) activeWeapon = 2;
        if (key == KeyEvent.VK_3 && hasPlasmaLauncher) activeWeapon = 3;

        if (key == KeyEvent.VK_SPACE) attack();
        if (key == KeyEvent.VK_E && nearStore) {
            isShopOpen = true;
            shopMessage = "";
            AudioEngine.play("blip");
        }
    }

    private void attack() {
        if (weaponCooldown > 0) return;

        isAttacking = true;
        attackTimer = 14;
        double cx = playerX + 15, cy = playerY + 15;
        double baseAngle = facing == 0 ? -Math.PI / 2 : facing == 1 ? Math.PI / 2
                         : facing == 2 ? Math.PI : 0;

        if (activeWeapon == 2) {
            weaponCooldown = 16;
            bullets.add(new Projectile(cx, cy, baseAngle, 15.0, 55, false));
            AudioEngine.play("shoot");
        } else if (activeWeapon == 3) {
            weaponCooldown = 34;
            for (int i = -2; i <= 2; i++) {
                bullets.add(new Projectile(cx, cy, baseAngle + i * 0.16, 13.0, 45, false));
            }
            AudioEngine.play("plasma");
        } else {
            boolean steel = activeWeapon == 1;
            weaponCooldown = steel ? 22 : 17;
            int damage = steel ? 85 : 30;
            double range = steel ? 115 : 62;
            boolean connected = false;
            for (Enemy en : enemies) {
                if (Point2D.distance(cx, cy, en.x + en.size / 2.0, en.y + en.size / 2.0) <= range) {
                    en.hp -= damage;
                    connected = true;
                }
            }
            AudioEngine.play(steel ? "steel" : "claw");
            if (connected) AudioEngine.play("hit", 0.6);
        }
    }

    private void handleShopKey(int key) {
        if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) {
            selectedShopItem = Math.max(0, selectedShopItem - 1);
            AudioEngine.play("blip", 0.5);
        }
        if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) {
            selectedShopItem = Math.min(SHOP_ITEMS - 1, selectedShopItem + 1);
            AudioEngine.play("blip", 0.5);
        }
        if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER) {
            buySelectedItem();
            shopMessageTimer = 70;
        }
        if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_E) {
            isShopOpen = false;
            shopMessageTimer = 0;
        }
    }

    private void buySelectedItem() {
        switch (selectedShopItem) {
            case 0: purchase(hasSteelClaws, 300, () -> { hasSteelClaws = true; activeWeapon = 1; }); break;
            case 1: purchase(hasCrossbow, 500, () -> { hasCrossbow = true; activeWeapon = 2; }); break;
            case 2: purchase(hasPlasmaLauncher, 900, () -> { hasPlasmaLauncher = true; activeWeapon = 3; }); break;
            case 3:
                if (playerHealth >= 100) { shopMessage = "¡SALUD AL MÁXIMO!"; AudioEngine.play("error"); }
                else purchase(false, 100, () -> playerHealth = Math.min(100, playerHealth + 50));
                break;
            default:
                if (sanity >= 100) { shopMessage = "¡CORDURA AL MÁXIMO!"; AudioEngine.play("error"); }
                else purchase(false, 150, () -> sanity = Math.min(100, sanity + 40));
                break;
        }
    }

    private void purchase(boolean alreadyOwned, int price, Runnable effect) {
        if (alreadyOwned) {
            shopMessage = "YA LO TIENES";
            AudioEngine.play("error");
        } else if (playerMoney < price) {
            shopMessage = "¡FONDOS INSUFICIENTES!";
            AudioEngine.play("error");
        } else {
            playerMoney -= price;
            effect.run();
            shopMessage = "¡COMPRA EXITOSA!";
            AudioEngine.play("buy");
            saveCheckpoint();
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

    @Override
    public void keyTyped(KeyEvent e) { }
}
