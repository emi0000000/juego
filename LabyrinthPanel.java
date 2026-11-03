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

    /**
     * Resolución en la que está pensado todo el juego.
     *
     * El dibujo siempre se hace en 1024x768 y luego se escala a la ventana real,
     * con bandas negras si hace falta. Así en pantalla completa todo se ve igual,
     * solo que más grande, en vez de descolocarse el HUD y quedar el mapa raro.
     */
    static final int VIEW_W = 1024;
    static final int VIEW_H = 768;

    private static final int PLAYER_SIZE = 30;

    /**
     * Ancho máximo de la caja de colisión de cualquier perro.
     *
     * La caja va centrada dentro del dibujo, así que un jefe de 78 px de ancho deja
     * un margen de (78 - MAX_COLLISION) / 2 a cada lado. Ese margen más la caja
     * tienen que caber DENTRO de una casilla de 60 px.
     *
     * Con 44 no cabía: 17 de margen + 44 = 61 > 60, así que la caja invadía siempre
     * la casilla de al lado. En un pasillo de una sola casilla eso dejaba al jefe
     * permanentemente contra un muro, sin poder moverse en ninguna dirección.
     * Con 40 el margen es 19 y ocupa hasta el píxel 58: entra justo.
     */
    private static final int MAX_COLLISION = 40;

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

    // Cinemáticas del prólogo y de los dos finales
    private Cinematics cinematic = null;
    private int cinematicKind = -1;
    private int cinematicShot = -1;

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
    private String rescueDialogue = "";

    /** Cada familiar rescatado deja algo de sí en Nox. */
    private static final String[] FAMILY_BUFFS = {
        "Peleas por tu hijo: +15% de daño",
        "La voz de Nina te calma: pierdes cordura más despacio",
        "Mitsy te cuida: +25 de salud máxima",
    };
    private static final String[] FAMILY_LINES = {
        "\"¡Papá! Sabía que vendrías...\"",
        "\"Tenía miedo. Ya no.\"",
        "\"Te dije que nos fuéramos, tonto. Pero viniste.\"",
    };

    // Fotos escondidas: el motivo para explorar el barrio
    private boolean[] photosFound = new boolean[Memories.TOTAL];
    private int photosThisLevel = 0;
    private boolean memoryActive = false;
    private int memoryTimer = 0;
    private String memoryText = "";
    private String memoryTitle = "";

    // Retos por barrio, para los logros
    private boolean tookDamageThisLevel = false;
    private boolean scaredThisLevel = false;
    private int killsThisLevel = 0;
    private int deaths = 0;

    // Secretos
    private boolean goldenMode = false;
    private boolean homeActive = false;
    private int homeTimer = 0;
    /**
     * Los restos del callejón bloquean el paso, así que Nox se queda parado al lado.
     * Sin esta marca, en cuanto la pantalla terminaba se volvía a disparar —y como
     * durante la pantalla no se puede mover, el juego se quedaba trabado para siempre.
     */
    private boolean homeSeen = false;

    // Segunda fase de los jefes
    private boolean bossEnraged = false;
    private boolean enrageMessageActive = false;
    private int enrageMessageTimer = 0;
    private int lightsOutTimer = 0;

    // Jefes
    private Enemy currentBoss;
    private boolean bossDefeated = false;
    private boolean bossEncountered = false;
    private boolean bossIntroActive = false;
    private int bossIntroTimer = 0;
    private boolean nearExitLocked = false;

    // ------------------------------------------------------------------
    // Alucinaciones
    //
    // Con la cordura baja, Nox deja de poder fiarse de lo que ve y oye. Nada de
    // esto hace daño: la idea es que el jugador dude, no que le maten por ello.
    // ------------------------------------------------------------------
    private final ArrayList<Ghost> ghosts = new ArrayList<>();
    private int hudLieTimer = 0;
    private int hudLieValue = 0;
    private int edgeShadowTimer = 0;
    private int edgeShadowSide = 0;
    private int whisperCooldown = 0;

    // Susto en tres tiempos: aviso, golpe y resaca
    private static final int SCARE_NONE = 0;
    private static final int SCARE_DREAD = 1;
    private static final int SCARE_IMPACT = 2;
    private static final int SCARE_AFTER = 3;
    private int scarePhase = SCARE_NONE;
    private int scareTimer = 0;
    private double scareDirX = 0, scareDirY = 0;

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
    /** Para no repetir el mismo susto dos veces seguidas: repetido deja de asustar. */
    private int lastJumpscareType = -1;

    // Tienda
    private boolean nearStore = false;
    private boolean isShopOpen = false;
    private int selectedShopItem = 0;
    private int shopStickCooldown = 0;
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

    /** Memoria reutilizable del buscador de rutas, para no crear un array por cálculo. */
    private final int[] pathPrev = new int[MAP_WIDTH * MAP_HEIGHT];
    private final java.util.ArrayDeque<Integer> pathQueue = new java.util.ArrayDeque<>();
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private int pathBudget = 0;

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
    // Mando de Xbox
    // ------------------------------------------------------------------

    /**
     * Traduce el mando a las mismas acciones que el teclado. Se llama al principio
     * de cada fotograma, antes de mover nada, y respeta el estado en el que está
     * la partida (prólogo, tienda, pausa, muerte...).
     */
    private void handleGamepad() {
        if (!ProjectExodusAdvanced.gamepadEnabled()) return;

        // El mando lo lee una sola pantalla por fotograma: si lo leyeran el menú y
        // el juego a la vez, la primera lectura se comería la detección de los
        // botones recién pulsados y aquí no llegaría ninguno.
        if (ProjectExodusAdvanced.isMenuActive()) return;

        Gamepad.poll();
        if (!Gamepad.isConnected()) return;

        boolean confirm = Gamepad.pressed(Gamepad.A);
        boolean cancel = Gamepad.pressed(Gamepad.B);
        boolean start = Gamepad.pressed(Gamepad.START);

        if (handleCinematicInput(confirm, start)) return;

        if (showingPrologue) {
            if (confirm || start) {
                showingPrologue = false;
                levelIntroActive = true;
                levelIntroTimer = 110;
                AudioEngine.play("blip");
            }
            return;
        }

        if (isDead) {
            if (confirm || start) { gameLoop.stop(); continueSavedGame(); }
            else if (cancel) exitToMenu();
            return;
        }

        if (gameWon) {
            if (confirm || cancel || start) exitToMenu();
            return;
        }

        if (paused) {
            if (start || confirm) paused = false;
            else if (cancel) exitToMenu();
            else if (Gamepad.pressed(Gamepad.X)) AudioEngine.toggleMusic();
            else if (Gamepad.pressed(Gamepad.Y)) AudioEngine.toggleSfx();
            return;
        }

        // Las pantallas de historia se saltan con (A), (B) o START
        if (memoryActive || homeActive || rescueMessageActive) {
            if (confirm || cancel || start) dismissStoryScreen();
            return;
        }

        if (levelIntroActive || isJumpscareActive || bossIntroActive || isTransitioning) return;

        if (isShopOpen) {
            // La navegación del stick necesita freno: si no, un empujón recorre la lista entera
            if (shopStickCooldown > 0) shopStickCooldown--;
            double sy = Gamepad.moveY();
            if (shopStickCooldown == 0 && Math.abs(sy) > 0.5) {
                selectedShopItem = (selectedShopItem + (sy < 0 ? -1 : 1) + SHOP_ITEMS) % SHOP_ITEMS;
                shopStickCooldown = 12;
                AudioEngine.play("blip", 0.5);
            }
            if (confirm) { buySelectedItem(); shopMessageTimer = 70; }
            if (cancel) { isShopOpen = false; shopMessageTimer = 0; }
            return;
        }

        if (start) { paused = true; return; }

        // El mapa también con el clic del stick derecho: en algunos mandos el botón
        // View queda incómodo o no se reconoce igual.
        if (Gamepad.pressed(Gamepad.BACK) || Gamepad.pressed(Gamepad.RS)) {
            showMinimap = !showMinimap;
            AudioEngine.play("blip", 0.5);
        }

        if (Gamepad.down(Gamepad.A) || Gamepad.rightTriggerDown()) attack();
        if (Gamepad.pressed(Gamepad.X)) useTuna();
        // El stick izquierdo no dispara el silbato: se pulsa sin querer al correr
        if (Gamepad.pressed(Gamepad.Y)) blowWhistle();
        if (cancel && nearStore) { isShopOpen = true; shopMessage = ""; AudioEngine.play("blip"); }

        if (Gamepad.pressed(Gamepad.RB)) cycleWeapon(1);
        if (Gamepad.pressed(Gamepad.LB)) cycleWeapon(-1);
    }

    /** Pasa al arma siguiente o anterior de entre las que Nox ya compró. */
    private void cycleWeapon(int direction) {
        List<Integer> owned = new ArrayList<>();
        owned.add(hasSteelClaws ? W_ACERO : W_GARRAS);
        if (hasCrossbow) owned.add(W_BALLESTA);
        if (hasPlasmaLauncher) owned.add(W_PLASMA);
        if (hasMolotov) owned.add(W_MOLOTOV);
        if (owned.size() < 2) return;

        int current = owned.indexOf(activeWeapon);
        if (current < 0) current = 0;
        activeWeapon = owned.get((current + direction + owned.size()) % owned.size());
        AudioEngine.play("blip", 0.5);
    }

    // ------------------------------------------------------------------
    // Cinemáticas
    // ------------------------------------------------------------------

    private void startCinematic(int kind) {
        cinematic = new Cinematics(kind);
        cinematicKind = kind;
        cinematicShot = -1;
        AudioEngine.setBossMode(false);
        AudioEngine.setMusicMode(0);
        AudioEngine.setTension(kind == Cinematics.FINAL_OSCURO ? 0.75 : 0.15);
    }

    /** Avanza la cinematica en curso y lanza el sonido del plano al cambiar de escena. */
    private void updateCinematic() {
        cinematic.update();

        if (cinematic.currentShot() != cinematicShot) {
            cinematicShot = cinematic.currentShot();
            cinematicAudioCue();
        }
        if (cinematic.isFinished()) endCinematic();
    }

    /** Un sonido en el plano donde toca, para que la escena tenga golpe. */
    private void cinematicAudioCue() {
        if (cinematicKind == Cinematics.PROLOGO) {
            if (cinematicShot == 2) AudioEngine.play("growl", 0.7);
            else if (cinematicShot == 3) AudioEngine.play("claw", 0.8);
            else if (cinematicShot == 4) { AudioEngine.play("boom"); AudioEngine.play("howl", 0.7); }
            else if (cinematicShot == 5) AudioEngine.play("home", 0.9);
        } else if (cinematicKind == Cinematics.FINAL_BUENO) {
            if (cinematicShot == 2) AudioEngine.play("rescue");
            else if (cinematicShot == 3) AudioEngine.play("victory");
        } else if (cinematicKind == Cinematics.FINAL_OSCURO) {
            if (cinematicShot == 1) AudioEngine.play("whisper");
            else if (cinematicShot == 2) AudioEngine.play("dread", 0.8);
            else if (cinematicShot == 3) AudioEngine.play("darkending");
        }
    }

    /** La cinematica termino: si era el prologo arranca el barrio 1; en los finales detras espera la pantalla de puntuacion. */
    private void endCinematic() {
        int kind = cinematicKind;
        cinematic = null;
        cinematicKind = -1;

        if (kind == Cinematics.PROLOGO) {
            showingPrologue = false;
            levelIntroActive = true;
            levelIntroTimer = 110;
            AudioEngine.setMusicMode(currentLevel);
        }
        // En los finales no hay que hacer nada: gameWon ya está puesto y detrás
        // espera la pantalla con la puntuación.
    }

    /** Avanza o salta la cinemática. @return true si había una en marcha. */
    private boolean handleCinematicInput(boolean next, boolean skip) {
        if (cinematic == null) return false;
        if (skip) {
            cinematic.skip();
            endCinematic();
        } else if (next) {
            cinematic.nextShot();
            if (cinematic.isFinished()) endCinematic();
            else { cinematicShot = cinematic.currentShot(); cinematicAudioCue(); }
        }
        return true;
    }

    /** Cierra la pantalla de historia que esté abierta y devuelve el control. */
    private void dismissStoryScreen() {
        if (!memoryActive && !homeActive && !rescueMessageActive) return;
        memoryActive = false;
        homeActive = false;
        rescueMessageActive = false;
        memoryTimer = 0;
        homeTimer = 0;
        rescueMessageTimer = 0;
        AudioEngine.play("blip", 0.5);
    }

    /** Vibración del mando: se ignora sola si no hay mando o si está desactivada. */
    private void shake(double low, double high, int frames) {
        if (!Settings.rumble()) return;
        Gamepad.rumble(low, high, frames);
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

    /** Arranca una partida: aplica la dificultad, carga el progreso guardado o pone los valores de una partida nueva, genera el barrio y pone en marcha el bucle. */
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
            photosFound = p.photos.clone();
            deaths = p.deaths;
            score = p.score;
            accumulatedMs = p.playTimeMs;
            activeWeapon = hasPlasmaLauncher ? W_PLASMA : hasCrossbow ? W_BALLESTA
                         : hasSteelClaws ? W_ACERO : W_GARRAS;
            showingPrologue = false;
        } else {
            currentLevel = 1;
            playerMoney = 0;
            sanity = 100;
            hasSteelClaws = hasCrossbow = hasPlasmaLauncher = hasMolotov = hasWhistle = false;
            armorLevel = 0;
            tunaCans = 0;
            familyRescued = new boolean[3];
            photosFound = new boolean[Memories.TOTAL];
            deaths = 0;
            score = 0;
            accumulatedMs = 0;
            activeWeapon = W_GARRAS;
            showingPrologue = true;
            playerHealth = maxHealth();

            // El código secreto del menú arranca la partida con todo el arsenal
            goldenMode = ProjectExodusAdvanced.isGoldenMode();
            if (goldenMode) {
                hasSteelClaws = hasCrossbow = hasPlasmaLauncher = hasMolotov = hasWhistle = true;
                armorLevel = 1;
                tunaCans = 2;
                activeWeapon = W_ACERO;
            }
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

        // Después de cargar el barrio, para que la cinemática mande sobre la música
        if (showingPrologue) startCinematic(Cinematics.PROLOGO);

        rainSystem.clear();
        for (int i = 0; i < 350; i++) rainSystem.add(new RainDrop());

        gameLoop.start();
        requestFocusInWindow();
    }

    // ------------------------------------------------------------------
    // Lo que aporta cada familiar rescatado
    // ------------------------------------------------------------------

    /** Mitsy cuida de Nox: más aguante. */
    private int maxHealth() { return familyRescued[2] ? 125 : 100; }

    /** Nox pelea por Will: más daño. */
    private double familyDamageBoost() { return familyRescued[0] ? 1.15 : 1.0; }

    /** La voz de Nina lo mantiene entero más tiempo. */
    private double sanityDrainFactor() { return familyRescued[1] ? 0.6 : 1.0; }

    private void applyDifficulty() {
        if (difficulty == 0) { diffEnemyMult = 0.65; diffHpMult = 0.75; diffDmgMult = 0.70; }
        else if (difficulty == 2) { diffEnemyMult = 1.50; diffHpMult = 1.45; diffDmgMult = 1.50; }
        else { diffEnemyMult = 1.0; diffHpMult = 1.0; diffDmgMult = 1.0; }
    }

    /** Prepara un barrio: genera el mapa, coloca a Nox en la entrada, limpia el estado de la pantalla anterior y guarda un punto de control. */
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
        memoryActive = false;
        homeActive = false;
        homeSeen = false;
        ghosts.clear();
        hudLieTimer = 0;
        edgeShadowTimer = 0;
        whisperCooldown = 0;
        scarePhase = SCARE_NONE;
        bossEnraged = false;
        enrageMessageActive = false;
        lightsOutTimer = 0;
        photosThisLevel = 0;
        tookDamageThisLevel = false;
        scaredThisLevel = false;
        killsThisLevel = 0;
        levelIntroActive = !showingPrologue;
        levelIntroTimer = 110;
        sanity = Math.min(100, sanity + 25);
        setBackground(levels[currentLevel - 1].bg);

        AudioEngine.setMusicMode(currentLevel);
        AudioEngine.setBossMode(false);

        saveCheckpoint();
    }

    /** Vuelca todo el progreso al guardado. Se llama al empezar barrio, al comprar y al morir. */
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
        p.photos = photosFound.clone();
        p.deaths = deaths;
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

        // Salida del barrio
        for (int x = MAP_WIDTH - 3; x <= MAP_WIDTH - 2; x++) {
            for (int y = MAP_HEIGHT - 3; y <= MAP_HEIGHT - 2; y++) map[x][y] = Tiles.EXIT;
        }

        // Mercado Negro: uno junto al inicio y otro a media ciudad
        boolean[][] open = MapGenerator.reachable(map, 2, 2);
        placeShop(6, 2, open);
        placeShop(MAP_WIDTH / 2, MAP_HEIGHT / 2, open);

        // Los puestos ocupan sitio, así que se vuelve a mirar qué queda accesible.
        // A partir de aquí, todo lo que se coloque tiene que estar dentro de esta zona.
        open = MapGenerator.reachable(map, 2, 2);

        // Monedas repartidas por las calles
        for (int i = 0; i < 230; i++) {
            int rx = rand.nextInt(MAP_WIDTH - 4) + 2;
            int ry = rand.nextInt(MAP_HEIGHT - 4) + 2;
            if (open[rx][ry] && map[rx][ry] == Tiles.FLOOR && (rx > 5 || ry > 5)) map[rx][ry] = Tiles.COIN;
        }

        // Pandilleros
        int wanted = (int) Math.round((20 + currentLevel * 5) * diffEnemyMult);
        int placed = 0, tries = 0;
        while (placed < wanted && tries < wanted * 40) {
            tries++;
            int rx = rand.nextInt(MAP_WIDTH - 12) + 10;
            int ry = rand.nextInt(MAP_HEIGHT - 12) + 10;
            if (!open[rx][ry]) continue;
            enemies.add(new Enemy(rx * TILE_SIZE, ry * TILE_SIZE, pickEnemyType(), currentLevel));
            placed++;
        }

        // El jefe se planta en la casilla accesible más lejana a la entrada: así custodia
        // el fondo del barrio y, sobre todo, se garantiza que Nox pueda llegar hasta él.
        // Antes se colocaba en una posición fija y en la mansión podía quedar emparedado,
        // lo que dejaba el nivel imposible de terminar.
        int[] bossTile = farthestFromStart(open);
        LevelInfo info = levels[currentLevel - 1];
        Enemy boss = new Enemy(bossTile[0] * TILE_SIZE, bossTile[1] * TILE_SIZE,
                               currentLevel, info.bossName, info.bossBreed);
        enemies.add(boss);
        currentBoss = boss;

        placeSecrets(open, bossTile);
    }

    /**
     * Reparte por el barrio las cosas que premian explorar: las tres fotos de la
     * familia, un bidón con dinero escondido y los restos del callejón.
     *
     * Se colocan lejos de la entrada y separadas entre sí, para que haya que
     * recorrer el mapa de verdad en vez de tropezarse con todo de camino al jefe.
     */
    private void placeSecrets(boolean[][] open, int[] bossTile) {
        ArrayList<int[]> photoSpots = new ArrayList<>();

        for (int intento = 0; intento < 4000 && photoSpots.size() < Memories.PER_LEVEL; intento++) {
            int rx = rand.nextInt(MAP_WIDTH - 4) + 2;
            int ry = rand.nextInt(MAP_HEIGHT - 4) + 2;
            if (!open[rx][ry] || map[rx][ry] != Tiles.FLOOR) continue;

            // Lejos de donde aparece Nox
            if (Math.hypot(rx - 2, ry - 2) < 14) continue;

            boolean toCloseToAnother = false;
            for (int[] s : photoSpots) {
                if (Math.hypot(rx - s[0], ry - s[1]) < 12) { toCloseToAnother = true; break; }
            }
            if (toCloseToAnother) continue;

            map[rx][ry] = Tiles.PHOTO;
            photoSpots.add(new int[]{rx, ry});
        }

        // Un bidón de los normales guarda premio. Por fuera es idéntico al resto.
        ArrayList<int[]> props = new ArrayList<>();
        for (int x = 2; x < MAP_WIDTH - 2; x++) {
            for (int y = 2; y < MAP_HEIGHT - 2; y++) {
                if (map[x][y] == Tiles.PROP) props.add(new int[]{x, y});
            }
        }
        if (!props.isEmpty()) {
            int[] lucky = props.get(rand.nextInt(props.size()));
            map[lucky[0]][lucky[1]] = Tiles.PROP_SECRET;
        }

        // Los restos del callejón, en un rincón apartado. Ocupan sitio, así que
        // después se comprueba que no hayan cortado el paso hacia el jefe o hacia
        // la salida; si lo han hecho, se quitan: un secreto no puede dejar el
        // barrio sin final.
        for (int intento = 0; intento < 2000; intento++) {
            int rx = rand.nextInt(MAP_WIDTH - 4) + 2;
            int ry = rand.nextInt(MAP_HEIGHT - 4) + 2;
            if (map[rx][ry] != Tiles.FLOOR) continue;
            if (Math.hypot(rx - 2, ry - 2) < 18) continue;
            if (!hasClearance(rx, ry)) continue;

            map[rx][ry] = Tiles.HOME;
            boolean[][] tras = MapGenerator.reachable(map, 2, 2);
            if (tras[MAP_WIDTH - 3][MAP_HEIGHT - 3] && tras[bossTile[0]][bossTile[1]]) break;
            map[rx][ry] = Tiles.FLOOR;
        }
    }

    /**
     * Casilla accesible más alejada del punto de entrada, prefiriendo las que tienen
     * hueco alrededor: si el jefe apareciera metido en un recoveco de una sola casilla,
     * lo primero que haría sería pelearse con las paredes para salir.
     */
    private int[] farthestFromStart(boolean[][] open) {
        int bestX = MAP_WIDTH - 4, bestY = MAP_HEIGHT - 4;
        long bestDist = -1;
        int fallbackX = bestX, fallbackY = bestY;
        long fallbackDist = -1;

        for (int x = 1; x < MAP_WIDTH - 1; x++) {
            for (int y = 1; y < MAP_HEIGHT - 1; y++) {
                if (!open[x][y] || map[x][y] == Tiles.EXIT) continue;
                long dx = x - 2, dy = y - 2;
                long d = dx * dx + dy * dy;

                if (d > fallbackDist) { fallbackDist = d; fallbackX = x; fallbackY = y; }
                if (d > bestDist && hasClearance(x, y)) { bestDist = d; bestX = x; bestY = y; }
            }
        }
        return bestDist < 0 ? new int[]{fallbackX, fallbackY} : new int[]{bestX, bestY};
    }

    /** Las nueve casillas alrededor están libres. */
    private boolean hasClearance(int tx, int ty) {
        for (int x = tx - 1; x <= tx + 1; x++) {
            for (int y = ty - 1; y <= ty + 1; y++) {
                if (x < 0 || y < 0 || x >= MAP_WIDTH || y >= MAP_HEIGHT) return false;
                if (Tiles.blocks(map[x][y])) return false;
            }
        }
        return true;
    }

    /**
     * Coloca un puesto de 2x2 lo más cerca posible del sitio pedido, siempre sobre
     * suelo al que se pueda llegar. Si el puesto acabara tapando el único paso hacia
     * la salida, se retira: es preferible quedarse sin tienda que sin barrio.
     */
    private void placeShop(int desiredX, int desiredY, boolean[][] open) {
        for (int radius = 0; radius < 14; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;

                    int sx = desiredX + dx, sy = desiredY + dy;
                    if (sx < 2 || sy < 2 || sx > MAP_WIDTH - 4 || sy > MAP_HEIGHT - 4) continue;
                    if (!fits(sx, sy, open)) continue;

                    for (int x = sx; x <= sx + 1; x++) {
                        for (int y = sy; y <= sy + 1; y++) map[x][y] = Tiles.SHOP;
                    }

                    if (MapGenerator.reachable(map, 2, 2)[MAP_WIDTH - 3][MAP_HEIGHT - 3]) return;

                    for (int x = sx; x <= sx + 1; x++) {
                        for (int y = sy; y <= sy + 1; y++) map[x][y] = Tiles.FLOOR;
                    }
                }
            }
        }
    }

    /** El bloque de 2x2 y su contorno están libres y son accesibles. */
    private boolean fits(int sx, int sy, boolean[][] open) {
        for (int x = sx - 1; x <= sx + 2; x++) {
            for (int y = sy - 1; y <= sy + 2; y++) {
                if (x < 1 || y < 1 || x >= MAP_WIDTH - 1 || y >= MAP_HEIGHT - 1) return false;
                if (map[x][y] == Tiles.EXIT) return false;
                boolean inner = x >= sx && x <= sx + 1 && y >= sy && y <= sy + 1;
                if (inner && (!open[x][y] || map[x][y] != Tiles.FLOOR)) return false;
            }
        }
        return true;
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
    /**
     * Un fotograma del juego. Lo llama el temporizador cada 16 ms (unos 60 por segundo).
     *
     * Primero se leen los controles y después vienen los cortes por estado: cinemática,
     * pantalla de historia, pausa, muerte, tienda, susto... Cada uno dibuja y sale con
     * un return. Así es imposible que se cuele lógica de juego mientras hay una pantalla
     * encima. Solo si no hay ninguna se actualizan jugador, balas, enemigos y efectos.
     */
    public void actionPerformed(ActionEvent e) {
        animationFrame++;
        handleGamepad();
        Achievements.updateToasts();

        if (cinematic != null) { updateCinematic(); repaint(); return; }

        if (showingPrologue || gameWon || isDead || paused) { repaint(); return; }

        if (memoryActive) {
            if (--memoryTimer <= 0) memoryActive = false;
            repaint();
            return;
        }
        if (homeActive) {
            if (--homeTimer <= 0) homeActive = false;
            repaint();
            return;
        }
        if (enrageMessageActive && --enrageMessageTimer <= 0) enrageMessageActive = false;
        if (lightsOutTimer > 0) lightsOutTimer--;

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
            updateJumpscare();
            if (shakeFrames > 0) shakeFrames--;
            fx.update();
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
        updateHallucinations();
        fx.update();

        if (playerHealth <= 0) triggerDeath();

        repaint();
    }

    /**
     * Mueve a Nox y resuelve todo lo que puede pisar: monedas, fotos, el callejón,
     * la salida y la cercanía de la tienda. También hace correr los tiempos de
     * espera de las armas, del silbato y de la invulnerabilidad tras un golpe.
     */
    private void updatePlayer() {
        double dx = 0, dy = 0;
        if (up) dy -= 1;
        if (down) dy += 1;
        if (left) dx -= 1;
        if (right) dx += 1;

        // Si se está usando el stick, manda el stick: así el movimiento es analógico
        // (Nox puede caminar despacio) en vez de ir siempre a tope.
        double padX = Gamepad.moveX(), padY = Gamepad.moveY();
        if (Math.abs(padX) > 0.01 || Math.abs(padY) > 0.01) { dx = padX; dy = padY; }

        boolean moving = dx != 0 || dy != 0;

        if (moving) {
            if (Math.abs(dx) > Math.abs(dy)) facing = dx < 0 ? 2 : 3;
            else facing = dy < 0 ? 0 : 1;
        }

        double magnitude = Math.hypot(dx, dy);
        if (magnitude > 1) { dx /= magnitude; dy /= magnitude; }
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
            if (playerMoney >= 5000) Achievements.unlock("rico");
            fx.text(playerX + 8, playerY - 6, "+$50", new Color(255, 200, 70), 15);
            AudioEngine.play("coin", 0.7);
        }

        if (map[cx][cy] == Tiles.PHOTO) {
            collectPhoto(cx, cy);
            return;
        }

        if (!homeSeen && touchesTile(cx, cy, Tiles.HOME)) {
            homeSeen = true;
            homeActive = true;
            homeTimer = 330;
            // Ver lo que queda de su casa le recuerda para qué está haciendo todo esto
            sanity = Math.min(100, sanity + 15);
            score += 600;
            Achievements.unlock("hogar");
            AudioEngine.play("home");
            return;
        }

        nearExitLocked = false;
        if (map[cx][cy] == Tiles.EXIT) {
            if (bossDefeated) {
                if (currentLevel >= MAX_LEVEL) {
                    triggerVictory();
                } else {
                    isTransitioning = true;
                    transitionTimer = 130;
                    playerHealth = Math.min(maxHealth(), playerHealth + 25);
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

    /**
     * Mueve los proyectiles y resuelve los impactos.
     *
     * Se recorre con iterador porque hay que ir quitando los que chocan, y quitar
     * de una lista mientras se recorre de otra forma lanza excepción.
     */
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
                if (p.container) explodeEnemy(p.x, p.y, 165, 42);
                else if (p.molotov && p.isEnemyBullet) explodeEnemy(p.x, p.y, 115, 26);
                else if (p.molotov) explode(p.x, p.y, 110, 90);
                else fx.hit(p.x, p.y, new Color(200, 200, 210));
                it.remove();
                continue;
            }

            boolean hit = false;
            if (p.isEnemyBullet) {
                if (Point2D.distance(p.x, p.y, playerX + 15, playerY + 15) < (p.container ? 34 : 20)) {
                    if (p.container) explodeEnemy(p.x, p.y, 165, 42);
                    else if (p.molotov) explodeEnemy(p.x, p.y, 115, 26);
                    else damagePlayer(p.damage, false);
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
        shakeScreen(16, 9);
        AudioEngine.playAt("boom", 1.0, x, y, playerX, playerY);
        shake(1.0, 0.7, 22);
        breakPropsNear(x, y, radius * 0.8);

        int caidos = 0;
        for (Enemy en : enemies) {
            double d = Point2D.distance(x, y, en.x + en.size / 2.0, en.y + en.size / 2.0);
            if (d < radius) {
                int dmg = (int) (damage * (1 - d / radius * 0.55) * familyDamageBoost());
                boolean vivo = en.hp > 0;
                en.hp -= dmg;               // la explosión ignora el escudo
                if (vivo && en.hp <= 0 && !en.isBoss) caidos++;
                showDamage(en.x, en.y - 18, String.valueOf(dmg), new Color(255, 170, 60));
            }
        }
        if (caidos >= 4) Achievements.unlock("piromano");
    }

    /** Explosión de un ataque de jefe: hiere a Nox, no a los suyos. */
    private void explodeEnemy(double x, double y, double radius, int damage) {
        fx.explosion(x, y, radius / 110.0);
        shakeScreen(20, 11);
        AudioEngine.playAt("boom", 1.0, x, y, playerX, playerY);
        shake(1.0, 0.8, 26);
        breakPropsNear(x, y, radius * 0.7);

        double d = Point2D.distance(x, y, playerX + 15, playerY + 15);
        if (d < radius) {
            int dmg = (int) Math.round(damage * (1 - d / radius * 0.5) * diffDmgMult);
            damagePlayer(Math.max(4, dmg), false);
        }
    }

    /**
     * Rompe los bidones, cajas y muebles que haya alrededor. Uno de ellos, distinto
     * en nada por fuera, guarda dinero: es uno de los secretos del juego.
     */
    private void breakPropsNear(double cx, double cy, double range) {
        int t0x = clampTile((int) ((cx - range) / TILE_SIZE), MAP_WIDTH);
        int t1x = clampTile((int) ((cx + range) / TILE_SIZE), MAP_WIDTH);
        int t0y = clampTile((int) ((cy - range) / TILE_SIZE), MAP_HEIGHT);
        int t1y = clampTile((int) ((cy + range) / TILE_SIZE), MAP_HEIGHT);

        for (int x = t0x; x <= t1x; x++) {
            for (int y = t0y; y <= t1y; y++) {
                if (!Tiles.breakable(map[x][y])) continue;
                double px = x * TILE_SIZE + TILE_SIZE / 2.0;
                double py = y * TILE_SIZE + TILE_SIZE / 2.0;
                if (Point2D.distance(cx, cy, px, py) > range) continue;

                boolean premiado = map[x][y] == Tiles.PROP_SECRET;
                map[x][y] = Tiles.FLOOR;
                fx.death(px, py, new Color(120, 90, 50));
                AudioEngine.playAt("crack", 0.7, px, py, playerX, playerY);

                if (premiado) {
                    playerMoney += 500;
                    score += 500;
                    fx.explosion(px, py, 0.5);
                    fx.text(px - 40, py - 30, "¡$500 ESCONDIDOS!", new Color(255, 210, 80), 18);
                    AudioEngine.play("buy");
                    Achievements.unlock("suerte");
                } else if (rand.nextInt(3) == 0) {
                    playerMoney += 25;
                    fx.text(px, py - 20, "+$25", new Color(255, 200, 70), 13);
                }
            }
        }
    }

    /** Número flotante de daño, si el jugador no los ha desactivado. */
    private void showDamage(double x, double y, String text, Color color) {
        if (Settings.damageNumbers()) fx.text(x, y, text, color, 15);
    }

    /** Aplica daño a un perro teniendo en cuenta el escudo, que solo protege de frente. */
    private void hurtEnemy(Enemy en, int damage, double fromX, double fromY) {
        int dealt = (int) Math.round(damage * familyDamageBoost());
        boolean blockedByShield = false;

        if (en.shield) {
            double ang = Math.atan2(fromY - (en.y + en.size / 2.0), fromX - (en.x + en.size / 2.0));
            double facingAng = en.facing == 3 ? 0 : en.facing == 1 ? Math.PI / 2
                             : en.facing == 2 ? Math.PI : -Math.PI / 2;
            double diff = Math.abs(normalizeAngle(ang - facingAng));
            if (diff < Math.PI / 2.2) {
                dealt = Math.max(1, (int) (dealt * 0.15));
                blockedByShield = true;
            }
        }

        en.hp -= dealt;
        en.hurtFlash = 6;

        if (blockedByShield) {
            fx.hit(fromX, fromY, new Color(200, 220, 255));
            fx.text(en.x, en.y - 18, "BLOQUEADO", new Color(160, 190, 230), 13);
            AudioEngine.playAt("clang", 0.8, en.x, en.y, playerX, playerY);
        } else {
            fx.hit(fromX, fromY, new Color(255, 190, 120));
            showDamage(en.x + rand.nextInt(14), en.y - 18, String.valueOf(dealt), Color.WHITE);
            AudioEngine.playAt("hit", 0.6, en.x, en.y, playerX, playerY);
        }
    }

    private static double normalizeAngle(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    /**
     * Actualiza a todos los perros: aparición del jefe, su segunda fase, muertes,
     * aturdimiento, ladridos, habilidades especiales, movimiento y ataques.
     *
     * Los refuerzos que invocan el alfa y Colmillo no se añaden aquí sino a una
     * cola aparte, que se vuelca al terminar: añadir a la lista mientras se recorre
     * lanzaría ConcurrentModificationException.
     */
    private void updateEnemies() {
        if (currentBoss != null && !bossEncountered
            && Point2D.distance(playerX, playerY, currentBoss.x, currentBoss.y) < 420) {
            bossEncountered = true;
            bossIntroActive = true;
            bossIntroTimer = 110;
            sanity = Math.max(0, sanity - 10);
            AudioEngine.setBossMode(true);
            AudioEngine.play("growl");
            shake(0.8, 0.2, 50);
            if (killsThisLevel == 0) Achievements.unlock("paciente");
        }

        // Segunda fase: a media vida el jefe cambia el ritmo de la pelea
        if (currentBoss != null && !bossEnraged && currentBoss.hp <= currentBoss.maxHp * 0.5) {
            bossEnraged = true;
            currentBoss.speed *= 1.30;
            currentBoss.specialCooldown = 60;
            currentBoss.chargeCooldown = 90;
            enrageMessageActive = true;
            enrageMessageTimer = 90;
            shakeScreen(24, 12);
            shake(1.0, 0.6, 45);
            AudioEngine.play("enrage");
        }

        boolean enemyNearby = false;
        pathBudget = 4;   // como mucho 4 cálculos de ruta por fotograma

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
            else if (dist < 600 && rand.nextInt(300) == 0) {
                en.barkCooldown = 180;
                // Se oye por el lado por el que viene: saber dónde está el perro
                // que ladra en la oscuridad es información, no un adorno
                AudioEngine.playAt("bark", 0.75, en.x, en.y, playerX, playerY);
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

        sanity += enemyNearby ? -0.05 * sanityDrainFactor() : 0.03;
        sanity = Math.max(0, Math.min(100, sanity));
        AudioEngine.setTension(1.0 - sanity / 100.0);
    }

    /**
     * Un perro acaba de caer: reparte dinero y puntos, y si era el jefe abre la
     * salida, rescata al familiar del barrio y reparte los logros que tocan.
     */
    private void onEnemyDown(Enemy en) {
        kills++;
        killsThisLevel++;
        fx.death(en.x + en.size / 2.0, en.y + en.size / 2.0, en.bodyColor);

        Achievements.unlock("primer_golpe");
        if (kills >= 200) Achievements.unlock("cazador");

        if (en.isBoss) {
            playerMoney += 500;
            score += 2000;
            bossDefeated = true;
            currentBoss = null;
            lightsOutTimer = 0;
            sanity = Math.min(100, sanity + 25);
            AudioEngine.setBossMode(false);
            AudioEngine.play("door");
            explode(en.x + en.size / 2.0, en.y + en.size / 2.0, 60, 0);

            Achievements.unlock("barrio" + currentLevel);
            if (!tookDamageThisLevel) Achievements.unlock("intacto");
            if (!scaredThisLevel) Achievements.unlock("sereno");

            String target = levels[currentLevel - 1].rescueTarget;
            if (target != null) {
                int idx = -1;
                for (int k = 0; k < familyNames.length; k++) {
                    if (familyNames[k].equals(target)) idx = k;
                }
                if (idx >= 0) {
                    familyRescued[idx] = true;
                    rescueDialogue = FAMILY_LINES[idx] + "\n" + FAMILY_BUFFS[idx];
                    // Mitsy sube la salud máxima, así que se aprovecha al momento
                    playerHealth = Math.min(maxHealth(), playerHealth + 25);
                }
                rescueMessageActive = true;
                rescueMessageTimer = 220;
                rescueMessageText = "¡HAS RESCATADO A " + target + "!";
                score += 1500;
                AudioEngine.play("rescue");

                boolean todos = true;
                for (boolean b : familyRescued) if (!b) todos = false;
                if (todos) Achievements.unlock("familia");
            }
            saveCheckpoint();
        } else if (!en.exploded) {
            playerMoney += en.reward;
            score += en.reward;
            if (playerMoney >= 5000) Achievements.unlock("rico");
            fx.text(en.x, en.y - 30, "+$" + en.reward, new Color(255, 200, 70), 15);
        }
    }

    /**
     * Mueve un perro hacia Nox.
     *
     * Los jefes siguen una ruta calculada con búsqueda en anchura; los normales van
     * en línea recta y, si se atascan, caminan de lado para rodear. El movimiento se
     * aplica eje por eje para que puedan deslizarse por las paredes en vez de frenarse.
     */
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

            if (--en.specialCooldown <= 0 && dist < 560) {
                en.specialCooldown = bossEnraged ? 150 : 250;
                bossSpecial(en);
            }
        }

        // Los jefes siguen una ruta calculada; los demás solo cuando se atascan
        boolean wantsPath = en.isBoss || en.stuckFrames > 18;

        // El jefe nunca hace cola por el presupuesto: como mucho hay uno por barrio,
        // así que su ruta cuesta un cálculo cada 14 fotogramas y poco más.
        //
        // Antes sí lo compartía, y como se añade el último a la lista de enemigos,
        // en barrios con muchos perros atascados (el basurero, con sus montañas de
        // basura) los normales le gastaban los cuatro cálculos y el jefe se quedaba
        // sin ruta: volvía al método tonto y se iba dando vueltas sin llegar nunca.
        boolean tienePermiso = en.isBoss || pathBudget > 0;

        if (wantsPath && --en.pathTimer <= 0 && tienePermiso) {
            if (!en.isBoss) pathBudget--;
            en.pathTimer = 14;
            int[] step = nextStepToPlayer(en);
            en.pathTileX = step == null ? -1 : step[0];
            en.pathTileY = step == null ? -1 : step[1];
        }
        if (!wantsPath) en.pathTileX = -1;

        // Se apunta de centro a centro. Comparando las esquinas, un jefe de 78 px
        // frente a un gato de 30 cambiaba de signo constantemente y vibraba en el sitio.
        double cx = en.x + en.size / 2.0;
        double cy = en.y + en.size / 2.0;

        boolean following = en.pathTileX >= 0 && dist > 90;
        double targetX = following ? en.pathTileX * TILE_SIZE + TILE_SIZE / 2.0
                                   : playerX + PLAYER_SIZE / 2.0;
        double targetY = following ? en.pathTileY * TILE_SIZE + TILE_SIZE / 2.0
                                   : playerY + PLAYER_SIZE / 2.0;

        double vx = Math.abs(targetX - cx) < speed ? 0 : Math.signum(targetX - cx) * speed;
        double vy = Math.abs(targetY - cy) < speed ? 0 : Math.signum(targetY - cy) * speed;

        // Rodear esquinas al azar solo si no hay ruta: si la hay, ya rodea bien
        if (!following && en.detourTimer > 0) {
            en.detourTimer--;
            double tmp = vx;
            vx = -vy * en.detourDir;
            vy = tmp * en.detourDir;
        }

        boolean moved = false;
        if (vx != 0 && !enemyBlocked(en, en.x + vx, en.y)) { en.x += vx; moved = true; }
        if (vy != 0 && !enemyBlocked(en, en.x, en.y + vy)) { en.y += vy; moved = true; }

        if (moved) {
            en.stuckFrames = 0;
        } else {
            en.stuckFrames++;
            en.chargeTimer = 0;   // una embestida contra un muro se corta

            if (en.stuckFrames > 10) {
                // El rodeo por este lado tampoco sale: se prueba por el otro
                en.detourDir = -en.detourDir;
                en.detourTimer = 45;
                en.stuckFrames = 0;
            } else if (en.detourTimer <= 0) {
                en.detourDir = rand.nextBoolean() ? 1 : -1;
                en.detourTimer = 45;
            }
        }

        if (vx > 0) en.facing = 3;
        else if (vx < 0) en.facing = 2;
        else if (vy < 0) en.facing = 0;
        else if (vy > 0) en.facing = 1;
    }

    private boolean touchesTile(int cx, int cy, int tile) {
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (map[clampTile(cx + i, MAP_WIDTH)][clampTile(cy + j, MAP_HEIGHT)] == tile) return true;
            }
        }
        return false;
    }

    /** Nox encuentra una foto quemada: recupera cordura y recuerda algo. */
    private void collectPhoto(int cx, int cy) {
        map[cx][cy] = Tiles.FLOOR;

        int slot = (currentLevel - 1) * Memories.PER_LEVEL
                 + Math.min(photosThisLevel, Memories.PER_LEVEL - 1);
        photosFound[slot] = true;

        memoryTitle = Memories.title(currentLevel, photosThisLevel);
        memoryText = Memories.text(currentLevel, photosThisLevel);
        photosThisLevel++;

        sanity = Math.min(100, sanity + Memories.SANITY_REWARD);
        score += 400;
        memoryActive = true;
        memoryTimer = 260;
        AudioEngine.play("photo");
        fx.text(playerX, playerY - 26, "+" + Memories.SANITY_REWARD + " CORDURA",
                new Color(200, 140, 255), 16);

        int total = 0;
        for (boolean b : photosFound) if (b) total++;
        if (total >= Memories.TOTAL) Achievements.unlock("album");

        saveCheckpoint();
    }

    /** Colisión de un perro usando su caja reducida y centrada dentro del dibujo. */
    private boolean enemyBlocked(Enemy en, double nx, double ny) {
        double inset = (en.size - en.collisionSize) / 2.0;
        return blocked(nx + inset, ny + inset, en.collisionSize);
    }

    // ==================================================================
    //  ATAQUES ESPECIALES DE LOS JEFES
    //
    //  Cada jefe pelea usando el terreno de su barrio. Sin esto los seis eran
    //  la misma pelea con otro nombre y otro color.
    // ==================================================================

    private void bossSpecial(Enemy boss) {
        double bx = boss.x + boss.size / 2.0;
        double by = boss.y + boss.size / 2.0;

        switch (currentLevel) {
            case 1:  rockyLanzaBidones(boss, bx, by); break;
            case 2:  spikeVuelcaPuestos(boss, bx, by); break;
            case 3:  rexEmboscada(boss); break;
            case 4:  titanLanzaContenedor(boss, bx, by); break;
            case 5:  shadowSalta(boss); break;
            default: colmilloApagaLuces(boss, bx, by); break;
        }
    }

    /** BARRIO 1 — Rocky arranca bidones del basurero y los lanza ardiendo. */
    private void rockyLanzaBidones(Enemy boss, double bx, double by) {
        announceBoss("¡ROCKY ARRANCA UN BIDÓN!");
        int cuantos = bossEnraged ? 3 : 2;
        for (int i = 0; i < cuantos; i++) {
            double angle = Math.atan2(playerY - by, playerX - bx) + (i - cuantos / 2.0) * 0.22;
            Projectile p = new Projectile(bx, by, angle, 7.0, 0, true);
            p.molotov = true;
            p.life = 70;
            bullets.add(p);
        }
        AudioEngine.playAt("throw", 1.0, bx, by, playerX, playerY);
    }

    /** BARRIO 2 — Spike revienta los puestos del mercado y cambia el terreno. */
    private void spikeVuelcaPuestos(Enemy boss, double bx, double by) {
        announceBoss("¡SPIKE VUELCA LOS PUESTOS!");
        int tx = clampTile((int) (bx / TILE_SIZE), MAP_WIDTH);
        int ty = clampTile((int) (by / TILE_SIZE), MAP_HEIGHT);
        int radio = bossEnraged ? 4 : 3;

        for (int x = tx - radio; x <= tx + radio; x++) {
            for (int y = ty - radio; y <= ty + radio; y++) {
                if (x < 1 || y < 1 || x >= MAP_WIDTH - 1 || y >= MAP_HEIGHT - 1) continue;
                if (map[x][y] != Tiles.WALL && !Tiles.breakable(map[x][y])) continue;
                if (Math.hypot(x - tx, y - ty) > radio) continue;
                map[x][y] = Tiles.FLOOR;
                fx.death(x * TILE_SIZE + 30, y * TILE_SIZE + 30, new Color(122, 82, 48));
            }
        }

        shakeScreen(18, 10);
        shake(0.9, 0.5, 20);
        AudioEngine.playAt("slam", 1.0, bx, by, playerX, playerY);

        if (Point2D.distance(bx, by, playerX + 15, playerY + 15) < radio * TILE_SIZE) {
            damagePlayer((int) Math.round(18 * diffDmgMult), false);
        }
    }

    /** BARRIO 3 — Rex se mete en los túneles y sale por detrás de Nox. */
    private void rexEmboscada(Enemy boss) {
        announceBoss("¡REX DESAPARECE EN EL TÚNEL!");
        fx.explosion(boss.x + boss.size / 2.0, boss.y + boss.size / 2.0, 0.5);
        AudioEngine.play("teleport");

        // Se busca una casilla libre justo detrás del jugador
        int px = clampTile((int) (playerX / TILE_SIZE), MAP_WIDTH);
        int py = clampTile((int) (playerY / TILE_SIZE), MAP_HEIGHT);
        int mejorX = -1, mejorY = -1;

        for (int radio = 2; radio <= 5 && mejorX < 0; radio++) {
            for (int i = 0; i < 40; i++) {
                double a = rand.nextDouble() * Math.PI * 2;
                int tx = clampTile(px + (int) (Math.cos(a) * radio), MAP_WIDTH);
                int ty = clampTile(py + (int) (Math.sin(a) * radio), MAP_HEIGHT);
                if (hasClearance(tx, ty)) { mejorX = tx; mejorY = ty; break; }
            }
        }
        if (mejorX >= 0) {
            boss.x = mejorX * TILE_SIZE;
            boss.y = mejorY * TILE_SIZE;
            boss.pathTileX = -1;
            fx.explosion(boss.x + boss.size / 2.0, boss.y + boss.size / 2.0, 0.6);
        }

        // Y llama a la manada
        if (enemies.size() + spawnQueue.size() < ENEMY_CAP) {
            int refuerzos = bossEnraged ? 3 : 2;
            for (int i = 0; i < refuerzos; i++) {
                spawnQueue.add(new Enemy(boss.x + rand.nextInt(120) - 60,
                                         boss.y + rand.nextInt(120) - 60, E_CORREDOR, currentLevel));
            }
        }
        AudioEngine.play("howl", 0.6);
    }

    /** BARRIO 4 — Titán levanta un contenedor y lo estrella donde estás. */
    private void titanLanzaContenedor(Enemy boss, double bx, double by) {
        announceBoss("¡TITÁN LEVANTA UN CONTENEDOR!");
        Projectile p = new Projectile(bx, by, Math.atan2(playerY - by, playerX - bx), 5.5, 0, true);
        p.molotov = true;
        p.container = true;
        p.life = 110;
        bullets.add(p);

        // Y de paso una onda expansiva a sus pies
        for (int i = 0; i < 10; i++) {
            bullets.add(new Projectile(bx, by, Math.PI * 2 * i / 10.0, 5.0, 12, true));
        }
        shakeScreen(14, 8);
        AudioEngine.playAt("slam", 1.0, bx, by, playerX, playerY);
    }

    /** BARRIO 5 — Shadow salta por encima del vacío y cae sobre ti. */
    private void shadowSalta(Enemy boss) {
        announceBoss("¡SHADOW SALTA!");
        double a = Math.atan2(playerY - boss.y, playerX - boss.x);
        double salto = bossEnraged ? 300 : 230;

        // Puede cruzar el vacío: por eso las azoteas no lo detienen
        double nx = boss.x + Math.cos(a) * salto;
        double ny = boss.y + Math.sin(a) * salto;
        nx = Math.max(TILE_SIZE, Math.min(nx, (MAP_WIDTH - 2) * TILE_SIZE));
        ny = Math.max(TILE_SIZE, Math.min(ny, (MAP_HEIGHT - 2) * TILE_SIZE));

        if (!enemyBlocked(boss, nx, ny)) {
            for (int i = 0; i < 8; i++) {
                fx.step(boss.x + boss.size / 2.0 + (nx - boss.x) * i / 8.0,
                        boss.y + boss.size / 2.0 + (ny - boss.y) * i / 8.0);
            }
            boss.x = nx;
            boss.y = ny;
            boss.pathTileX = -1;
        }

        fx.explosion(boss.x + boss.size / 2.0, boss.y + boss.size / 2.0, 0.7);
        shakeScreen(12, 7);
        shake(0.7, 0.4, 14);
        AudioEngine.play("growl", 0.7);

        if (Point2D.distance(boss.x, boss.y, playerX, playerY) < 90) {
            damagePlayer((int) Math.round(20 * diffDmgMult), false);
        }
    }

    /** BARRIO 6 — Colmillo apaga las luces de la mansión y suelta a sus sombras. */
    private void colmilloApagaLuces(Enemy boss, double bx, double by) {
        announceBoss("¡COLMILLO APAGA LAS LUCES!");
        lightsOutTimer = bossEnraged ? 300 : 200;
        AudioEngine.play("lightsout");
        sanity = Math.max(0, sanity - 6);

        if (enemies.size() + spawnQueue.size() < ENEMY_CAP) {
            int sombras = bossEnraged ? 4 : 3;
            for (int i = 0; i < sombras; i++) {
                double a = Math.PI * 2 * i / sombras;
                Enemy sombra = new Enemy(bx + Math.cos(a) * 140, by + Math.sin(a) * 140,
                                         E_CORREDOR, currentLevel);
                sombra.bodyColor = new Color(18, 14, 22);
                spawnQueue.add(sombra);
            }
        }

        for (int i = 0; i < 14; i++) {
            bullets.add(new Projectile(bx, by, Math.PI * 2 * i / 14.0, 6.0, 15, true));
        }
    }

    private void announceBoss(String texto) {
        fx.text(playerX - 60, playerY - 70, texto, new Color(255, 90, 90), 17);
    }

    /** Temblor de pantalla, respetando la opción de accesibilidad. */
    private void shakeScreen(int frames, int magnitude) {
        if (!Settings.screenShake()) return;
        shakeFrames = Math.max(shakeFrames, frames);
        shakeMagnitude = magnitude;
    }

    /**
     * Siguiente casilla del camino más corto entre un perro y Nox, con una búsqueda
     * en anchura sobre la rejilla del mapa.
     *
     * Los jefes la usan siempre. Ir en línea recta y rodear al azar funciona en la
     * calle, pero entre las filas de contenedores del puerto el jefe daba vueltas sin
     * llegar nunca; y como hay que derrotarlo para salir del barrio, eso deja el nivel
     * bloqueado. Los pandilleros normales solo la piden cuando llevan rato trabados.
     *
     * @return la casilla a la que avanzar, o null si no hay camino
     */
    private int[] nextStepToPlayer(Enemy en) {
        int sx = clampTile((int) ((en.x + en.size / 2.0) / TILE_SIZE), MAP_WIDTH);
        int sy = clampTile((int) ((en.y + en.size / 2.0) / TILE_SIZE), MAP_HEIGHT);
        int tx = clampTile((int) ((playerX + PLAYER_SIZE / 2.0) / TILE_SIZE), MAP_WIDTH);
        int ty = clampTile((int) ((playerY + PLAYER_SIZE / 2.0) / TILE_SIZE), MAP_HEIGHT);

        if ((sx == tx && sy == ty) || Tiles.blocks(map[sx][sy])) return null;

        java.util.Arrays.fill(pathPrev, -1);
        pathQueue.clear();

        int start = sy * MAP_WIDTH + sx;
        int goal = ty * MAP_WIDTH + tx;
        pathPrev[start] = start;
        pathQueue.add(start);

        boolean found = false;
        while (!pathQueue.isEmpty()) {
            int cur = pathQueue.poll();
            if (cur == goal) { found = true; break; }
            int cxT = cur % MAP_WIDTH, cyT = cur / MAP_WIDTH;
            for (int[] d : DIRS) {
                int nx = cxT + d[0], ny = cyT + d[1];
                if (nx < 0 || ny < 0 || nx >= MAP_WIDTH || ny >= MAP_HEIGHT) continue;
                int idx = ny * MAP_WIDTH + nx;
                if (pathPrev[idx] != -1 || Tiles.blocks(map[nx][ny])) continue;
                pathPrev[idx] = cur;
                pathQueue.add(idx);
            }
        }
        if (!found) return null;

        // Se retrocede desde Nox hasta el primer paso que da el perro
        int cur = goal;
        int guard = 0;
        while (pathPrev[cur] != start && pathPrev[cur] != cur && guard++ < pathPrev.length) {
            cur = pathPrev[cur];
        }
        return new int[]{cur % MAP_WIDTH, cur / MAP_WIDTH};
    }

    /** Disparos y daño por contacto de un perro, según su tipo y su distancia. */
    private void enemyAttacks(Enemy en, double dist) {
        int fireInterval = en.isBoss ? 42 : 85;
        if (en.hasGun && dist < 430 && animationFrame % fireInterval == 0) {
            int dmg = en.isBoss ? 20 : 13;
            double bx = en.x + en.size / 2.0, by = en.y + en.size / 2.0;
            bullets.add(new Projectile(bx, by, playerX + 15, playerY + 15, en.isBoss ? 9.0 : 7.5, dmg, true));
            fx.muzzle(bx, by, Math.atan2(playerY - by, playerX - bx), new Color(255, 170, 60));
            AudioEngine.playAt("shoot", 0.45, bx, by, playerX, playerY);
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
        tookDamageThisLevel = true;
        sanity = Math.max(0, sanity - 1.5);
        showDamage(playerX + 4, playerY - 12, "-" + reduced, new Color(255, 80, 80));
        AudioEngine.play("hurt", 0.6);
        // Cuanto más fuerte el golpe, más fuerte la vibración
        shake(Math.min(0.9, 0.25 + reduced / 45.0), 0.35, 14);
        return true;
    }

    /** Lluvia, parpadeos de la luz, salpicaduras, brasas de los bidones y temblor. */
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

    /**
     * Susto en tres tiempos, en vez de un fogonazo suelto.
     *
     * 1. AVISO: la música se corta, la luz se cierra y algo se oye acercarse por
     *    un lado concreto. Dura casi un segundo y medio: lo justo para que dé
     *    tiempo a ponerse nervioso.
     * 2. GOLPE: el perro se abalanza creciendo hacia la cámara, con rugido y
     *    vibración.
     * 3. RESACA: pantalla apagada, pitido en los oídos y el corazón desbocado.
     *
     * Un susto que avisa asusta más que uno que aparece sin más, porque el miedo
     * se construye en la espera.
     */
    private void triggerJumpscare() {
        scaredThisLevel = true;
        sanity = Math.max(0, sanity - 15);

        // Quien haya desactivado los sustos pierde cordura igual, pero sin la
        // pantalla de golpe: el juego sigue siendo el mismo, solo deja de agredir.
        if (!Settings.jumpscares()) {
            AudioEngine.play("growl", 0.5);
            return;
        }

        isJumpscareActive = true;
        jumpscareType = Jumpscares.pick(rand, currentLevel, lastJumpscareType);
        lastJumpscareType = jumpscareType;
        scarePhase = SCARE_DREAD;
        scareTimer = 80;
        jumpscareTimer = 80;

        // Viene de un lado concreto, y se oye por ahí
        double angle = rand.nextDouble() * Math.PI * 2;
        scareDirX = Math.cos(angle);
        scareDirY = Math.sin(angle);

        AudioEngine.duckMusic(3.2);
        AudioEngine.playAt("dread", 1.0,
            playerX + scareDirX * 260, playerY + scareDirY * 260, playerX, playerY);
        AudioEngine.playAt("steps", 0.9,
            playerX + scareDirX * 200, playerY + scareDirY * 200, playerX, playerY);
    }

    /** Avanza el susto de una fase a la siguiente. */
    private void updateJumpscare() {
        if (--scareTimer > 0) return;

        switch (scarePhase) {
            case SCARE_DREAD:
                scarePhase = SCARE_IMPACT;
                scareTimer = 42;
                // El susurro asusta por lo contrario: no truena, se apaga
                if (jumpscareType == Jumpscares.SUSURRO) {
                    shakeScreen(8, 4);
                    AudioEngine.play("whisper");
                    shake(0.35, 0.2, 20);
                } else {
                    shakeScreen(26, 13);
                    AudioEngine.play("scare");
                    AudioEngine.play(Jumpscares.impactSfx(jumpscareType));
                    shake(1.0, 1.0, 45);
                }
                break;

            case SCARE_IMPACT:
                scarePhase = SCARE_AFTER;
                scareTimer = 55;
                AudioEngine.play("tinnitus", 0.9);
                break;

            default:
                scarePhase = SCARE_NONE;
                isJumpscareActive = false;
        }
        jumpscareTimer = scareTimer;
    }

    // ------------------------------------------------------------------
    // Alucinaciones por cordura baja
    // ------------------------------------------------------------------

    /** 0 cuando Nox está entero, 1 cuando está roto del todo. */
    private double madness() {
        return Math.max(0, Math.min(1, (68 - sanity) / 68.0));
    }

    /** Los perros que no existen, los sonidos a la espalda, las sombras del borde y la barra de vida que miente. Todo escala con lo baja que este la cordura. */
    private void updateHallucinations() {
        double locura = madness();

        Iterator<Ghost> it = ghosts.iterator();
        while (it.hasNext()) {
            Ghost gh = it.next();
            gh.x += gh.vx;
            gh.y += gh.vy;
            gh.life--;

            // Se desvanecen si te acercas: nunca llegan a tocarte porque no existen
            if (Point2D.distance(gh.x, gh.y, playerX, playerY) < 110) gh.life -= 6;
            if (gh.life <= 0) it.remove();
        }

        if (locura <= 0) return;

        // Perros que no están: aparecen fuera del círculo de luz y se acercan
        if (ghosts.size() < 3 && rand.nextInt(Math.max(60, (int) (620 - locura * 520))) == 0) {
            double angle = rand.nextDouble() * Math.PI * 2;
            double dist = 330 + rand.nextInt(140);
            Ghost gh = new Ghost();
            gh.x = playerX + Math.cos(angle) * dist;
            gh.y = playerY + Math.sin(angle) * dist;
            gh.vx = -Math.cos(angle) * (0.7 + rand.nextDouble() * 0.9);
            gh.vy = -Math.sin(angle) * (0.7 + rand.nextDouble() * 0.9);
            gh.size = 38 + rand.nextInt(18);
            gh.breed = rand.nextInt(6);
            gh.maxLife = 120 + rand.nextInt(90);
            gh.life = gh.maxLife;
            ghosts.add(gh);
        }

        // Sonidos a la espalda, sin nada detrás
        if (whisperCooldown > 0) {
            whisperCooldown--;
        } else if (rand.nextInt(Math.max(90, (int) (760 - locura * 620))) == 0) {
            whisperCooldown = 200;
            double angle = rand.nextDouble() * Math.PI * 2;
            double sx = playerX + Math.cos(angle) * 240;
            double sy = playerY + Math.sin(angle) * 240;
            String[] voces = {"whisper", "steps", "bark", "growl"};
            AudioEngine.playAt(voces[rand.nextInt(voces.length)], 0.55, sx, sy, playerX, playerY);
        }

        // Sombras que cruzan el borde de la vista
        if (edgeShadowTimer > 0) {
            edgeShadowTimer--;
        } else if (rand.nextInt(Math.max(70, (int) (600 - locura * 500))) == 0) {
            edgeShadowTimer = 26;
            edgeShadowSide = rand.nextInt(4);
        }

        // Y a partir de cierto punto, ni la barra de vida es de fiar
        if (hudLieTimer > 0) {
            hudLieTimer--;
        } else if (locura > 0.55 && rand.nextInt(900) == 0) {
            hudLieTimer = 40;
            hudLieValue = rand.nextInt(Math.max(2, playerHealth / 3));
        }
    }

    /** Dibuja los perros imaginarios translucidos, con opacidad segun lo que les quede. */
    private void drawGhosts(Graphics2D g2d) {
        for (Ghost gh : ghosts) {
            double k = gh.life / (double) gh.maxLife;
            float alpha = (float) Math.max(0, Math.min(0.55, k * 0.55));
            java.awt.Composite saved = g2d.getComposite();
            g2d.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, alpha));
            Sprites.drawDog(g2d, (int) gh.x, (int) gh.y, gh.size,
                            new Color(16, 12, 18), gh.breed, false, false, 1, animationFrame);
            g2d.setComposite(saved);
        }
    }

    /** Una sombra que cruza el borde de la pantalla y se va sin llegar a verse bien. */
    private void drawEdgeShadow(Graphics2D g2d) {
        if (edgeShadowTimer <= 0) return;
        double k = edgeShadowTimer / 26.0;
        int alpha = (int) (150 * Math.sin(k * Math.PI));
        if (alpha <= 0) return;

        g2d.setColor(new Color(0, 0, 0, alpha));
        int grosor = 130;
        switch (edgeShadowSide) {
            case 0: g2d.fillOval(-grosor, VIEW_H / 3, grosor * 2, VIEW_H / 3); break;
            case 1: g2d.fillOval(VIEW_W - grosor, VIEW_H / 3, grosor * 2, VIEW_H / 3); break;
            case 2: g2d.fillOval(VIEW_W / 3, -grosor, VIEW_W / 3, grosor * 2); break;
            default: g2d.fillOval(VIEW_W / 3, VIEW_H - grosor, VIEW_W / 3, grosor * 2); break;
        }
    }

    class Ghost {
        double x, y, vx, vy;
        int size, breed, life, maxLife;
    }

    /** Nox ha caido: cuenta la muerte, guarda y abre la pantalla de derrota. */
    private void triggerDeath() {
        isDead = true;
        playerHealth = 0;
        deaths++;
        if (deaths >= 9) Achievements.unlock("nueve_vidas");
        saveCheckpoint();
        AudioEngine.setBossMode(false);
        AudioEngine.play("death");
        shake(1.0, 0.5, 60);
    }

    /** Colmillo ha caido: decide cual de los dos finales toca segun la cordura y lanza su cinematica. */
    private void triggerVictory() {
        gameWon = true;
        // El precio de la cordura: si Nox llega destrozado por dentro, gana pero se pierde a sí mismo
        darkEnding = sanity < 45;
        score += 5000 + (int) (sanity * 20);
        accumulatedMs = totalPlayTime();
        AudioEngine.setBossMode(false);
        Gamepad.stopRumble();

        // La cinemática cuenta el final; detrás queda la pantalla de puntuación
        startCinematic(darkEnding ? Cinematics.FINAL_OSCURO : Cinematics.FINAL_BUENO);

        Achievements.unlock(darkEnding ? "final_oscuro" : "final_bueno");
        if (difficulty == 2) Achievements.unlock("pesadilla");
        SaveManager.submitScoreAsync("NOX", score, currentLevel, difficulty);
        SaveManager.deleteSave();
    }

    private void exitToMenu() {
        gameLoop.stop();
        AudioEngine.setBossMode(false);
        Gamepad.stopRumble();
        host.goToMenu();
    }

    // ==================================================================
    //  DIBUJO
    // ==================================================================

    /**
     * Lo llama Swing cada vez que hay que repintar.
     *
     * Aquí solo se prepara la escala: el juego se dibuja siempre como si la pantalla
     * midiera 1024x768 y después se ajusta al tamaño real de la ventana. Todo el
     * dibujo de verdad está en drawGame().
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                             RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Se ajusta el dibujo de 1024x768 al tamaño real de la ventana, centrado y
        // sin deformar. En ventana normal la escala es 1 y esto no hace nada.
        int realW = super.getWidth(), realH = super.getHeight();
        double scale = Math.min(realW / (double) VIEW_W, realH / (double) VIEW_H);
        if (scale <= 0) scale = 1;
        int offsetX = (int) ((realW - VIEW_W * scale) / 2);
        int offsetY = (int) ((realH - VIEW_H * scale) / 2);

        java.awt.geom.AffineTransform baseTransform = g2d.getTransform();
        if (offsetX > 0 || offsetY > 0) {
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, realW, realH);
        }
        g2d.translate(offsetX, offsetY);
        g2d.scale(scale, scale);
        g2d.clipRect(0, 0, VIEW_W, VIEW_H);

        try {
            drawGame(g2d);
        } finally {
            g2d.setTransform(baseTransform);
            g2d.setClip(null);
        }
    }

    /** Dibuja el fotograma completo en orden: mundo, personajes, efectos, oscuridad, luces, HUD y las pantallas de encima. */
    private void drawGame(Graphics2D g2d) {
        if (cinematic != null) {
            cinematic.draw(g2d, VIEW_W, VIEW_H);
            return;
        }
        if (showingPrologue) { drawPrologue(g2d); return; }

        LevelInfo info = levels[Math.min(currentLevel, MAX_LEVEL) - 1];

        int shakeX = 0, shakeY = 0;
        if (shakeFrames > 0) {
            shakeX = rand.nextInt(shakeMagnitude * 2 + 1) - shakeMagnitude;
            shakeY = rand.nextInt(shakeMagnitude * 2 + 1) - shakeMagnitude;
        }

        camX = (int) playerX - VIEW_W / 2 + 15 + shakeX;
        camY = (int) playerY - VIEW_H / 2 + 15 + shakeY;
        camX = Math.max(0, Math.min(camX, MAP_WIDTH * TILE_SIZE - VIEW_W));
        camY = Math.max(0, Math.min(camY, MAP_HEIGHT * TILE_SIZE - VIEW_H));

        g2d.translate(-camX, -camY);
        drawWorld(g2d);
        drawActors(g2d, info);
        drawGhosts(g2d);
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
        if (memoryActive) drawMemory(g2d);
        if (homeActive) drawBurnedHome(g2d);
        if (isTransitioning) drawTransition(g2d);
        if (levelIntroActive) drawLevelIntro(g2d, info);
        if (isDead) drawDeath(g2d);
        if (gameWon) drawVictory(g2d);
        if (paused) drawPause(g2d);

        Achievements.drawToasts(g2d, VIEW_W, VIEW_H);
    }

    /** Pantalla del recuerdo al encontrar una foto quemada. */
    private void drawMemory(Graphics2D g2d) {
        g2d.setColor(new Color(8, 6, 12, 225));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);

        int w = 660, h = 300;
        int x = VIEW_W / 2 - w / 2, y = VIEW_H / 2 - h / 2;
        g2d.setColor(new Color(20, 16, 24));
        g2d.fillRect(x, y, w, h);
        g2d.setColor(new Color(190, 140, 255));
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawRect(x, y, w, h);
        g2d.setStroke(new BasicStroke(1f));

        centeredText(g2d, memoryTitle, y + 44, new Font("Consolas", Font.BOLD, 20),
                     new Color(190, 140, 255));

        g2d.setFont(new Font("Consolas", Font.ITALIC, 19));
        g2d.setColor(new Color(228, 224, 232));
        int ly = y + 110;
        for (String line : memoryText.split("\n")) {
            int lw = g2d.getFontMetrics().stringWidth(line);
            g2d.drawString(line, VIEW_W / 2 - lw / 2, ly);
            ly += 30;
        }

        Sprites.drawBurnedPhoto(g2d, x + w / 2 - 34, y + h - 96, animationFrame);

        centeredText(g2d, "+" + Memories.SANITY_REWARD + " DE CORDURA", y + h - 26,
                     new Font("Consolas", Font.BOLD, 15), new Color(120, 255, 160));
        drawContinueHint(g2d);
    }

    /** Recordatorio de que no hay que esperar: se puede seguir cuando quieras. */
    private void drawContinueHint(Graphics2D g2d) {
        String texto = Gamepad.isConnected() ? "(A) Continuar" : "[ESPACIO] Continuar";
        int alpha = 130 + (int) (90 * Math.sin(animationFrame * 0.09));
        centeredText(g2d, texto, VIEW_H - 22, new Font("Consolas", Font.BOLD, 15),
                     new Color(210, 210, 215, Math.max(0, Math.min(255, alpha))));
    }

    /** El secreto más difícil de encontrar: lo que quedó del callejón de Nox. */
    private void drawBurnedHome(Graphics2D g2d) {
        g2d.setColor(new Color(10, 6, 5, 246));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);

        centeredText(g2d, "EL CALLEJÓN QUEMADO", 110,
                     new Font("Consolas", Font.BOLD, 34), new Color(220, 120, 60));

        String[] lines = {
            "Aquí vivían.",
            "",
            "Todavía se distingue la caja donde dormían los gatitos,",
            "el hueco entre los ladrillos por donde entraba el sol,",
            "la manta que Mitsy arrastró desde el mercado.",
            "",
            "Todo negro.",
            "",
            "Nox se queda un momento. No hay nada que llevarse:",
            "solo el olor a humo, que ya casi no se nota.",
            "",
            "Luego se da la vuelta y sigue caminando.",
        };

        g2d.setFont(new Font("Consolas", Font.PLAIN, 19));
        int ly = 190;
        for (String line : lines) {
            g2d.setColor(line.equals("Todo negro.") ? new Color(230, 110, 90) : new Color(212, 200, 190));
            int lw = g2d.getFontMetrics().stringWidth(line);
            g2d.drawString(line, VIEW_W / 2 - lw / 2, ly);
            ly += 27;
        }

        Sprites.drawBurnedHome(g2d, VIEW_W / 2 - 30, ly, 60, animationFrame);

        centeredText(g2d, "+15 DE CORDURA   -   ahora recuerdas por qué haces esto",
                     VIEW_H - 58, new Font("Consolas", Font.BOLD, 16), new Color(255, 170, 90));
        drawContinueHint(g2d);
    }

    private int viewX0() { return Math.max(0, camX / TILE_SIZE - 1); }
    private int viewY0() { return Math.max(0, camY / TILE_SIZE - 1); }
    private int viewX1() { return Math.min(MAP_WIDTH, (camX + VIEW_W) / TILE_SIZE + 2); }
    private int viewY1() { return Math.min(MAP_HEIGHT, (camY + VIEW_H) / TILE_SIZE + 2); }

    /** Estampa las casillas visibles. Solo recorre las que caben en pantalla, no las 2.500 del mapa. */
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
                    case Tiles.PHOTO:
                        g2d.drawImage(Sprites.tile(Tiles.FLOOR, theme, variant, TILE_SIZE), px, py, null);
                        Sprites.drawPhoto(g2d, px, py, TILE_SIZE, animationFrame);
                        break;
                    case Tiles.PROP_SECRET:
                        // Por fuera es un bidón corriente: si se notara, no sería un secreto
                        g2d.drawImage(Sprites.tile(Tiles.PROP, theme, variant, TILE_SIZE), px, py, null);
                        break;
                    case Tiles.HOME:
                        g2d.drawImage(Sprites.tile(Tiles.FLOOR, theme, variant, TILE_SIZE), px, py, null);
                        Sprites.drawBurnedHome(g2d, px, py, TILE_SIZE, animationFrame);
                        break;
                    default:
                        g2d.drawImage(Sprites.tile(tile, theme, variant, TILE_SIZE), px, py, null);
                }
            }
        }
    }

    /** Dibuja perros con su barra de vida, proyectiles y a Nox con su arma. */
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

        boolean moving = up || down || left || right || Math.abs(Gamepad.moveX()) > 0.01
                       || Math.abs(Gamepad.moveY()) > 0.01;
        Sprites.drawNox(g2d, (int) playerX, (int) playerY, facing, animationFrame,
                        moving, isAttacking, hurtFlash > 0, armorLevel, goldenMode);

        if (isAttacking && activeWeapon <= W_ACERO) {
            Sprites.drawClawSlash(g2d, (int) playerX + 15, (int) playerY + 15,
                                  facing, activeWeapon, animationFrame);
        }
    }

    /**
     * Hasta dónde ve Nox, en píxeles.
     *
     * La cordura **no mata**: quedarse a cero no acaba la partida, lo que hace es
     * dejarle casi ciego. Con la cordura llena ve unos 520 px; a cero, apenas 140,
     * que son poco más de dos casillas alrededor. La curva no es recta: la vista se
     * cierra deprisa en el tramo bajo, para que perder cordura se note de verdad.
     *
     * Encima se aplican los recortes temporales: cuando Colmillo apaga las luces,
     * durante el aviso de un susto, y el latido que la hace palpitar con la locura.
     */
    private float lightRadius() {
        float radius = (float) (140 + 380 * Math.pow(Math.max(0, sanity) / 100.0, 1.35));

        // Colmillo corta la luz de la mansión: se ve mucho menos durante unos segundos
        if (lightsOutTimer > 0) {
            double cierre = Math.min(1, lightsOutTimer / 45.0);
            radius *= (float) (1 - 0.68 * cierre);
        }

        // Durante el aviso del susto, la vista se va cerrando poco a poco
        if (scarePhase == SCARE_DREAD) {
            double avance = 1 - scareTimer / 80.0;
            radius *= (float) (1 - 0.55 * avance);
        }

        // Con la cordura por los suelos, la luz late al ritmo del corazón
        double locura = madness();
        if (locura > 0) {
            double latido = Math.sin(animationFrame * (0.06 + locura * 0.06));
            radius *= (float) (1 - locura * 0.10 * (0.5 + 0.5 * latido));
        }

        // Nunca del todo a cero: siempre queda un resquicio para poder moverse
        return Math.max(60f, radius);
    }

    /** Lluvia, oscuridad alrededor de Nox y destellos. El radio de luz depende de la cordura, de si Colmillo apago las luces y de si hay un susto en marcha. */
    private void drawWeather(Graphics2D g2d, LevelInfo info, int lightX, int lightY) {
        g2d.setColor(new Color(180, 200, 220, 95));
        for (RainDrop r : rainSystem) {
            g2d.drawLine((int) r.x, (int) r.y, (int) r.x - 3, (int) r.y + 18);
        }

        Point2D center = new Point2D.Float(lightX, lightY);
        float radius = lightRadius();

        try {
            g2d.setPaint(new RadialGradientPaint(center, radius,
                new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(0, 0, 0, 0), info.fog1, info.fog2}));
            g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        } catch (Exception ignored) { }

        if (flickerActive && Settings.screenFlashes()) {
            g2d.setColor(new Color(20, 0, 0, flickerAlpha));
            g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        }
        if (hurtFlash > 0 && Settings.screenFlashes()) {
            g2d.setColor(new Color(180, 0, 0, hurtFlash * 8));
            g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        }

        drawEdgeShadow(g2d);
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
        g2d.fillRect(10, 10, VIEW_W - 20, 44);
        g2d.setColor(new Color(70, 70, 80));
        g2d.drawRect(10, 10, VIEW_W - 20, 44);

        int max = maxHealth();
        // Con la cordura por los suelos, ni la barra de vida es de fiar
        int mostrada = hudLieTimer > 0 ? hudLieValue : playerHealth;

        g2d.setColor(new Color(60, 15, 15));
        g2d.fillRect(20, 20, 200, 22);
        g2d.setColor(mostrada > max * 0.35 ? new Color(0, 220, 100) : new Color(255, 60, 60));
        g2d.fillRect(20, 20, (int) (200.0 * Math.max(0, Math.min(max, mostrada)) / max), 22);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(20, 20, 200, 22);
        g2d.setFont(new Font("Consolas", Font.BOLD, 13));
        g2d.drawString(Math.max(0, mostrada) + " / " + max, 84, 37);

        g2d.setColor(new Color(255, 190, 70));
        g2d.setFont(new Font("Consolas", Font.BOLD, 14));
        g2d.drawString(info.name + "   $" + playerMoney + "   " + weaponLabel(), 232, 30);

        g2d.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2d.setColor(new Color(170, 200, 230));
        String gear = ARMOR_NAMES[Math.min(3, armorLevel)] + "   [Q] ATÚN x" + tunaCans;
        if (hasWhistle) gear += "   [F] SILBATO " + (whistleCooldown > 0 ? (whistleCooldown / 60 + 1) + "s" : "LISTO");
        g2d.drawString(gear, 232, 47);

        g2d.setFont(new Font("Consolas", Font.BOLD, 12));
        int fx0 = VIEW_W - 250;
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

        // Fotos encontradas en este barrio
        g2d.setColor(new Color(0, 0, 0, 215));
        g2d.fillRect(246, 60, 120, 20);
        g2d.setColor(new Color(190, 140, 255));
        g2d.setFont(new Font("Consolas", Font.BOLD, 11));
        g2d.drawString("FOTOS " + photosThisLevel + "/" + Memories.PER_LEVEL, 254, 74);

        if (enrageMessageActive && currentBoss != null) {
            centeredText(g2d, "¡" + currentBoss.name + " SE ENFURECE!", 182,
                         new Font("Consolas", Font.BOLD, 30), new Color(255, 60, 40));
        }
        if (lightsOutTimer > 0) {
            centeredText(g2d, "SIN LUZ", 218, new Font("Consolas", Font.BOLD, 20),
                         new Color(150, 60, 200));
        }

        if (currentBoss != null && bossEncountered && !bossDefeated) {
            int bw = 520, bx = VIEW_W / 2 - bw / 2, by = 118;
            g2d.setColor(new Color(0, 0, 0, 220));
            g2d.fillRect(bx - 4, by - 22, bw + 8, 44);
            g2d.setColor(new Color(255, 40, 40));
            g2d.setFont(new Font("Consolas", Font.BOLD, 15));
            int w = g2d.getFontMetrics().stringWidth(currentBoss.name);
            g2d.drawString(currentBoss.name, VIEW_W / 2 - w / 2, by - 6);
            g2d.setColor(new Color(50, 10, 10));
            g2d.fillRect(bx, by, bw, 14);
            g2d.setColor(new Color(255, 60, 200));
            g2d.fillRect(bx, by, (int) (bw * Math.max(0, currentBoss.hp) / (double) currentBoss.maxHp), 14);
            g2d.setColor(Color.WHITE);
            g2d.drawRect(bx, by, bw, 14);
        }

        if (nearStore && !isShopOpen) {
            centeredText(g2d, ">>> [E] MERCADO NEGRO <<<", VIEW_H - 55,
                         new Font("Consolas", Font.BOLD, 17), new Color(255, 0, 150));
        }
        if (nearExitLocked) {
            centeredText(g2d, "¡DERROTA A " + info.bossName + " PARA AVANZAR!", VIEW_H - 55,
                         new Font("Consolas", Font.BOLD, 17), new Color(255, 60, 60));
        }

        g2d.setColor(new Color(120, 120, 130));
        g2d.setFont(new Font("Consolas", Font.PLAIN, 12));
        String controls = Gamepad.isConnected()
            ? "(A) Atacar  (X) Curar  (Y) Silbato  (B) Tienda  LB/RB Arma  BACK Mapa  START Pausa"
            : "[ESPACIO] Atacar  [1-4] Arma  [Q] Curar  [M] Mapa  [P/ESC] Pausa";
        if (Gamepad.isConnected() && showMinimap) controls = "(BACK) Cerrar el mapa   " + controls;
        g2d.drawString(controls, 14, VIEW_H - 14);

        String cloud = SaveManager.getCloudStatus();
        if (Gamepad.isConnected()) cloud = "MANDO  |  " + cloud;
        int cw = g2d.getFontMetrics().stringWidth(cloud);
        g2d.drawString(cloud, VIEW_W - cw - 14, VIEW_H - 14);
    }

    /** Nombre del arma equipada para el HUD. */
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
        g2d.drawString(text, VIEW_W / 2 - w / 2, y);
    }

    /** Mapa del barrio con muros, agua, vacio, tiendas, salida, perros y Nox. */
    private void drawMinimap(Graphics2D g2d) {
        int mx = VIEW_W - 235, my = 100, size = 210;
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
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);

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

        Sprites.drawNox(g2d, VIEW_W - 150, VIEW_H - 190, 2, animationFrame, false, false, false, 0);

        centeredText(g2d, "Presiona ESPACIO o ENTER para comenzar...", VIEW_H - 28,
                     new Font("Consolas", Font.ITALIC, 16), new Color(160, 160, 160));
    }

    /** Cartel con el nombre del barrio, su jefe y a quien se rescata alli. */
    private void drawLevelIntro(Graphics2D g2d, LevelInfo info) {
        g2d.setColor(new Color(0, 0, 0, 215));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        centeredText(g2d, info.name, VIEW_H / 2 - 20, new Font("Consolas", Font.BOLD, 36), new Color(255, 60, 60));
        String sub = (currentLevel == MAX_LEVEL ? "JEFE FINAL: " : "JEFE DEL BARRIO: ") + info.bossName;
        centeredText(g2d, sub, VIEW_H / 2 + 25, new Font("Consolas", Font.PLAIN, 20), Color.WHITE);
        if (info.rescueTarget != null) {
            centeredText(g2d, "Aquí retienen a " + info.rescueTarget, VIEW_H / 2 + 60,
                         new Font("Consolas", Font.ITALIC, 17), new Color(120, 255, 160));
        }
    }

    private void drawBossIntro(Graphics2D g2d, LevelInfo info) {
        g2d.setColor(new Color(20, 0, 0, 195));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        centeredText(g2d, "¡" + info.bossName + " APARECE!", VIEW_H / 2 - 10,
                     new Font("Consolas", Font.BOLD, 38), new Color(255, 30, 30));
        centeredText(g2d, info.bossTaunt, VIEW_H / 2 + 32,
                     new Font("Consolas", Font.ITALIC, 18), new Color(230, 230, 230));
    }

    /** Pantalla del rescate: lo que dice el familiar y lo que le aporta a Nox. */
    private void drawRescueMessage(Graphics2D g2d) {
        g2d.setColor(new Color(0, 40, 20, 210));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        centeredText(g2d, rescueMessageText, VIEW_H / 2 - 60,
                     new Font("Consolas", Font.BOLD, 36), new Color(0, 255, 120));

        // El familiar dice algo y deja su marca en Nox
        String[] partes = rescueDialogue.split("\n");
        if (partes.length > 0 && !partes[0].isEmpty()) {
            centeredText(g2d, partes[0], VIEW_H / 2 - 6,
                         new Font("Consolas", Font.ITALIC, 22), new Color(235, 235, 235));
        }
        if (partes.length > 1) {
            centeredText(g2d, partes[1], VIEW_H / 2 + 44,
                         new Font("Consolas", Font.BOLD, 19), new Color(255, 200, 80));
        }
        centeredText(g2d, "La salida del barrio se ha abierto.", VIEW_H / 2 + 96,
                     new Font("Consolas", Font.PLAIN, 17), new Color(200, 200, 200));
        drawContinueHint(g2d);
    }

    /** Cartel entre barrios mientras se genera el siguiente. */
    private void drawTransition(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 245));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        centeredText(g2d, "BARRIO CONQUISTADO", VIEW_H / 2 - 20,
                     new Font("Consolas", Font.BOLD, 44), new Color(255, 50, 50));
        centeredText(g2d, "Entrando a una zona más peligrosa...", VIEW_H / 2 + 28,
                     new Font("Consolas", Font.PLAIN, 21), Color.WHITE);
        centeredText(g2d, "Progreso guardado", VIEW_H / 2 + 66,
                     new Font("Consolas", Font.ITALIC, 15), new Color(140, 140, 140));
    }

    /** Pantalla de derrota con las estadisticas y la opcion de reintentar. */
    private void drawDeath(Graphics2D g2d) {
        g2d.setColor(new Color(30, 0, 0, 240));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        centeredText(g2d, "NOX HA CAÍDO", VIEW_H / 2 - 70,
                     new Font("Consolas", Font.BOLD, 50), new Color(255, 40, 40));
        centeredText(g2d, "La pandilla de Colmillo sigue al mando de la ciudad.", VIEW_H / 2 - 20,
                     new Font("Consolas", Font.PLAIN, 19), new Color(200, 200, 200));
        centeredText(g2d, "Barrio " + currentLevel + "   |   " + kills + " perros derrotados   |   "
                          + score + " puntos", VIEW_H / 2 + 20,
                     new Font("Consolas", Font.PLAIN, 18), new Color(255, 190, 70));
        centeredText(g2d, "[ENTER] Reintentar desde el barrio " + currentLevel, VIEW_H / 2 + 70,
                     new Font("Consolas", Font.BOLD, 19), new Color(120, 255, 160));
        centeredText(g2d, "[ESC] Volver al menú", VIEW_H / 2 + 102,
                     new Font("Consolas", Font.PLAIN, 17), new Color(160, 160, 160));
    }

    /** Dos finales: depende de con cuánta cordura llegue Nox a la mansión. */
    private void drawVictory(Graphics2D g2d) {
        long secs = accumulatedMs / 1000;

        if (darkEnding) {
            g2d.setColor(new Color(10, 0, 8, 252));
            g2d.fillRect(0, 0, VIEW_W, VIEW_H);

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
                g2d.drawString(line, VIEW_W / 2 - w / 2, ly);
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
            g2d.fillRect(0, 0, VIEW_W, VIEW_H);

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
                g2d.drawString(line, VIEW_W / 2 - w / 2, ly);
                ly += 26;
            }

            Sprites.drawNox(g2d, VIEW_W / 2 - 15, ly + 12, 1, animationFrame, false, false, false, armorLevel);

            centeredText(g2d, "PUNTUACIÓN: " + score + "   |   " + kills + " perros   |   "
                              + (secs / 60) + "m " + (secs % 60) + "s   |   " + difficultyName(),
                         ly + 84, new Font("Consolas", Font.PLAIN, 16), new Color(255, 190, 70));
        }

        centeredText(g2d, "Presiona ESC para volver al menú", VIEW_H - 26,
                     new Font("Consolas", Font.ITALIC, 16), new Color(150, 150, 150));
    }

    private String difficultyName() {
        return difficulty == 0 ? "FÁCIL" : difficulty == 2 ? "PESADILLA" : "NORMAL";
    }

    /** Menu de pausa con los ajustes rapidos de sonido. */
    private void drawPause(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 210));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);
        centeredText(g2d, "PAUSA", VIEW_H / 2 - 60, new Font("Consolas", Font.BOLD, 46), Color.WHITE);

        boolean pad = Gamepad.isConnected();
        centeredText(g2d, pad ? "(START) Continuar     (B) Salir al menú"
                              : "[P] Continuar     [ESC] Salir al menú",
                     VIEW_H / 2, new Font("Consolas", Font.PLAIN, 19), new Color(210, 210, 210));
        centeredText(g2d, (pad ? "(X) Música: " : "[N] Música: ") + (AudioEngine.isMusicOn() ? "ON" : "OFF")
                          + (pad ? "     (Y) Efectos: " : "     [B] Efectos: ")
                          + (AudioEngine.isSfxOn() ? "ON" : "OFF"),
                     VIEW_H / 2 + 40, new Font("Consolas", Font.PLAIN, 18), new Color(255, 190, 70));
        centeredText(g2d, "Tu progreso se guarda al empezar cada barrio.", VIEW_H / 2 + 90,
                     new Font("Consolas", Font.ITALIC, 15), new Color(140, 140, 140));
    }

    /** Los diez articulos con su precio y si ya se compraron. */
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

    /** Pantalla del Mercado Negro con los diez articulos y el que esta seleccionado. */
    private void drawShop(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 238));
        g2d.fillRect(0, 0, VIEW_W, VIEW_H);

        int w = 880, h = 610;
        int x = VIEW_W / 2 - w / 2, y = VIEW_H / 2 - h / 2;
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
        switch (scarePhase) {
            case SCARE_DREAD:  drawScareDread(g2d);  return;
            case SCARE_IMPACT: drawScareImpact(g2d); return;
            case SCARE_AFTER:  drawScareAfter(g2d);  return;
            default: break;
        }
    }

    private void drawScareDread(Graphics2D g2d) {
        Jumpscares.drawDread(g2d, jumpscareType, 1 - scareTimer / 80.0,
                             scareDirX, scareDirY, VIEW_W, VIEW_H, animationFrame);
    }

    private void drawScareImpact(Graphics2D g2d) {
        double crecimiento = Math.min(1, (1 - scareTimer / 42.0) * 3.4);
        Jumpscares.drawImpact(g2d, jumpscareType, crecimiento,
                              scareDirX, scareDirY, VIEW_W, VIEW_H, animationFrame, rand);
    }

    private void drawScareAfter(Graphics2D g2d) {
        Jumpscares.drawAfter(g2d, jumpscareType, scareTimer / 55.0,
                             VIEW_W, VIEW_H, animationFrame);
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
        int stuckFrames = 0;
        int pathTimer = 0;
        int pathTileX = -1;
        int pathTileY = -1;

        /**
         * Caja con la que choca contra las paredes, siempre más pequeña que el dibujo
         * y centrada dentro de él. Un pasillo mide 60 px y un jefe se dibuja de 78:
         * con la caja del tamaño del dibujo no cabía por ningún paso estrecho y se
         * quedaba atorado. El daño y el alcance siguen usando el tamaño visible.
         */
        int collisionSize;

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
            collisionSize = Math.min(size, MAX_COLLISION);
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
            this.collisionSize = MAX_COLLISION;
        }
    }

    class Projectile {
        double x, y, dx, dy;
        int damage;
        boolean isEnemyBullet, molotov;
        /** Contenedor de Titán: explota más fuerte y en más radio que un molotov. */
        boolean container;
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

    /**
     * Teclado del juego.
     *
     * Va de lo más general a lo más concreto: primero la pantalla completa, que
     * funciona siempre; después cada estado que se traga las teclas (cinemática,
     * muerte, victoria, pausa, pantallas de historia, tienda); y al final, solo si
     * no hay nada de eso, los controles de jugar.
     */
    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        // Funciona en cualquier momento, incluso en las pantallas de texto
        if (key == KeyEvent.VK_F11) {
            if (host != null) host.toggleFullscreen();
            return;
        }

        if (handleCinematicInput(key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER,
                                 key == KeyEvent.VK_ESCAPE)) {
            return;
        }

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

        // Las pantallas de historia se pueden saltar en vez de esperar a que pasen
        if (memoryActive || homeActive || rescueMessageActive) {
            if (key == KeyEvent.VK_SPACE || key == KeyEvent.VK_ENTER || key == KeyEvent.VK_E) {
                dismissStoryScreen();
            }
            return;
        }

        if (levelIntroActive || isJumpscareActive || bossIntroActive || isTransitioning) return;

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

    /** Come una lata de atun si le queda alguna y no tiene la vida llena. */
    private void useTuna() {
        if (tunaCans <= 0) {
            fx.text(playerX, playerY - 20, "SIN ATÚN", new Color(200, 200, 200), 14);
            AudioEngine.play("error", 0.5);
            return;
        }
        if (playerHealth >= maxHealth()) {
            fx.text(playerX, playerY - 20, "SALUD LLENA", new Color(200, 200, 200), 14);
            return;
        }
        tunaCans--;
        int healed = Math.min(50, maxHealth() - playerHealth);
        playerHealth += healed;
        fx.text(playerX, playerY - 20, "+" + healed, new Color(80, 255, 130), 18);
        AudioEngine.play("heal");
    }

    /** Silbato ultrasonico: aturde a todo perro a menos de 300 px, jefes incluidos. */
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
        if (affected >= 8) Achievements.unlock("silbato");
    }

    /** Ataca con el arma equipada. Las de cuerpo a cuerpo golpean en un radio y rompen bidones; las de disparo crean proyectiles. */
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
                // El zarpazo también revienta bidones, cajas y muebles
                breakPropsNear(cx + Math.cos(baseAngle) * 30, cy + Math.sin(baseAngle) * 30, range * 0.6);
                AudioEngine.play(steel ? "steel" : "claw");
                if (!connected) fx.hit(cx + Math.cos(baseAngle) * 40, cy + Math.sin(baseAngle) * 40,
                                       new Color(150, 150, 160));
            }
        }
    }

    /** Teclado dentro de la tienda: moverse, comprar y cerrar. */
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

    /** Compra el articulo marcado en la tienda. */
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

    /** Cobra si se puede pagar y no se tiene ya, aplica el efecto y avisa por pantalla. */
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

            if (hasSteelClaws && hasCrossbow && hasPlasmaLauncher && hasMolotov && hasWhistle) {
                Achievements.unlock("arsenal");
            }
            if (armorLevel >= 3) Achievements.unlock("blindado");

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
