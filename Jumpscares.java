import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.util.Random;

/**
 * Los sustos del juego.
 *
 * Cada uno se juega en tres tiempos —aviso, golpe y resaca— pero se ven y suenan
 * distintos. La variedad importa: un susto que ya has visto deja de asustar, así
 * que hay diez y no se repiten seguidos.
 *
 * Todos se pueden desactivar desde OPCIONES. Cuando están apagados, el juego ni
 * siquiera llega a llamar aquí: la cordura baja igual, pero no hay pantallazo.
 */
public final class Jumpscares {

    public static final int EMBESTIDA = 0;
    public static final int OJOS = 1;
    public static final int ESTATICA = 2;
    public static final int JAURIA = 3;
    public static final int REFLEJO = 4;
    public static final int GARRAS = 5;
    public static final int FAUCES = 6;
    public static final int SUSURRO = 7;
    public static final int COLMILLO = 8;
    public static final int SOMBRA = 9;

    public static final int COUNT = 10;

    private Jumpscares() { }

    /** Texto que aparece bajo el golpe. */
    public static String text(int type) {
        switch (type) {
            case OJOS:     return "TE ESTÁ MIRANDO";
            case ESTATICA: return "NO ESTÁS SOLO";
            case JAURIA:   return "SON DEMASIADOS";
            case REFLEJO:  return "¿ESO ERES TÚ?";
            case GARRAS:   return "¡DETRÁS DE TI!";
            case FAUCES:   return "¡CORRE!";
            case SUSURRO:  return "";
            case COLMILLO: return "COLMILLO TE VE";
            case SOMBRA:   return "ALGO TE SIGUE";
            default:       return "¡¡ TE ENCONTRÓ !!";
        }
    }

    /** Sonido del golpe. El del susurro es el silencio, por eso no tiene. */
    public static String impactSfx(int type) {
        switch (type) {
            case OJOS:
            case REFLEJO:  return "shriek";
            case GARRAS:   return "rip";
            case JAURIA:   return "howl";
            case SOMBRA:   return "breath";
            case SUSURRO:  return "whisper";
            default:       return "lunge";
        }
    }

    /** Elige un susto, sin repetir el anterior y respetando en qué barrio estamos. */
    public static int pick(Random rand, int level, int previous) {
        for (int intento = 0; intento < 24; intento++) {
            int t = rand.nextInt(COUNT);
            if (t == previous) continue;
            // Colmillo solo aparece cuando ya se le conoce
            if (t == COLMILLO && level < 4) continue;
            return t;
        }
        return EMBESTIDA;
    }

    // ==================================================================
    //  FASE 1 — EL AVISO
    // ==================================================================

