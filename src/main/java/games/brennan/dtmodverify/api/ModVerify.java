package games.brennan.dtmodverify.api;

import games.brennan.dtmodverify.DtModVerify;
import games.brennan.dtmodverify.relay.VerifyClient;
import games.brennan.dtmodverify.scan.InstalledFileScanner;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The facade a host mod calls. Everything is static and safe to call from any thread; nothing
 * here ever throws at the caller.
 *
 * <pre>
 *   ModVerify.scan(Set.of("minecraft", "neoforge", "forge", "dungeontrain"));   // title screen / server start
 *   ...
 *   List&lt;FileFingerprint&gt; files = ModVerify.current(5000);                       // "" = not known yet
 *   ModVerify.verify(relayBase, dtVersion, files).thenAccept(answer -> ...);
 *   Verdict v = ModVerify.tierOf(file, approvedSha1s, knownPackages, exemptIds);
 * </pre>
 */
public final class ModVerify {

    private ModVerify() {}

    /**
     * Start the once-per-JVM installed-file scan on a daemon thread (or return the one already
     * running). {@code exemptIds}: mod ids whose file is judged by id elsewhere and so is never
     * hashed — the game, the loader and the host's own jar with whatever it embeds.
     */
    public static CompletableFuture<List<FileFingerprint>> scan(Set<String> exemptIds) {
        return InstalledFileScanner.scan(exemptIds);
    }

    /**
     * The fingerprints if the scan has finished within {@code timeoutMs} (0 = don't wait), else an
     * empty list — which means "not known yet", never "no files".
     */
    public static List<FileFingerprint> current(long timeoutMs) {
        return InstalledFileScanner.current(timeoutMs);
    }

    /** Has the scan finished (with any result)? */
    public static boolean isDone() {
        return InstalledFileScanner.isDone();
    }

    /** The fingerprint whose file declares {@code modId}, or null. Non-blocking. */
    public static FileFingerprint fileOf(String modId) {
        return InstalledFileScanner.fileOf(modId);
    }

    /** The pure tier rule — see {@link FileVerdicts#tierOf}. */
    public static Verdict tierOf(FileFingerprint f, Set<String> approvedSha1s, Map<String, Set<String>> knownPackages,
                                 Set<String> exemptIds) {
        return FileVerdicts.tierOf(f, approvedSha1s, knownPackages, exemptIds);
    }

    /** The tier rule with a carrier allowlist — see {@link FileVerdicts#tierOf(FileFingerprint, Set, Map, Set, Set)}. */
    public static Verdict tierOf(FileFingerprint f, Set<String> approvedSha1s, Map<String, Set<String>> knownPackages,
                                 Set<String> exemptIds, Set<String> carrierApprovedIds) {
        return FileVerdicts.tierOf(f, approvedSha1s, knownPackages, exemptIds, carrierApprovedIds);
    }

    /**
     * POST the files to {@code relayBaseUrl}'s verify route. Never completes exceptionally; a
     * transport failure answers status 0 with an empty body. Only hashes, ids and package names
     * are sent.
     */
    public static CompletableFuture<VerifyClient.VerifyAnswer> verify(String relayBaseUrl, String dtVersion,
                                                                      List<FileFingerprint> files) {
        return VerifyClient.verify(relayBaseUrl, dtVersion, files);
    }

    /** This mod's own version as NeoForge loaded it, or {@code "?"} outside a mod environment. */
    /**
     * The file on disk a loaded mod file came from — jar-in-jar parent chain walked up, and a file a
     * custom locator opened from inside a library shell mapped to that shell (0.2.1). Use this, not
     * the parent chain alone, when comparing the mod list with {@link FileFingerprint#fileName()}.
     */
    public static java.nio.file.Path outermostPath(net.neoforged.neoforgespi.locating.IModFile file) {
        return InstalledFileScanner.outermostPath(file);
    }

    public static String version() {
        try {
            return ModList.get().getModContainerById(DtModVerify.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("?");
        } catch (Throwable t) {
            return "?";
        }
    }
}
