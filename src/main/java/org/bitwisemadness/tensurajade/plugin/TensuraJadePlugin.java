package org.bitwisemadness.tensurajade.plugin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import org.bitwisemadness.tensurajade.TensuraJade;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import org.bitwisemadness.tensurajade.provider.TensuraBlockProvider;
import org.bitwisemadness.tensurajade.provider.TensuraEntityProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade discovers this class from FML's {@code ModFileScanData}, because
 * {@link WailaPlugin} is {@code @Retention(RUNTIME)} - no entrypoint declaration is
 * needed in the mod metadata.
 *
 * <p>Registration is client-only: the analysis data is read from the client-side
 * capabilities Tensura already syncs, so no server data provider is required.</p>
 */
@WailaPlugin(TensuraJade.MOD_ID)
public final class TensuraJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        TensuraJadeConfig.register(registration);

        // LivingEntity rather than Entity: Tensura's analysis only ever targets living
        // mobs, and Jade resolves providers up the target's class hierarchy.
        registration.registerEntityComponent(new TensuraEntityProvider(), LivingEntity.class);
        registration.registerBlockComponent(new TensuraBlockProvider(), Block.class);
    }
}
