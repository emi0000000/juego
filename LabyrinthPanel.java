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
import java.util.List;
import java.util.Random;

/** Motor del juego: la ciudad, los perros, los jefes y todo lo que ocurre dentro del barrio. */
public class LabyrinthPanel extends JPanel implements ActionListener, KeyListener {

    // Tipos de perro
    private static final int E_CALLEJERO = 0;
    private static final int E_ARMADO = 1;
    private static final int E_CORREDOR = 2;
    private static final int E_BRUTO = 3;
    private static final int E_ESCUDO = 4;
    private static final int E_ALFA = 5;
    private static final int E_KAMIKAZE = 6;

    // Armas
    private static final int W_GARRAS = 0;
    private static final int W_ACERO = 1;
    private static final int W_BALLESTA = 2;
    private static final int W_PLASMA = 3;
    private static final int W_MOLOTOV = 4;

    private static final int PLAYER_SIZE = 30;
    private static final int MAX_LEVEL = 6;
    private static final int SHOP_ITEMS = 10;
    private static final int MAX_TUNA = 5;
    private static final int ENEMY_CAP = 95;

    /** Reducción de daño según la armadura equipada. */
    private static final double[] ARMOR_REDUCTION = {0.0, 0.15, 0.30, 0.45};
    private static final String[] ARMOR_NAMES = {"SIN ARMADURA", "COLLAR", "CHALECO", "PLACAS"};

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
    private int theme = 0;

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
    private boolean darkEnding = false;
    private boolean isTransitioning = false;
    private int transitionTimer = 0;
    private boolean continueRequested = false;

    // Familia
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

    // Arsenal
    private boolean hasSteelClaws = false;
    private boolean hasCrossbow = false;
    private boolean hasPlasmaLauncher = false;
    private boolean hasMolotov = false;
    private boolean hasWhistle = false;
    private int armorLevel = 0;
    private int tunaCans = 0;
    private int activeWeapon = W_GARRAS;
    private boolean isAttacking = false;
    private int attackTimer = 0;
    private int weaponCooldown = 0;
    private int whistleCooldown = 0;

    private int damageCooldown = 0;
    private int hurtFlash = 0;

    // Estadísticas
    private int kills = 0;
    private int score = 0;
    private long levelStartMs = 0;
    private long accumulatedMs = 0;

