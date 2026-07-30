import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Todo lo que se ve en pantalla se dibuja con Java 2D: no hay ni una imagen en disco.
 *
 * Las casillas del escenario se pre-dibujan una sola vez en pequeñas imágenes en memoria
 * (8 variantes por material) y luego se estampan. Eso permite meter muchísimo más detalle
 * —ladrillos, corrugado, azulejos rotos, vetas de mármol— sin recalcularlo 60 veces por segundo.
 */
public final class Sprites {

    private Sprites() { }

    private static final Stroke THIN = new BasicStroke(1f);
    private static final Stroke MED = new BasicStroke(2f);
    public static final int VARIANTS = 8;

    private static final Map<Long, BufferedImage> CACHE = new HashMap<>();

    /** Ruido determinista por casilla: la misma casilla se ve siempre igual. */
    public static int hash(int x, int y) {
        int h = (x * 73856093) ^ (y * 19349663);
        h ^= (h >>> 13);
        h *= 1274126177;
        h ^= (h >>> 16);
        return h & 0x7FFFFFFF;
    }

    private static Color shade(Color c, double f) {
        return new Color(
            (int) Math.max(0, Math.min(255, c.getRed() * f)),
            (int) Math.max(0, Math.min(255, c.getGreen() * f)),
            (int) Math.max(0, Math.min(255, c.getBlue() * f)));
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    // ==================================================================
    //  PALETAS POR BARRIO
    // ==================================================================

    /** floor, floorAlt, wall, wallDark, wallLight, accent */
    private static final Color[][] PALETTE = {
        // 0 BASURERO — tierra, bolsas de basura, cartón y chatarra
        {new Color(58, 50, 40), new Color(44, 38, 30), new Color(30, 29, 33),
         new Color(18, 18, 21), new Color(107, 83, 52), new Color(150, 96, 42)},
        // 1 MERCADO — adoquín, madera y toldos de rayas
        {new Color(72, 66, 56), new Color(56, 50, 42), new Color(122, 82, 48),
         new Color(74, 48, 26), new Color(216, 207, 192), new Color(168, 50, 50)},
        // 2 METRO — concreto, azulejo blanco sucio y tuberías
        {new Color(60, 64, 72), new Color(46, 50, 58), new Color(126, 134, 142),
         new Color(58, 64, 72), new Color(176, 182, 188), new Color(196, 168, 40)},
        // 3 PUERTO — muelle de concreto y contenedores de carga
        {new Color(64, 72, 74), new Color(50, 58, 60), new Color(138, 58, 40),
         new Color(64, 30, 22), new Color(180, 186, 190), new Color(214, 176, 40)},
        // 4 AZOTEAS — alquitrán, grava, ladrillo y tanques de agua
        {new Color(48, 44, 54), new Color(36, 33, 42), new Color(96, 66, 78),
         new Color(52, 36, 44), new Color(150, 150, 162), new Color(120, 96, 60)},
        // 5 MANSIÓN — mármol apagado por el polvo, papel tapiz y molduras doradas
        {new Color(112, 106, 100), new Color(34, 26, 33), new Color(104, 42, 74),
         new Color(56, 22, 42), new Color(198, 158, 84), new Color(150, 26, 30)},
    };

    private static Color pal(int theme, int i) {
        return PALETTE[Math.max(0, Math.min(PALETTE.length - 1, theme))][i];
    }

    // ==================================================================
    //  CACHÉ DE CASILLAS
    // ==================================================================

    public static BufferedImage tile(int type, int theme, int variant, int size) {
        long key = ((long) type << 44) | ((long) theme << 36) | ((long) variant << 20) | size;
        BufferedImage img = CACHE.get(key);
        if (img == null) {
            img = renderTile(type, theme, variant, size);
            CACHE.put(key, img);
        }
        return img;
    }

    private static BufferedImage renderTile(int type, int theme, int variant, int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Random r = new Random(variant * 7919L + type * 104729L + theme * 1299709L);

        switch (type) {
            case Tiles.WALL:  wall(g, theme, s, r);  break;
            case Tiles.PROP:  floor(g, theme, s, r); prop(g, theme, s, r); break;
            case Tiles.DECOR: decor(g, theme, s, r); break;
            case Tiles.WATER: water(g, s, r);        break;
            case Tiles.VOID:  emptiness(g, s, r);    break;
            default:          floor(g, theme, s, r);
        }

        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------
    //  SUELOS
    // ------------------------------------------------------------------

    private static void floor(Graphics2D g, int theme, int s, Random r) {
        Color base = pal(theme, 0), alt = pal(theme, 1);

        switch (theme) {
            case 1: { // MERCADO — adoquines
                g.setColor(alt);
                g.fillRect(0, 0, s, s);
                int cell = s / 4;
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 4; j++) {
                        int ox = (j % 2 == 0) ? 0 : cell / 2;
                        g.setColor(shade(base, 0.85 + r.nextDouble() * 0.4));
                        g.fillRoundRect(i * cell + ox - cell / 2, j * cell + 1,
                                        cell - 2, cell - 2, 4, 4);
                    }
                }
                break;
            }
            case 2: { // METRO — losas de andén
                g.setColor(base);
                g.fillRect(0, 0, s, s);
                g.setColor(shade(base, 0.72));
                g.drawLine(0, s / 2, s, s / 2);
                g.drawLine(s / 2, 0, s / 2, s);
                for (int i = 0; i < 30; i++) {
                    g.setColor(shade(base, 0.8 + r.nextDouble() * 0.5));
                    g.fillRect(r.nextInt(s), r.nextInt(s), 2, 2);
                }
                break;
            }
            case 3: { // PUERTO — concreto con juntas de dilatación
                g.setColor(base);
                g.fillRect(0, 0, s, s);
                g.setColor(shade(base, 0.7));
                g.fillRect(0, s - 3, s, 2);
                g.fillRect(s - 3, 0, 2, s);
                g.setColor(new Color(20, 22, 24, 60));
                for (int i = 0; i < 5; i++) {
                    g.fillOval(r.nextInt(s), r.nextInt(s), 6 + r.nextInt(12), 4 + r.nextInt(8));
                }
                break;
            }
            case 4: { // AZOTEAS — alquitrán con grava
                g.setColor(base);
                g.fillRect(0, 0, s, s);
                for (int i = 0; i < 70; i++) {
                    g.setColor(shade(base, 1.1 + r.nextDouble() * 0.7));
                    g.fillRect(r.nextInt(s), r.nextInt(s), 2, 2);
                }
                g.setColor(alpha(alt, 120));
                g.fillOval(r.nextInt(s / 2), r.nextInt(s / 2), s / 2, s / 3);
                break;
            }
            case 5: { // MANSIÓN — mármol a cuadros con vetas
                int half = s / 2;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        g.setColor((i + j) % 2 == 0 ? base : alt);
                        g.fillRect(i * half, j * half, half, half);
                    }
                }
                g.setStroke(THIN);
                for (int i = 0; i < 6; i++) {
                    boolean light = r.nextBoolean();
                    g.setColor(light ? new Color(255, 255, 255, 40) : new Color(0, 0, 0, 45));
                    int x1 = r.nextInt(s), y1 = r.nextInt(s);
                    g.drawLine(x1, y1, x1 + r.nextInt(20) - 10, y1 + r.nextInt(20) - 10);
                }
                break;
            }
            default: { // BASURERO — tierra con desperdicio suelto
                g.setColor(base);
                g.fillRect(0, 0, s, s);
                for (int i = 0; i < 6; i++) {
                    g.setColor(alpha(alt, 90 + r.nextInt(80)));
                    g.fillOval(r.nextInt(s), r.nextInt(s), 8 + r.nextInt(16), 5 + r.nextInt(10));
                }
                for (int i = 0; i < 5; i++) {   // papeles y latas
                    g.setColor(new Color(190, 185, 170, 90 + r.nextInt(60)));
                    g.fillRect(r.nextInt(s - 6), r.nextInt(s - 4), 3 + r.nextInt(5), 2 + r.nextInt(3));
                }
            }
        }

        g.setColor(new Color(0, 0, 0, 40));
        g.drawRect(0, 0, s - 1, s - 1);
    }

    // ------------------------------------------------------------------
    //  MUROS Y ESTRUCTURAS
    // ------------------------------------------------------------------

    private static void wall(Graphics2D g, int theme, int s, Random r) {
        Color w = pal(theme, 2), dark = pal(theme, 3), light = pal(theme, 4), accent = pal(theme, 5);

        switch (theme) {
            case 0: { // BASURERO — montaña de bolsas, cartón y chatarra
                g.setColor(new Color(24, 22, 20));
                g.fillRect(0, 0, s, s);
                g.setColor(shade(light, 0.9));   // caja de cartón al fondo
                g.fillRect(4 + r.nextInt(8), 6 + r.nextInt(8), s / 2, s / 2);
                g.setColor(shade(light, 0.6));
                g.drawRect(4, 6, s / 2, s / 2);
                for (int i = 0; i < 5; i++) {    // bolsas de basura
                    int bx = r.nextInt(s - 20), by = r.nextInt(s - 18);
                    g.setColor(shade(w, 0.8 + r.nextDouble() * 0.7));
                    g.fillOval(bx, by, 18 + r.nextInt(14), 16 + r.nextInt(12));
                    g.setColor(new Color(255, 255, 255, 22));
                    g.fillOval(bx + 4, by + 3, 6, 4);
                }
                g.setColor(shade(accent, 0.75));  // chatarra oxidada asomando
                g.fillRect(s - 22, s - 16, 18, 5);
                break;
            }
            case 1: { // MERCADO — puesto de madera con toldo de rayas
                g.setColor(dark);
                g.fillRect(0, 0, s, s);
                g.setColor(w);                      // mostrador
                g.fillRect(2, s / 3, s - 4, s - s / 3 - 2);
                g.setColor(shade(w, 0.7));
                for (int i = 1; i < 5; i++) g.drawLine(2, s / 3 + i * 8, s - 2, s / 3 + i * 8);
                for (int i = 0; i < 6; i++) {       // toldo a rayas
                    g.setColor(i % 2 == 0 ? accent : light);
                    g.fillRect(i * (s / 6), 0, s / 6, s / 3);
                }
                g.setColor(new Color(0, 0, 0, 70));
                g.fillRect(0, s / 3 - 3, s, 4);
                g.setColor(shade(accent, 1.2));     // fruta en el mostrador
                for (int i = 0; i < 4; i++) {
                    g.fillOval(6 + i * 12, s / 2, 8, 8);
                }
                break;
            }
            case 2: { // METRO — azulejo blanco sucio con tuberías
                g.setColor(dark);
                g.fillRect(0, 0, s, s);
                int tw = s / 4, th = s / 6;
                for (int j = 0; j < 6; j++) {
                    for (int i = 0; i < 5; i++) {
                        int ox = (j % 2 == 0) ? 0 : -tw / 2;
                        if (r.nextInt(14) == 0) continue;   // azulejo caído
                        g.setColor(shade(w, 0.85 + r.nextDouble() * 0.35));
                        g.fillRect(i * tw + ox + 1, j * th + 1, tw - 2, th - 2);
                    }
                }
                g.setColor(new Color(40, 55, 35, 70));      // humedad
                g.fillOval(-6, s / 2, s / 2, s / 2);
                g.setColor(shade(light, 0.6));              // tubería vertical
                g.fillRect(s - 14, 0, 8, s);
                g.setColor(shade(light, 0.85));
                g.fillRect(s - 14, 0, 3, s);
                g.setColor(shade(light, 0.45));
                g.fillRect(s - 16, s / 3, 12, 6);
                break;
            }
            case 3: { // PUERTO — contenedor de carga corrugado
                Color[] paints = {
                    new Color(138, 58, 40), new Color(38, 92, 96),
                    new Color(40, 66, 116), new Color(150, 118, 34), new Color(70, 92, 58)};
                Color body = paints[r.nextInt(paints.length)];
                g.setColor(body);
                g.fillRect(0, 0, s, s);
                for (int i = 0; i < s; i += 6) {            // corrugado vertical
                    g.setColor(shade(body, 1.22));
                    g.fillRect(i, 0, 2, s);
                    g.setColor(shade(body, 0.72));
                    g.fillRect(i + 3, 0, 2, s);
                }
                g.setColor(shade(body, 0.5));               // marco
                g.fillRect(0, 0, s, 4);
                g.fillRect(0, s - 4, s, 4);
                g.setColor(new Color(70, 45, 30, 120));     // óxido
                g.fillOval(-4, s - 20, 20, 16);
                g.setColor(new Color(235, 235, 230, 170));  // código estarcido
                g.setFont(new Font("Consolas", Font.BOLD, 10));
                g.drawString("MSK " + (100 + r.nextInt(899)), 6, s / 2);
                break;
            }
            case 4: { // AZOTEAS — caseta de ladrillo con ventana
                g.setColor(w);
                g.fillRect(0, 0, s, s);
                g.setColor(shade(w, 0.72));
                for (int j = 0; j < 8; j++) {               // ladrillos
                    int ox = (j % 2 == 0) ? 0 : -6;
                    for (int i = 0; i < 5; i++) g.drawRect(i * 13 + ox, j * 8, 13, 8);
                }
                if (r.nextBoolean()) {                      // ventana iluminada
                    g.setColor(new Color(206, 168, 96, 200));
                    g.fillRect(s / 4, s / 3, s / 2, s / 3);
                    g.setColor(dark);
                    g.setStroke(MED);
                    g.drawRect(s / 4, s / 3, s / 2, s / 3);
                    g.drawLine(s / 2, s / 3, s / 2, s / 3 + s / 3);
                    g.setStroke(THIN);
                }
                g.setColor(shade(light, 0.75));             // remate del pretil
                g.fillRect(0, 0, s, 5);
                break;
            }
            default: { // MANSIÓN — papel tapiz con moldura y cuadro
                g.setColor(w);
                g.fillRect(0, 0, s, s);
                g.setColor(shade(w, 1.28));                 // damasco
                for (int j = 0; j < 5; j++) {
                    for (int i = 0; i < 4; i++) {
                        int ox = (j % 2 == 0) ? 0 : 7;
                        g.fillOval(i * 15 + ox, j * 13, 6, 9);
                    }
                }
                g.setColor(dark);                           // zócalo
                g.fillRect(0, s - 12, s, 12);
                g.setColor(light);                          // moldura dorada
                g.fillRect(0, s - 14, s, 3);
                if (r.nextInt(3) == 0) {                    // retrato de un perro
                    g.setColor(light);
                    g.fillRect(s / 4 - 2, 8, s / 2 + 4, s / 2 + 4);
                    g.setColor(new Color(28, 20, 26));
                    g.fillRect(s / 4, 10, s / 2, s / 2);
                    g.setColor(new Color(90, 50, 40));
                    g.fillOval(s / 4 + 5, 16, s / 2 - 10, s / 2 - 14);
                    g.setColor(new Color(220, 60, 40));
                    g.fillOval(s / 4 + 10, 24, 4, 4);
                    g.fillOval(s / 2 + 2, 24, 4, 4);
                }
            }
        }

        g.setColor(new Color(0, 0, 0, 90));
        g.drawRect(0, 0, s - 1, s - 1);
    }

    // ------------------------------------------------------------------
    //  OBSTÁCULOS PEQUEÑOS
    // ------------------------------------------------------------------

    private static void prop(Graphics2D g, int theme, int s, Random r) {
        int cx = s / 2;

        g.setColor(new Color(0, 0, 0, 110));
        g.fillOval(8, s - 18, s - 16, 12);

        switch (theme) {
            case 1: { // MERCADO — cajas de fruta apiladas
                for (int i = 0; i < 2; i++) {
                    int by = s - 22 - i * 16;
                    g.setColor(shade(pal(theme, 2), 1.0 - i * 0.12));
                    g.fillRect(9, by, s - 18, 16);
                    g.setColor(shade(pal(theme, 3), 1.0));
                    g.drawRect(9, by, s - 18, 16);
                    g.drawLine(9, by + 8, s - 9, by + 8);
                }
                g.setColor(new Color(190, 60, 50));
                for (int i = 0; i < 3; i++) g.fillOval(12 + i * 12, s - 44, 9, 9);
                break;
            }
            case 2: { // METRO — banco de andén
                g.setColor(new Color(80, 60, 40));
                g.fillRect(6, s / 2, s - 12, 8);
                g.fillRect(6, s / 2 - 12, s - 12, 6);
                g.setColor(new Color(50, 52, 58));
                g.fillRect(10, s / 2 + 8, 5, 14);
                g.fillRect(s - 15, s / 2 + 8, 5, 14);
                break;
            }
            case 3: { // PUERTO — caja de carga con flejes
                g.setColor(new Color(122, 88, 48));
                g.fillRect(8, s / 3, s - 16, s / 2);
                g.setColor(new Color(80, 56, 28));
                g.drawRect(8, s / 3, s - 16, s / 2);
                g.drawLine(8, s / 3, s - 8, s / 3 + s / 2);
                g.drawLine(s - 8, s / 3, 8, s / 3 + s / 2);
                g.setColor(new Color(60, 60, 66));
                g.fillRect(cx - 3, s / 3, 5, s / 2);
                break;
            }
            case 4: { // AZOTEAS — tanque de agua sobre patas
                g.setColor(new Color(96, 70, 44));
                g.fillRoundRect(10, 10, s - 20, s - 26, 8, 8);
                g.setColor(new Color(66, 46, 28));
                for (int i = 1; i < 4; i++) g.drawLine(10, 10 + i * 9, s - 10, 10 + i * 9);
                g.setColor(new Color(58, 58, 64));
                g.fillRect(13, s - 18, 4, 12);
                g.fillRect(s - 17, s - 18, 4, 12);
                break;
            }
            case 5: { // MANSIÓN — sillón señorial
                g.setColor(new Color(96, 26, 34));
                g.fillRoundRect(8, s / 3, s - 16, s / 2, 10, 10);
                g.setColor(new Color(126, 40, 48));
                g.fillRoundRect(12, s / 3 - 12, s - 24, 20, 8, 8);
                g.setColor(pal(theme, 4));
                g.drawRoundRect(8, s / 3, s - 16, s / 2, 10, 10);
                break;
            }
            default: { // BASURERO — bidón oxidado
                g.setColor(new Color(78, 58, 34));
                g.fillRoundRect(12, 12, s - 24, s - 20, 6, 6);
                g.setColor(new Color(108, 78, 42));
                g.fillRoundRect(12, 12, s - 24, 8, 6, 6);
                g.setColor(new Color(52, 38, 22));
                g.drawRoundRect(12, 12, s - 24, s - 20, 6, 6);
                g.drawLine(12, s / 2, s - 12, s / 2);
                g.setColor(new Color(140, 92, 40, 130));
                g.fillOval(16, s - 22, 12, 9);
            }
        }
    }

    // ------------------------------------------------------------------
    //  SUELO DECORADO (se puede pisar)
    // ------------------------------------------------------------------

    private static void decor(Graphics2D g, int theme, int s, Random r) {
        floor(g, theme, s, r);

        switch (theme) {
            case 2: { // METRO — vías del tren
                g.setColor(new Color(46, 34, 24));
                for (int i = 0; i < 5; i++) g.fillRect(0, i * 12 + 2, s, 7);
                g.setColor(new Color(122, 124, 130));
                g.fillRect(s / 4 - 2, 0, 5, s);
                g.fillRect(3 * s / 4 - 2, 0, 5, s);
                g.setColor(new Color(180, 184, 190));
                g.fillRect(s / 4 - 2, 0, 2, s);
                g.fillRect(3 * s / 4 - 2, 0, 2, s);
                break;
            }
            case 3: { // PUERTO — marcas de carga
                g.setColor(new Color(214, 176, 40, 170));
                for (int i = -s; i < s * 2; i += 14) {
                    g.fillPolygon(new int[]{i, i + 7, i + 14, i + 7},
                                  new int[]{s / 2, s / 2 - 7, s / 2, s / 2 + 7}, 4);
                }
                break;
            }
            case 4: { // AZOTEAS — tablón de madera sobre el vacío
                g.setColor(new Color(112, 82, 48));
                g.fillRect(0, 6, s, s - 12);
                g.setColor(new Color(78, 56, 32));
                for (int i = 0; i < 4; i++) g.drawLine(0, 6 + i * 12, s, 6 + i * 12);
                g.setColor(new Color(150, 116, 70));
                g.fillRect(0, 8, s, 2);
                g.setColor(new Color(40, 40, 44));
                g.fillOval(5, 12, 4, 4);
                g.fillOval(s - 10, s - 18, 4, 4);
                break;
            }
            case 5: { // MANSIÓN — alfombra roja con ribete dorado
                g.setColor(new Color(122, 22, 28));
                g.fillRect(4, 0, s - 8, s);
                g.setColor(new Color(150, 32, 36));
                g.fillRect(10, 0, s - 20, s);
                g.setColor(pal(theme, 4));
                g.fillRect(6, 0, 3, s);
                g.fillRect(s - 9, 0, 3, s);
                break;
            }
            case 1: { // MERCADO — manta tendida en el suelo
                g.setColor(new Color(120, 70, 40, 160));
                g.fillRect(4, 6, s - 8, s - 12);
                g.setColor(new Color(180, 140, 60, 120));
                for (int i = 0; i < 4; i++) g.drawLine(4, 8 + i * 12, s - 4, 8 + i * 12);
                break;
            }
            default: { // BASURERO — charco de aceite
                g.setColor(new Color(14, 12, 18, 210));
                g.fillOval(3, 8, s - 6, s - 20);
                g.setColor(new Color(70, 40, 120, 90));
                g.fillOval(10, 14, s / 2, s / 4);
                g.setColor(new Color(30, 90, 90, 70));
                g.fillOval(s / 2, s / 2, s / 3, s / 5);
            }
        }
    }

    // ------------------------------------------------------------------
    //  AGUA Y VACÍO
    // ------------------------------------------------------------------

    private static void water(Graphics2D g, int s, Random r) {
        g.setColor(new Color(12, 26, 36));
        g.fillRect(0, 0, s, s);
        g.setColor(new Color(18, 40, 54));
        for (int i = 0; i < 4; i++) {
            g.fillOval(-8 + r.nextInt(s), r.nextInt(s), 20 + r.nextInt(24), 6 + r.nextInt(6));
        }
        g.setColor(new Color(60, 110, 140, 60));
        for (int i = 0; i < 3; i++) {
            int y = r.nextInt(s);
            g.drawLine(0, y, s, y + r.nextInt(5) - 2);
        }
    }

    private static void emptiness(Graphics2D g, int s, Random r) {
        g.setColor(new Color(6, 6, 10));
        g.fillRect(0, 0, s, s);
        // Luces de la calle, veinte pisos más abajo
        for (int i = 0; i < 4; i++) {
            int lx = r.nextInt(s), ly = r.nextInt(s);
            Color c = r.nextBoolean() ? new Color(200, 160, 70) : new Color(90, 140, 200);
            g.setColor(alpha(c, 40 + r.nextInt(50)));
            g.fillOval(lx, ly, 3, 3);
            g.setColor(alpha(c, 16));
            g.fillOval(lx - 3, ly - 3, 9, 9);
        }
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, s, 6);
    }

    // ------------------------------------------------------------------
    //  Elementos animados (no se pueden cachear)
    // ------------------------------------------------------------------

    /** Reflejo que se mueve sobre el agua. */
    public static void waterShimmer(Graphics2D g, int px, int py, int s, int tx, int ty, int frame) {
        int h = hash(tx, ty);
        double t = frame * 0.05 + h % 100;
        g.setColor(new Color(120, 180, 220, 45));
        for (int i = 0; i < 3; i++) {
            int y = (int) (py + (Math.sin(t + i * 2.1) * 0.5 + 0.5) * (s - 6)) + 3;
            g.drawLine(px + 4, y, px + s - 4, y + 1);
        }
    }

    public static void drawCoin(Graphics2D g, int px, int py, int tile, int frame) {
        double spin = Math.abs(Math.sin(frame * 0.06 + px * 0.01));
        int w = (int) (6 + 14 * spin);
        int cx = px + tile / 2;
        int cy = py + tile / 2 + (int) (Math.sin(frame * 0.05 + py * 0.02) * 3);

        g.setColor(new Color(255, 200, 60, 55));
        g.fill(new Ellipse2D.Double(cx - 14, cy - 14, 28, 28));
        g.setColor(new Color(230, 170, 40));
        g.fill(new Ellipse2D.Double(cx - w / 2.0, cy - 10, w, 20));
        g.setColor(new Color(255, 225, 130));
        g.fill(new Ellipse2D.Double(cx - w / 2.0 + 2, cy - 8, Math.max(1, w - 4), 16));
        if (w > 12) {
            g.setColor(new Color(180, 130, 20));
            g.setFont(new Font("Consolas", Font.BOLD, 12));
            g.drawString("$", cx - 3, cy + 5);
        }
    }

    public static void drawStore(Graphics2D g, int tx, int ty, int tile, int frame) {
        int px = tx * tile, py = ty * tile;
        g.setColor(new Color(32, 8, 32));
        g.fillRect(px, py, tile, tile);

        int pulse = 160 + (int) (80 * Math.sin(frame * 0.08));
        Color neon = new Color(255, 0, 140, Math.max(60, Math.min(255, pulse)));
        g.setColor(new Color(255, 0, 140, 30));
        g.fillRect(px - 4, py - 4, tile + 8, tile + 8);
        g.setColor(neon);
        g.setStroke(MED);
        g.drawRect(px + 3, py + 3, tile - 6, tile - 6);
        g.setStroke(THIN);

        for (int i = 0; i < tile; i += 10) {
            g.setColor(i % 20 == 0 ? new Color(140, 20, 70) : new Color(60, 10, 40));
            g.fillRect(px + i, py + 4, 10, 10);
        }
        g.setColor(neon);
        g.setFont(new Font("Consolas", Font.BOLD, 11));
        g.drawString("ARMAS", px + 6, py + tile - 10);
    }

    public static void drawExit(Graphics2D g, int tx, int ty, int tile, boolean unlocked, int frame) {
        int px = tx * tile, py = ty * tile;

        if (unlocked) {
            int pulse = 120 + (int) (100 * Math.sin(frame * 0.07));
            g.setColor(new Color(0, 255, 120, Math.max(40, Math.min(200, pulse)) / 3));
            g.fillRect(px - 6, py - 6, tile + 12, tile + 12);
            g.setColor(new Color(15, 60, 30));
            g.fillRect(px, py, tile, tile);
            g.setColor(new Color(0, 255, 120));
            g.setStroke(MED);
            g.drawRect(px + 5, py + 5, tile - 10, tile - 10);
            g.fillPolygon(new int[]{px + tile / 2 - 8, px + tile / 2 + 8, px + tile / 2},
                          new int[]{py + tile / 2 + 6, py + tile / 2 + 6, py + tile / 2 - 8}, 3);
            g.setStroke(THIN);
        } else {
            g.setColor(new Color(45, 12, 12));
            g.fillRect(px, py, tile, tile);
            g.setColor(new Color(200, 40, 40));
            g.setStroke(MED);
            g.drawRect(px + 5, py + 5, tile - 10, tile - 10);
            g.drawLine(px + 12, py + 12, px + tile - 12, py + tile - 12);
            g.drawLine(px + tile - 12, py + 12, px + 12, py + tile - 12);
            g.setStroke(THIN);
            g.setColor(new Color(230, 190, 60));
            g.fillRect(px + tile / 2 - 6, py + tile / 2 - 2, 12, 10);
            g.drawArc(px + tile / 2 - 5, py + tile / 2 - 9, 10, 10, 0, 180);
        }
    }

    /** Halo de luz para farolas, neones y fuego. Se dibuja después de la oscuridad. */
    public static void drawLight(Graphics2D g, int cx, int cy, float radius, Color color, int intensity) {
        if (radius <= 1) return;
        try {
            g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), radius,
                new float[]{0f, 0.45f, 1f},
                new Color[]{alpha(color, intensity), alpha(color, intensity / 3), alpha(color, 0)}));
            g.fillOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));
        } catch (Exception ignored) { }
    }

    // ==================================================================
    //  NOX  (el gato protagonista)
    // ==================================================================

    public static void drawNox(Graphics2D g, int x, int y, int facing, int frame,
                               boolean moving, boolean attacking, boolean hurt, int armor) {

        AffineTransform saved = g.getTransform();
        g.translate(x + 15, y + 15);
        g.scale(1.45, 1.45);
        g.translate(-(x + 15), -(y + 15));

        drawNoxBody(g, x, y, facing, frame, moving, hurt, armor);

        g.setTransform(saved);
    }

    private static void drawNoxBody(Graphics2D g, int x, int y, int facing, int frame,
                                    boolean moving, boolean hurt, int armor) {

        double walk = moving ? Math.sin(frame * 0.35) : 0;
        int bob = (int) Math.round(moving ? Math.abs(walk) * 2 : 0);
        int by = y - bob;

        g.setColor(new Color(0, 0, 0, 110));
        g.fill(new Ellipse2D.Double(x - 3, y + 28, 36, 11));

        Color fur = hurt ? new Color(120, 40, 40) : new Color(22, 22, 30);
        Color furDark = hurt ? new Color(80, 25, 25) : new Color(12, 12, 18);
        Color rim = hurt ? new Color(220, 80, 80) : new Color(70, 90, 130);

        // Cola
        double tailSway = Math.sin(frame * 0.16) * 9;
        int tailBaseX = x + 15, tailBaseY = by + 24;
        int tailTipX, tailTipY, tailCtrlX, tailCtrlY;
        if (facing == 3) {
            tailBaseX = x + 3;
            tailCtrlX = x - 14; tailCtrlY = by + 14;
            tailTipX = x - 10; tailTipY = (int) (by - 2 + tailSway);
        } else if (facing == 2) {
            tailBaseX = x + 27;
            tailCtrlX = x + 44; tailCtrlY = by + 14;
            tailTipX = x + 40; tailTipY = (int) (by - 2 + tailSway);
        } else {
            tailCtrlX = (int) (x + 30 + tailSway); tailCtrlY = by + 26;
            tailTipX = (int) (x + 26 + tailSway * 1.5); tailTipY = by + 6;
        }
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(furDark);
        g.draw(new QuadCurve2D.Double(tailBaseX, tailBaseY, tailCtrlX, tailCtrlY, tailTipX, tailTipY));
        g.setStroke(THIN);

        // Patas
        int legSwing = (int) Math.round(walk * 3);
        g.setColor(furDark);
        g.fillRoundRect(x + 1, by + 26 + legSwing, 8, 10, 4, 4);
        g.fillRoundRect(x + 21, by + 26 - legSwing, 8, 10, 4, 4);
        g.fillRoundRect(x + 3, by + 20 - legSwing, 7, 9, 4, 4);
        g.fillRoundRect(x + 20, by + 20 + legSwing, 7, 9, 4, 4);

        // Cuerpo
        g.setColor(fur);
        g.fillRoundRect(x + 1, by + 10, 28, 22, 14, 14);
        g.setColor(shade(fur, 1.5));
        g.fillRoundRect(x + 4, by + 12, 22, 8, 10, 10);

        // Armadura comprada en el Mercado Negro
        if (armor > 0) {
            Color[] armorColors = {null, new Color(120, 80, 40), new Color(90, 92, 100), new Color(180, 186, 200)};
            Color ac = armorColors[Math.min(3, armor)];
            g.setColor(ac);
            g.fillRoundRect(x + 2, by + 14, 26, 12, 8, 8);
            g.setColor(shade(ac, 0.6));
            g.drawRoundRect(x + 2, by + 14, 26, 12, 8, 8);
            if (armor >= 2) {
                g.setColor(shade(ac, 1.4));
                g.fillRect(x + 13, by + 14, 4, 12);
            }
            if (armor >= 3) {
                g.setColor(new Color(220, 230, 245, 180));
                g.fillOval(x + 5, by + 16, 4, 4);
                g.fillOval(x + 21, by + 16, 4, 4);
            }
        }

        // Cabeza
        int hx = x + 3, hy = by - 6;
        if (facing == 2) hx -= 3;
        if (facing == 3) hx += 3;
        if (facing == 0) hy -= 2;

        Polygon earL = new Polygon(new int[]{hx + 2, hx + 5, hx + 11}, new int[]{hy + 8, hy - 6, hy + 5}, 3);
        Polygon earR = new Polygon(new int[]{hx + 22, hx + 19, hx + 13}, new int[]{hy + 8, hy - 6, hy + 5}, 3);
        g.setColor(furDark);
        g.fill(earL);
        g.fill(earR);
        g.setColor(new Color(90, 45, 55));
        g.fillPolygon(new int[]{hx + 4, hx + 6, hx + 9}, new int[]{hy + 6, hy - 2, hy + 4}, 3);
        g.fillPolygon(new int[]{hx + 20, hx + 18, hx + 15}, new int[]{hy + 6, hy - 2, hy + 4}, 3);

        g.setColor(fur);
        g.fill(new Ellipse2D.Double(hx, hy + 2, 24, 20));

        g.setColor(new Color(200, 200, 210, 130));
        int wy = hy + 15;
        g.drawLine(hx + 4, wy, hx - 6, wy - 3);
        g.drawLine(hx + 4, wy + 2, hx - 6, wy + 4);
        g.drawLine(hx + 20, wy, hx + 30, wy - 3);
        g.drawLine(hx + 20, wy + 2, hx + 30, wy + 4);

        g.setColor(shade(fur, 1.7));
        g.fill(new Ellipse2D.Double(hx + 7, hy + 12, 10, 7));
        g.setColor(new Color(190, 90, 110));
        g.fillPolygon(new int[]{hx + 10, hx + 14, hx + 12}, new int[]{hy + 13, hy + 13, hy + 16}, 3);

        int ex = hx + 5, ey = hy + 7;
        int eyeDx = facing == 2 ? -2 : facing == 3 ? 2 : 0;
        int eyeDy = facing == 0 ? -2 : facing == 1 ? 1 : 0;

        try {
            Point2D gc = new Point2D.Float(hx + 12, ey + 3);
            g.setPaint(new RadialGradientPaint(gc, 18f, new float[]{0f, 1f},
                new Color[]{new Color(120, 255, 60, 70), new Color(120, 255, 60, 0)}));
            g.fillRect(hx - 8, ey - 12, 40, 30);
        } catch (Exception ignored) { }

        g.setColor(new Color(150, 255, 60));
        g.fill(new Ellipse2D.Double(ex + eyeDx, ey + eyeDy, 6, 8));
        g.fill(new Ellipse2D.Double(ex + 9 + eyeDx, ey + eyeDy, 6, 8));
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(ex + 2 + eyeDx, ey + 1 + eyeDy, 2, 6));
        g.fill(new Ellipse2D.Double(ex + 11 + eyeDx, ey + 1 + eyeDy, 2, 6));
        g.setColor(new Color(255, 255, 255, 200));
        g.fillRect(ex + 1 + eyeDx, ey + 1 + eyeDy, 1, 2);
        g.fillRect(ex + 10 + eyeDx, ey + 1 + eyeDy, 1, 2);

        g.setColor(new Color(180, 150, 150, 190));
        g.drawLine(hx + 16, hy + 2, hx + 20, hy + 12);

        g.setColor(alpha(rim, 90));
        g.setStroke(THIN);
        g.drawRoundRect(x + 1, by + 10, 28, 22, 14, 14);
        g.draw(new Ellipse2D.Double(hx, hy + 2, 24, 20));
    }

    public static void drawClawSlash(Graphics2D g, int cx, int cy, int facing, int power, int frame) {
        int radius = power == 1 ? 115 : 62;
        int baseAngle = facing == 3 ? 0 : facing == 0 ? 90 : facing == 2 ? 180 : 270;
        Color c = power == 1 ? new Color(180, 220, 255) : new Color(255, 120, 120);

        for (int i = 0; i < 3; i++) {
            int r = radius - i * 9;
            int a = 210 - i * 55;
            g.setColor(alpha(c, Math.max(0, a)));
            g.setStroke(new BasicStroke(4f - i, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(cx - r / 2, cy - r / 2, r, r, baseAngle - 45 + i * 6, 90);
        }
        g.setStroke(THIN);
    }

    // ==================================================================
    //  PERROS
    // ==================================================================

    public static void drawDog(Graphics2D g, int x, int y, int size, Color body, int breed,
                               boolean hasGun, boolean isBoss, int facing, int frame) {
        drawDog(g, x, y, size, body, breed, hasGun, isBoss, facing, frame, false, false);
    }

    /**
     * @param shield  lleva escudo (hay que golpearlo por la espalda)
     * @param stunned aturdido por el silbato
     */
    public static void drawDog(Graphics2D g, int x, int y, int size, Color body, int breed,
                               boolean hasGun, boolean isBoss, int facing, int frame,
                               boolean shield, boolean stunned) {

        double walk = Math.sin(frame * 0.25 + x * 0.01);
        Color dark = shade(body, 0.55);
        Color light = shade(body, 1.35);

        g.setColor(new Color(0, 0, 0, 110));
        g.fill(new Ellipse2D.Double(x - 2, y + size - 6, size + 6, size * 0.30));

        double wag = Math.sin(frame * 0.3) * (size * 0.18);
        g.setStroke(new BasicStroke(Math.max(3f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(dark);
        int tx = facing == 3 ? x - 2 : x + size + 2;
        g.draw(new QuadCurve2D.Double(
            facing == 3 ? x + 4 : x + size - 4, y + size * 0.55,
            tx, y + size * 0.25,
            tx + (facing == 3 ? -4 : 4), y + wag));
        g.setStroke(THIN);

        int legW = Math.max(5, size / 6);
        int legH = Math.max(7, size / 4);
        int swing = (int) Math.round(walk * (size / 14.0));
        g.setColor(dark);
        g.fillRoundRect(x + 2, y + size - legH + swing, legW, legH, 3, 3);
        g.fillRoundRect(x + size - legW - 2, y + size - legH - swing, legW, legH, 3, 3);

        g.setColor(body);
        g.fillRoundRect(x, y + size / 5, size, (int) (size * 0.72), size / 4, size / 4);
        g.setColor(light);
        g.fillRoundRect(x + size / 6, y + size / 4, (int) (size * 0.66), size / 5, size / 5, size / 5);

        int headSize = (int) (size * (breed == 0 ? 0.62 : breed == 5 ? 0.66 : 0.55));
        int hx = x + (size - headSize) / 2;
        int hy = y - headSize / 4;
        if (facing == 2) hx -= size / 8;
        if (facing == 3) hx += size / 8;

        g.setColor(dark);
        if (breed == 2 || breed == 4 || breed == 5) {
            g.fillPolygon(new int[]{hx + 1, hx + headSize / 5, hx + headSize / 2},
                          new int[]{hy + headSize / 3, hy - headSize / 2, hy + headSize / 4}, 3);
            g.fillPolygon(new int[]{hx + headSize - 1, hx + headSize - headSize / 5, hx + headSize / 2},
                          new int[]{hy + headSize / 3, hy - headSize / 2, hy + headSize / 4}, 3);
        } else {
            g.fillRoundRect(hx - headSize / 8, hy + headSize / 8, headSize / 3, (int) (headSize * 0.55), 6, 6);
            g.fillRoundRect(hx + headSize - headSize / 4, hy + headSize / 8, headSize / 3, (int) (headSize * 0.55), 6, 6);
        }

        g.setColor(shade(body, 0.85));
        g.fillRoundRect(hx, hy, headSize, headSize, headSize / 3, headSize / 3);

        int snoutW = (int) (headSize * (breed == 0 ? 0.55 : 0.45));
        int snoutH = (int) (headSize * 0.38);
        int sx = hx + (headSize - snoutW) / 2;
        int sy = hy + headSize - snoutH / 2;
        g.setColor(shade(body, 0.7));
        g.fillRoundRect(sx, sy, snoutW, snoutH, 6, 6);
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(sx + snoutW / 2.0 - 3, sy + 2, 6, 4));

        g.setColor(new Color(240, 240, 230));
        int fangY = sy + snoutH - 2;
        g.fillPolygon(new int[]{sx + 3, sx + 7, sx + 5}, new int[]{fangY, fangY, fangY + 6}, 3);
        g.fillPolygon(new int[]{sx + snoutW - 7, sx + snoutW - 3, sx + snoutW - 5},
                      new int[]{fangY, fangY, fangY + 6}, 3);

        int eyeR = Math.max(5, headSize / 6);
        int eyeY = hy + headSize / 3;
        Color eyeColor = stunned ? new Color(170, 170, 190)
                       : isBoss ? new Color(255, 40, 40) : new Color(255, 210, 40);
        try {
            Point2D gc = new Point2D.Float(hx + headSize / 2f, eyeY + eyeR / 2f);
            g.setPaint(new RadialGradientPaint(gc, headSize * 0.9f, new float[]{0f, 1f},
                new Color[]{alpha(eyeColor, isBoss ? 90 : 55), alpha(eyeColor, 0)}));
            g.fillRect(hx - headSize, eyeY - headSize, headSize * 3, headSize * 2);
        } catch (Exception ignored) { }

        g.setColor(eyeColor);
        g.fill(new Ellipse2D.Double(hx + headSize / 5.0, eyeY, eyeR, eyeR));
        g.fill(new Ellipse2D.Double(hx + headSize - headSize / 5.0 - eyeR, eyeY, eyeR, eyeR));
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(hx + headSize / 5.0 + eyeR / 3.0, eyeY + eyeR / 4.0, eyeR / 2.5, eyeR / 2.0));
        g.fill(new Ellipse2D.Double(hx + headSize - headSize / 5.0 - eyeR + eyeR / 3.0, eyeY + eyeR / 4.0,
                                    eyeR / 2.5, eyeR / 2.0));
        g.setStroke(MED);
        g.drawLine(hx + headSize / 6, eyeY - 3, (int) (hx + headSize / 2.6), eyeY + 1);
        g.drawLine(hx + headSize - headSize / 6, eyeY - 3, (int) (hx + headSize - headSize / 2.6), eyeY + 1);
        g.setStroke(THIN);

        g.setColor(isBoss ? new Color(140, 20, 20) : new Color(70, 45, 25));
        g.fillRect(hx - 2, hy + headSize - 3, headSize + 4, Math.max(4, size / 12));
        if (isBoss) {
            g.setColor(new Color(210, 210, 220));
            for (int i = 0; i < 4; i++) {
                int px = hx + 2 + i * (headSize / 4);
                g.fillPolygon(new int[]{px, px + 5, px + 2},
                              new int[]{hy + headSize - 3, hy + headSize - 3, hy + headSize - 9}, 3);
            }
            g.setColor(new Color(200, 90, 90, 200));
            g.setStroke(MED);
            g.drawLine(hx + headSize - 6, hy + 3, hx + headSize / 2, hy + headSize / 2);
            g.setStroke(THIN);
        }

        if (hasGun) {
            g.setColor(new Color(60, 60, 65));
            int gy = y + size / 2;
            if (facing == 2) {
                g.fillRect(x - size / 3, gy, size / 3, Math.max(4, size / 9));
                g.setColor(new Color(120, 120, 130));
                g.fillRect(x - size / 3, gy - 2, size / 8, Math.max(4, size / 9) + 4);
            } else {
                g.fillRect(x + size, gy, size / 3, Math.max(4, size / 9));
                g.setColor(new Color(120, 120, 130));
                g.fillRect(x + size + size / 4, gy - 2, size / 8, Math.max(4, size / 9) + 4);
            }
        }

        // Escudo: tapa el lado hacia el que mira
        if (shield) {
            int sw = Math.max(8, size / 4), sh = (int) (size * 0.95);
            int px = facing == 2 ? x - sw : facing == 3 ? x + size : x + size / 2 - sh / 2;
            int py = facing == 0 ? y - sw / 2 : facing == 1 ? y + size - sw / 2 : y;
            int rw = (facing == 0 || facing == 1) ? sh : sw;
            int rh = (facing == 0 || facing == 1) ? sw : sh;
            g.setColor(new Color(84, 86, 94));
            g.fillRoundRect(px, py, rw, rh, 6, 6);
            g.setColor(new Color(150, 154, 164));
            g.drawRoundRect(px, py, rw, rh, 6, 6);
            g.setColor(new Color(180, 60, 40));
            g.fillOval(px + rw / 2 - 4, py + rh / 2 - 4, 8, 8);
        }

        // Estrellas de aturdido
        if (stunned) {
            g.setColor(new Color(255, 240, 120));
            for (int i = 0; i < 3; i++) {
                double a = frame * 0.15 + i * 2.09;
                int sxx = (int) (x + size / 2 + Math.cos(a) * size * 0.5);
                int syy = (int) (y - size / 4 + Math.sin(a) * 6);
                g.fillOval(sxx, syy, 6, 6);
            }
        }
    }
}
