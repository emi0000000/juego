import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.util.Random;

/**
 * Cinemáticas del juego: el prólogo y los dos finales.
 *
 * No son vídeo ni imágenes: son escenas dibujadas con Java 2D, igual que el resto
 * del juego, encadenadas con fundidos y texto que aparece a su tiempo. Eso permite
 * tener secuencias narrativas sin añadir un solo archivo al proyecto.
 *
 * Cada cinemática es una lista de planos. Un plano dura unos fotogramas, se dibuja
 * solo y lleva sus propias frases. La cámara se mueve con un valor de 0 a 1 que
 * avanza dentro del plano, así que hacer un travelling es multiplicar por ese valor.
 */
public final class Cinematics {

    public static final int PROLOGO = 0;
    public static final int FINAL_BUENO = 1;
    public static final int FINAL_OSCURO = 2;

    /** Fotogramas que dura cada plano de cada cinemática. */
    private static final int[][] SHOT_LENGTHS = {
        {300, 340, 300, 280, 340, 300},   // prólogo
        {260, 320, 340, 260},             // final bueno
        {260, 340, 340, 260},             // final oscuro
    };

    private static final String[][][] SHOT_TEXTS = {
        // ---------- PRÓLOGO ----------
        {
            {"La ciudad no duerme.", "Solo deja de mirar."},
            {"En un callejón, detrás del mercado,", "Nox tenía todo lo que quería.",
             "Mitsy. Will. Nina."},
            {"Pero la ciudad tiene dueño.", "Y el dueño cobra."},
            {"Colmillo le pidió su parte.", "", "Nox se negó."},
            {"Esa noche ardió el callejón.", "Cuando volvió, ya no había nadie."},
            {"Seis barrios lo separan de su familia.", "En cada uno le espera un perro de Colmillo."},
        },
        // ---------- FINAL BUENO ----------
        {
            {"Don Colmillo cayó en su propia mansión."},
            {"La pandilla se deshizo esa misma noche.", "Nadie volvió a cobrar por respirar."},
            {"Mitsy, Will y Nina volvieron a casa.", "",
             "Nox aguantó la oscuridad", "sin dejar que lo cambiara."},
            {"Y por primera vez en mucho tiempo,", "la ciudad amaneció tranquila."},
        },
        // ---------- FINAL OSCURO ----------
        {
            {"Don Colmillo cayó en su propia mansión."},
            {"Mitsy lo llamó por su nombre.", "", "Nox no se dio la vuelta."},
            {"Demasiadas noches en la oscuridad.", "Demasiados aullidos.", "",
             "El gato que entró a la mansión", "no fue el que salió."},
            {"Su familia volvió a casa.", "Él se quedó en la ciudad.", "",
             "Y la ciudad aprendió a temerle."},
        },
    };

    private final int kind;
    private final Random rand = new Random(7);
    private int shot = 0;
    private int timer;
    private int frame = 0;
    private boolean finished = false;

    public Cinematics(int kind) {
        this.kind = Math.max(0, Math.min(SHOT_LENGTHS.length - 1, kind));
        this.timer = SHOT_LENGTHS[this.kind][0];
    }

    public boolean isFinished() { return finished; }

    public int shotCount() { return SHOT_LENGTHS[kind].length; }

    public int currentShot() { return shot; }

    public void update() {
        if (finished) return;
        frame++;
        if (--timer > 0) return;
        nextShot();
    }

    /** Pasa al plano siguiente; si era el último, la cinemática termina. */
    public void nextShot() {
        if (finished) return;
        shot++;
        if (shot >= SHOT_LENGTHS[kind].length) {
            finished = true;
            return;
        }
        timer = SHOT_LENGTHS[kind][shot];
    }

    /** Salta la cinemática entera. */
    public void skip() { finished = true; }

    // ==================================================================
    //  DIBUJO
    // ==================================================================

    public void draw(Graphics2D g, int w, int h) {
        if (finished) return;

        int total = SHOT_LENGTHS[kind][shot];
        double t = 1 - timer / (double) total;   // 0 al empezar el plano, 1 al acabar

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);

