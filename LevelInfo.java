import java.awt.Color;

/** Datos de cada barrio: su nombre, su jefe, su paleta de colores y a quién se rescata ahí. */
public class LevelInfo {

    public final String name;
    public final String bossName;
    public final String rescueTarget;
    public final String bossTaunt;
    public final int bossBreed;
    /** bg: color de relleno del panel. street: asfalto. bldg1/bldg2: muros y bordes. fog1/fog2: viñeta. */
    public final Color bg, street, bldg1, bldg2, fog1, fog2, bossColor;

    public LevelInfo(String name, String bossName, int bossBreed, String rescueTarget, String bossTaunt,
                     Color bg, Color street, Color bldg1, Color bldg2,
                     Color fog1, Color fog2, Color bossColor) {
        this.name = name;
        this.bossName = bossName;
        this.bossBreed = bossBreed;
        this.rescueTarget = rescueTarget;
        this.bossTaunt = bossTaunt;
        this.bg = bg;
        this.street = street;
        this.bldg1 = bldg1;
        this.bldg2 = bldg2;
        this.fog1 = fog1;
        this.fog2 = fog2;
        this.bossColor = bossColor;
    }

    /** Los seis barrios de la ciudad, del basurero a la mansión de Colmillo. */
    public static LevelInfo[] buildCampaign() {
        return new LevelInfo[] {
            new LevelInfo("BARRIO 1: EL BASURERO", "ROCKY EL BULLDOG", 0, null,
                "\"Aquí terminan los gatos callejeros, Nox...\"",
                new Color(14, 13, 16), new Color(46, 42, 38),
                new Color(78, 68, 56), new Color(112, 98, 78),
                new Color(14, 7, 6, 170), new Color(4, 2, 2, 255), new Color(165, 75, 35)),

            new LevelInfo("BARRIO 2: MERCADO CENTRAL", "SPIKE EL PIT BULL", 1, "WILL",
                "\"Tu cachorro llora tu nombre por las noches.\"",
                new Color(18, 15, 10), new Color(52, 44, 28),
                new Color(96, 76, 36), new Color(134, 106, 48),
                new Color(20, 13, 3, 170), new Color(7, 4, 1, 255), new Color(196, 140, 30)),

            new LevelInfo("BARRIO 3: METRO ABANDONADO", "REX EL PASTOR ALEMÁN", 2, "NINA",
                "\"En la oscuridad, nadie te oye maullar.\"",
                new Color(10, 13, 18), new Color(36, 42, 52),
                new Color(52, 66, 88), new Color(78, 98, 128),
                new Color(4, 14, 20, 172), new Color(1, 4, 8, 255), new Color(130, 100, 48)),

            new LevelInfo("BARRIO 4: PUERTO INDUSTRIAL", "TITÁN EL GRAN DANÉS", 3, null,
                "\"Te haré pedazos y te arrojaré al mar.\"",
                new Color(11, 16, 16), new Color(36, 48, 48),
                new Color(56, 80, 80), new Color(84, 116, 116),
                new Color(4, 16, 16, 170), new Color(1, 5, 5, 255), new Color(96, 96, 110)),

            new LevelInfo("BARRIO 5: LAS AZOTEAS", "SHADOW EL DÓBERMAN", 4, "MITSY",
                "\"Tu esposa gritó tu nombre... hasta que dejó de hacerlo.\"",
                new Color(16, 13, 22), new Color(46, 38, 60),
                new Color(74, 58, 98), new Color(104, 82, 136),
                new Color(11, 5, 20, 172), new Color(3, 1, 7, 255), new Color(48, 44, 58)),

            new LevelInfo("NIVEL FINAL: LA MANSIÓN DE COLMILLO", "DON COLMILLO", 5, null,
                "\"Bienvenido a tu tumba, gato callejero.\"",
                new Color(18, 9, 16), new Color(52, 28, 46),
                new Color(88, 40, 76), new Color(126, 58, 108),
                new Color(20, 3, 18, 174), new Color(6, 1, 6, 255), new Color(150, 24, 24)),
        };
    }
}
