import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;

/**
 * Todos los personajes y el escenario se dibujan con formas de Java 2D.
 * No se cargan imágenes desde disco: así el juego se ve igual en cualquier
 * computadora sin tener que copiar una carpeta de sprites junto al .java.
 */
public final class Sprites {

    private Sprites() { }

    private static final Stroke THIN = new BasicStroke(1f);
    private static final Stroke MED = new BasicStroke(2f);

    /** Ruido determinista por casilla: la misma casilla se ve siempre igual. */
    public static int hash(int x, int y) {
        int h = (x * 73856093) ^ (y * 19349663);
        h ^= (h >>> 13);
        h *= 1274126177;
        h ^= (h >>> 16);
        return h & 0x7FFFFFFF;
    }

    private static Color shade(Color c, double factor) {
        int r = (int) Math.max(0, Math.min(255, c.getRed() * factor));
        int g = (int) Math.max(0, Math.min(255, c.getGreen() * factor));
        int b = (int) Math.max(0, Math.min(255, c.getBlue() * factor));
        return new Color(r, g, b);
    }

    // ==================================================================
    //  NOX  (el gato protagonista)
    // ==================================================================

    /**
     * @param x,y     esquina superior izquierda de la caja de colisión (30x30)
     * @param facing  0 arriba, 1 abajo, 2 izquierda, 3 derecha
     * @param hurt    parpadea en rojo cuando acaba de recibir daño
     */
    public static void drawNox(Graphics2D g, int x, int y, int facing, int frame,
                               boolean moving, boolean attacking, boolean hurt) {

        // Nox se dibuja más grande que su caja de colisión (30x30) para que destaque
        // entre los perros sin volverse difícil de mover por los callejones.
        java.awt.geom.AffineTransform saved = g.getTransform();
        g.translate(x + 15, y + 15);
        g.scale(1.45, 1.45);
        g.translate(-(x + 15), -(y + 15));

        drawNoxBody(g, x, y, facing, frame, moving, hurt);

        g.setTransform(saved);
    }

