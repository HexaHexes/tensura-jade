package org.bitwisemadness.tensurajade.mixin;

import net.minecraft.network.chat.Component;
import org.bitwisemadness.mobinfocommon.variant.VariantHelper;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import org.bitwisemadness.tensurajade.provider.VariantRelay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import snownee.jade.addon.core.ObjectNameProvider;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Qualifies Jade's entity title with the target's variant - "Red Fang
 * Direwolf", "Emerald Fire Dragon" - the same way the Pseudocore's Race line
 * has always read (issue #3).
 *
 * <p><strong>Why a variable swap, rather than cancelling and adding our own
 * line.</strong> The provider's name line is added <em>untagged</em>
 * ({@code tooltip.add(component)} with no uid), so Jade's public
 * {@code ITooltip.replace(uid, ...)} cannot find it - the only public seam is
 * gone. What remains is better anyway: this injector swaps the
 * <b>component</b> after Jade has built it, so the custom-name check, the
 * accessibility decorations and the theme's title styling all stay Jade's own
 * work. Nothing is duplicated from the provider body; if the variant does not
 * resolve, the component passes through untouched and the vanilla title
 * stands.</p>
 *
 * <p>The variant itself arrives through {@link VariantRelay}'s server-data
 * channel - every species stores its {@code Variant} in world-save NBT that
 * the client never receives directly - and is resolved by the shared library,
 * so a horn's Race line and this title cannot drift apart. A custom-named mob
 * keeps its name: what someone named the mob is not a species statement.</p>
 *
 * <p>{@code remap = false} because the target is Jade's own method, not
 * Minecraft's. The descriptor is written out in full because
 * {@code ObjectNameProvider} holds a static {@code getEntityName} and
 * {@code ForEntity} carries the synthetic {@code Accessor} bridge - a bare
 * name would not resolve to a single method.</p>
 */
@Mixin(value = ObjectNameProvider.ForEntity.class, remap = false)
public final class ObjectNameProviderMixin {

    /**
     * Runs where the provider stores its freshly built name, before the theme
     * styles and adds it. Anchored on the store rather than the call so the
     * local is already assigned when the swap happens. The handler receives
     * the local being modified followed by the method's own arguments.
     */
    @ModifyVariable(
            method = "appendTooltip(Lsnownee/jade/api/ITooltip;Lsnownee/jade/api/EntityAccessor;Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At(value = "STORE", ordinal = 0),
            ordinal = 0)
    private Component tensuraJade$qualifyWithVariant(Component name, ITooltip tooltip,
                                                     EntityAccessor accessor, IPluginConfig config) {
        if (!config.get(TensuraJadeConfig.VARIANT_TITLES)) {
            return name;
        }
        VariantHelper.Variant variant = VariantRelay.resolve(accessor.getEntity(), accessor.getServerData());
        if (variant == null) {
            return name;
        }
        return VariantHelper.qualifiedName(name, variant);
    }
}
