package games.brennan.dtmodverify;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Dungeon Train Mod Verification. A library: it registers nothing, listens to nothing and has no
 * config or commands. Dungeon Train calls {@link games.brennan.dtmodverify.api.ModVerify} when its
 * Free Play check needs to know what the player actually has installed.
 */
@Mod(DtModVerify.MOD_ID)
public final class DtModVerify {

    public static final String MOD_ID = "dtmodverify";
    public static final String LOG_PREFIX = "[DtModVerify]";

    private static final Logger LOGGER = LogUtils.getLogger();

    public DtModVerify() {
        LOGGER.info("{} loaded — mod file verification available (version {})",
            LOG_PREFIX, games.brennan.dtmodverify.api.ModVerify.version());
    }
}