    private static void drawNoxBody(Graphics2D g, int x, int y, int facing, int frame,
                                    boolean moving, boolean hurt) {

        double walk = moving ? Math.sin(frame * 0.35) : 0;
        int bob = (int) Math.round(moving ? Math.abs(walk) * 2 : 0);
        int by = y - bob;

        // Sombra en el suelo
        g.setColor(new Color(0, 0, 0, 110));
        g.fill(new Ellipse2D.Double(x - 3, y + 28, 36, 11));

        Color fur = hurt ? new Color(120, 40, 40) : new Color(22, 22, 30);
        Color furDark = hurt ? new Color(80, 25, 25) : new Color(12, 12, 18);
        Color rim = hurt ? new Color(220, 80, 80) : new Color(70, 90, 130);

        // Cola: se mueve al caminar
        double tailSway = Math.sin(frame * 0.16) * 9;
        int tailBaseX = x + 15, tailBaseY = by + 24;
        int tailTipX, tailTipY, tailCtrlX, tailCtrlY;
        if (facing == 3) {        // mirando a la derecha, cola atrás (izquierda)
            tailBaseX = x + 3;
            tailCtrlX = x - 14; tailCtrlY = by + 14;
            tailTipX = x - 10; tailTipY = (int) (by - 2 + tailSway);
        } else if (facing == 2) { // mirando a la izquierda
            tailBaseX = x + 27;
            tailCtrlX = x + 44; tailCtrlY = by + 14;
            tailTipX = x + 40; tailTipY = (int) (by - 2 + tailSway);
        } else {                  // arriba / abajo
            tailCtrlX = (int) (x + 30 + tailSway); tailCtrlY = by + 26;
            tailTipX = (int) (x + 26 + tailSway * 1.5); tailTipY = by + 6;
        }
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
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

        // Cabeza
        int hx = x + 3, hy = by - 6;
        if (facing == 2) hx -= 3;
        if (facing == 3) hx += 3;
        if (facing == 0) hy -= 2;

        // Orejas
        Polygon earL = new Polygon(
            new int[]{hx + 2, hx + 5, hx + 11},
            new int[]{hy + 8, hy - 6, hy + 5}, 3);
        Polygon earR = new Polygon(
            new int[]{hx + 22, hx + 19, hx + 13},
            new int[]{hy + 8, hy - 6, hy + 5}, 3);
        g.setColor(furDark);
        g.fill(earL);
        g.fill(earR);
        g.setColor(new Color(90, 45, 55));
        g.fillPolygon(new int[]{hx + 4, hx + 6, hx + 9}, new int[]{hy + 6, hy - 2, hy + 4}, 3);
        g.fillPolygon(new int[]{hx + 20, hx + 18, hx + 15}, new int[]{hy + 6, hy - 2, hy + 4}, 3);

        g.setColor(fur);
        g.fill(new Ellipse2D.Double(hx, hy + 2, 24, 20));

        // Bigotes
        g.setColor(new Color(200, 200, 210, 130));
        int wy = hy + 15;
        g.drawLine(hx + 4, wy, hx - 6, wy - 3);
        g.drawLine(hx + 4, wy + 2, hx - 6, wy + 4);
        g.drawLine(hx + 20, wy, hx + 30, wy - 3);
        g.drawLine(hx + 20, wy + 2, hx + 30, wy + 4);

        // Hocico y nariz
        g.setColor(shade(fur, 1.7));
        g.fill(new Ellipse2D.Double(hx + 7, hy + 12, 10, 7));
        g.setColor(new Color(190, 90, 110));
        g.fillPolygon(new int[]{hx + 10, hx + 14, hx + 12}, new int[]{hy + 13, hy + 13, hy + 16}, 3);

        // Ojos: verdes y brillantes, con resplandor
        int ex = hx + 5, ey = hy + 7;
        int eyeDx = facing == 2 ? -2 : facing == 3 ? 2 : 0;
        int eyeDy = facing == 0 ? -2 : facing == 1 ? 1 : 0;

        try {
            Point2D gc = new Point2D.Float(hx + 12, ey + 3);
            g.setPaint(new RadialGradientPaint(gc, 18f,
                new float[]{0f, 1f},
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

        // Cicatriz sobre el ojo derecho: recuerdo de la noche del incendio
        g.setColor(new Color(180, 150, 150, 190));
        g.drawLine(hx + 16, hy + 2, hx + 20, hy + 12);

        // Contorno tenue para que no se pierda en la oscuridad
        g.setColor(new Color(rim.getRed(), rim.getGreen(), rim.getBlue(), 90));
        g.setStroke(THIN);
        g.drawRoundRect(x + 1, by + 10, 28, 22, 14, 14);
        g.draw(new Ellipse2D.Double(hx, hy + 2, 24, 20));
    }

    /** Arco de zarpazo. power 0 = garras normales, 1 = garras de acero. */
    public static void drawClawSlash(Graphics2D g, int cx, int cy, int facing, int power, int frame) {
        int radius = power == 1 ? 115 : 62;
        int baseAngle = facing == 3 ? 0 : facing == 0 ? 90 : facing == 2 ? 180 : 270;
        Color c = power == 1 ? new Color(180, 220, 255) : new Color(255, 120, 120);

        for (int i = 0; i < 3; i++) {
            int r = radius - i * 9;
            int alpha = 210 - i * 55;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, alpha)));
            g.setStroke(new BasicStroke(4f - i, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(cx - r / 2, cy - r / 2, r, r, baseAngle - 45 + i * 6, 90);
        }
        g.setStroke(THIN);
    }

    // ==================================================================
    //  PERROS  (pandilleros y jefes)
    // ==================================================================

    /**
     * @param breed 0 bulldog, 1 pit bull, 2 pastor alemán, 3 gran danés,
     *              4 dóberman, 5 Don Colmillo
     */
    public static void drawDog(Graphics2D g, int x, int y, int size, Color body, int breed,
                               boolean hasGun, boolean isBoss, int facing, int frame) {

        double walk = Math.sin(frame * 0.25 + x * 0.01);
        Color dark = shade(body, 0.55);
        Color light = shade(body, 1.35);

        g.setColor(new Color(0, 0, 0, 110));
        g.fill(new Ellipse2D.Double(x - 2, y + size - 6, size + 6, size * 0.30));

        // Cola
        double wag = Math.sin(frame * 0.3) * (size * 0.18);
        g.setStroke(new BasicStroke(Math.max(3f, size / 12f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(dark);
        int tx = facing == 3 ? x - 2 : x + size + 2;
        g.draw(new QuadCurve2D.Double(
            facing == 3 ? x + 4 : x + size - 4, y + size * 0.55,
            tx, y + size * 0.25,
            tx + (facing == 3 ? -4 : 4), y + wag));
        g.setStroke(THIN);

        // Patas
        int legW = Math.max(5, size / 6);
        int legH = Math.max(7, size / 4);
        int swing = (int) Math.round(walk * (size / 14.0));
        g.setColor(dark);
        g.fillRoundRect(x + 2, y + size - legH + swing, legW, legH, 3, 3);
        g.fillRoundRect(x + size - legW - 2, y + size - legH - swing, legW, legH, 3, 3);

        // Cuerpo
        g.setColor(body);
        g.fillRoundRect(x, y + size / 5, size, (int) (size * 0.72), size / 4, size / 4);
        g.setColor(light);
        g.fillRoundRect(x + size / 6, y + size / 4, (int) (size * 0.66), size / 5, size / 5, size / 5);

        // Cabeza
        int headSize = (int) (size * (breed == 0 ? 0.62 : breed == 5 ? 0.66 : 0.55));
        int hx = x + (size - headSize) / 2;
        int hy = y - headSize / 4;
        if (facing == 2) hx -= size / 8;
        if (facing == 3) hx += size / 8;

        // Orejas según la raza
        g.setColor(dark);
        if (breed == 2 || breed == 4 || breed == 5) {
            // puntiagudas (pastor alemán, dóberman, Colmillo)
            g.fillPolygon(new int[]{hx + 1, hx + headSize / 5, hx + headSize / 2},
                          new int[]{hy + headSize / 3, hy - headSize / 2, hy + headSize / 4}, 3);
            g.fillPolygon(new int[]{hx + headSize - 1, hx + headSize - headSize / 5, hx + headSize / 2},
                          new int[]{hy + headSize / 3, hy - headSize / 2, hy + headSize / 4}, 3);
        } else {
            // caídas (bulldog, pit bull, gran danés)
            g.fillRoundRect(hx - headSize / 8, hy + headSize / 8, headSize / 3, (int) (headSize * 0.55), 6, 6);
            g.fillRoundRect(hx + headSize - headSize / 4, hy + headSize / 8, headSize / 3, (int) (headSize * 0.55), 6, 6);
        }

        g.setColor(shade(body, 0.85));
        g.fillRoundRect(hx, hy, headSize, headSize, headSize / 3, headSize / 3);

        // Hocico
        int snoutW = (int) (headSize * (breed == 0 ? 0.55 : 0.45));
        int snoutH = (int) (headSize * 0.38);
        int sx = hx + (headSize - snoutW) / 2;
        int sy = hy + headSize - snoutH / 2;
        g.setColor(shade(body, 0.7));
        g.fillRoundRect(sx, sy, snoutW, snoutH, 6, 6);
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(sx + snoutW / 2.0 - 3, sy + 2, 6, 4));

        // Colmillos
        g.setColor(new Color(240, 240, 230));
        int fangY = sy + snoutH - 2;
        g.fillPolygon(new int[]{sx + 3, sx + 7, sx + 5}, new int[]{fangY, fangY, fangY + 6}, 3);
        g.fillPolygon(new int[]{sx + snoutW - 7, sx + snoutW - 3, sx + snoutW - 5},
                      new int[]{fangY, fangY, fangY + 6}, 3);

        // Ojos furiosos
        int eyeR = Math.max(5, headSize / 6);
        int eyeY = hy + headSize / 3;
        Color eyeColor = isBoss ? new Color(255, 40, 40) : new Color(255, 210, 40);
        try {
            Point2D gc = new Point2D.Float(hx + headSize / 2f, eyeY + eyeR / 2f);
            g.setPaint(new RadialGradientPaint(gc, headSize * 0.9f,
                new float[]{0f, 1f},
                new Color[]{new Color(eyeColor.getRed(), eyeColor.getGreen(), eyeColor.getBlue(), isBoss ? 90 : 55),
                            new Color(eyeColor.getRed(), eyeColor.getGreen(), eyeColor.getBlue(), 0)}));
            g.fillRect(hx - headSize, eyeY - headSize, headSize * 3, headSize * 2);
        } catch (Exception ignored) { }

        g.setColor(eyeColor);
        g.fill(new Ellipse2D.Double(hx + headSize / 5.0, eyeY, eyeR, eyeR));
        g.fill(new Ellipse2D.Double(hx + headSize - headSize / 5.0 - eyeR, eyeY, eyeR, eyeR));
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(hx + headSize / 5.0 + eyeR / 3.0, eyeY + eyeR / 4.0, eyeR / 2.5, eyeR / 2.0));
        g.fill(new Ellipse2D.Double(hx + headSize - headSize / 5.0 - eyeR + eyeR / 3.0, eyeY + eyeR / 4.0,
                                    eyeR / 2.5, eyeR / 2.0));
        // Ceño fruncido
        g.setStroke(MED);
        g.drawLine(hx + headSize / 6, eyeY - 3, (int) (hx + headSize / 2.6), eyeY + 1);
        g.drawLine(hx + headSize - headSize / 6, eyeY - 3, (int) (hx + headSize - headSize / 2.6), eyeY + 1);
        g.setStroke(THIN);

        // Collar (con púas si es jefe)
        g.setColor(isBoss ? new Color(140, 20, 20) : new Color(70, 45, 25));
        g.fillRect(hx - 2, hy + headSize - 3, headSize + 4, Math.max(4, size / 12));
        if (isBoss) {
            g.setColor(new Color(210, 210, 220));
            for (int i = 0; i < 4; i++) {
                int px = hx + 2 + i * (headSize / 4);
                g.fillPolygon(new int[]{px, px + 5, px + 2},
                              new int[]{hy + headSize - 3, hy + headSize - 3, hy + headSize - 9}, 3);
            }
            // Cicatriz de batalla
            g.setColor(new Color(200, 90, 90, 200));
            g.setStroke(MED);
            g.drawLine(hx + headSize - 6, hy + 3, hx + headSize / 2, hy + headSize / 2);
            g.setStroke(THIN);
        }

        // Arma
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
    }

    // ==================================================================
    //  ESCENARIO
    // ==================================================================

    /** Asfalto mojado con charcos y alcantarillas. */
    public static void drawGround(Graphics2D g, int tx, int ty, int tile, Color base, int frame) {
        int px = tx * tile, py = ty * tile;
        int h = hash(tx, ty);

        g.setColor(shade(base, 1.0 + ((h & 15) - 7) * 0.012));
        g.fillRect(px, py, tile, tile);

        g.setColor(new Color(0, 0, 0, 45));
        g.drawLine(px, py, px + tile, py);
        g.drawLine(px, py, px, py + tile);

        // Charco: refleja un poco de luz y tiembla con la lluvia
        if ((h % 11) == 0) {
            int pw = tile / 2 + (h % 13);
            int ph = tile / 4 + (h % 7);
            int ox = px + (h % (tile / 3 + 1));
            int oy = py + tile / 3;
            g.setColor(new Color(30, 45, 70, 120));
            g.fill(new Ellipse2D.Double(ox, oy, pw, ph));
            g.setColor(new Color(120, 160, 220, 60));
            int ripple = (int) (Math.sin(frame * 0.08 + tx) * 2);
            g.draw(new Ellipse2D.Double(ox + 3, oy + 2 + ripple, pw - 6, Math.max(2, ph - 4)));
        }

        // Alcantarilla
        if ((h % 47) == 0) {
            g.setColor(new Color(45, 45, 50));
            g.fill(new Ellipse2D.Double(px + tile / 4.0, py + tile / 4.0, tile / 2.0, tile / 2.0));
            g.setColor(new Color(25, 25, 30));
            for (int i = 0; i < 3; i++) {
                g.drawLine(px + tile / 4, py + tile / 3 + i * 5, px + 3 * tile / 4, py + tile / 3 + i * 5);
            }
        }
    }

    /** Edificio con ventanas encendidas y, a veces, un letrero de neón. */
    public static void drawBuilding(Graphics2D g, int tx, int ty, int tile,
                                    Color wall, Color edge, int frame) {
        int px = tx * tile, py = ty * tile;
        int h = hash(tx, ty);

        g.setColor(shade(wall, 1.0 + ((h & 7) - 3) * 0.05));
        g.fillRect(px, py, tile, tile);

        // Bloques de ladrillo
        g.setColor(shade(wall, 0.8));
        for (int i = 1; i < 4; i++) g.drawLine(px, py + i * tile / 4, px + tile, py + i * tile / 4);

        // Ventanas
        int cols = 2, rows = 2;
        int wW = tile / 4, wH = tile / 5;
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int wx = px + tile / 5 + c * (tile / 2);
                int wy = py + tile / 5 + r * (tile / 3);
                int bit = (h >> (c * 3 + r * 5)) & 7;
                if (bit == 0) {
                    // Ventana encendida, con un leve titileo
                    int flick = ((frame / 23 + h) % 40 == 0) ? 60 : 0;
                    g.setColor(new Color(Math.min(255, 200 - flick), Math.min(255, 165 - flick), 90, 190));
                } else if (bit == 1) {
                    g.setColor(new Color(60, 120, 160, 150));
                } else {
                    g.setColor(new Color(10, 12, 18, 200));
                }
                g.fillRect(wx, wy, wW, wH);
                g.setColor(new Color(0, 0, 0, 120));
                g.drawRect(wx, wy, wW, wH);
            }
        }

        // Letrero de neón ocasional
        if ((h % 29) == 0) {
            Color neon = ((h >> 4) % 3 == 0) ? new Color(255, 40, 130)
                       : ((h >> 4) % 3 == 1) ? new Color(40, 220, 255)
                       : new Color(180, 60, 255);
            int alpha = 150 + (int) (70 * Math.sin(frame * 0.05 + h));
            g.setColor(new Color(neon.getRed(), neon.getGreen(), neon.getBlue(), Math.max(60, Math.min(255, alpha))));
            g.fillRect(px + tile / 6, py + tile - tile / 4, tile - tile / 3, 5);
            g.setColor(new Color(neon.getRed(), neon.getGreen(), neon.getBlue(), 40));
            g.fillRect(px + tile / 8, py + tile - tile / 4 - 5, tile - tile / 4, 16);
        }

        g.setColor(edge);
        g.drawRect(px, py, tile, tile);
    }

    /** Moneda girando. */
    public static void drawCoin(Graphics2D g, int px, int py, int tile, int frame) {
        double spin = Math.abs(Math.sin(frame * 0.06 + px * 0.01));
        int w = (int) (6 + 14 * spin);
        int cx = px + tile / 2, cy = py + tile / 2 + (int) (Math.sin(frame * 0.05 + py * 0.02) * 3);

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

    /** Puesto del Mercado Negro. */
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

        // Toldo a rayas
        g.setColor(new Color(90, 10, 50));
        for (int i = 0; i < tile; i += 10) {
            g.setColor(i % 20 == 0 ? new Color(140, 20, 70) : new Color(60, 10, 40));
            g.fillRect(px + i, py + 4, 10, 10);
        }
        g.setColor(neon);
        g.setFont(new Font("Consolas", Font.BOLD, 11));
        g.drawString("ARMAS", px + 6, py + tile - 10);
    }

    /** Puerta de salida del barrio. */
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
            // Flecha hacia adelante
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
            // Candado
            g.setColor(new Color(230, 190, 60));
            g.fillRect(px + tile / 2 - 6, py + tile / 2 - 2, 12, 10);
            g.drawArc(px + tile / 2 - 5, py + tile / 2 - 9, 10, 10, 0, 180);
        }
    }
}