    private final ArrayList<RainDrop> rainSystem = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<Enemy> spawnQueue = new ArrayList<>();
    private final ArrayList<Projectile> bullets = new ArrayList<>();
    private final Particles fx = new Particles();
    private final Random rand = new Random();
    private int animationFrame = 0;
    private int camX = 0, camY = 0;

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
            hasMolotov = p.molotov;
            hasWhistle = p.whistle;
            armorLevel = p.armor;
            tunaCans = p.tuna;
            familyRescued = p.family.clone();
            score = p.score;
            accumulatedMs = p.playTimeMs;
            activeWeapon = hasPlasmaLauncher ? W_PLASMA : hasCrossbow ? W_BALLESTA
                         : hasSteelClaws ? W_ACERO : W_GARRAS;
            showingPrologue = false;
        } else {
            currentLevel = 1;
            playerHealth = 100;
            playerMoney = 0;
            sanity = 100;
            hasSteelClaws = hasCrossbow = hasPlasmaLauncher = hasMolotov = hasWhistle = false;
            armorLevel = 0;
            tunaCans = 0;
            familyRescued = new boolean[3];
            score = 0;
            accumulatedMs = 0;
            activeWeapon = W_GARRAS;
            showingPrologue = true;
        }

        kills = 0;
        isDead = false;
        gameWon = false;
        darkEnding = false;
        paused = false;
        showMinimap = false;
        flickerActive = false;
        shakeFrames = 0;
        damageCooldown = 0;
        hurtFlash = 0;
        weaponCooldown = 0;
        whistleCooldown = 0;
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
        theme = currentLevel - 1;
        buildLevel();
        playerX = TILE_SIZE * 2;
        playerY = TILE_SIZE * 2;
        up = down = left = right = false;
        bullets.clear();
        spawnQueue.clear();
        fx.clear();
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
        p.molotov = hasMolotov;
        p.whistle = hasWhistle;
        p.armor = armorLevel;
        p.tuna = tunaCans;
        p.family = familyRescued.clone();
        p.score = score;
        p.playTimeMs = totalPlayTime();
        SaveManager.save(p);
    }

    private long totalPlayTime() {
        return accumulatedMs + (System.currentTimeMillis() - levelStartMs);
    }

    // ------------------------------------------------------------------
    // Construcción del barrio
    // ------------------------------------------------------------------

    private void buildLevel() {
        map = MapGenerator.generate(currentLevel, rand, MAP_WIDTH, MAP_HEIGHT);
        enemies.clear();
        currentBoss = null;

        // Mercado Negro: uno junto al inicio y otro a media ciudad
        placeShop(6, 2);
        placeShop(MAP_WIDTH / 2 + 2, MAP_HEIGHT / 2);

        // Salida del barrio
        for (int x = MAP_WIDTH - 3; x <= MAP_WIDTH - 2; x++) {
            for (int y = MAP_HEIGHT - 3; y <= MAP_HEIGHT - 2; y++) map[x][y] = Tiles.EXIT;
        }

        // Monedas repartidas por las calles
        for (int i = 0; i < 230; i++) {
            int rx = rand.nextInt(MAP_WIDTH - 4) + 2;
            int ry = rand.nextInt(MAP_HEIGHT - 4) + 2;
            if (map[rx][ry] == Tiles.FLOOR && (rx > 5 || ry > 5)) map[rx][ry] = Tiles.COIN;
        }

        // Pandilleros
        int wanted = (int) Math.round((20 + currentLevel * 5) * diffEnemyMult);
        int placed = 0, tries = 0;
        while (placed < wanted && tries < wanted * 20) {
            tries++;
            int rx = rand.nextInt(MAP_WIDTH - 12) + 10;
            int ry = rand.nextInt(MAP_HEIGHT - 12) + 10;
            if (!Tiles.walkable(map[rx][ry])) continue;
            enemies.add(new Enemy(rx * TILE_SIZE, ry * TILE_SIZE, pickEnemyType(), currentLevel));
            placed++;
        }

        // Jefe, custodiando la salida
        int bx = MAP_WIDTH - 8, by = MAP_HEIGHT - 8;
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int y = by - 1; y <= by + 1; y++) {
                if (x > 0 && y > 0 && x < MAP_WIDTH - 1 && y < MAP_HEIGHT - 1) map[x][y] = Tiles.FLOOR;
            }
        }
        LevelInfo info = levels[currentLevel - 1];
        Enemy boss = new Enemy(bx * TILE_SIZE, by * TILE_SIZE, currentLevel, info.bossName, info.bossBreed);
        enemies.add(boss);
        currentBoss = boss;
    }

    private void placeShop(int sx, int sy) {
        for (int x = sx; x <= sx + 1; x++) {
            for (int y = sy; y <= sy + 1; y++) {
                if (x > 0 && y > 0 && x < MAP_WIDTH - 1 && y < MAP_HEIGHT - 1) map[x][y] = Tiles.SHOP;
            }
        }
        // Se despeja el borde para poder llegar caminando
        for (int x = sx - 1; x <= sx + 2; x++) {
            for (int y = sy - 1; y <= sy + 2; y++) {
                if (x > 0 && y > 0 && x < MAP_WIDTH - 1 && y < MAP_HEIGHT - 1
                    && map[x][y] != Tiles.SHOP && Tiles.blocks(map[x][y])) {
                    map[x][y] = Tiles.FLOOR;
                }
            }
        }
    }

    /** Cada barrio va sumando tipos de perro nuevos. */
    private int pickEnemyType() {
        int roll = rand.nextInt(100);
        switch (currentLevel) {
            case 1:  return roll < 75 ? E_CALLEJERO : E_CORREDOR;
            case 2:  return roll < 45 ? E_CALLEJERO : roll < 75 ? E_ARMADO : E_CORREDOR;
            case 3:  return roll < 34 ? E_CALLEJERO : roll < 60 ? E_ARMADO
                          : roll < 80 ? E_CORREDOR : E_BRUTO;
            case 4:  return roll < 26 ? E_CALLEJERO : roll < 50 ? E_ARMADO : roll < 66 ? E_CORREDOR
                          : roll < 82 ? E_BRUTO : E_ESCUDO;
            case 5:  return roll < 20 ? E_CALLEJERO : roll < 42 ? E_ARMADO : roll < 56 ? E_CORREDOR
                          : roll < 72 ? E_BRUTO : roll < 86 ? E_ESCUDO : E_KAMIKAZE;
            default: return roll < 16 ? E_CALLEJERO : roll < 36 ? E_ARMADO : roll < 48 ? E_CORREDOR
                          : roll < 64 ? E_BRUTO : roll < 78 ? E_ESCUDO : roll < 90 ? E_KAMIKAZE : E_ALFA;
        }
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
        return Tiles.blocks(map[x1][y1]) || Tiles.blocks(map[x2][y1])
            || Tiles.blocks(map[x1][y2]) || Tiles.blocks(map[x2][y2]);
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
        fx.update();

        if (playerHealth <= 0) triggerDeath();

        repaint();
    }

    private void updatePlayer() {
        double dx = 0, dy = 0;
        if (up) { dy -= 1; facing = 0; }
        if (down) { dy += 1; facing = 1; }
        if (left) { dx -= 1; facing = 2; }
        if (right) { dx += 1; facing = 3; }

        boolean moving = dx != 0 || dy != 0;
        if (dx != 0 && dy != 0) { dx *= 0.7071; dy *= 0.7071; }
        dx *= playerSpeed;
        dy *= playerSpeed;

        // Eje por eje: así Nox se desliza por las paredes en vez de quedarse trabado
        if (dx != 0 && !blocked(playerX + dx, playerY, PLAYER_SIZE)) playerX += dx;
        if (dy != 0 && !blocked(playerX, playerY + dy, PLAYER_SIZE)) playerY += dy;

        if (moving && animationFrame % 9 == 0) fx.step(playerX + 12, playerY + 32);

        int cx = clampTile((int) ((playerX + 15) / TILE_SIZE), MAP_WIDTH);
        int cy = clampTile((int) ((playerY + 15) / TILE_SIZE), MAP_HEIGHT);

        if (map[cx][cy] == Tiles.COIN) {
            map[cx][cy] = Tiles.FLOOR;
            playerMoney += 50;
            score += 25;
            fx.text(playerX + 8, playerY - 6, "+$50", new Color(255, 200, 70), 15);
            AudioEngine.play("coin", 0.7);
        }

        nearExitLocked = false;
        if (map[cx][cy] == Tiles.EXIT) {
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
        for (int i = -2; i <= 2 && !nearStore; i++) {
            for (int j = -2; j <= 2; j++) {
                if (map[clampTile(cx + i, MAP_WIDTH)][clampTile(cy + j, MAP_HEIGHT)] == Tiles.SHOP) {
                    nearStore = true;
                    break;
                }
            }
        }

        if (isAttacking && --attackTimer <= 0) isAttacking = false;
        if (weaponCooldown > 0) weaponCooldown--;
        if (whistleCooldown > 0) whistleCooldown--;
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

            if (p.life <= 0 || Tiles.blocks(map[tx][ty])) {
                if (p.molotov) explode(p.x, p.y, 110, 90);
                else fx.hit(p.x, p.y, new Color(200, 200, 210));
                it.remove();
                continue;
            }

            boolean hit = false;
            if (p.isEnemyBullet) {
                if (Point2D.distance(p.x, p.y, playerX + 15, playerY + 15) < 20) {
                    damagePlayer(p.damage, false);
                    fx.hit(p.x, p.y, new Color(255, 90, 60));
                    hit = true;
                }
            } else {
                for (Enemy en : enemies) {
                    double reach = en.size * 0.75;
                    if (Point2D.distance(p.x, p.y, en.x + en.size / 2.0, en.y + en.size / 2.0) < reach) {
                        if (p.molotov) explode(p.x, p.y, 110, 90);
                        else hurtEnemy(en, p.damage, p.x, p.y);
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) it.remove();
        }
    }

    /** Daño en área del molotov y del cachorro kamikaze. */
    private void explode(double x, double y, double radius, int damage) {
        fx.explosion(x, y, radius / 110.0);
        shakeFrames = 16;
        shakeMagnitude = 9;
        AudioEngine.play("boom");
        for (Enemy en : enemies) {
            double d = Point2D.distance(x, y, en.x + en.size / 2.0, en.y + en.size / 2.0);
            if (d < radius) {
                int dmg = (int) (damage * (1 - d / radius * 0.55));
                en.hp -= dmg;               // la explosión ignora el escudo
                fx.text(en.x, en.y - 18, String.valueOf(dmg), new Color(255, 170, 60), 15);
            }
        }
    }

    /** Aplica daño a un perro teniendo en cuenta el escudo, que solo protege de frente. */
    private void hurtEnemy(Enemy en, int damage, double fromX, double fromY) {
        int dealt = damage;
        boolean blockedByShield = false;

        if (en.shield) {
            double ang = Math.atan2(fromY - (en.y + en.size / 2.0), fromX - (en.x + en.size / 2.0));
            double facingAng = en.facing == 3 ? 0 : en.facing == 1 ? Math.PI / 2
                             : en.facing == 2 ? Math.PI : -Math.PI / 2;
            double diff = Math.abs(normalizeAngle(ang - facingAng));
            if (diff < Math.PI / 2.2) {
                dealt = Math.max(1, (int) (damage * 0.15));
                blockedByShield = true;
            }
        }

        en.hp -= dealt;
        en.hurtFlash = 6;

        if (blockedByShield) {
            fx.hit(fromX, fromY, new Color(200, 220, 255));
            fx.text(en.x, en.y - 18, "BLOQUEADO", new Color(160, 190, 230), 13);
            AudioEngine.play("clang", 0.7);
        } else {
            fx.hit(fromX, fromY, new Color(255, 190, 120));
            fx.text(en.x + rand.nextInt(14), en.y - 18, String.valueOf(dealt), Color.WHITE, 15);
            AudioEngine.play("hit", 0.5);
        }
    }

    private static double normalizeAngle(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
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
                onEnemyDown(en);
                it.remove();
                continue;
            }

            if (en.hurtFlash > 0) en.hurtFlash--;
            if (en.stunTimer > 0) {
                en.stunTimer--;
                continue;                       // aturdido por el silbato: no se mueve ni ataca
            }

            double dist = Point2D.distance(playerX, playerY, en.x, en.y);
            if (dist < 280) enemyNearby = true;

            if (!en.isBoss && dist < 130 && !isJumpscareActive && rand.nextInt(420) == 3) {
                triggerJumpscare();
                damagePlayer((int) Math.round(10 * diffDmgMult), true);
            }

            if (en.barkCooldown > 0) en.barkCooldown--;
            else if (dist < 300 && rand.nextInt(300) == 0) {
                en.barkCooldown = 180;
                AudioEngine.play("bark", 0.45);
            }

            // El alfa aúlla y llama refuerzos
            if (en.type == E_ALFA && dist < 520 && --en.specialCooldown <= 0) {
                en.specialCooldown = 400;
                AudioEngine.play("howl", 0.8);
                fx.text(en.x, en.y - 26, "¡REFUERZOS!", new Color(255, 120, 60), 14);
                if (enemies.size() + spawnQueue.size() < ENEMY_CAP) {
                    for (int k = 0; k < 2; k++) {
                        spawnQueue.add(new Enemy(en.x + rand.nextInt(90) - 45,
                                                 en.y + rand.nextInt(90) - 45, E_CALLEJERO, currentLevel));
                    }
                }
            }

            // El kamikaze se lanza y explota
            if (en.type == E_KAMIKAZE && dist < 46) {
                explode(en.x + en.size / 2.0, en.y + en.size / 2.0, 105, 55);
                damagePlayer((int) Math.round(30 * diffDmgMult), false);
                en.hp = 0;
                en.exploded = true;
                continue;
            }

            moveEnemy(en, dist);
            enemyAttacks(en, dist);
        }

        if (!spawnQueue.isEmpty()) {
            enemies.addAll(spawnQueue);
            spawnQueue.clear();
        }

        sanity += enemyNearby ? -0.05 : 0.03;
        sanity = Math.max(0, Math.min(100, sanity));
        AudioEngine.setTension(1.0 - sanity / 100.0);
    }

    private void onEnemyDown(Enemy en) {
        kills++;
        fx.death(en.x + en.size / 2.0, en.y + en.size / 2.0, en.bodyColor);

        if (en.isBoss) {
            playerMoney += 500;
            score += 2000;
            bossDefeated = true;
            currentBoss = null;
            sanity = Math.min(100, sanity + 25);
            AudioEngine.setBossMode(false);
            AudioEngine.play("door");
            explode(en.x + en.size / 2.0, en.y + en.size / 2.0, 60, 0);

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
        } else if (!en.exploded) {
            playerMoney += en.reward;
            score += en.reward;
            fx.text(en.x, en.y - 30, "+$" + en.reward, new Color(255, 200, 70), 15);
        }
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

            if (--en.specialCooldown <= 0 && dist < 520) {
                en.specialCooldown = 240;
                double bx = en.x + en.size / 2.0, by = en.y + en.size / 2.0;
                for (int i = 0; i < 12; i++) {
                    bullets.add(new Projectile(bx, by, Math.PI * 2 * i / 12.0, 6.0, 14, true));
                }
                fx.explosion(bx, by, 0.4);
                AudioEngine.play("plasma", 0.5);
            }
        }

        double vx = Math.signum(playerX - en.x) * speed;
        double vy = Math.signum(playerY - en.y) * speed;

        // Rodear esquinas: si se queda pegado a un muro, camina de lado un rato
        if (en.detourTimer > 0) {
            en.detourTimer--;
            double tmp = vx;
            vx = -vy * en.detourDir;
            vy = tmp * en.detourDir;
        }

        boolean moved = false;
        if (vx != 0 && !blocked(en.x + vx, en.y, en.size)) { en.x += vx; moved = true; }
        if (vy != 0 && !blocked(en.x, en.y + vy, en.size)) { en.y += vy; moved = true; }

        if (!moved && en.detourTimer <= 0) {
            en.detourDir = rand.nextBoolean() ? 1 : -1;
            en.detourTimer = 32;
        }

        if (vx > 0) en.facing = 3;
        else if (vx < 0) en.facing = 2;
        else if (vy < 0) en.facing = 0;
        else if (vy > 0) en.facing = 1;
    }

    private void enemyAttacks(Enemy en, double dist) {
        int fireInterval = en.isBoss ? 42 : 85;
        if (en.hasGun && dist < 430 && animationFrame % fireInterval == 0) {
            int dmg = en.isBoss ? 20 : 13;
            double bx = en.x + en.size / 2.0, by = en.y + en.size / 2.0;
            bullets.add(new Projectile(bx, by, playerX + 15, playerY + 15, en.isBoss ? 9.0 : 7.5, dmg, true));
            fx.muzzle(bx, by, Math.atan2(playerY - by, playerX - bx), new Color(255, 170, 60));
            AudioEngine.play("shoot", 0.30);
        }

        if (en.type == E_KAMIKAZE) return;

        double contact = en.size * 0.9;
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

        int reduced = (int) Math.round(amount * (1 - ARMOR_REDUCTION[Math.min(3, armorLevel)]));
        reduced = Math.max(1, reduced);

        playerHealth -= reduced;
        damageCooldown = 26;
        hurtFlash = 12;
        sanity = Math.max(0, sanity - 1.5);
        fx.text(playerX + 4, playerY - 12, "-" + reduced, new Color(255, 80, 80), 16);
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

        // Salpicaduras de lluvia alrededor de Nox
        for (int i = 0; i < 2; i++) {
            double sx = playerX + rand.nextInt(900) - 450;
            double sy = playerY + rand.nextInt(700) - 350;
            int tx = clampTile((int) (sx / TILE_SIZE), MAP_WIDTH);
            int ty = clampTile((int) (sy / TILE_SIZE), MAP_HEIGHT);
            if (Tiles.walkable(map[tx][ty])) fx.splash(sx, sy);
        }

        // Brasas de los bidones encendidos del basurero
        if (theme == 0 && animationFrame % 3 == 0) {
            int tx = clampTile((int) (playerX / TILE_SIZE) + rand.nextInt(16) - 8, MAP_WIDTH);
            int ty = clampTile((int) (playerY / TILE_SIZE) + rand.nextInt(12) - 6, MAP_HEIGHT);
            if (map[tx][ty] == Tiles.PROP && Sprites.hash(tx, ty) % 3 == 0) {
                fx.ember(tx * TILE_SIZE + TILE_SIZE / 2.0, ty * TILE_SIZE + TILE_SIZE / 2.0);
            }
        }
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
        // El precio de la cordura: si Nox llega destrozado por dentro, gana pero se pierde a sí mismo
        darkEnding = sanity < 45;
        score += 5000 + (int) (sanity * 20);
        accumulatedMs = totalPlayTime();
        AudioEngine.setBossMode(false);
        AudioEngine.play(darkEnding ? "darkending" : "victory");
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

        camX = (int) playerX - getWidth() / 2 + 15 + shakeX;
        camY = (int) playerY - getHeight() / 2 + 15 + shakeY;
        camX = Math.max(0, Math.min(camX, MAP_WIDTH * TILE_SIZE - getWidth()));
        camY = Math.max(0, Math.min(camY, MAP_HEIGHT * TILE_SIZE - getHeight()));

        g2d.translate(-camX, -camY);
        drawWorld(g2d);
        drawActors(g2d, info);
        fx.draw(g2d);
        g2d.translate(camX, camY);

        // La linterna sigue a Nox: en los bordes del mapa la cámara se detiene
        // pero el gato se sigue moviendo, así que la luz no puede quedarse fija en el centro.
        drawWeather(g2d, info, (int) playerX - camX + 15, (int) playerY - camY + 15);
        drawLights(g2d);
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

    private int viewX0() { return Math.max(0, camX / TILE_SIZE - 1); }
    private int viewY0() { return Math.max(0, camY / TILE_SIZE - 1); }
    private int viewX1() { return Math.min(MAP_WIDTH, (camX + getWidth()) / TILE_SIZE + 2); }
    private int viewY1() { return Math.min(MAP_HEIGHT, (camY + getHeight()) / TILE_SIZE + 2); }

    private void drawWorld(Graphics2D g2d) {
        for (int x = viewX0(); x < viewX1(); x++) {
            for (int y = viewY0(); y < viewY1(); y++) {
                int px = x * TILE_SIZE, py = y * TILE_SIZE;
                int tile = map[x][y];
                int variant = Sprites.hash(x, y) % Sprites.VARIANTS;

                switch (tile) {
                    case Tiles.SHOP:
                        Sprites.drawStore(g2d, x, y, TILE_SIZE, animationFrame);
                        break;
                    case Tiles.EXIT:
                        Sprites.drawExit(g2d, x, y, TILE_SIZE, bossDefeated, animationFrame);
                        break;
                    case Tiles.COIN:
                        g2d.drawImage(Sprites.tile(Tiles.FLOOR, theme, variant, TILE_SIZE), px, py, null);
                        Sprites.drawCoin(g2d, px, py, TILE_SIZE, animationFrame);
                        break;
                    case Tiles.WATER:
                        g2d.drawImage(Sprites.tile(Tiles.WATER, theme, variant, TILE_SIZE), px, py, null);
                        Sprites.waterShimmer(g2d, px, py, TILE_SIZE, x, y, animationFrame);
                        break;
                    default:
                        g2d.drawImage(Sprites.tile(tile, theme, variant, TILE_SIZE), px, py, null);
                }
            }
        }
    }

    private void drawActors(Graphics2D g2d, LevelInfo info) {
        for (Enemy en : enemies) {
            Color body = en.hurtFlash > 0 ? new Color(255, 210, 210)
                       : en.isBoss ? info.bossColor : en.bodyColor;
            Sprites.drawDog(g2d, (int) en.x, (int) en.y, en.size, body, en.breed,
                            en.hasGun, en.isBoss, en.facing, animationFrame,
                            en.shield, en.stunTimer > 0);

            if (en.isBoss) {
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Consolas", Font.BOLD, 13));
                int w = g2d.getFontMetrics().stringWidth(en.name);
                g2d.drawString(en.name, (int) en.x + en.size / 2 - w / 2, (int) en.y - 26);
            }

            int barW = en.size;
            g2d.setColor(new Color(0, 0, 0, 200));
            g2d.fillRect((int) en.x, (int) en.y - 14, barW, 6);
            g2d.setColor(en.isBoss ? new Color(255, 60, 200) : new Color(220, 50, 50));
            g2d.fillRect((int) en.x, (int) en.y - 14,
                         (int) (barW * Math.max(0, en.hp) / (double) en.maxHp), 6);
        }

        for (Projectile p : bullets) {
            Color c = p.molotov ? new Color(255, 150, 40)
                    : p.isEnemyBullet ? new Color(255, 70, 50) : new Color(120, 230, 255);
            g2d.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
            g2d.fillOval((int) p.x - 6, (int) p.y - 6, 22, 22);
            g2d.setColor(c);
            g2d.fillOval((int) p.x, (int) p.y, 10, 10);
        }

        boolean moving = up || down || left || right;
        Sprites.drawNox(g2d, (int) playerX, (int) playerY, facing, animationFrame,
                        moving, isAttacking, hurtFlash > 0, armorLevel);

        if (isAttacking && activeWeapon <= W_ACERO) {
            Sprites.drawClawSlash(g2d, (int) playerX + 15, (int) playerY + 15,
                                  facing, activeWeapon, animationFrame);
        }
    }

    private void drawWeather(Graphics2D g2d, LevelInfo info, int lightX, int lightY) {
        g2d.setColor(new Color(180, 200, 220, 95));
        for (RainDrop r : rainSystem) {
            g2d.drawLine((int) r.x, (int) r.y, (int) r.x - 3, (int) r.y + 18);
        }

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

    /** Focos de luz que se dibujan encima de la oscuridad: neones, fuego y la salida. */
    private void drawLights(Graphics2D g2d) {
        for (int x = viewX0(); x < viewX1(); x++) {
            for (int y = viewY0(); y < viewY1(); y++) {
                int sx = x * TILE_SIZE + TILE_SIZE / 2 - camX;
                int sy = y * TILE_SIZE + TILE_SIZE / 2 - camY;

                if (map[x][y] == Tiles.SHOP) {
                    int pulse = 30 + (int) (12 * Math.sin(animationFrame * 0.08));
                    Sprites.drawLight(g2d, sx, sy, 105, new Color(255, 40, 150), pulse);
                } else if (map[x][y] == Tiles.EXIT && bossDefeated) {
                    Sprites.drawLight(g2d, sx, sy, 140, new Color(60, 255, 140), 45);
                } else if (map[x][y] == Tiles.PROP && theme == 0 && Sprites.hash(x, y) % 3 == 0) {
                    int flick = 70 + (int) (30 * Math.sin(animationFrame * 0.25 + x));
                    Sprites.drawLight(g2d, sx, sy, 120, new Color(255, 150, 50), flick);
                } else if (map[x][y] == Tiles.WALL && theme == 4 && Sprites.hash(x, y) % 5 == 0) {
                    Sprites.drawLight(g2d, sx, sy, 95, new Color(230, 180, 100), 45);
                }
            }
        }

        // El resplandor de los ojos de Nox
        Sprites.drawLight(g2d, (int) playerX - camX + 15, (int) playerY - camY + 6,
                          70, new Color(140, 255, 90), 34);
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    private void drawHud(Graphics2D g2d, LevelInfo info) {
        g2d.setColor(new Color(0, 0, 0, 215));
        g2d.fillRect(10, 10, getWidth() - 20, 44);
        g2d.setColor(new Color(70, 70, 80));
        g2d.drawRect(10, 10, getWidth() - 20, 44);

        g2d.setColor(new Color(60, 15, 15));
        g2d.fillRect(20, 20, 200, 22);
        g2d.setColor(playerHealth > 35 ? new Color(0, 220, 100) : new Color(255, 60, 60));
        g2d.fillRect(20, 20, Math.max(0, Math.min(100, playerHealth)) * 2, 22);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(20, 20, 200, 22);
        g2d.setFont(new Font("Consolas", Font.BOLD, 13));
        g2d.drawString(Math.max(0, playerHealth) + " HP", 88, 37);

        g2d.setColor(new Color(255, 190, 70));
        g2d.setFont(new Font("Consolas", Font.BOLD, 14));
        g2d.drawString(info.name + "   $" + playerMoney + "   " + weaponLabel(), 232, 30);

        g2d.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2d.setColor(new Color(170, 200, 230));
        String gear = ARMOR_NAMES[Math.min(3, armorLevel)] + "   [Q] ATÚN x" + tunaCans;
        if (hasWhistle) gear += "   [F] SILBATO " + (whistleCooldown > 0 ? (whistleCooldown / 60 + 1) + "s" : "LISTO");
        g2d.drawString(gear, 232, 47);

        g2d.setFont(new Font("Consolas", Font.BOLD, 12));
        int fx0 = getWidth() - 250;
        for (int i = 0; i < familyNames.length; i++) {
            g2d.setColor(familyRescued[i] ? new Color(0, 230, 110) : new Color(90, 20, 20));
            g2d.fillRoundRect(fx0 + i * 78, 18, 68, 26, 8, 8);
            g2d.setColor(familyRescued[i] ? Color.BLACK : new Color(160, 120, 120));
            int w = g2d.getFontMetrics().stringWidth(familyNames[i]);
            g2d.drawString(familyNames[i], fx0 + i * 78 + 34 - w / 2, 36);
        }

        // Cordura: se marca en rojo cuando ya condiciona el final
        g2d.setColor(new Color(0, 0, 0, 215));
        g2d.fillRect(10, 60, 230, 20);
        g2d.setColor(sanity < 45 ? new Color(210, 30, 60) : new Color(140, 30, 210));
        g2d.fillRect(14, 64, (int) (202 * (sanity / 100.0)), 12);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Consolas", Font.PLAIN, 11));
        g2d.drawString("CORDURA " + (int) sanity + "%", 18, 74);

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
        g2d.drawString("[ESPACIO] Atacar  [1-4] Arma  [Q] Curar  [M] Mapa  [P/ESC] Pausa", 14, getHeight() - 14);
        String cloud = SaveManager.getCloudStatus();
        int cw = g2d.getFontMetrics().stringWidth(cloud);
        g2d.drawString(cloud, getWidth() - cw - 14, getHeight() - 14);
    }

    private String weaponLabel() {
        switch (activeWeapon) {
            case W_ACERO:    return "[1] GARRAS DE ACERO";
            case W_BALLESTA: return "[2] BALLESTA";
            case W_PLASMA:   return "[3] PLASMA";
            case W_MOLOTOV:  return "[4] MOLOTOV";
            default:         return "[1] GARRAS";
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
                switch (map[x][y]) {
                    case Tiles.WALL:
                        g2d.setColor(new Color(70, 70, 82)); g2d.fillRect(px, py, 4, 4); break;
                    case Tiles.WATER:
                        g2d.setColor(new Color(30, 70, 100)); g2d.fillRect(px, py, 4, 4); break;
                    case Tiles.VOID:
                        g2d.setColor(new Color(10, 10, 14)); g2d.fillRect(px, py, 4, 4); break;
                    case Tiles.EXIT:
                        g2d.setColor(bossDefeated ? new Color(0, 255, 120) : new Color(255, 50, 50));
                        g2d.fillRect(px, py, 5, 5); break;
                    case Tiles.SHOP:
                        g2d.setColor(new Color(255, 0, 150)); g2d.fillRect(px, py, 4, 4); break;
                    default:
                        break;
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
        int ly = 138;
        for (String line : lines) { g2d.drawString(line, 60, ly); ly += 26; }

        g2d.setColor(new Color(255, 180, 50));
        g2d.setFont(new Font("Consolas", Font.BOLD, 15));
        ly += 10;
        for (int i = 0; i < levels.length; i++) {
            g2d.drawString((i + 1) + ". " + levels[i].name + " — " + levels[i].bossName, 60, ly);
            ly += 21;
        }

        g2d.setColor(new Color(190, 90, 220));
        g2d.setFont(new Font("Consolas", Font.ITALIC, 15));
        g2d.drawString("Cuida tu CORDURA: de ella depende cómo termina esta historia.", 60, ly + 16);

        Sprites.drawNox(g2d, getWidth() - 150, getHeight() - 190, 2, animationFrame, false, false, false, 0);

        centeredText(g2d, "Presiona ESPACIO o ENTER para comenzar...", getHeight() - 28,
                     new Font("Consolas", Font.ITALIC, 16), new Color(160, 160, 160));
    }

    private void drawLevelIntro(Graphics2D g2d, LevelInfo info) {
        g2d.setColor(new Color(0, 0, 0, 215));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        centeredText(g2d, info.name, getHeight() / 2 - 20, new Font("Consolas", Font.BOLD, 36), new Color(255, 60, 60));
        String sub = (currentLevel == MAX_LEVEL ? "JEFE FINAL: " : "JEFE DEL BARRIO: ") + info.bossName;
        centeredText(g2d, sub, getHeight() / 2 + 25, new Font("Consolas", Font.PLAIN, 20), Color.WHITE);
        if (info.rescueTarget != null) {
            centeredText(g2d, "Aquí retienen a " + info.rescueTarget, getHeight() / 2 + 60,
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

    /** Dos finales: depende de con cuánta cordura llegue Nox a la mansión. */
    private void drawVictory(Graphics2D g2d) {
        long secs = accumulatedMs / 1000;

        if (darkEnding) {
            g2d.setColor(new Color(10, 0, 8, 252));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            centeredText(g2d, "FINAL OSCURO", 90, new Font("Consolas", Font.BOLD, 26), new Color(150, 40, 190));
            centeredText(g2d, "EL GATO QUE NO VOLVIÓ", 150,
                         new Font("Consolas", Font.BOLD, 40), new Color(220, 40, 60));

            String[] text = {
                "Don Colmillo cayó. La pandilla se deshizo esa misma noche.",
                "",
                "Pero cuando Mitsy lo llamó por su nombre,",
                "Nox no se dio la vuelta.",
                "",
                "Demasiadas noches en la oscuridad. Demasiados aullidos.",
                "El gato que entró a la mansión no fue el que salió.",
                "",
                "Su familia volvió a casa. Él se quedó en la ciudad,",
                "y la ciudad aprendió a temerle igual que a Colmillo.",
            };
            g2d.setFont(new Font("Consolas", Font.PLAIN, 18));
            int ly = 220;
            for (String line : text) {
                g2d.setColor(new Color(195, 185, 195));
                int w = g2d.getFontMetrics().stringWidth(line);
                g2d.drawString(line, getWidth() / 2 - w / 2, ly);
                ly += 26;
            }

            centeredText(g2d, "Terminaste con " + (int) sanity + "% de cordura.", ly + 22,
                         new Font("Consolas", Font.BOLD, 17), new Color(150, 40, 190));
            centeredText(g2d, "Llega con más de 45% para ver el otro final.", ly + 48,
                         new Font("Consolas", Font.ITALIC, 15), new Color(140, 130, 140));
            centeredText(g2d, "PUNTUACIÓN: " + score + "   |   " + kills + " perros   |   "
                              + (secs / 60) + "m " + (secs % 60) + "s   |   " + difficultyName(),
                         ly + 78, new Font("Consolas", Font.PLAIN, 16), new Color(255, 190, 70));
        } else {
            g2d.setColor(new Color(0, 0, 0, 250));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            centeredText(g2d, "FINAL BUENO", 90, new Font("Consolas", Font.BOLD, 26), new Color(0, 200, 110));
            centeredText(g2d, "¡HAS DERROTADO A DON COLMILLO!", 150,
                         new Font("Consolas", Font.BOLD, 38), new Color(0, 255, 120));

            String[] text = {
                "La pandilla se deshizo esa misma noche.",
                "",
                "Mitsy, Will y Nina están a salvo.",
                "",
                "Nox aguantó la oscuridad sin dejar que lo cambiara,",
                "y volvió a ser el gato del callejón:",
                "el que ronronea cuando sus gatitos se le suben encima.",
                "",
                "La ciudad amaneció, por primera vez en mucho tiempo,",
                "sin nadie a quien pagarle protección.",
            };
            g2d.setFont(new Font("Consolas", Font.PLAIN, 18));
            int ly = 220;
            for (String line : text) {
                g2d.setColor(new Color(220, 220, 220));
                int w = g2d.getFontMetrics().stringWidth(line);
                g2d.drawString(line, getWidth() / 2 - w / 2, ly);
                ly += 26;
            }

            Sprites.drawNox(g2d, getWidth() / 2 - 15, ly + 12, 1, animationFrame, false, false, false, armorLevel);

            centeredText(g2d, "PUNTUACIÓN: " + score + "   |   " + kills + " perros   |   "
                              + (secs / 60) + "m " + (secs % 60) + "s   |   " + difficultyName(),
                         ly + 84, new Font("Consolas", Font.PLAIN, 16), new Color(255, 190, 70));
        }

        centeredText(g2d, "Presiona ESC para volver al menú", getHeight() - 26,
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

    private String[] shopItems() {
        return new String[]{
            "Garras de Acero      cuerpo a cuerpo devastador    $300 " + (hasSteelClaws ? "[COMPRADO]" : ""),
            "Ballesta de Chatarra ataque a distancia            $500 " + (hasCrossbow ? "[COMPRADO]" : ""),
            "Lanzador de Plasma   ráfaga de 5 disparos          $900 " + (hasPlasmaLauncher ? "[COMPRADO]" : ""),
            "Cóctel Molotov       explosión en área             $700 " + (hasMolotov ? "[COMPRADO]" : ""),
            "Silbato Ultrasónico  aturde a los perros           $450 " + (hasWhistle ? "[COMPRADO]" : ""),
            "Collar Reforzado     -15% de daño recibido         $250 " + (armorLevel >= 1 ? "[COMPRADO]" : ""),
            "Chaleco de Cuero     -30% de daño recibido         $600 " + (armorLevel >= 2 ? "[COMPRADO]" : ""),
            "Placas de Acero      -45% de daño recibido        $1100 " + (armorLevel >= 3 ? "[COMPRADO]" : ""),
            "Lata de Atún         +50 salud, la llevas contigo   $100 (tienes " + tunaCans + "/" + MAX_TUNA + ")",
            "Amuleto de Cordura   +40 de cordura                $150",
        };
    }

    private void drawShop(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 238));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        int w = 880, h = 610;
        int x = getWidth() / 2 - w / 2, y = getHeight() / 2 - h / 2;
        g2d.setColor(new Color(22, 8, 24));
        g2d.fillRect(x, y, w, h);
        g2d.setColor(new Color(255, 0, 150));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRect(x, y, w, h);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setFont(new Font("Consolas", Font.BOLD, 26));
        g2d.drawString("MERCADO NEGRO", x + 26, y + 42);
        g2d.setColor(new Color(255, 190, 70));
        g2d.setFont(new Font("Consolas", Font.BOLD, 22));
        String funds = "FONDOS: $" + playerMoney;
        g2d.drawString(funds, x + w - 30 - g2d.getFontMetrics().stringWidth(funds), y + 42);

        String[] items = shopItems();
        g2d.setFont(new Font("Consolas", Font.PLAIN, 16));
        for (int i = 0; i < items.length; i++) {
            int iy = y + 108 + i * 44;
            if (i == selectedShopItem) {
                g2d.setColor(new Color(255, 0, 150, 60));
                g2d.fillRect(x + 20, iy - 20, w - 40, 32);
                g2d.setColor(Color.YELLOW);
                g2d.drawString("> " + items[i], x + 32, iy);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.drawString("  " + items[i], x + 32, iy);
            }
        }

        if (shopMessageTimer > 0) {
            g2d.setColor(shopMessage.startsWith("¡COMPRA") ? new Color(0, 255, 120) : new Color(255, 60, 60));
            g2d.setFont(new Font("Consolas", Font.BOLD, 20));
            g2d.drawString(shopMessage, x + 26, y + 78);
        }

        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font("Consolas", Font.PLAIN, 15));
        g2d.drawString("[W/S] Navegar    [ENTER] Comprar    [ESC/E] Cerrar", x + 26, y + h - 22);
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
        boolean hasGun, isBoss, shield, exploded;
        String name;
        Color bodyColor;
        int facing = 1;
        int barkCooldown = 0;
        int specialCooldown = 200;
        int chargeCooldown = 260;
        int chargeTimer = 0;
        int detourTimer = 0;
        int detourDir = 1;
        int stunTimer = 0;
        int hurtFlash = 0;

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
                    speed = 4.5; size = 34; breed = 4;
                    contactDamage = 9; reward = 130;
                    bodyColor = new Color(150, 110, 60);
                    break;
                case E_BRUTO:
                    maxHp = (int) Math.round(base * 1.9 * diffHpMult);
                    speed = 1.7; size = 54; breed = 0;
                    contactDamage = 22; reward = 260;
                    bodyColor = new Color(70, 65, 70);
                    break;
                case E_ESCUDO:
                    maxHp = (int) Math.round(base * 1.3 * diffHpMult);
                    speed = 2.0; size = 46; breed = 0; shield = true;
                    contactDamage = 16; reward = 300;
                    bodyColor = new Color(88, 70, 58);
                    break;
                case E_ALFA:
                    maxHp = (int) Math.round(base * 1.5 * diffHpMult);
                    speed = 2.9; size = 50; breed = 2; hasGun = true;
                    contactDamage = 18; reward = 350;
                    specialCooldown = 260;
                    bodyColor = new Color(60, 52, 46);
                    break;
                case E_KAMIKAZE:
                    maxHp = (int) Math.round(base * 0.35 * diffHpMult);
                    speed = 5.2; size = 30; breed = 4;
                    contactDamage = 0; reward = 120;
                    bodyColor = new Color(180, 120, 40);
                    break;
                default:
                    maxHp = (int) Math.round(base * diffHpMult);
                    speed = 2.7; size = 40; breed = 1;
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
        boolean isEnemyBullet, molotov;
        int life = 150;

        Projectile(double x, double y, double targetX, double targetY,
                   double speed, int damage, boolean isEnemy) {
            this(x, y, Math.atan2(targetY - y, targetX - x), speed, damage, isEnemy);
        }

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
            if (key == KeyEvent.VK_ENTER) { gameLoop.stop(); continueSavedGame(); }
            else if (key == KeyEvent.VK_ESCAPE) exitToMenu();
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

        if (key == KeyEvent.VK_1) activeWeapon = hasSteelClaws ? W_ACERO : W_GARRAS;
        if (key == KeyEvent.VK_2 && hasCrossbow) activeWeapon = W_BALLESTA;
        if (key == KeyEvent.VK_3 && hasPlasmaLauncher) activeWeapon = W_PLASMA;
        if (key == KeyEvent.VK_4 && hasMolotov) activeWeapon = W_MOLOTOV;

        if (key == KeyEvent.VK_SPACE) attack();
        if (key == KeyEvent.VK_Q) useTuna();
        if (key == KeyEvent.VK_F) blowWhistle();

        if (key == KeyEvent.VK_E && nearStore) {
            isShopOpen = true;
            shopMessage = "";
            AudioEngine.play("blip");
        }
    }

    private void useTuna() {
        if (tunaCans <= 0) {
            fx.text(playerX, playerY - 20, "SIN ATÚN", new Color(200, 200, 200), 14);
            AudioEngine.play("error", 0.5);
            return;
        }
        if (playerHealth >= 100) {
            fx.text(playerX, playerY - 20, "SALUD LLENA", new Color(200, 200, 200), 14);
            return;
        }
        tunaCans--;
        int healed = Math.min(50, 100 - playerHealth);
        playerHealth += healed;
        fx.text(playerX, playerY - 20, "+" + healed, new Color(80, 255, 130), 18);
        AudioEngine.play("heal");
    }

    private void blowWhistle() {
        if (!hasWhistle) return;
        if (whistleCooldown > 0) {
            AudioEngine.play("error", 0.4);
            return;
        }
        whistleCooldown = 600;
        AudioEngine.play("whistle");
        fx.explosion(playerX + 15, playerY + 15, 0.35);

        int affected = 0;
        for (Enemy en : enemies) {
            if (Point2D.distance(playerX, playerY, en.x, en.y) < 300) {
                en.stunTimer = en.isBoss ? 70 : 160;
                affected++;
            }
        }
        fx.text(playerX - 20, playerY - 30, affected + " ATURDIDOS", new Color(255, 240, 120), 16);
    }

    private void attack() {
        if (weaponCooldown > 0) return;

        isAttacking = true;
        attackTimer = 14;
        double cx = playerX + 15, cy = playerY + 15;
        double baseAngle = facing == 0 ? -Math.PI / 2 : facing == 1 ? Math.PI / 2
                         : facing == 2 ? Math.PI : 0;

        switch (activeWeapon) {
            case W_BALLESTA:
                weaponCooldown = 16;
                bullets.add(new Projectile(cx, cy, baseAngle, 15.0, 55, false));
                fx.muzzle(cx, cy, baseAngle, new Color(140, 230, 255));
                AudioEngine.play("shoot");
                break;

            case W_PLASMA:
                weaponCooldown = 34;
                for (int i = -2; i <= 2; i++) {
                    bullets.add(new Projectile(cx, cy, baseAngle + i * 0.16, 13.0, 45, false));
                }
                fx.muzzle(cx, cy, baseAngle, new Color(180, 120, 255));
                AudioEngine.play("plasma");
                break;

            case W_MOLOTOV: {
                weaponCooldown = 58;
                Projectile p = new Projectile(cx, cy, baseAngle, 9.0, 0, false);
                p.molotov = true;
                p.life = 45;              // vuela un tramo y revienta
                bullets.add(p);
                AudioEngine.play("throw");
                break;
            }

            default: {
                boolean steel = activeWeapon == W_ACERO;
                weaponCooldown = steel ? 22 : 17;
                int damage = steel ? 85 : 30;
                double range = steel ? 115 : 62;
                boolean connected = false;
                for (Enemy en : enemies) {
                    if (Point2D.distance(cx, cy, en.x + en.size / 2.0, en.y + en.size / 2.0) <= range) {
                        hurtEnemy(en, damage, cx, cy);
                        connected = true;
                    }
                }
                AudioEngine.play(steel ? "steel" : "claw");
                if (!connected) fx.hit(cx + Math.cos(baseAngle) * 40, cy + Math.sin(baseAngle) * 40,
                                       new Color(150, 150, 160));
            }
        }
    }

    private void handleShopKey(int key) {
        if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) {
            selectedShopItem = (selectedShopItem - 1 + SHOP_ITEMS) % SHOP_ITEMS;
            AudioEngine.play("blip", 0.5);
        }
        if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) {
            selectedShopItem = (selectedShopItem + 1) % SHOP_ITEMS;
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
            case 0: purchase(hasSteelClaws, 300, () -> { hasSteelClaws = true; activeWeapon = W_ACERO; }); break;
            case 1: purchase(hasCrossbow, 500, () -> { hasCrossbow = true; activeWeapon = W_BALLESTA; }); break;
            case 2: purchase(hasPlasmaLauncher, 900, () -> { hasPlasmaLauncher = true; activeWeapon = W_PLASMA; }); break;
            case 3: purchase(hasMolotov, 700, () -> { hasMolotov = true; activeWeapon = W_MOLOTOV; }); break;
            case 4: purchase(hasWhistle, 450, () -> hasWhistle = true); break;
            case 5: purchase(armorLevel >= 1, 250, () -> armorLevel = Math.max(armorLevel, 1)); break;
            case 6: purchase(armorLevel >= 2, 600, () -> armorLevel = Math.max(armorLevel, 2)); break;
            case 7: purchase(armorLevel >= 3, 1100, () -> armorLevel = 3); break;
            case 8:
                if (tunaCans >= MAX_TUNA) { shopMessage = "NO PUEDES CARGAR MÁS"; AudioEngine.play("error"); }
                else purchase(false, 100, () -> tunaCans++);
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
