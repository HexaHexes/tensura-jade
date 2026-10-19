package org.bitwisemadness.tensurajade.client;

import io.github.manasmods.tensura.client.screen.SettingsScreen;
import snownee.jade.impl.config.PluginConfig;

/**
 * Decides whether Tensura's floating analysis overlay should be drawn at all.
 *
 * <p>This class touches <em>both</em> mods at once - Tensura's settings screen and
 * Jade's config store - so it only ever loads from the mixin, whose config declares
 * {@code requiredMods=["tensura","jade"]}. Neither reference is safe elsewhere.</p>
 *
 * <p>Reads Jade's config live rather than caching it: the settings screen writes into
 * the same {@link PluginConfig} entries, so a cached flag would go stale the moment a
 * player toggled the option.</p>
 */
public final class OverlaySuppression {

    /**
     * The element id Tensura's HUD editor uses for the analysis panel, matching the
     * {@code getPreviewElement() == 3} test in its own {@code pre_renderAnalysis()}.
     */
    private static final int PREVIEW_ANALYSIS = 3;

    private OverlaySuppression() {
    }

    /**
     * {@code true} when Tensura's analysis overlay should be skipped.
     *
     * <p>Suppression is deliberately lifted while the panel is being repositioned in
     * Tensura's own HUD editor: hiding a preview the player is trying to place would
     * make that screen useless, so the editor still gets to see it.</p>
     *
     * <p>Fails <strong>off</strong> rather than on: if the toggle cannot be read (our
     * config entries never registered, for instance), suppressing would delete
     * information with nothing left in its place.</p>
     */
    public static boolean shouldSuppress() {
        if (SettingsScreen.getPreviewElement() == PREVIEW_ANALYSIS) {
            return false;
        }
        try {
            return PluginConfig.INSTANCE.get(org.bitwisemadness.tensurajade.config.TensuraJadeConfig.HIDE_TENSURA_OVERLAY);
        } catch (Throwable t) {
            return false;
        }
    }
}
