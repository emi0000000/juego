import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cada barrio se construye con reglas propias, no repintando la misma cuadrícula:
 * el basurero son montañas de desperdicio, el metro son túneles con vías, el puerto
 * son filas de contenedores junto al agua, las azoteas son tejados separados por el
 * vacío y la mansión son salones conectados por pasillos.
 */
public final class MapGenerator {

    private MapGenerator() { }

    /**
     * Construye el mapa de un barrio: elige su algoritmo, lo rodea de muro, despeja
     * la entrada y la salida, garantiza un camino entre ambas y conecta lo aislado.
     */
    public static int[][] generate(int level, Random rand, int w, int h) {
        int[][] m;
        switch (level) {
            case 1:  m = basurero(rand, w, h); break;
            case 2:  m = mercado(rand, w, h); break;
            case 3:  m = metro(rand, w, h); break;
            case 4:  m = puerto(rand, w, h); break;
            case 5:  m = azoteas(rand, w, h); break;
            default: m = mansion(rand, w, h); break;
        }

        border(m, w, h);
        clear(m, 1, 1, 5, 5, w, h);                       // plaza de inicio
        clear(m, w - 6, h - 6, w - 2, h - 2, w, h);       // explanada de la salida

        // Se garantiza a mano que exista un camino de la entrada a la salida pasando
        // por el centro: ningún generador aleatorio puede dejar el barrio sin solución.
        carve(m, rand, 2, 2, w / 2, h / 2, w, h);
        carve(m, rand, w / 2, h / 2, w - 3, h - 3, w, h);

        // Y se enganchan las zonas que hayan quedado aisladas, para que no se
        // desperdicie medio barrio detrás de un muro.
        connectRegions(m, rand, w, h);

        return m;
    }

    /**
     * Busca bolsas de suelo separadas del resto y les abre un paso hasta la zona principal.
     * Sin esto, el basurero encerraba casi la mitad del mapa detrás de las montañas de basura.
     */
    private static void connectRegions(int[][] m, Random rand, int w, int h) {
        boolean[][] visited = new boolean[w][h];
        List<int[]> main = flood(m, visited, 2, 2, w, h);
        if (main.isEmpty()) return;

        int connected = 0;
        for (int x = 1; x < w - 1 && connected < 30; x++) {
            for (int y = 1; y < h - 1 && connected < 30; y++) {
                if (visited[x][y] || !Tiles.walkable(m[x][y])) continue;

                List<int[]> region = flood(m, visited, x, y, w, h);
                if (region.size() < 8) continue;   // bolsas minúsculas: no vale la pena abrirlas

                int[] from = region.get(region.size() / 2);
                int[] to = nearest(main, from);
                carve(m, rand, from[0], from[1], to[0], to[1], w, h);
                connected++;
            }
        }
    }

    /**
     * Marca todas las casillas a las que se puede llegar caminando desde un punto.
     * El motor lo usa para no colocar nunca al jefe, las tiendas ni los enemigos
     * dentro de una zona incomunicada.
     */
    public static boolean[][] reachable(int[][] m, int sx, int sy) {
        int w = m.length, h = m[0].length;
        boolean[][] visited = new boolean[w][h];
        flood(m, visited, sx, sy, w, h);
        return visited;
    }

