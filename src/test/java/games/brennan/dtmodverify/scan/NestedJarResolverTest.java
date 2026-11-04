package games.brennan.dtmodverify.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The custom-locator case (Sodium's NeoForge shell): a library jar in {@code mods/} bundles
 * {@code META-INF/jarjar/X.jar} and opens it itself, so the loaded file is a path inside the
 * shell's zip file system with no parent.
 */
class NestedJarResolverTest {

    private static final String[] INNER = {"net/caffeinemc/mods/sodium/A.class", "net/caffeinemc/mods/sodium/B.class"};

    @Test
    @DisplayName("A nested path inside a mods/ shell resolves to the shell by its bundled bytes; foreign bytes or names do not")
    void resolvesTheShell(@TempDir Path game) throws Exception {
        Path mods = Files.createDirectories(game.resolve("mods"));
        byte[] inner = CarrierFinderTest.zip(INNER);
        byte[] other = CarrierFinderTest.zip(new String[]{"net/caffeinemc/mods/sodium/A.class"});
        Path shell = mods.resolve("sodium-neoforge-0.8.12+mc1.21.1.jar");
        Files.write(shell, CarrierFinderTest.zip(new String[]{"net/caffeinemc/mods/sodium/service/Locator.class"},
            new String[]{"META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.12+mc1.21.1-mod.jar", "META-INF/jarjar/fabric-api-base-0.4.42.jar"},
            new byte[][]{inner, other}));
        Files.write(mods.resolve("jei.jar"), CarrierFinderTest.zip(new String[]{"mezz/jei/X.class"}));

        try (FileSystem zip = FileSystems.newFileSystem(shell, Map.of())) {
            Path nested = zip.getPath("META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.12+mc1.21.1-mod.jar");
            assertEquals(shell, NestedJarResolver.enclosingJar(nested, mods));
            assertEquals(shell, InstalledFileScanner.outermostPathForTest(nested, mods), "the scanner's mapping uses it");

            Path fabric = zip.getPath("META-INF/jarjar/fabric-api-base-0.4.42.jar");
            assertEquals(shell, NestedJarResolver.enclosingJar(fabric, mods), "every bundled module maps to the one shell");

            // A nested file whose bytes are not what the shell bundles under that name: not vouched for.
            Path renamed = zip.getPath("META-INF/jarjar/renamed.jar");
            assertNull(NestedJarResolver.enclosingJar(renamed, mods), "no entry of that name anywhere");
        }
        Path elsewhere = Files.createDirectories(game.resolve("other")).resolve("loose.jar");
        Files.write(elsewhere, CarrierFinderTest.zip(new String[]{"net/caffeinemc/mods/sodium/A.class"}));
        try (FileSystem zip = FileSystems.newFileSystem(elsewhere, Map.of())) {
            // Same name as a bundled entry, different bytes → null.
            Path spoof = Files.write(game.resolve("spoof.jar"), CarrierFinderTest.zip(new String[]{"a/b/C.class"}));
            try (FileSystem z2 = FileSystems.newFileSystem(spoof, Map.of())) {
                Path fake = z2.getPath("/META-INF/jarjar/fabric-api-base-0.4.42.jar");
                assertNull(NestedJarResolver.enclosingJar(fake, mods));
            }
        }
        assertNull(NestedJarResolver.enclosingJar(shell, mods), "a plain jar on disk is not nested");
        assertNull(NestedJarResolver.enclosingJar(null, mods));
        assertNull(NestedJarResolver.enclosingJar(shell, game.resolve("nope")));
    }
}
