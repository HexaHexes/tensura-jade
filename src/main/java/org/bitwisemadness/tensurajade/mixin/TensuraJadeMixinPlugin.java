package org.bitwisemadness.tensurajade.mixin;

import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Belt-and-braces guard for the Tensura mixin config.
 *
 * <p>The config already declares {@code requiredMods=["tensura","jade"]} in
 * neoforge.mods.toml, which is the primary mechanism keeping it from loading without
 * both mods. This plugin re-checks independently, because a mixin config that applies
 * against a missing target class fails at mixin apply time with a hard crash rather
 * than a clean skip - and the cost of the duplicate check is one ModList lookup.</p>
 *
 * <p>Uses {@link net.neoforged.fml.ModList} only (never the loading-time list), so it
 * is safe at the point FML evaluates configs.</p>
 *
 * <p>When ModList cannot answer because it has not been built yet, this fails
 * <strong>open</strong>: deferring the decision is correct there, since a mixin config
 * that declines to load is never reconsidered.</p>
 */
public final class TensuraJadeMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Tensura Jade Mixins");

    /** Both mods are required: neither one alone has anything for the overlay to do. */
    private static final String[] REQUIRED_MODS = {"tensura", "jade"};

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            net.neoforged.fml.ModList modList = net.neoforged.fml.ModList.get();
            if (modList == null) {
                // Not built yet - fail open and let FML's requiredMods gate stand.
                return true;
            }
            for (String modId : REQUIRED_MODS) {
                if (!modList.isLoaded(modId)) {
                    LOGGER.info("Skipping {}: mod '{}' is not loaded", mixinClassName, modId);
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            // Never let a guard failure take the game down; FML's requiredMods still applies.
            LOGGER.warn("Could not verify mixin prerequisites for {}; applying", mixinClassName, t);
            return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
