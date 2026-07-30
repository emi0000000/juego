import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Chispas, humo, brasas, salpicaduras y números de daño.
 * Es lo que hace que un golpe se sienta como un golpe y no como restar un número.
 */
public final class Particles {

    private static final int MAX = 700;

    private static final int SPARK = 0;
    private static final int SMOKE = 1;
    private static final int EMBER = 2;
    private static final int DEBRIS = 3;
    private static final int RING = 4;

    private final List<P> parts = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final Random rand = new Random();

    private static final class P {
        double x, y, vx, vy, size, gravity, drag = 0.96;
        double life, maxLife;
        Color color;
        int type;
    }

    private static final class Text {
        double x, y, vy;
        double life, maxLife;
        String value;
        Color color;
        int size;
    }

    public void clear() {
        parts.clear();
        texts.clear();
    }

    public int count() { return parts.size(); }

    // ------------------------------------------------------------------
    // Emisores
    // ------------------------------------------------------------------

    private void add(double x, double y, double vx, double vy, double size,
                     double life, Color color, int type, double gravity) {
        if (parts.size() >= MAX) return;
        P p = new P();
        p.x = x; p.y = y; p.vx = vx; p.vy = vy;
        p.size = size; p.life = life; p.maxLife = life;
        p.color = color; p.type = type; p.gravity = gravity;
        parts.add(p);
    }

