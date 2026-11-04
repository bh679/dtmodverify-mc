package games.brennan.dtmodverify.scan;

import com.mojang.logging.LogUtils;
import games.brennan.dtmodverify.DtModVerify;
import games.brennan.dtmodverify.api.FileFingerprint.Carrier;
import games.brennan.dtmodverify.fingerprint.PackageFingerprint;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the installed jar a <b>generated</b> mod file came from.
 *
 * <p>Some mods ship as a bootstrap: a jar in {@code mods/} that, on every launch, unpacks a
 * bundled {@code META-INF/jar-jar/<name>.jar}, rewrites it for the running game and registers a
 * mod locator for the folder it wrote to. The loader then lists the <em>generated</em> file as a
 * mod, and its bytes differ from machine to machine, so no published hash can ever match it. The
 * bootstrap itself is a published, hashable file — and because it rewrites the generated jar
 * before the loader scans it, an approved bootstrap vouches for what was loaded.</p>
 *
 * <p>{@link #find} proves that relationship for one scanned file, with three conditions:</p>
 * <ol>
 *   <li>the file is not itself in {@code mods/} (the caller checks; a jar the player put there is
 *       judged as itself);</li>
 *   <li>exactly one plain jar in {@code mods/} bundles an entry named
 *       {@code META-INF/jar-jar/<the scanned file's name>} — the first found wins, a second is
 *       logged and ignored;</li>
 *   <li>every package key of the scanned file is among the bundled entry's package keys: the
 *       generated code is nothing the bundle did not contain. Rewriting changes the game classes
 *       a mod refers to, not the mod's own package names.</li>
 * </ol>
 * Any failure (unreadable jar, missing folder) answers null: no carrier, the file is judged on
 * its own as before. Whether an approved carrier <em>counts</em> is the verdict's business
 * ({@link games.brennan.dtmodverify.api.FileVerdicts#tierOf}) and, on the relay, an allowlist
 * of mod ids — this class only reports what is on disk.
 */
public final class CarrierFinder {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String P = DtModVerify.LOG_PREFIX;
    static final String BUNDLE_PREFIX = "META-INF/jar-jar/";

    private CarrierFinder() {}

    /**
     * The carrier of {@code generated}, or null. {@code generatedPackages} is the scanned file's
     * own package fingerprint (already computed by the scanner). Pure apart from reading
     * {@code modsDir}; never throws.
     */
    public static Carrier find(Path generated, Path modsDir, List<String> generatedPackages) {
        try {
            if (generated == null || modsDir == null || !Files.isDirectory(modsDir)) return null;
            String entryName = BUNDLE_PREFIX + generated.getFileName();
            Carrier found = null;
            for (Path jar : modsJars(modsDir)) {
                byte[] bundled = bundledEntry(jar, entryName);
                if (bundled == null) continue;
                if (found != null) {
                    LOGGER.info("{} carrier: {} is also bundled by {}; keeping {}", P, generated.getFileName(),
                        jar.getFileName(), found.fileName());
                    continue;
                }
                if (!covers(bundled, generatedPackages)) {
                    LOGGER.info("{} carrier: {} bundles {} but the generated file holds packages the bundle does not",
                        P, jar.getFileName(), generated.getFileName());
                    continue;
                }
                InstalledFileScanner.Hashes h = InstalledFileScanner.hashes(jar);
                found = new Carrier(jar.getFileName().toString(), h.size(), h.sha1(), h.murmur2());
            }
            return found;
        } catch (Throwable t) {
            LOGGER.debug("{} carrier: could not look for {}'s carrier: {}", P, generated, t.toString());
            return null;
        }
    }

    /** Plain jars in {@code modsDir}, sorted by name so "first wins" is stable. */
    static List<Path> modsJars(Path modsDir) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(modsDir)) {
            s.filter(InstalledFileScanner::isPlainJar).sorted().forEach(out::add);
        }
        return out;
    }

    /** The bytes of {@code entryName} inside {@code jar}, or null when it has no such entry or cannot be read. */
    static byte[] bundledEntry(Path jar, String entryName) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry e = zip.getEntry(entryName);
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                return in.readAllBytes();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    /** Does the bundled jar's package fingerprint contain every key of the generated file's? Pure. */
    static boolean covers(byte[] bundledJar, List<String> generatedPackages) {
        try {
            Set<String> bundle = Set.copyOf(PackageFingerprint.ofBytes(bundledJar));
            return bundle.containsAll(generatedPackages == null ? List.of() : generatedPackages);
        } catch (IOException e) {
            return false;
        }
    }
}