    /**
     * Algoritmo de inundación: marca todo el suelo conectado con el punto de partida
     * y devuelve esas casillas. Es la base de todas las comprobaciones de acceso.
     */
    private static List<int[]> flood(int[][] m, boolean[][] visited, int sx, int sy, int w, int h) {
        List<int[]> region = new ArrayList<>();
        if (sx < 0 || sy < 0 || sx >= w || sy >= h || visited[sx][sy] || !Tiles.walkable(m[sx][sy])) {
            return region;
        }

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy});
        visited[sx][sy] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            region.add(c);
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (visited[nx][ny] || !Tiles.walkable(m[nx][ny])) continue;
                visited[nx][ny] = true;
                queue.add(new int[]{nx, ny});
            }
        }
        return region;
    }

    /** Casilla del grupo que está más cerca de un punto dado. */
    private static int[] nearest(List<int[]> pool, int[] from) {
        int[] best = pool.get(0);
        long bestDist = Long.MAX_VALUE;
        for (int[] c : pool) {
            long dx = c[0] - from[0], dy = c[1] - from[1];
            long d = dx * dx + dy * dy;
            if (d < bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Barrio 1 — EL BASURERO: descampado con montañas de desperdicio
    // ------------------------------------------------------------------

    private static int[][] basurero(Random rand, int w, int h) {
        int[][] m = filled(w, h, Tiles.FLOOR);

        for (int i = 0; i < 68; i++) {
            int cx = rand.nextInt(w - 4) + 2;
            int cy = rand.nextInt(h - 4) + 2;
            int r = 1 + rand.nextInt(3);
            for (int x = cx - r; x <= cx + r; x++) {
                for (int y = cy - r; y <= cy + r; y++) {
                    if (!in(x, y, w, h)) continue;
                    int dx = x - cx, dy = y - cy;
                    if (dx * dx + dy * dy <= r * r) m[x][y] = Tiles.WALL;
                }
            }
        }

        scatter(m, rand, w, h, Tiles.PROP, 90);    // bidones oxidados
        scatter(m, rand, w, h, Tiles.DECOR, 70);   // charcos de aceite
        return m;
    }

    // ------------------------------------------------------------------
    // Barrio 2 — MERCADO CENTRAL: hileras de puestos con pasillos entre medio
    // ------------------------------------------------------------------

    private static int[][] mercado(Random rand, int w, int h) {
        int[][] m = filled(w, h, Tiles.FLOOR);

        for (int by = 3; by < h - 4; by += 5) {
            int bx = 2;
            while (bx < w - 4) {
                int len = 2 + rand.nextInt(4);
                if (bx + len >= w - 3) break;
                for (int k = 0; k < len; k++) {
                    m[bx + k][by] = Tiles.WALL;
                    m[bx + k][by + 1] = Tiles.WALL;
                }
                bx += len + 2 + rand.nextInt(3);
            }
        }

        // Cajas de fruta amontonadas al pie de los puestos
        for (int i = 0; i < 110; i++) {
            int x = rand.nextInt(w - 2) + 1;
            int y = rand.nextInt(h - 2) + 1;
            if (m[x][y] == Tiles.FLOOR && touches(m, x, y, Tiles.WALL, w, h)) m[x][y] = Tiles.PROP;
        }
        scatter(m, rand, w, h, Tiles.DECOR, 120);  // adoquín manchado
        return m;
    }

    // ------------------------------------------------------------------
    // Barrio 3 — METRO ABANDONADO: túneles largos con vías y andenes
    // ------------------------------------------------------------------

    private static int[][] metro(Random rand, int w, int h) {
        int[][] m = filled(w, h, Tiles.WALL);

        List<Integer> tunnels = new ArrayList<>();
        for (int y = 4; y < h - 4; y += 6) {
            tunnels.add(y);
            for (int x = 1; x < w - 1; x++) {
                m[x][y - 1] = Tiles.FLOOR;   // andén
                m[x][y] = Tiles.DECOR;       // vías
                m[x][y + 1] = Tiles.FLOOR;   // andén
            }
        }

        // Escaleras y pasillos que conectan un túnel con el siguiente
        for (int t = 0; t + 1 < tunnels.size(); t++) {
            int yA = tunnels.get(t), yB = tunnels.get(t + 1);
            int connectors = 3 + rand.nextInt(3);
            for (int c = 0; c < connectors; c++) {
                int x = 2 + rand.nextInt(w - 5);
                for (int y = yA + 1; y <= yB - 1; y++) {
                    m[x][y] = Tiles.FLOOR;
                    if (x + 1 < w - 1) m[x + 1][y] = Tiles.FLOOR;
                }
            }
        }

        // Vagones abandonados sobre las vías
        for (int i = 0; i < 30; i++) {
            int x = rand.nextInt(w - 4) + 2;
            int y = tunnels.get(rand.nextInt(tunnels.size()));
            if (m[x][y] == Tiles.DECOR && m[x + 1][y] == Tiles.DECOR) {
                m[x][y] = Tiles.PROP;
                m[x + 1][y] = Tiles.PROP;
            }
        }
        return m;
    }

    // ------------------------------------------------------------------
    // Barrio 4 — PUERTO INDUSTRIAL: contenedores apilados y agua inundando el muelle
    // ------------------------------------------------------------------

    private static int[][] puerto(Random rand, int w, int h) {
        int[][] m = filled(w, h, Tiles.FLOOR);

        // Filas de contenedores
        for (int by = 4; by < h - 5; by += 6) {
            int bx = 3;
            while (bx < w - 6) {
                int cw = 2 + rand.nextInt(3);
                int ch = 2 + rand.nextInt(3);
                for (int x = bx; x < bx + cw && x < w - 2; x++) {
                    for (int y = by; y < by + ch && y < h - 2; y++) m[x][y] = Tiles.WALL;
                }
                bx += cw + 2 + rand.nextInt(3);
            }
        }

        // Zonas inundadas: lejos del inicio y de la salida para no cortar el paso
        for (int i = 0; i < 9; i++) {
            int pw = 3 + rand.nextInt(4);
            int ph = 3 + rand.nextInt(5);
            int px = 8 + rand.nextInt(Math.max(1, w - pw - 14));
            int py = 8 + rand.nextInt(Math.max(1, h - ph - 14));
            for (int x = px; x < px + pw; x++) {
                for (int y = py; y < py + ph; y++) {
                    if (in(x, y, w, h)) m[x][y] = Tiles.WATER;
                }
            }
        }

        scatter(m, rand, w, h, Tiles.PROP, 80);    // cajas y bitas de amarre
        scatter(m, rand, w, h, Tiles.DECOR, 90);   // marcas amarillas de carga
        return m;
    }

    // ------------------------------------------------------------------
    // Barrio 5 — LAS AZOTEAS: tejados sueltos separados por el vacío
    // ------------------------------------------------------------------

    private static int[][] azoteas(Random rand, int w, int h) {
        int[][] m = filled(w, h, Tiles.VOID);
        List<int[]> roofs = new ArrayList<>();

        for (int gy = 1; gy < h - 6; gy += 9) {
            for (int gx = 1; gx < w - 6; gx += 9) {
                int rw = 5 + rand.nextInt(4);
                int rh = 5 + rand.nextInt(4);
                int ox = gx + rand.nextInt(3);
                int oy = gy + rand.nextInt(3);
                if (ox + rw >= w - 1 || oy + rh >= h - 1) continue;
                for (int x = ox; x < ox + rw; x++) {
                    for (int y = oy; y < oy + rh; y++) m[x][y] = Tiles.FLOOR;
                }
                roofs.add(new int[]{ox + rw / 2, oy + rh / 2});
            }
        }

        // Tablones de madera uniendo cada azotea con la siguiente
        for (int i = 0; i + 1 < roofs.size(); i++) {
            int[] a = roofs.get(i), b = roofs.get(i + 1);
            plank(m, a[0], a[1], b[0], b[1], w, h);
            if (i + 4 < roofs.size()) {
                int[] c = roofs.get(i + 4);
                plank(m, a[0], a[1], c[0], c[1], w, h);
            }
        }

        // Tanques de agua y antenas en los tejados
        for (int i = 0; i < 60; i++) {
            int x = rand.nextInt(w - 2) + 1;
            int y = rand.nextInt(h - 2) + 1;
            if (m[x][y] == Tiles.FLOOR && !touches(m, x, y, Tiles.VOID, w, h)) m[x][y] = Tiles.PROP;
        }
        return m;
    }

    private static void plank(int[][] m, int x0, int y0, int x1, int y1, int w, int h) {
        int x = x0, y = y0, guard = 0;
        while ((x != x1 || y != y1) && guard++ < 400) {
            if (in(x, y, w, h) && m[x][y] == Tiles.VOID) m[x][y] = Tiles.DECOR;
            if (x != x1) x += Integer.signum(x1 - x);
            else if (y != y1) y += Integer.signum(y1 - y);
        }
    }

    // ------------------------------------------------------------------
    // Barrio 6 — LA MANSIÓN DE COLMILLO: salones unidos por pasillos alfombrados
    // ------------------------------------------------------------------

    private static int[][] mansion(Random rand, int w, int h) {
        int[][] m = filled(w, h, Tiles.WALL);
        List<int[]> rooms = new ArrayList<>();

        for (int attempt = 0; attempt < 260 && rooms.size() < 18; attempt++) {
            int rw = 5 + rand.nextInt(5);
            int rh = 4 + rand.nextInt(5);
            int rx = 2 + rand.nextInt(w - rw - 4);
            int ry = 2 + rand.nextInt(h - rh - 4);

            boolean overlaps = false;
            for (int[] r : rooms) {
                if (rx < r[0] + r[2] + 2 && rx + rw + 2 > r[0]
                 && ry < r[1] + r[3] + 2 && ry + rh + 2 > r[1]) { overlaps = true; break; }
            }
            if (overlaps) continue;

            for (int x = rx; x < rx + rw; x++) {
                for (int y = ry; y < ry + rh; y++) m[x][y] = Tiles.FLOOR;
            }
            rooms.add(new int[]{rx, ry, rw, rh});
        }

        // Pasillos con alfombra roja entre salones consecutivos
        for (int i = 0; i + 1 < rooms.size(); i++) {
            int[] a = rooms.get(i), b = rooms.get(i + 1);
            corridor(m, a[0] + a[2] / 2, a[1] + a[3] / 2, b[0] + b[2] / 2, b[1] + b[3] / 2, w, h);
        }

        // Muebles contra las paredes de los salones
        for (int i = 0; i < 90; i++) {
            int x = rand.nextInt(w - 2) + 1;
            int y = rand.nextInt(h - 2) + 1;
            if (m[x][y] == Tiles.FLOOR && touches(m, x, y, Tiles.WALL, w, h)) m[x][y] = Tiles.PROP;
        }
        return m;
    }

    /** Pasillo en L con alfombra entre dos salones de la mansión, de dos casillas de ancho. */
    private static void corridor(int[][] m, int x0, int y0, int x1, int y1, int w, int h) {
        int x = x0, y = y0, guard = 0;
        while (x != x1 && guard++ < 400) {
            if (in(x, y, w, h)) m[x][y] = Tiles.DECOR;
            if (in(x, y + 1, w, h) && m[x][y + 1] == Tiles.WALL) m[x][y + 1] = Tiles.DECOR;
            x += Integer.signum(x1 - x);
        }
        while (y != y1 && guard++ < 800) {
            if (in(x, y, w, h)) m[x][y] = Tiles.DECOR;
            if (in(x + 1, y, w, h) && m[x + 1][y] == Tiles.WALL) m[x + 1][y] = Tiles.DECOR;
            y += Integer.signum(y1 - y);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static int[][] filled(int w, int h, int value) {
        int[][] m = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) m[x][y] = value;
        }
        return m;
    }

    private static void border(int[][] m, int w, int h) {
        for (int x = 0; x < w; x++) { m[x][0] = Tiles.WALL; m[x][h - 1] = Tiles.WALL; }
        for (int y = 0; y < h; y++) { m[0][y] = Tiles.WALL; m[w - 1][y] = Tiles.WALL; }
    }

    private static boolean in(int x, int y, int w, int h) {
        return x > 0 && y > 0 && x < w - 1 && y < h - 1;
    }

    private static void clear(int[][] m, int x0, int y0, int x1, int y1, int w, int h) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                if (in(x, y, w, h)) m[x][y] = Tiles.FLOOR;
            }
        }
    }

    /**
     * Excava a la fuerza un pasillo de 3 casillas de ancho entre dos puntos, avanzando
     * en zigzag. Es lo que garantiza que siempre se pueda llegar de la entrada a la salida.
     */
    private static void carve(int[][] m, Random rand, int x0, int y0, int x1, int y1, int w, int h) {
        int x = x0, y = y0, guard = 0;
        while ((x != x1 || y != y1) && guard++ < 6000) {
            clear(m, x - 1, y - 1, x + 1, y + 1, w, h);
            boolean moveX;
            if (x == x1) moveX = false;
            else if (y == y1) moveX = true;
            else moveX = rand.nextInt(100) < 55;
            if (moveX) x += Integer.signum(x1 - x);
            else y += Integer.signum(y1 - y);
        }
        clear(m, x1 - 1, y1 - 1, x1 + 1, y1 + 1, w, h);
    }

    private static void scatter(int[][] m, Random rand, int w, int h, int tile, int count) {
        for (int i = 0; i < count; i++) {
            int x = rand.nextInt(w - 2) + 1;
            int y = rand.nextInt(h - 2) + 1;
            if (m[x][y] == Tiles.FLOOR) m[x][y] = tile;
        }
    }

    /** Alguna de las ocho casillas de alrededor es del tipo indicado. */
    private static boolean touches(int[][] m, int x, int y, int tile, int w, int h) {
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int nx = x + i, ny = y + j;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && m[nx][ny] == tile) return true;
            }
        }
        return false;
    }
}
