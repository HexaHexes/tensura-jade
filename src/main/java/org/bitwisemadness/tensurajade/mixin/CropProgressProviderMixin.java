package org.bitwisemadness.tensurajade.mixin;

import org.bitwisemadness.tensurajade.analysis.TensuraAnalysis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.addon.vanilla.CropProgressProvider;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Stops Jade reporting a growth <em>percentage</em> for {@code HipokuteGrass} that has
 * already failed to grow, so that Tensura's {@code Failed Growth} line is the only
 * statement made about that plant's progress.
 *
 * <p><strong>Why the percentage is wrong.</strong> Jade's crop-progress provider gates on
 * {@code CropBlock}, {@code BushBlock} or {@code BonemealableBlock}, then recognises which
 * vanilla age constant the block carries and divides the age by a matching literal - 3.0
 * for {@code AGE_3}. {@code HipokuteGrass.AGE} <em>is</em> {@code AGE_3}, so an age of 2
 * reads {@code 2/3 = 67%}. But that plant is finished: its own {@code randomTick} returns
 * as soon as {@code age >= 2}, so it will not grow again, and a full bar would need the age
 * 3 that the property allows and the plant never reaches. That is precisely what Tensura
 * reports as {@code Failed Growth} for the same block.</p>
 *
 * <p><strong>The whole method is cancelled, deliberately.</strong> Its only job is to
 * append the crop-progress row, so there is no neighbouring content to lose. Cancelling
 * rather than filtering keeps the decision in one place:
 * {@link TensuraAnalysis#suppressCropPercentage} - which also answers to the block panel,
 * so the two cannot drift from the suppression that makes room for it.
 *
 * <p>Note what that predicate does not do: it does not fire whenever a percentage would be
 * wrong, only whenever this addon is in a position to replace one. With
 * {@code tensura_jade.block_analysis} off the panel is disabled and Jade's own wording is
 * left untouched. Gated-out viewers are the exception, and deliberately so - see the
 * predicate's own note.</p>
 *
 * <p>Client-only by construction: the mixin config lists this class under
 * {@code "client"}, and a Jade tooltip is only ever assembled on the client.</p>
 *
 * <p>{@code remap = false} because the target is Jade's own method, not Minecraft's.
 * The descriptor is written out in full because {@code CropProgressProvider} carries two
 * {@code appendTooltip} overloads - the real one taking {@code BlockAccessor}, plus the
 * synthetic bridge taking the raw {@code Accessor} - and a bare name would not resolve to
 * a single method.</p>
 */
@Mixin(value = CropProgressProvider.class, remap = false)
public final class CropProgressProviderMixin {

    @Inject(
            method = "appendTooltip(Lsnownee/jade/api/ITooltip;Lsnownee/jade/api/BlockAccessor;Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void tensuraJade$suppressFailedGrowthPercentage(ITooltip tooltip,
                                                            BlockAccessor accessor,
                                                            IPluginConfig config,
                                                            CallbackInfo ci) {
        if (TensuraAnalysis.suppressCropPercentage(accessor, config)) {
            ci.cancel();
        }
    }
}
