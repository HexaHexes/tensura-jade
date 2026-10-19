package org.bitwisemadness.tensurajade.provider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.bitwisemadness.tensurajade.analysis.TensuraAnalysis;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Appends Tensura's entity analysis to Jade's tooltip for whatever mob is being looked at.
 *
 * <p>The {@code ModList} gate is load-bearing rather than decorative: it runs before the
 * first reference to {@link TensuraAnalysis}, so that class is never loaded - and so its
 * Tensura imports are never resolved - on an installation without Tensura.</p>
 */
public final class TensuraEntityProvider implements IComponentProvider<EntityAccessor> {

    @Override
    public ResourceLocation getUid() {
        return TensuraJadeConfig.ENTITY_ANALYSIS;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!config.get(TensuraJadeConfig.ENTITY_ANALYSIS)) {
            return;
        }
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded("tensura")) {
            return;
        }

        LivingEntity target = TensuraAnalysis.resolveLiving(accessor.getEntity());
        if (target == null) {
            return;
        }
        TensuraAnalysis.appendEntity(tooltip, accessor, config, target);
    }
}
