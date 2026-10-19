package org.bitwisemadness.tensurajade.provider;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.bitwisemadness.tensurajade.analysis.TensuraAnalysis;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Appends Tensura's block analysis to Jade's tooltip for whatever block is being looked at.
 *
 * <p>Same load-bearing {@code ModList} gate as the entity provider: it keeps
 * {@link TensuraAnalysis} - and its Tensura imports - off the classpath entirely when
 * Tensura is absent.</p>
 */
public final class TensuraBlockProvider implements IComponentProvider<BlockAccessor> {

    @Override
    public ResourceLocation getUid() {
        return TensuraJadeConfig.BLOCK_ANALYSIS;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!config.get(TensuraJadeConfig.BLOCK_ANALYSIS)) {
            return;
        }
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded("tensura")) {
            return;
        }
        TensuraAnalysis.appendBlock(tooltip, accessor, config);
    }
}
