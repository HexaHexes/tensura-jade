package org.bitwisemadness.tensurajade.mixin;

import io.github.manasmods.tensura.handler.client.OverlayHandler;
import org.bitwisemadness.tensurajade.client.OverlaySuppression;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Tensura's analysis window from drawing, leaving Jade's tooltip as the single
 * place the analysis is shown.
 *
 * <p><strong>Two injection points, deliberately.</strong> Tensura's analysis is not one
 * method: {@code pre_renderAnalysis()} draws the panel's backdrop and, in its passing
 * case, reaches {@code renderAnalysis()} through the {@code ANALYSIS_LAYER} renderer
 * (a {@code REF_invokeStatic} method reference, which is why no bytecode anywhere says
 * {@code invokestatic renderAnalysis}). Suppressing only one of the pair would leave a
 * backdrop floating with nothing under it, or content with nothing behind it. Both are
 * analysis-only code paths - they touch no other HUD layer - so cancelling either has no
 * side effects.</p>
 *
 * <p>Client-only by construction: the mixin config lists this class under
 * {@code "client"}, and {@code OverlayHandler} itself is a client class.</p>
 *
 * <p>{@code remap = false} because both target names are Tensura's own methods, not
 * Minecraft's - mapping them through the intermediary refmap would fail to resolve.</p>
 */
@Mixin(value = OverlayHandler.class, remap = false)
public final class OverlayHandlerMixin {

    @Inject(method = "pre_renderAnalysis", at = @At("HEAD"), cancellable = true)
    private static void tensuraJade$skipAnalysisBackdrop(CallbackInfo ci) {
        if (OverlaySuppression.shouldSuppress()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderAnalysis", at = @At("HEAD"), cancellable = true)
    private static void tensuraJade$skipAnalysisContent(CallbackInfo ci) {
        if (OverlaySuppression.shouldSuppress()) {
            ci.cancel();
        }
    }
}
