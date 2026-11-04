package games.brennan.dtmodverify.scan;

import com.mojang.logging.LogUtils;
import games.brennan.dtmodverify.DtModVerify;
import org.slf4j.Logger;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Finds the installed jar a mod file loaded by a <b>custom locator</b> lives in.
 *
 * <p>NeoForge's own jar-in-jar marks every nested mod file with a parent, so
 * {@link InstalledFileScanner} walks it up to the file on disk. Some mods bypass that: a library
 * shell in {@code mods/} registers its own {@code IModFileCandidateLocator} and opens the jars under
 * its {@code META-INF/jarjar/} itself (Sodium's NeoForge build does, for the mod proper and the
 * Forgified Fabric API modules it bundles). Those files reach the mod list with no parent and a
 * path inside the shell's zip file system — nothing on disk to hash, and the shell itself declares
 * no mod so it is never scanned. Before this class every such mod was unverifiable by construction.</p>
 *
 * <p>{@link #enclosingJar} proves the relationship for one nested file: exactly the plain
 * {@code mods/} jar bundling an entry {@code META-INF/jarjar/<the file's name>} whose bytes are the
 * file's bytes (SHA-1 equal; size equal when the nested bytes cannot be read). The caller then
 * attributes the nested file's ids to that jar, which is what the player installed and what the
 * platforms publish. Null when nothing matches — the file stays unresolved, as before.</p>
 */
public final class NestedJarResolver {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String P = DtModVerify.LOG_PREFIX;

    /** NeoForge's jar-in-jar folder (CustomSkinLoader's bootstrap uses {@code META-INF/jar-jar/}: see {@link CarrierFinder}). */
    static final String JARJAR_PREFIX = "META-INF/jarjar/";

    private NestedJarResolver() {}

    /**
     * The {@code mods/} jar that bundles {@code nested} (a path on a non-default file system), or
     * null. Pure apart from reading {@code modsDir}; never throws.
     */
    public static Path enclosingJar(Path nested, Path modsDir) {
        try {
            if (nested == null || nested.getFileName() == null || modsDir == null || !Files.isDirectory(modsDir)) return null;
            if (nested.getFileSystem() == FileSystems.getDefault()) return null;
            String entryName = JARJAR_PREFIX + nested.getFileName();
            byte[] bytes = readQuietly(nested);
            long size = bytes != null ? bytes.length : sizeQuietly(nested);
            byte[] want = bytes != null ? sha1(bytes) : null;
            for (Path jar : CarrierFinder.modsJars(modsDir)) {
                byte[] bundled = CarrierFinder.bundledEntry(jar, entryName);
                if (bundled == null) continue;
                boolean same = want != null ? Arrays.equals(want, sha1(bundled)) : (size >= 0 && bundled.length == size);
                if (same) return jar;
                LOGGER.info("{} nested jar: {} bundles an entry named {} but its bytes differ", P, jar.getFileName(),
                    nested.getFileName());
            }
            return null;
        } catch (Throwable t) {
            LOGGER.debug("{} nested jar: could not look for {}'s enclosing jar: {}", P, nested, t.toString());
            return null;
        }
    }

    private static byte[] readQuietly(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (Exception e) {
            return null;
        }
    }

    private static long sizeQuietly(Path p) {
        try {
            return Files.size(p);
        } catch (Exception e) {
            return -1;
        }
    }

    private static byte[] sha1(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-1").digest(bytes);
    }
}