    /** Chispazo al conectar un golpe. */
    public void hit(double x, double y, Color color) {
        for (int i = 0; i < 9; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double sp = 1.5 + rand.nextDouble() * 4;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                2 + rand.nextDouble() * 2, 14 + rand.nextInt(10), color, SPARK, 0.12);
        }
    }

    /** El perro cae: polvo, tierra y un anillo de impacto. */
    public void death(double x, double y, Color color) {
        for (int i = 0; i < 22; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double sp = 1 + rand.nextDouble() * 4.5;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp - 1,
                3 + rand.nextDouble() * 3, 26 + rand.nextInt(20), color, DEBRIS, 0.22);
        }
        for (int i = 0; i < 10; i++) {
            add(x + rand.nextInt(20) - 10, y + rand.nextInt(20) - 10,
                (rand.nextDouble() - 0.5) * 0.8, -0.5 - rand.nextDouble(),
                8 + rand.nextDouble() * 10, 40 + rand.nextInt(25),
                new Color(90, 85, 80), SMOKE, -0.01);
        }
        add(x, y, 0, 0, 10, 18, new Color(255, 200, 120), RING, 0);
    }

    /** Explosión del molotov o del cachorro kamikaze. */
    public void explosion(double x, double y, double power) {
        int n = (int) (30 * power);
        for (int i = 0; i < n; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double sp = 2 + rand.nextDouble() * 7 * power;
            Color c = rand.nextBoolean() ? new Color(255, 170, 40) : new Color(255, 90, 30);
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp,
                3 + rand.nextDouble() * 4, 20 + rand.nextInt(22), c, SPARK, 0.10);
        }
        for (int i = 0; i < 16; i++) {
            add(x + rand.nextInt(40) - 20, y + rand.nextInt(40) - 20,
                (rand.nextDouble() - 0.5) * 1.2, -0.8 - rand.nextDouble() * 1.2,
                14 + rand.nextDouble() * 16, 55 + rand.nextInt(30),
                new Color(60, 56, 52), SMOKE, -0.015);
        }
        add(x, y, 0, 0, 20, 24, new Color(255, 210, 130), RING, 0);
    }

    /** Brasas que suben de un bidón encendido. */
    public void ember(double x, double y) {
        add(x + rand.nextInt(14) - 7, y, (rand.nextDouble() - 0.5) * 0.6,
            -0.7 - rand.nextDouble() * 0.9, 2 + rand.nextDouble() * 2,
            40 + rand.nextInt(40), new Color(255, 150 + rand.nextInt(80), 50), EMBER, -0.006);
    }

    /** Salpicadura de la lluvia al tocar el suelo. */
    public void splash(double x, double y) {
        for (int i = 0; i < 3; i++) {
            double a = -Math.PI / 2 + (rand.nextDouble() - 0.5) * 1.6;
            double sp = 0.6 + rand.nextDouble() * 1.2;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 1.5,
                8 + rand.nextInt(6), new Color(160, 190, 220), SPARK, 0.18);
        }
    }

    /** Fogonazo al disparar. */
    public void muzzle(double x, double y, double angle, Color color) {
        for (int i = 0; i < 6; i++) {
            double a = angle + (rand.nextDouble() - 0.5) * 0.7;
            double sp = 2 + rand.nextDouble() * 3;
            add(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 2 + rand.nextDouble() * 2,
                8 + rand.nextInt(6), color, SPARK, 0.02);
        }
    }

    /** Polvo bajo las patas de Nox al correr. */
    public void step(double x, double y) {
        add(x, y, (rand.nextDouble() - 0.5) * 0.5, -0.15,
            3 + rand.nextDouble() * 3, 16 + rand.nextInt(10),
            new Color(120, 115, 105), SMOKE, -0.005);
    }

    /** Número flotante de daño o de dinero. */
    public void text(double x, double y, String value, Color color, int size) {
        if (texts.size() > 40) texts.remove(0);
        Text t = new Text();
        t.x = x; t.y = y; t.vy = -1.1;
        t.life = 42; t.maxLife = 42;
        t.value = value; t.color = color; t.size = size;
        texts.add(t);
    }

    // ------------------------------------------------------------------
    // Simulación y dibujo
    // ------------------------------------------------------------------

    public void update() {
        Iterator<P> it = parts.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.x += p.vx;
            p.y += p.vy;
            p.vy += p.gravity;
            p.vx *= p.drag;
            p.vy *= p.drag;
            if (--p.life <= 0) it.remove();
        }

        Iterator<Text> tit = texts.iterator();
        while (tit.hasNext()) {
            Text t = tit.next();
            t.y += t.vy;
            t.vy *= 0.94;
            if (--t.life <= 0) tit.remove();
        }
    }

    /** Se dibuja dentro del mundo, con la cámara ya aplicada. */
    public void draw(Graphics2D g) {
        for (P p : parts) {
            double k = p.life / p.maxLife;
            int a = (int) Math.max(0, Math.min(255, k * 255));

            switch (p.type) {
                case SMOKE: {
                    double grow = p.size * (2.2 - k);
                    g.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) (a * 0.35)));
                    g.fillOval((int) (p.x - grow / 2), (int) (p.y - grow / 2), (int) grow, (int) grow);
                    break;
                }
                case RING: {
                    double r = p.size * (1 + (1 - k) * 5);
                    g.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) (a * 0.7)));
                    g.setStroke(new java.awt.BasicStroke((float) Math.max(1, 3 * k)));
                    g.drawOval((int) (p.x - r), (int) (p.y - r), (int) (r * 2), (int) (r * 2));
                    g.setStroke(new java.awt.BasicStroke(1f));
                    break;
                }
                case EMBER: {
                    g.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) (a * 0.5)));
                    g.fillOval((int) (p.x - p.size * 1.6), (int) (p.y - p.size * 1.6),
                               (int) (p.size * 3.2), (int) (p.size * 3.2));
                    g.setColor(new Color(255, 240, 200, a));
                    g.fillOval((int) p.x, (int) p.y, (int) p.size, (int) p.size);
                    break;
                }
                case DEBRIS: {
                    g.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
                    g.fillRect((int) p.x, (int) p.y, (int) p.size, (int) p.size);
                    break;
                }
                default: {
                    g.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int) (a * 0.4)));
                    g.fillOval((int) (p.x - p.size), (int) (p.y - p.size), (int) (p.size * 3), (int) (p.size * 3));
                    g.setColor(new Color(255, 250, 230, a));
                    g.fillOval((int) p.x, (int) p.y, (int) p.size, (int) p.size);
                }
            }
        }

        for (Text t : texts) {
            double k = t.life / t.maxLife;
            int a = (int) Math.max(0, Math.min(255, k * 255));
            g.setFont(new Font("Consolas", Font.BOLD, t.size));
            g.setColor(new Color(0, 0, 0, (int) (a * 0.7)));
            g.drawString(t.value, (int) t.x + 1, (int) t.y + 1);
            g.setColor(new Color(t.color.getRed(), t.color.getGreen(), t.color.getBlue(), a));
            g.drawString(t.value, (int) t.x, (int) t.y);
        }
    }
}