        if (kind == PROLOGO) drawPrologoShot(g, w, h, t);
        else if (kind == FINAL_BUENO) drawBuenoShot(g, w, h, t);
        else drawOscuroShot(g, w, h, t);

        drawTexts(g, w, h, t);
        drawFades(g, w, h, t);
        drawSkipHint(g, w, h);
    }

    /** Las frases aparecen escalonadas y se van al final del plano. */
    private void drawTexts(Graphics2D g, int w, int h, double t) {
        String[] lines = SHOT_TEXTS[kind][shot];
        int base = h - 150;

        g.setColor(new Color(0, 0, 0, 165));
        g.fillRect(0, base - 44, w, lines.length * 32 + 54);

        g.setFont(new Font("Consolas", Font.BOLD, 21));
        for (int i = 0; i < lines.length; i++) {
            double aparece = 0.10 + i * 0.10;
            if (t < aparece) continue;
            double entrada = Math.min(1, (t - aparece) / 0.12);
            double salida = t > 0.88 ? Math.max(0, 1 - (t - 0.88) / 0.12) : 1;
            int alpha = (int) (235 * entrada * salida);
            if (alpha <= 0) continue;

            g.setColor(new Color(232, 228, 226, alpha));
            int lw = g.getFontMetrics().stringWidth(lines[i]);
            g.drawString(lines[i], w / 2 - lw / 2, base + i * 32);
        }
    }

    /** Fundido a negro al entrar y al salir de cada plano. */
    private void drawFades(Graphics2D g, int w, int h, double t) {
        int alpha = 0;
        if (t < 0.09) alpha = (int) (255 * (1 - t / 0.09));
        else if (t > 0.93) alpha = (int) (255 * ((t - 0.93) / 0.07));
        if (alpha <= 0) return;
        g.setColor(new Color(0, 0, 0, Math.min(255, alpha)));
        g.fillRect(0, 0, w, h);
    }

    /** Aviso discreto en la esquina de que se puede avanzar o saltar. */
    private void drawSkipHint(Graphics2D g, int w, int h) {
        String texto = Gamepad.isConnected()
            ? "(A) Siguiente     (START) Saltar"
            : "[ESPACIO] Siguiente     [ESC] Saltar";
        g.setFont(new Font("Consolas", Font.ITALIC, 13));
        int alpha = 90 + (int) (60 * Math.sin(frame * 0.06));
        g.setColor(new Color(150, 150, 158, Math.max(0, Math.min(255, alpha))));
        int lw = g.getFontMetrics().stringWidth(texto);
        g.drawString(texto, w - lw - 20, h - 18);
    }

    // ------------------------------------------------------------------
    //  PRÓLOGO
    // ------------------------------------------------------------------

    private void drawPrologoShot(Graphics2D g, int w, int h, double t) {
        switch (shot) {
            case 0: escenaCiudad(g, w, h, t); break;
            case 1: escenaFamilia(g, w, h, t); break;
            case 2: escenaPandilla(g, w, h, t); break;
            case 3: escenaNegativa(g, w, h, t); break;
            case 4: escenaIncendio(g, w, h, t); break;
            default: escenaCenizas(g, w, h, t); break;
        }
    }

    /** La ciudad de noche bajo la lluvia, con la cámara bajando despacio. */
    private void escenaCiudad(Graphics2D g, int w, int h, double t) {
        cieloNocturno(g, w, h);
        skyline(g, w, h, (int) (h * 0.32 + t * 26), 0.55, new Color(11, 10, 16), 15);
        skyline(g, w, h, (int) (h * 0.50 + t * 18), 0.75, new Color(17, 15, 24), 11);
        skyline(g, w, h, (int) (h * 0.68 + t * 10), 1.0, new Color(23, 20, 31), 8);
        lluvia(g, w, h, 220, 0.75);
    }

    /** El callejón: la única escena cálida de todo el juego. */
    private void escenaFamilia(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(20, 15, 13));
        g.fillRect(0, 0, w, h);

        // Paredes del callejón, en perspectiva
        g.setColor(new Color(32, 25, 22));
        g.fillPolygon(new int[]{0, (int) (w * 0.26), (int) (w * 0.26), 0},
                      new int[]{0, (int) (h * 0.18), (int) (h * 0.9), h}, 4);
        g.fillPolygon(new int[]{w, (int) (w * 0.74), (int) (w * 0.74), w},
                      new int[]{0, (int) (h * 0.18), (int) (h * 0.9), h}, 4);
        g.setColor(new Color(26, 20, 18));
        g.fillRect(0, (int) (h * 0.72), w, h);

        // Una bombilla colgando: el foco de calor de la escena
        int luzX = w / 2, luzY = (int) (h * 0.20);
        g.setColor(new Color(60, 50, 40));
        g.drawLine(luzX, 0, luzX, luzY);
        double parpadeo = 0.9 + 0.1 * Math.sin(frame * 0.11);
        try {
            g.setPaint(new RadialGradientPaint(new Point2D.Float(luzX, luzY), (float) (330 * parpadeo),
                new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(255, 205, 130, 165), new Color(210, 150, 80, 60),
                            new Color(0, 0, 0, 0)}));
            g.fillRect(0, 0, w, h);
        } catch (Exception ignored) { }
        g.setColor(new Color(255, 225, 165));
        g.fillOval(luzX - 9, luzY - 9, 18, 18);

        // La familia, sobre una caja. Todo el grupo se dibuja a mayor tamaño para
        // que sea el centro del plano y se les vea la cara.
        int suelo = (int) (h * 0.68);
        g.setColor(new Color(78, 58, 38));
        g.fillRect(w / 2 - 210, suelo, 420, 70);
        g.setColor(new Color(56, 40, 26));
        g.fillRect(w / 2 - 210, suelo, 420, 8);

        java.awt.geom.AffineTransform grupo = g.getTransform();
        g.translate(w / 2, suelo);
        g.scale(1.9, 1.9);

        // Nox y Mitsy, mirándose
        Sprites.drawNox(g, -74, -46, 3, frame, false, false, false, 0);
        Sprites.drawNox(g, 22, -46, 2, frame + 55, false, false, false, 0);

        // Will y Nina, más pequeños, entre ellos
        java.awt.geom.AffineTransform padres = g.getTransform();
        g.translate(-30, -18);
        g.scale(0.6, 0.6);
        Sprites.drawNox(g, 0, 0, 3, frame + 40, false, false, false, 0);
        g.setTransform(padres);
        g.translate(4, -16);
        g.scale(0.6, 0.6);
        Sprites.drawNox(g, 0, 0, 2, frame + 95, false, false, false, 0);

        g.setTransform(grupo);
        viñeta(g, w, h, 0.55);
    }

    /** Colmillo y los suyos llegando: solo ojos en la oscuridad. */
    private void escenaPandilla(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(8, 6, 8));
        g.fillRect(0, 0, w, h);
        skyline(g, w, h, (int) (h * 0.55), 1.0, new Color(15, 12, 18), 10);

        // Se van acercando conforme avanza el plano
        int suelo = (int) (h * 0.78);
        int[] offsets = {-300, -160, 0, 170, 320};
        for (int i = 0; i < offsets.length; i++) {
            double avance = Math.min(1, t * 1.4 - i * 0.05);
            if (avance <= 0) continue;
            int size = (int) (44 + 62 * avance);
            int x = w / 2 + (int) (offsets[i] * (1.25 - 0.25 * avance)) - size / 2;
            int y = suelo - size + (int) (18 * (1 - avance));
            Color c = i == 2 ? new Color(52, 12, 12) : new Color(20, 15, 16);
            Sprites.drawDog(g, x, y, size, c, i % 5, false, i == 2, 1, frame);
        }

        lluvia(g, w, h, 160, 0.6);
        viñeta(g, w, h, 0.8);
    }

    /** El primer plano de Nox diciendo que no. */
    private void escenaNegativa(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(10, 9, 12));
        g.fillRect(0, 0, w, h);

        // Nox muy cerca de la cámara, creciendo un poco durante el plano
        java.awt.geom.AffineTransform saved = g.getTransform();
        double escala = 4.2 + t * 0.5;
        g.translate(w / 2, h * 0.56);
        g.scale(escala, escala);
        g.translate(-15, -20);
        Sprites.drawNox(g, 0, 0, 1, frame, false, false, false, 0);
        g.setTransform(saved);

        // La lluvia le cae por delante
        lluvia(g, w, h, 130, 0.55);
        viñeta(g, w, h, 0.9);
    }

    /** El callejón ardiendo y la familia arrastrada fuera de plano. */
    private void escenaIncendio(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(24, 8, 4));
        g.fillRect(0, 0, w, h);

        // Resplandor del fuego, latiendo
        double pulso = 0.85 + 0.15 * Math.sin(frame * 0.22);
        try {
            g.setPaint(new RadialGradientPaint(
                new Point2D.Float(w / 2f, h * 0.78f), (float) (h * 0.95 * pulso),
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(255, 170, 60, 200), new Color(190, 60, 20, 110),
                            new Color(0, 0, 0, 0)}));
            g.fillRect(0, 0, w, h);
        } catch (Exception ignored) { }

        // Siluetas de las llamas
        g.setColor(new Color(20, 8, 6));
        for (int i = 0; i < 9; i++) {
            int fx = i * w / 8 - 40;
            int alto = (int) (h * (0.18 + 0.10 * Math.abs(Math.sin(frame * 0.09 + i))));
            g.fillPolygon(new int[]{fx, fx + 70, fx + 140},
                          new int[]{h, h - alto, h}, 3);
        }

        // La familia, alejándose a rastras hacia el borde
        int suelo = (int) (h * 0.66);
        int arrastre = (int) (t * w * 0.35);
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(w * 0.62 + arrastre, suelo);
        g.scale(0.9, 0.9);
        Sprites.drawDog(g, 0, 0, 66, new Color(14, 10, 12), 2, false, false, 3, frame);
        g.setTransform(saved);

        saved = g.getTransform();
        g.translate(w * 0.72 + arrastre, suelo + 18);
        g.scale(0.7, 0.7);
        Sprites.drawNox(g, 0, 0, 2, frame, false, false, true, 0);
        g.setTransform(saved);

        // Y Nox llegando tarde, pequeño al fondo
        double llegada = Math.min(1, t * 1.5);
        Sprites.drawNox(g, (int) (w * 0.14 + llegada * w * 0.12), suelo + 30,
                        3, frame, true, false, false, 0);

        humo(g, w, h);
    }

    /** Lo que queda cuando se apaga: ceniza y un gato solo. */
    private void escenaCenizas(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(12, 10, 11));
        g.fillRect(0, 0, w, h);
        skyline(g, w, h, (int) (h * 0.52), 1.0, new Color(18, 15, 20), 12);

        int suelo = (int) (h * 0.76);
        g.setColor(new Color(22, 19, 20));
        g.fillRect(0, suelo, w, h - suelo);

        // Restos del callejón
        g.setColor(new Color(30, 22, 18));
        g.fillRect(w / 2 - 120, suelo - 30, 90, 30);
        g.setColor(new Color(16, 12, 12));
        g.fillRect(w / 2 - 40, suelo - 46, 70, 46);

        // Brasas
        for (int i = 0; i < 14; i++) {
            int a = 60 + (int) (120 * Math.abs(Math.sin(frame * 0.05 + i * 1.7)));
            g.setColor(new Color(255, 130, 45, a));
            g.fillOval(w / 2 - 130 + (i * 23) % 220, suelo - 8 - (i % 4) * 7, 4, 4);
        }

        // Nox de espaldas, mirando lo que fue su casa
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(w / 2 + 130, suelo - 60);
        g.scale(1.9, 1.9);
        Sprites.drawNox(g, 0, 0, 2, frame, false, false, false, 0);
        g.setTransform(saved);

        lluvia(g, w, h, 200, 0.7);
        viñeta(g, w, h, 0.75);
    }

    // ------------------------------------------------------------------
    //  FINAL BUENO
    // ------------------------------------------------------------------

    private void drawBuenoShot(Graphics2D g, int w, int h, double t) {
        switch (shot) {
            case 0: escenaColmilloCaido(g, w, h, t, false); break;
            case 1: escenaVuelta(g, w, h, t); break;
            case 2: escenaAzoteaJuntos(g, w, h, t); break;
            default: escenaAmanecer(g, w, h, t); break;
        }
    }

    /** Los cuatro caminando de vuelta bajo la lluvia. */
    private void escenaVuelta(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(12, 12, 18));
        g.fillRect(0, 0, w, h);
        skyline(g, w, h, (int) (h * 0.50), 1.0, new Color(19, 18, 26), 12);

        int suelo = (int) (h * 0.74);
        g.setColor(new Color(24, 23, 30));
        g.fillRect(0, suelo, w, h - suelo);

        // Van cruzando el plano de izquierda a derecha
        int avance = (int) (-160 + t * (w + 260));
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(avance, suelo - 62);
        g.scale(1.5, 1.5);
        Sprites.drawNox(g, 0, 0, 3, frame, true, false, false, 0);
        g.setTransform(saved);

        saved = g.getTransform();
        g.translate(avance + 70, suelo - 56);
        g.scale(1.3, 1.3);
        Sprites.drawNox(g, 0, 0, 3, frame + 30, true, false, false, 0);
        g.setTransform(saved);

        saved = g.getTransform();
        g.translate(avance + 128, suelo - 40);
        g.scale(0.85, 0.85);
        Sprites.drawNox(g, 0, 0, 3, frame + 60, true, false, false, 0);
        g.setTransform(saved);

        saved = g.getTransform();
        g.translate(avance + 172, suelo - 38);
        g.scale(0.85, 0.85);
        Sprites.drawNox(g, 0, 0, 3, frame + 95, true, false, false, 0);
        g.setTransform(saved);

        lluvia(g, w, h, 170, 0.6);
        viñeta(g, w, h, 0.6);
    }

    /** Los cuatro sentados en el borde de una azotea. */
    private void escenaAzoteaJuntos(Graphics2D g, int w, int h, double t) {
        cieloAmanecer(g, w, h, t * 0.5);
        skyline(g, w, h, (int) (h * 0.58), 1.0, new Color(20, 17, 26), 12);

        int borde = (int) (h * 0.72);
        g.setColor(new Color(26, 24, 30));
        g.fillRect(0, borde, w, h - borde);
        g.setColor(new Color(34, 31, 38));
        g.fillRect(0, borde - 8, w, 8);

        int cx = w / 2 - 110;
        Sprites.drawNox(g, cx, borde - 44, 1, frame, false, false, false, 0);
        Sprites.drawNox(g, cx + 74, borde - 44, 1, frame + 50, false, false, false, 0);

        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(cx + 140, borde - 32);
        g.scale(0.62, 0.62);
        Sprites.drawNox(g, 0, 0, 1, frame + 20, false, false, false, 0);
        g.setTransform(saved);
        saved = g.getTransform();
        g.translate(cx + 188, borde - 32);
        g.scale(0.62, 0.62);
        Sprites.drawNox(g, 0, 0, 1, frame + 75, false, false, false, 0);
        g.setTransform(saved);

        viñeta(g, w, h, 0.4);
    }

    /** El sol saliendo sobre la ciudad. */
    private void escenaAmanecer(Graphics2D g, int w, int h, double t) {
        cieloAmanecer(g, w, h, t);
        int solY = (int) (h * 0.62 - t * h * 0.22);
        try {
            g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, solY), (float) (h * 0.55),
                new float[]{0f, 0.35f, 1f},
                new Color[]{new Color(255, 220, 150, 220), new Color(255, 150, 80, 90),
                            new Color(0, 0, 0, 0)}));
            g.fillRect(0, 0, w, h);
        } catch (Exception ignored) { }
        g.setColor(new Color(255, 235, 190));
        g.fillOval(w / 2 - 60, solY - 60, 120, 120);

        skyline(g, w, h, (int) (h * 0.62), 1.0, new Color(24, 18, 24), 13);
        viñeta(g, w, h, 0.35);
    }

    // ------------------------------------------------------------------
    //  FINAL OSCURO
    // ------------------------------------------------------------------

    private void drawOscuroShot(Graphics2D g, int w, int h, double t) {
        switch (shot) {
            case 0: escenaColmilloCaido(g, w, h, t, true); break;
            case 1: escenaNoSeDaLaVuelta(g, w, h, t); break;
            case 2: escenaSeAleja(g, w, h, t); break;
            default: escenaNuevoDueno(g, w, h, t); break;
        }
    }

    /** Mitsy lo llama y él no se gira. */
    private void escenaNoSeDaLaVuelta(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(10, 8, 12));
        g.fillRect(0, 0, w, h);
        skyline(g, w, h, (int) (h * 0.52), 1.0, new Color(16, 13, 20), 12);

        int suelo = (int) (h * 0.76);
        g.setColor(new Color(20, 18, 24));
        g.fillRect(0, suelo, w, h - suelo);

        // Mitsy, detrás, llamándolo
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(w * 0.24, suelo - 54);
        g.scale(1.3, 1.3);
        Sprites.drawNox(g, 0, 0, 3, frame, false, false, false, 0);
        g.setTransform(saved);

        g.setFont(new Font("Consolas", Font.ITALIC, 20));
        double vozAlpha = Math.max(0, Math.sin(t * Math.PI));
        g.setColor(new Color(220, 210, 220, (int) (200 * vozAlpha)));
        g.drawString("\"¿Nox?\"", (int) (w * 0.24), suelo - 90);

        // Nox, delante, de espaldas y quieto
        saved = g.getTransform();
        g.translate(w * 0.66, suelo - 70);
        g.scale(2.0, 2.0);
        Sprites.drawNox(g, 0, 0, 3, frame, false, false, false, 0);
        g.setTransform(saved);

        lluvia(g, w, h, 190, 0.65);
        viñeta(g, w, h, 0.85);
    }

    /** Se va, y la oscuridad se lo traga. */
    private void escenaSeAleja(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(8, 6, 10));
        g.fillRect(0, 0, w, h);

        int suelo = (int) (h * 0.76);
        g.setColor(new Color(16, 14, 20));
        g.fillRect(0, suelo, w, h - suelo);

        // Se aleja hacia el fondo: cada vez más pequeño y más oscuro
        double lejania = t;
        double escala = 2.2 - 1.7 * lejania;
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(w * 0.5, suelo - 60 - lejania * 40);
        g.scale(escala, escala);
        Sprites.drawNox(g, 0, 0, 3, frame, true, false, false, 0);
        g.setTransform(saved);

        // La oscuridad se cierra sobre él
        try {
            float r = (float) (h * (0.75 - 0.45 * lejania));
            g.setPaint(new RadialGradientPaint(
                new Point2D.Float(w / 2f, (float) (suelo - 60 - lejania * 40)), r,
                new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 250)}));
            g.fillRect(0, 0, w, h);
        } catch (Exception ignored) { }

        lluvia(g, w, h, 150, 0.5);
    }

    /** La ciudad tiene un dueño nuevo, y también tiene ojos verdes. */
    private void escenaNuevoDueno(Graphics2D g, int w, int h, double t) {
        g.setColor(new Color(7, 6, 9));
        g.fillRect(0, 0, w, h);
        skyline(g, w, h, (int) (h * 0.42), 0.7, new Color(13, 11, 17), 14);
        skyline(g, w, h, (int) (h * 0.62), 1.0, new Color(18, 15, 22), 10);

        // Nox en lo alto, enorme, dominando el plano
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(w * 0.5, h * 0.60);
        g.scale(3.0, 3.0);
        g.translate(-15, -15);
        Sprites.drawNox(g, 0, 0, 1, frame, false, false, false, 0);
        g.setTransform(saved);

        // Ojos por toda la ciudad: ya no es el único
        int aparecen = (int) (t * 16);
        for (int i = 0; i < aparecen; i++) {
            int ex = (i * 137) % w;
            int ey = (int) (h * 0.66) + (i * 53) % (int) (h * 0.28);
            int a = 90 + (int) (100 * Math.abs(Math.sin(frame * 0.05 + i)));
            g.setColor(new Color(150, 255, 60, a));
            g.fillOval(ex, ey, 5, 7);
            g.fillOval(ex + 11, ey, 5, 7);
        }

        lluvia(g, w, h, 210, 0.7);
        viñeta(g, w, h, 0.8);
    }

    /** Común a los dos finales: Colmillo derrotado en su salón. */
    private void escenaColmilloCaido(Graphics2D g, int w, int h, double t, boolean oscuro) {
        g.setColor(oscuro ? new Color(14, 4, 10) : new Color(16, 8, 12));
        g.fillRect(0, 0, w, h);

        // Suelo de mármol de la mansión
        int suelo = (int) (h * 0.62);
        int celda = 74;
        for (int i = 0; i < w / celda + 2; i++) {
            for (int j = 0; j < 5; j++) {
                boolean claro = (i + j) % 2 == 0;
                g.setColor(claro ? new Color(46, 42, 44) : new Color(24, 20, 24));
                g.fillRect(i * celda - 30, suelo + j * 42, celda, 42);
            }
        }

        // Colmillo, tirado
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(w * 0.36, suelo + 30);
        g.rotate(Math.PI / 2.2);
        Sprites.drawDog(g, 0, 0, 150, new Color(74, 14, 14), 0, false, true, 1, 0);
        g.setTransform(saved);

        // Nox de pie, frente a él
        saved = g.getTransform();
        g.translate(w * 0.62, suelo - 46);
        g.scale(1.8, 1.8);
        Sprites.drawNox(g, 0, 0, 2, frame, false, false, false, 0);
        g.setTransform(saved);

        // Un foco desde arriba
        try {
            g.setPaint(new RadialGradientPaint(
                new Point2D.Float((float) (w * 0.55), (float) (h * 0.25)), (float) (h * 0.85),
                new float[]{0f, 1f},
                new Color[]{new Color(255, 220, 190, oscuro ? 40 : 70), new Color(0, 0, 0, 0)}));
            g.fillRect(0, 0, w, h);
        } catch (Exception ignored) { }

        viñeta(g, w, h, oscuro ? 0.85 : 0.7);
    }

    // ------------------------------------------------------------------
    //  Piezas reutilizables
    // ------------------------------------------------------------------

    private void cieloNocturno(Graphics2D g, int w, int h) {
        g.setColor(new Color(9, 8, 14));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 60; i++) {
            int sx = (i * 173) % w;
            int sy = (i * 91) % (h / 2);
            int a = 40 + (int) (50 * Math.abs(Math.sin(frame * 0.02 + i)));
            g.setColor(new Color(180, 190, 220, a));
            g.fillRect(sx, sy, 2, 2);
        }
    }

    /** Degradado de cielo pintado línea a línea; t=0 es de noche y t=1 es de día. */
    private void cieloAmanecer(Graphics2D g, int w, int h, double t) {
        for (int y = 0; y < h; y++) {
            double k = y / (double) h;
            int r = (int) ((18 + 150 * t) * (1 - k) + 30 * k);
            int gg = (int) ((16 + 90 * t) * (1 - k) + 24 * k);
            int b = (int) ((26 + 60 * t) * (1 - k) + 32 * k);
            g.setColor(new Color(Math.min(255, r), Math.min(255, gg), Math.min(255, b)));
            g.drawLine(0, y, w, y);
        }
    }

    /**
     * Fila de edificios al fondo con ventanas encendidas. Se dibujan varias con
     * distinta altura y color para dar profundidad. Las alturas y las ventanas salen
     * del mismo hash que usa el juego, así que la silueta es siempre la misma.
     */
    private void skyline(Graphics2D g, int w, int h, int baseY, double scale, Color color, int count) {
        g.setColor(color);
        int ancho = w / count;
        for (int i = 0; i < count + 1; i++) {
            int alto = (int) ((90 + Sprites.hash(i, (int) (scale * 100)) % 210) * scale);
            int x = i * ancho - 10;
            g.fillRect(x, baseY - alto, ancho - 6, alto + (h - baseY));

            // Ventanas encendidas
            for (int vy = baseY - alto + 14; vy < baseY - 14; vy += 22) {
                for (int vx = x + 8; vx < x + ancho - 16; vx += 18) {
                    if (Sprites.hash(vx, vy) % 5 != 0) continue;
                    g.setColor(new Color(210, 170, 90, 60));
                    g.fillRect(vx, vy, 7, 9);
                    g.setColor(color);
                }
            }
        }
    }

    /** Lluvia de fondo. La cantidad y la fuerza cambian según el tono de la escena. */
    private void lluvia(Graphics2D g, int w, int h, int cantidad, double intensidad) {
        g.setColor(new Color(170, 190, 215, (int) (70 * intensidad)));
        for (int i = 0; i < cantidad; i++) {
            // Con multiplicaciones simples las gotas salían alineadas en diagonales
            // muy visibles; el hash las reparte de verdad.
            int semilla = Sprites.hash(i, 31);
            int velocidad = 16 + semilla % 14;
            int x = (semilla % (w + 200)) - 100 - (frame * velocidad / 4) % (w + 200);
            if (x < -100) x += w + 200;
            int y = ((semilla / 7) + frame * velocidad) % (h + 40) - 20;
            int largo = 12 + semilla % 9;
            g.drawLine(x, y, x - largo / 4, y + largo);
        }
    }

    /** Columnas de humo que suben, crecen y se desvanecen. Para la escena del incendio. */
    private void humo(Graphics2D g, int w, int h) {
        for (int i = 0; i < 18; i++) {
            double fase = (frame * 0.6 + i * 40) % 400;
            int x = (i * 97) % w;
            int y = (int) (h - fase);
            int size = (int) (40 + fase * 0.5);
            int a = (int) (60 * (1 - fase / 400));
            if (a <= 0) continue;
            g.setColor(new Color(40, 34, 32, a));
            g.fillOval(x - size / 2, y - size / 2, size, size);
        }
    }

    private void viñeta(Graphics2D g, int w, int h, double fuerza) {
        try {
            g.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h / 2f), (float) (h * 0.85),
                new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, (int) (245 * fuerza))}));
            g.fillRect(0, 0, w, h);
        } catch (Exception ignored) { }
    }

    /** Título grande, para el remate del prólogo y el de los finales. */
    public static void drawTitle(Graphics2D g, String texto, String subtitulo,
                                 int w, int h, Color color, int frame) {
        g.setFont(new Font("Consolas", Font.BOLD, 52));
        int tw = g.getFontMetrics().stringWidth(texto);
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(texto, w / 2 - tw / 2 + 3, h / 2 + 3);
        g.setColor(color);
        g.drawString(texto, w / 2 - tw / 2, h / 2);

        if (subtitulo != null && !subtitulo.isEmpty()) {
            g.setFont(new Font("Consolas", Font.PLAIN, 20));
            int sw = g.getFontMetrics().stringWidth(subtitulo);
            g.setColor(new Color(200, 200, 205));
            g.drawString(subtitulo, w / 2 - sw / 2, h / 2 + 44);
        }
    }
}