    public static void drawDread(Graphics2D g, int type, double progress,
                                 double dirX, double dirY, int w, int h, int frame) {
        g.setColor(new Color(0, 0, 0, (int) (120 * progress)));
        g.fillRect(0, 0, w, h);

        int cx = (int) (w / 2 + dirX * w * 0.45);
        int cy = (int) (h / 2 + dirY * h * 0.45);

        try {
            float r = (float) (200 + 320 * progress);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r,
                new float[]{0f, 1f},
                new Color[]{new Color(0, 0, 0, (int) (215 * progress)), new Color(0, 0, 0, 0)}));
            g.fillRect(0, 0, w, h);
        } catch (Exception ignored) { }

        // El susurro no avisa con imagen: avisa apagándolo todo
        if (type == SUSURRO) {
            g.setColor(new Color(0, 0, 0, (int) (235 * progress)));
            g.fillRect(0, 0, w, h);
            return;
        }

        if (progress > 0.45) {
            double aparicion = (progress - 0.45) / 0.55;
            java.awt.Composite saved = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (float) (0.30 * aparicion)));

            if (type == JAURIA) {
                for (int i = 0; i < 5; i++) {
                    double a = Math.PI * 2 * i / 5.0 + frame * 0.01;
                    int size = (int) (70 + 60 * aparicion);
                    Sprites.drawDog(g, (int) (w / 2 + Math.cos(a) * w * 0.36) - size / 2,
                                    (int) (h / 2 + Math.sin(a) * h * 0.34) - size / 2,
                                    size, new Color(8, 6, 10), 2, false, false, 1, frame);
                }
            } else {
                int size = (int) (90 + 120 * aparicion);
                Sprites.drawDog(g, cx - size / 2, cy - size / 2, size,
                                new Color(8, 6, 10), 2, false, false, 1, frame);
            }
            g.setComposite(saved);
        }

        if (progress > 0.75) {
            int pulso = (int) (60 * Math.abs(Math.sin(frame * 0.35)));
            g.setColor(new Color(90, 0, 0, pulso));
            g.fillRect(0, 0, w, h);
        }
    }

    // ==================================================================
    //  FASE 2 — EL GOLPE
    // ==================================================================

    public static void drawImpact(Graphics2D g, int type, double growth,
                                  double dirX, double dirY, int w, int h,
                                  int frame, Random rand) {
        switch (type) {
            case OJOS:     ojos(g, growth, w, h, frame); break;
            case ESTATICA: estatica(g, growth, w, h, rand); break;
            case JAURIA:   jauria(g, growth, w, h, frame); break;
            case REFLEJO:  reflejo(g, growth, w, h, frame); break;
            case GARRAS:   garras(g, growth, w, h, rand); break;
            case FAUCES:   fauces(g, growth, w, h); break;
            case SUSURRO:  susurro(g, growth, w, h, frame); break;
            case COLMILLO: colmillo(g, growth, dirX, dirY, w, h, frame); break;
            case SOMBRA:   sombra(g, growth, w, h, frame); break;
            default:       embestida(g, growth, dirX, dirY, w, h, frame); break;
        }

        String texto = text(type);
        if (growth > 0.55 && !texto.isEmpty()) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, h - 96, w, 64);
            g.setFont(new Font("Consolas", Font.BOLD, 46));
            g.setColor(Color.WHITE);
            int tw = g.getFontMetrics().stringWidth(texto);
            g.drawString(texto, w / 2 - tw / 2, h - 52);
        }
    }

    /** El clásico: un perro enorme se abalanza sobre la cámara. */
    private static void embestida(Graphics2D g, double growth, double dirX, double dirY,
                                  int w, int h, int frame) {
        g.setColor(new Color(150, 0, 0, 240));
        g.fillRect(0, 0, w, h);
        int size = (int) (140 + 560 * growth);
        int px = w / 2 - size / 2 + (int) (dirX * 60 * (1 - growth));
        int py = h / 2 - size / 4 + (int) (dirY * 60 * (1 - growth));
        Sprites.drawDog(g, px, py, size, new Color(40, 22, 20), 0, false, true, 1, frame);
    }

    /** Dos ojos que se abren en la oscuridad y se te vienen encima. */
    private static void ojos(Graphics2D g, double growth, int w, int h, int frame) {
        g.setColor(new Color(0, 0, 0, 250));
        g.fillRect(0, 0, w, h);

        double apertura = Math.min(1, growth * 2.2);
        int sep = (int) (120 + 340 * growth);
        int rx = (int) (26 + 96 * growth);
        int ry = (int) (rx * apertura);
        int cy = h / 2 - 40;

        for (int lado = -1; lado <= 1; lado += 2) {
            int cx = w / 2 + lado * sep / 2;
            try {
                g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), rx * 2.6f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 40, 30, 130), new Color(255, 40, 30, 0)}));
                g.fillRect(cx - rx * 3, cy - rx * 3, rx * 6, rx * 6);
            } catch (Exception ignored) { }

            g.setColor(new Color(255, 60, 40));
            g.fillOval(cx - rx, cy - ry, rx * 2, ry * 2);
            g.setColor(Color.BLACK);
            int pupila = Math.max(2, rx / 4);
            g.fillOval(cx - pupila, cy - ry + ry / 4, pupila * 2, Math.max(3, ry * 3 / 2));
        }
    }

    /** Interferencia de televisión vieja con algo dentro del ruido. */
    private static void estatica(Graphics2D g, double growth, int w, int h, Random rand) {
        g.setColor(new Color(6, 6, 6, 244));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 90; i++) {
            g.setColor(new Color(255, 255, 255, 20 + rand.nextInt(80)));
            g.fillRect(rand.nextInt(w), rand.nextInt(h), 20 + rand.nextInt(180), 2 + rand.nextInt(7));
        }
        int size = (int) (200 + 420 * growth);
        java.awt.Composite saved = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(
            java.awt.AlphaComposite.SRC_OVER, (float) (0.55 + 0.4 * growth)));
        Sprites.drawDog(g, w / 2 - size / 2 + rand.nextInt(9) - 4,
                        h / 2 - size / 4 + rand.nextInt(9) - 4,
                        size, new Color(24, 24, 26), 2, false, true, 1, 0);
        g.setComposite(saved);
    }

    /** No es uno: son todos, y te rodean. */
    private static void jauria(Graphics2D g, double growth, int w, int h, int frame) {
        g.setColor(new Color(10, 0, 0, 246));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 7; i++) {
            double a = Math.PI * 2 * i / 7.0;
            double dist = (1 - growth) * 420 + 110;
            int size = (int) (110 + 130 * growth);
            int px = (int) (w / 2 + Math.cos(a) * dist) - size / 2;
            int py = (int) (h / 2 + Math.sin(a) * dist * 0.7) - size / 2;
            Sprites.drawDog(g, px, py, size, new Color(26, 14, 14), i % 6, false, true, 1, frame);
        }
    }

    /** El propio Nox devolviéndote la mirada, con los ojos que no son suyos. */
    private static void reflejo(Graphics2D g, double growth, int w, int h, int frame) {
        g.setColor(new Color(4, 6, 8, 248));
        g.fillRect(0, 0, w, h);

        int size = (int) (220 + 460 * growth);
        int cx = w / 2, cy = h / 2 - 20;

        // Cabeza de gato: la de Nox, pero mal
        g.setColor(new Color(16, 16, 22));
        g.fillOval(cx - size / 2, cy - size / 2, size, size);

        Polygon orejaI = new Polygon(
            new int[]{cx - size / 2 + 10, cx - size / 4, cx - size / 12},
            new int[]{cy - size / 3, cy - size, cy - size / 2 - 10}, 3);
        Polygon orejaD = new Polygon(
            new int[]{cx + size / 2 - 10, cx + size / 4, cx + size / 12},
            new int[]{cy - size / 3, cy - size, cy - size / 2 - 10}, 3);
        g.fillPolygon(orejaI);
        g.fillPolygon(orejaD);

        // Los ojos verdes de Nox, virados a rojo
        int eyeR = (int) (size * 0.13);
        int eyeY = cy - size / 10;
        Color iris = new Color((int) (150 + 105 * growth), (int) (255 - 195 * growth), 60);
        for (int lado = -1; lado <= 1; lado += 2) {
            int ex = cx + lado * size / 5;
            try {
                g.setPaint(new RadialGradientPaint(new Point2D.Float(ex, eyeY), eyeR * 3f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(iris.getRed(), iris.getGreen(), 60, 90),
                                new Color(iris.getRed(), iris.getGreen(), 60, 0)}));
                g.fillRect(ex - eyeR * 3, eyeY - eyeR * 3, eyeR * 6, eyeR * 6);
            } catch (Exception ignored) { }
            g.setColor(iris);
            g.fillOval(ex - eyeR, eyeY - (int) (eyeR * 1.35), eyeR * 2, (int) (eyeR * 2.7));
            g.setColor(Color.BLACK);
            g.fillOval(ex - eyeR / 4, eyeY - eyeR, eyeR / 2, eyeR * 2);
        }

        // Demasiadas bocas
        g.setColor(new Color(120, 20, 24));
        int bocaY = cy + size / 5;
        g.fillRoundRect(cx - size / 5, bocaY, size * 2 / 5, (int) (size * 0.12 * growth) + 4, 8, 8);
        g.setColor(new Color(240, 240, 235));
        for (int i = 0; i < 6; i++) {
            int tx = cx - size / 5 + 6 + i * (size * 2 / 5 - 12) / 5;
            g.fillPolygon(new int[]{tx, tx + 8, tx + 4},
                          new int[]{bocaY, bocaY, bocaY + (int) (16 * growth)}, 3);
        }

        // Bigotes desperdigados
        g.setColor(new Color(200, 200, 210, 120));
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 3; i++) {
            g.drawLine(cx - size / 4, bocaY - 10 + i * 8, cx - size / 2 - 30, bocaY - 30 + i * 16);
            g.drawLine(cx + size / 4, bocaY - 10 + i * 8, cx + size / 2 + 30, bocaY - 30 + i * 16);
        }
        g.setStroke(new BasicStroke(1f));
    }

    /** Zarpazos que rasgan la pantalla de lado a lado. */
    private static void garras(Graphics2D g, double growth, int w, int h, Random rand) {
        g.setColor(new Color(0, 0, 0, 240));
        g.fillRect(0, 0, w, h);

        int cortes = 4;
        for (int i = 0; i < cortes; i++) {
            double avance = Math.min(1, growth * 1.6 - i * 0.12);
            if (avance <= 0) continue;

            int x0 = -80 + i * 60;
            int y0 = (int) (h * 0.12) + i * 46;
            int largo = (int) (w * 1.4 * avance);
            int grosor = 16 + i * 5;

            // La carne de debajo del corte
            g.setColor(new Color(150, 20, 18));
            g.fillPolygon(new int[]{x0, x0 + largo, x0 + largo, x0},
                          new int[]{y0, y0 + (int) (h * 0.42), y0 + (int) (h * 0.42) + grosor, y0 + grosor}, 4);
            // El borde brillante del zarpazo
            g.setColor(new Color(255, 210, 200, (int) (200 * (1 - avance * 0.4))));
            g.setStroke(new BasicStroke(3f));
            g.drawLine(x0, y0, x0 + largo, y0 + (int) (h * 0.42));
            g.setStroke(new BasicStroke(1f));
        }

        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(180, 30, 25, 60 + rand.nextInt(120)));
            g.fillOval(rand.nextInt(w), rand.nextInt(h), 4 + rand.nextInt(10), 4 + rand.nextInt(10));
        }
    }

    /** Una boca que se abre hasta ocupar toda la pantalla. */
    private static void fauces(Graphics2D g, double growth, int w, int h) {
        g.setColor(new Color(28, 8, 8, 246));
        g.fillRect(0, 0, w, h);

        int abertura = (int) (h * 0.10 + h * 0.85 * growth);
        int cy = h / 2;

        g.setColor(new Color(90, 14, 18));
        g.fillOval(-w / 6, cy - abertura / 2, w + w / 3, abertura);
        g.setColor(new Color(40, 6, 10));
        g.fillOval(-w / 8, cy - abertura / 3, w + w / 4, abertura * 2 / 3);

        // Colmillos arriba y abajo
        g.setColor(new Color(238, 234, 224));
        int dientes = 9;
        for (int i = 0; i < dientes; i++) {
            int dx = i * w / dientes;
            int ancho = w / dientes - 8;
            int alto = (int) (abertura * (i % 2 == 0 ? 0.26 : 0.18));
            g.fillPolygon(new int[]{dx, dx + ancho, dx + ancho / 2},
                          new int[]{cy - abertura / 2, cy - abertura / 2, cy - abertura / 2 + alto}, 3);
            g.fillPolygon(new int[]{dx, dx + ancho, dx + ancho / 2},
                          new int[]{cy + abertura / 2, cy + abertura / 2, cy + abertura / 2 - alto}, 3);
        }

        // La lengua al fondo
        g.setColor(new Color(140, 40, 50));
        g.fillOval(w / 3, cy + abertura / 8, w / 3, abertura / 4);
    }

    /** El susto silencioso: no se ve nada, y eso es peor. */
    private static void susurro(Graphics2D g, double growth, int w, int h, int frame) {
        g.setColor(new Color(0, 0, 0, 252));
        g.fillRect(0, 0, w, h);

        // Solo una silueta apenas insinuada, que se va sin llegar a verse
        double visible = Math.sin(growth * Math.PI);
        java.awt.Composite saved = g.getComposite();
        g.setComposite(java.awt.AlphaComposite.getInstance(
            java.awt.AlphaComposite.SRC_OVER, (float) (0.13 * visible)));
        Sprites.drawDog(g, w / 2 - 180, h / 2 - 120, 360,
                        new Color(60, 60, 70), 2, false, false, 1, frame);
        g.setComposite(saved);

        g.setFont(new Font("Consolas", Font.ITALIC, 22));
        g.setColor(new Color(150, 150, 160, (int) (170 * visible)));
        String s = "...detrás...";
        int tw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, w / 2 - tw / 2, h / 2 + 210);
    }

    /** La cara del jefe, para cuando ya sabes quién es. */
    private static void colmillo(Graphics2D g, double growth, double dirX, double dirY,
                                 int w, int h, int frame) {
        g.setColor(new Color(40, 0, 8, 246));
        g.fillRect(0, 0, w, h);
        int size = (int) (200 + 620 * growth);
        Sprites.drawDog(g, w / 2 - size / 2, h / 2 - size / 4, size,
                        new Color(96, 16, 16), 0, false, true, 1, frame);

        g.setColor(new Color(120, 0, 0, (int) (70 * growth)));
        g.fillRect(0, 0, w, h);
    }

    /** Una figura alta y quieta que de golpe está encima. */
    private static void sombra(Graphics2D g, double growth, int w, int h, int frame) {
        g.setColor(new Color(0, 0, 0, 250));
        g.fillRect(0, 0, w, h);

        int alto = (int) (h * (0.35 + 0.75 * growth));
        int ancho = (int) (alto * 0.42);
        int cx = w / 2;
        int base = h - 20;

        g.setColor(new Color(10, 10, 13));
        g.fillRoundRect(cx - ancho / 2, base - alto, ancho, alto, ancho / 3, ancho / 3);
        g.fillOval(cx - ancho / 3, base - alto - ancho / 3, ancho * 2 / 3, ancho * 2 / 3);

        // Orejas puntiagudas apenas visibles contra el negro
        g.fillPolygon(new int[]{cx - ancho / 3, cx - ancho / 6, cx - ancho / 12},
                      new int[]{base - alto - ancho / 6, base - alto - ancho / 2, base - alto - ancho / 5}, 3);
        g.fillPolygon(new int[]{cx + ancho / 3, cx + ancho / 6, cx + ancho / 12},
                      new int[]{base - alto - ancho / 6, base - alto - ancho / 2, base - alto - ancho / 5}, 3);

        int brillo = (int) (200 * Math.min(1, growth * 2));
        g.setColor(new Color(230, 220, 90, brillo));
        int ojoY = base - alto - ancho / 8;
        g.fillOval(cx - ancho / 5, ojoY, ancho / 9, ancho / 16);
        g.fillOval(cx + ancho / 10, ojoY, ancho / 9, ancho / 16);
    }

    // ==================================================================
    //  FASE 3 — LA RESACA
    // ==================================================================

    public static void drawAfter(Graphics2D g, int type, double k, int w, int h, int frame) {
        g.setColor(new Color(0, 0, 0, (int) (230 * k)));
        g.fillRect(0, 0, w, h);

        // La imagen se queda un momento quemada en la retina
        if (k > 0.5) {
            java.awt.Composite saved = g.getComposite();
            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (float) ((k - 0.5) * 0.5)));
            if (type == OJOS || type == REFLEJO) {
                g.setColor(new Color(255, 80, 60));
                g.fillOval(w / 2 - 180, h / 2 - 60, 90, 60);
                g.fillOval(w / 2 + 90, h / 2 - 60, 90, 60);
            } else {
                Sprites.drawDog(g, w / 2 - 260, h / 2 - 260, 520,
                                new Color(70, 20, 20), 0, false, true, 1, frame);
            }
            g.setComposite(saved);
        }

        double latido = Math.abs(Math.sin(frame * 0.28));
        g.setColor(new Color(120, 0, 0, (int) (90 * k * latido)));
        g.fillRect(0, 0, w, h);
    }
}
