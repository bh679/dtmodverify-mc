package games.brennan.dtmodverify.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import games.brennan.dtmodverify.fingerprint.CurseForgeFingerprint;
import games.brennan.dtmodverify.fingerprint.PackageFingerprint;

import java.util.List;

/**
 * One installed mod <b>file</b> as the hash-verified whitelist sees it: the jar's name and size,
 * its SHA-1 (the identity the relay and Modrinth key on), its CurseForge fingerprint
 * ({@link CurseForgeFingerprint}), every mod id that file declares — its own {@code [[mods]]}
 * plus those of any jar it embeds under {@code META-INF/jarjar/}, since NeoForge loads those as
 * mods but the player installed exactly one file — and its package fingerprint.
 *
 * <p><b>Carrier.</b> Some mods are not the file the player installed: a bootstrap jar in
 * {@code mods/} writes a generated jar elsewhere on every launch and registers its own locator
 * for it (CustomSkinLoader's Universal build is one). The generated jar hashes differently on
 * every machine, so no store file ever matches it, yet the bootstrap IS a published, hashable
 * file. When {@link games.brennan.dtmodverify.scan.CarrierFinder} can prove that relationship
 * the fingerprint names the bootstrap as its {@link Carrier}; {@link FileVerdicts#tierOf} may
 * then verify the file by the carrier's hash. Null for every ordinary jar.</p>
 *
 * <p>Immutable; produced by {@link games.brennan.dtmodverify.scan.InstalledFileScanner}, sent to
 * the relay by {@link games.brennan.dtmodverify.relay.VerifyClient} and judged by
 * {@link FileVerdicts}.</p>
 *
 * @param fileName the jar's file name, e.g. {@code jei-1.21.1-neoforge-19.21.0.247.jar}
 * @param size     bytes on disk
 * @param sha1     lowercase 40-hex SHA-1 of the file
 * @param murmur2  CurseForge fingerprint, unsigned 32-bit
 * @param modIds   lowercase mod ids the file declares, in discovery order
 * @param packages the jar's package fingerprint — see {@link PackageFingerprint} — outer jar and
 *                 embedded jars together, sorted, at most {@link PackageFingerprint#MAX}
 * @param carrier  the {@code mods/} jar that generated this file, or null
 */
public record FileFingerprint(String fileName, long size, String sha1, long murmur2, List<String> modIds,
                              List<String> packages, Carrier carrier) {

    /**
     * The installed jar a generated mod file came from: the same four facts the relay keys a
     * published file on, so it can be placed on Modrinth or CurseForge like any other file.
     */
    public record Carrier(String fileName, long size, String sha1, long murmur2) {
        /** The wire form, nested under {@code "carrier"}. */
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("sha1", sha1);
            o.addProperty("murmur2", murmur2);
            o.addProperty("size", size);
            o.addProperty("fileName", fileName);
            return o;
        }
    }

    public FileFingerprint {
        modIds = List.copyOf(modIds);
        packages = packages == null ? List.of() : List.copyOf(packages);
    }

    /** The pre-carrier shape: an ordinary installed jar. */
    public FileFingerprint(String fileName, long size, String sha1, long murmur2, List<String> modIds,
                           List<String> packages) {
        this(fileName, size, sha1, murmur2, modIds, packages, null);
    }

    /** The pre-package shape, for tests and callers that only have a hash. */
    public FileFingerprint(String fileName, long size, String sha1, long murmur2, List<String> modIds) {
        this(fileName, size, sha1, murmur2, modIds, List.of(), null);
    }

    /** This fingerprint with a carrier attached. */
    public FileFingerprint withCarrier(Carrier c) {
        return new FileFingerprint(fileName, size, sha1, murmur2, modIds, packages, c);
    }

    /** The carrier's SHA-1, or null without one. */
    public String carrierSha1() {
        return carrier == null ? null : carrier.sha1();
    }

    /**
     * The wire form for {@code POST /approved-mods/verify}:
     * {@code {sha1, murmur2, size, fileName, modIds, packages, carrier?}} — {@code carrier} only
     * when the file has one.
     */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("sha1", sha1);
        o.addProperty("murmur2", murmur2);
        o.addProperty("size", size);
        o.addProperty("fileName", fileName);
        JsonArray ids = new JsonArray();
        for (String id : modIds) ids.add(id);
        o.add("modIds", ids);
        JsonArray pk = new JsonArray();
        for (String p : packages) pk.add(p);
        o.add("packages", pk);
        if (carrier != null) o.add("carrier", carrier.toJson());
        return o;
    }

    /** {@code "<file> (id1, id2)"} — how a login notice or a cause tooltip names this file. */
    public String display() {
        return fileName + " (" + String.join(", ", modIds) + ")";
    }
}
