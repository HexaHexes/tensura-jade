package org.bitwisemadness.tensurajade.provider;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.mobinfocommon.variant.VariantBridge;
import org.bitwisemadness.mobinfocommon.variant.VariantHelper;
import org.bitwisemadness.tensurajade.TensuraJade;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Relays a looked-at mob's stored {@code Variant} tag to the client, so the
 * title can be qualified with it (issue #3).
 *
 * <p><strong>The variant tags live server-side.</strong> Every species in
 * scope writes {@code Variant} in {@code addAdditionalSaveData}, which is
 * world-save NBT and never synced to the client on its own - the client's copy
 * of a mob carries no such key. Reading it client-side would therefore work in
 * singleplayer (where the integrated server shares the entity object) and
 * silently fall back to the plain name on a dedicated server. Relaying through
 * Jade's own server-data channel makes both paths the same path: the server
 * authors the value, the client consumes it, and a server without this addon
 * simply sends nothing - the mixin then leaves the vanilla name alone rather
 * than guessing.</p>
 *
 * <p><strong>Cost.</strong> Reading the tag means {@code saveWithoutId}, which
 * serialises the whole entity - the same thing Jade's own NBT-dump provider
 * does, at the same look-up tick rate. The {@link VariantBridge} pre-check
 * keeps packs with none of the four variant mods from paying even that: no
 * source mod, no serialisation, no bytes on the wire.</p>
 *
 * <p>Registered for {@link LivingEntity}: variants are a mob concern, and Jade
 * resolves providers up the target's class hierarchy, so every living target
 * reaches this relay while nothing else does.</p>
 */
public final class VariantRelay implements IServerDataProvider<EntityAccessor> {

    /**
     * The serverData key the relayed value travels under. Namespaced rather
     * than the bare {@code Variant}, so another provider writing into the same
     * compound cannot collide with the relay by accident.
     */
    public static final String RELAY_KEY = "tensura_jade_variant";

    /** The provider's identity in Jade's registry of server data providers. */
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(TensuraJade.MOD_ID, "variant_relay");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag serverData, EntityAccessor accessor) {
        if (!VariantBridge.anyVariantSourceLoaded()) {
            return;
        }
        Entity target = accessor.getEntity();
        if (!(target instanceof LivingEntity)) {
            return;
        }
        CompoundTag full = new CompoundTag();
        target.saveWithoutId(full);
        if (full.contains("Variant")) {
            serverData.put(RELAY_KEY, full.get("Variant"));
        }
    }

    /**
     * The read-back half of the relay: the variant the server sent for this
     * target, or null when there is none to read.
     *
     * <p>Kept beside the write so the key and its shape live in one place -
     * the relayed tag is re-wrapped under the {@code Variant} key the shared
     * library reads, and resolution runs through the library exactly as the
     * horn's Race line does it, so the title and the tooltip can never
     * disagree about what a mob is called.</p>
     */
    public static VariantHelper.Variant resolve(Entity target, CompoundTag serverData) {
        if (serverData == null || !serverData.contains(RELAY_KEY)) {
            return null;
        }
        if (!(target instanceof LivingEntity)) {
            return null;
        }
        CompoundTag variantTag = new CompoundTag();
        variantTag.put("Variant", serverData.get(RELAY_KEY));
        return VariantHelper.variantOf(
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()), variantTag);
    }
}
