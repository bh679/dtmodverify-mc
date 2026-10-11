package games.brennan.dtmodverify.relay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import games.brennan.dtmodverify.DtModVerify;
import games.brennan.dtmodverify.api.FileFingerprint;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The verify POST: sends the installed files' fingerprints to the host's relay
 * ({@code POST <base>/approved-mods/verify}) and hands back whatever it answered. Only hashes, ids
 * and package names travel — never file contents. The caller owns the answer: this class does not
 * parse the approved list, so the relay's payload shape stays the host's business.
 */
public final class VerifyClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String P = DtModVerify.LOG_PREFIX;

    // Pin HTTP/1.1: Java's default HTTP/2 client can't h2c-upgrade over plaintext http://, which
    // breaks local 127.0.0.1 relay testing.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    /** A whole mod list's hashes are small, but the relay may call CurseForge for the unknown ones. */
    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(45);

    /**
     * The relay's answer. {@code status} is the HTTP status, or 0 when the request never got an
     * answer (transport failure, timeout, bad URL) — then {@code body} is empty.
     */
    public record VerifyAnswer(int status, String body) {
        public VerifyAnswer {
            body = body == null ? "" : body;
        }

        /** 2xx. */
        public boolean ok() {
            return status / 100 == 2;
        }
    }

    private VerifyClient() {}

    /** {@code <base>/approved-mods/verify}. */
    public static String verifyUrl(String base) {
        return base + "/approved-mods/verify";
    }

    /** The request body: {@code {"dt": "<version>", "files": [...]}} — {@code dt} only when it looks like a version. */
    public static String verifyBody(String dtVersion, List<FileFingerprint> files) {
        JsonObject body = new JsonObject();
        String v = dtVersion == null ? "" : dtVersion.trim();
        if (!v.isEmpty() && Character.isDigit(v.charAt(0))) body.addProperty("dt", v);
        JsonArray arr = new JsonArray();
        for (FileFingerprint f : files) arr.add(f.toJson());
        body.add("files", arr);
        return body.toString();
    }

    /**
     * POST the files to {@code baseUrl}'s verify route. Never throws and never completes
     * exceptionally: a transport failure completes with status 0 and an empty body.
     */
    public static CompletableFuture<VerifyAnswer> verify(String baseUrl, String dtVersion, List<FileFingerprint> files) {
        try {
            String body = verifyBody(dtVersion, files);
            HttpRequest req = HttpRequest.newBuilder(URI.create(verifyUrl(baseUrl)))
                    .timeout(VERIFY_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .handle((resp, err) -> {
                        if (err != null || resp == null) {
                            LOGGER.debug("{} verify failed: {}", P, err == null ? "no response" : err.toString());
                            return new VerifyAnswer(0, "");
                        }
                        return new VerifyAnswer(resp.statusCode(), resp.body());
                    });
        } catch (Throwable t) {
            LOGGER.debug("{} verify failed to start: {}", P, t.toString());
            return CompletableFuture.completedFuture(new VerifyAnswer(0, ""));
        }
    }

    /**
     * One INFO line naming the files the relay judged suspect or revoked — the first place to look
     * when a run is Free Play. Plausible/unknown never cost a run, so they are not named.
     */
    public static void logVerdicts(String body, int sent) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("files")) return;
            List<String> unverified = new ArrayList<>();
            for (JsonElement e : root.getAsJsonObject().getAsJsonArray("files")) {
                if (!e.isJsonObject()) continue;
                JsonObject f = e.getAsJsonObject();
                String status = f.has("status") ? f.get("status").getAsString() : "";
                if ("suspect".equals(status) || "revoked".equals(status)) {
                    String sha = f.has("sha1") ? f.get("sha1").getAsString() : "?";
                    unverified.add((sha.length() >= 8 ? sha.substring(0, 8) : sha)
                        + ":" + status + (f.has("reason") ? "/" + f.get("reason").getAsString() : ""));
                }
            }
            if (unverified.isEmpty()) {
                LOGGER.info("{} verify: none of {} installed file(s) suspect", P, sent);
            } else {
                LOGGER.info("{} verify: {} of {} file(s) suspect: {}",
                    P, unverified.size(), sent, String.join(", ", unverified));
            }
        } catch (Throwable t) {
            LOGGER.debug("{} verify: could not read verdicts: {}", P, t.toString());
        }
    }
}
