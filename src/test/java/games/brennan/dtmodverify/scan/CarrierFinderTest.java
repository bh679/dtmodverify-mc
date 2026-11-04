package games.brennan.dtmodverify.scan;

import games.brennan.dtmodverify.api.FileFingerprint;
import games.brennan.dtmodverify.api.FileFingerprint.Carrier;
import games.brennan.dtmodverify.fingerprint.PackageFingerprint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The bootstrap case: a jar in {@code mods/} bundles {@code META-INF/jar-jar/X.jar}; the game
 * loads a rewritten {@code X.jar} from elsewhere. No live {@code ModList}; everything on disk in
 * a temp game dir.
 */
class CarrierFinderTest {

    private static final String[] CSL = {"customskinloader/config/A.class", "customskinloader/loader/B.class",
        "customskinloader/mod/C.class"};

    @Test
    @DisplayName("A generated jar whose packages the bundled jar covers names the mods/ jar that bundles it")
    void findsTheCarrier(@TempDir Path game) throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        Path core = Files.createDirectories(game.resolve("CustomSkinLoader").resolve("Core"));
        byte[] bundled = zip(CSL);
        Path bootstrap = mods.resolve("CustomSkinLoader_Universal-15.1.jar");
        Files.write(bootstrap, zip(new String[]{"customskinloader/bootstrap/Boot.class"},
            new String[]{"META-INF/jar-jar/CustomSkinLoader-Common.jar"}, new byte[][]{bundled}));
        Files.write(mods.resolve("jei.jar"), zip(new String[]{"mezz/jei/X.class"}));
        Path generated = core.resolve("CustomSkinLoader-Common.jar");
        Files.write(generated, zip(new String[]{"customskinloader/config/A.class", "customskinloader/mod/C.class"}));

        List<String> pk = PackageFingerprint.ofJar(generated);
        Carrier c = CarrierFinder.find(generated, mods, pk);

        assertNotNull(c);
        assertEquals("CustomSkinLoader_Universal-15.1.jar", c.fileName());
        InstalledFileScanner.Hashes h = InstalledFileScanner.hashes(bootstrap);
        assertEquals(h.sha1(), c.sha1());
        assertEquals(h.murmur2(), c.murmur2());
        assertEquals(h.size(), c.size());

        FileFingerprint f = InstalledFileScanner.fingerprint(generated, List.of("customskinloader")).withCarrier(c);
        var o = f.toJson();
        assertEquals(c.sha1(), o.getAsJsonObject("carrier").get("sha1").getAsString());
        assertEquals("CustomSkinLoader_Universal-15.1.jar", o.getAsJsonObject("carrier").get("fileName").getAsString());
        assertEquals(c.sha1(), f.carrierSha1());
    }

    @Test
    @DisplayName("No carrier: a foreign package in the generated jar, no bundling jar, a missing mods folder")
    void standsAside(@TempDir Path game) throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        Path core = Files.createDirectories(game.resolve("Core"));
        Files.write(mods.resolve("bootstrap.jar"), zip(new String[0], new String[]{"META-INF/jar-jar/X.jar"},
            new byte[][]{zip(CSL)}));
        Path generated = core.resolve("X.jar");
        Files.write(generated, zip(new String[]{"customskinloader/config/A.class", "net/xray/Cheat.class"}));
        assertNull(CarrierFinder.find(generated, mods, PackageFingerprint.ofJar(generated)),
            "code the bundle does not contain: the bootstrap does not vouch for it");

        Path other = core.resolve("Y.jar");
        Files.write(other, zip(new String[]{"customskinloader/config/A.class"}));
        assertNull(CarrierFinder.find(other, mods, PackageFingerprint.ofJar(other)), "nothing bundles Y.jar");

        assertNull(CarrierFinder.find(generated, game.resolve("nope"), List.of()), "no mods folder");
        assertNull(CarrierFinder.find(null, mods, List.of()));
    }

    @Test
    @DisplayName("Two jars bundling the same name: the first by name wins, and a resource-only generated jar is covered")
    void firstWinsAndEmptyIsCovered(@TempDir Path game) throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        Files.write(mods.resolve("a-boot.jar"), zip(new String[0], new String[]{"META-INF/jar-jar/X.jar"}, new byte[][]{zip(CSL)}));
        Files.write(mods.resolve("b-boot.jar"), zip(new String[0], new String[]{"META-INF/jar-jar/X.jar"}, new byte[][]{zip(CSL)}));
        Path generated = Files.createDirectories(game.resolve("Core")).resolve("X.jar");
        Files.write(generated, zip(new String[]{"assets/x/lang/en_us.json"}));
        Carrier c = CarrierFinder.find(generated, mods, PackageFingerprint.ofJar(generated));
        assertNotNull(c);
        assertEquals("a-boot.jar", c.fileName());
    }

    static byte[] zip(String[] emptyEntries) throws Exception {
        return zip(emptyEntries, new String[0], new byte[0][]);
    }

    static byte[] zip(String[] emptyEntries, String[] dataNames, byte[][] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            for (String n : emptyEntries) {
                z.putNextEntry(new ZipEntry(n));
                z.write(new byte[]{(byte) 0xCA, (byte) 0xFE});
                z.closeEntry();
            }
            for (int i = 0; i < dataNames.length; i++) {
                z.putNextEntry(new ZipEntry(dataNames[i]));
                z.write(data[i]);
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
