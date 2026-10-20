package org.bitwisemadness.tensurajade.config;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.tensurajade.TensuraJade;
import snownee.jade.api.IWailaClientRegistration;

/**
 * Every toggle this addon exposes, hosted inside Jade's own plugin config screen so
 * the settings sit next to the tooltip they control.
 *
 * <p>Key shape matters: Jade derives a setting's <em>category</em> by truncating the
 * path at its first {@code '.'}, and refuses to name a category it was not already
 * told about - so the group is named by the derived primary key itself. Paths
 * therefore carry a dot, and every one of them resolves to {@link #CATEGORY}.</p>
 */
public final class TensuraJadeConfig {
    /** The settings group the toggles appear under: every key's derived primary key. */
    public static final ResourceLocation CATEGORY = key("tensura_jade");

    /** Append the target's analysis lines to Jade's entity tooltip. */
    public static final ResourceLocation ENTITY_ANALYSIS = key("tensura_jade.entity_analysis");

    /** Append the target's analysis lines to Jade's block tooltip. */
    public static final ResourceLocation BLOCK_ANALYSIS = key("tensura_jade.block_analysis");

    /** Include the ability list (Tensura cycles one ability at a time; Jade lists them). */
    public static final ResourceLocation SHOW_ABILITIES = key("tensura_jade.show_abilities");

    /**
     * Suppress Tensura's own floating analysis overlay, so the same information is not
     * shown twice. Only ever honoured while Jade is installed - the mixin config
     * requires both mods.
     */
    public static final ResourceLocation HIDE_TENSURA_OVERLAY = key("tensura_jade.hide_tensura_overlay");

    /**
     * Honour Tensura's analysis rules: the {@code analysis_level} and
     * {@code analysis_distance} attributes, the analysis mode, and the blurred
     * "12k+" EP readout. Off means "show everything to everyone".
     */
    public static final ResourceLocation RESPECT_ANALYSIS_GATING = key("tensura_jade.respect_analysis_gating");

    private TensuraJadeConfig() {
    }

    /**
     * Registers the toggles this addon alone owns, grouped by their dotted paths under
     * {@link #CATEGORY}.
     *
     * <p>{@link #ENTITY_ANALYSIS} and {@link #BLOCK_ANALYSIS} are deliberately <em>not</em>
     * registered here, because Jade already creates them from the providers themselves.
     * {@code WailaClientRegistration.registerEntityComponent/registerBlockComponent} call
     * {@code tryAddConfig(provider)}, which registers {@code provider.getUid()} with
     * {@code PluginConfig}. In a registration session that call is deferred - but
     * {@code ClientRegistrationSession.end()} flips the session inactive <em>before</em>
     * it flushes providers, so by the time {@link TensuraEntityProvider} and
     * {@link TensuraBlockProvider} are installed their keys go straight into
     * {@code PluginConfig}. The queued entries above are flushed <em>after</em> that pass,
     * so naming those two keys here as well puts a second copy in front of
     * {@code PluginConfig.addConfig}, which rejects it with
     * {@code Duplicate config key} - aborting the flush and leaving every later toggle
     * unregistered. That is exactly how {@link #RESPECT_ANALYSIS_GATING} came to be read
     * on every analysis line without ever existing.</p>
     *
     * <p>Nothing is lost by leaving them out: {@code enabledByDefault()} is {@code true}
     * and neither provider overrides it, so Jade still ships both toggles on, under this
     * same {@link #CATEGORY}.</p>
     */
    public static void register(IWailaClientRegistration registration) {
        registration.addConfig(SHOW_ABILITIES, true);
        registration.addConfig(HIDE_TENSURA_OVERLAY, true);
        registration.addConfig(RESPECT_ANALYSIS_GATING, true);
    }

    private static ResourceLocation key(String path) {
        return ResourceLocation.fromNamespaceAndPath(TensuraJade.MOD_ID, path);
    }
}
