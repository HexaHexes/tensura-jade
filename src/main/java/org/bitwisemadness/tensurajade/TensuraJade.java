package org.bitwisemadness.tensurajade;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges Tensura's entity and block analysis window into Jade's tooltip.
 *
 * <p>The addon does nothing at all unless <em>both</em> mods are present: with Jade
 * missing there is no window to merge into, and with Tensura missing there is no
 * analysis to show. Every code path that touches either mod sits behind a
 * {@code ModList} gate or a mixin config {@code requiredMods} declaration.</p>
 */
@Mod(TensuraJade.MOD_ID)
public final class TensuraJade {
    public static final String MOD_ID = "tensura_jade";
    public static final Logger LOGGER = LoggerFactory.getLogger("Tensura Jade");

    public TensuraJade(IEventBus modBus, ModContainer container) {
    }
}
