package org.bitwisemadness.tensurajade.gametest;

import static org.bitwisemadness.tensurajade.gametest.TestSupport.check;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.entityAccessor;

import io.github.manasmods.tensura.entity.monster.DirewolfEntity;
import io.github.manasmods.tensura.entity.variant.DirewolfVariant;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.mobinfocommon.variant.VariantHelper;
import org.bitwisemadness.tensurajade.TensuraJade;
import org.bitwisemadness.tensurajade.provider.VariantRelay;

/**
 * Proves the server half of the variant-qualified title (issue #3): the relay
 * writes the stored {@code Variant} into Jade's serverData channel, the
 * read-back resolves it through the shared library exactly as the horn's Race
 * line does, and a mob that carries no variant relays nothing at all.
 *
 * <p>The title <em>composition</em> client-side - Jade's name provider, the
 * theme call - cannot be proven headless; what is pinned here is everything
 * the mixin consumes, so a relay that stops writing or a resolver that stops
 * reading fails the suite rather than quietly degrading titles in-game.</p>
 */
@GameTestHolder(TensuraJade.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VariantRelayGameTests {

    /** Tensura's direwolf, resolved by id so a registry rename fails loudly here. */
    private static final ResourceLocation DIREWOLF_ID = ResourceLocation.parse("tensura:direwolf");

    private VariantRelayGameTests() {
    }

    /**
     * A direwolf's stored variant reaches the serverData channel and reads
     * back as the same words the horn's Race line would show - and qualifies
     * the species the same way.
     */
    @GameTest(template = "test_area")
    public static void direwolf_variant_reaches_the_client(GameTestHelper helper) {
        DirewolfEntity wolf = spawnDirewolf(helper, DIREWOLF_ID);
        wolf.setVariant(DirewolfVariant.RED_FANG);

        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        CompoundTag serverData = new CompoundTag();
        new VariantRelay().appendServerData(serverData, entityAccessor(viewer, wolf));

        check(serverData.contains(VariantRelay.RELAY_KEY, Tag.TAG_INT),
                "a direwolf's variant must be relayed as an int tag, got: " + serverData);

        VariantHelper.Variant variant = VariantRelay.resolve(wolf, serverData);
        check(variant != null, "the relayed variant must resolve through the library");
        check("Red Fang".equals(variant.text()),
                "RED_FANG must read <Red Fang> but read <" + variant.text() + ">");
        check(!variant.replacesRace(), "a direwolf variant decorates the species, not replaces it");

        // The shared composition: what Jade's title mixin will show above the
        // species name.
        check("Red Fang Direwolf".equals(VariantHelper.qualifiedName("Direwolf", variant)),
                "the title must compose exactly as the Race line does");

        helper.succeed();
    }

    /**
     * A mob that stores no variant relays nothing, and the read-back finds
     * nothing - the vanilla title stands rather than a guess being made.
     */
    @GameTest(template = "test_area")
    public static void a_mob_without_a_variant_relays_nothing(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));

        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        CompoundTag serverData = new CompoundTag();
        new VariantRelay().appendServerData(serverData, entityAccessor(viewer, cow));

        check(!serverData.contains(VariantRelay.RELAY_KEY),
                "a cow stores no Variant; the relay must write nothing, got: " + serverData);
        check(VariantRelay.resolve(cow, serverData) == null,
                "with nothing relayed, resolution must find no variant");

        helper.succeed();
    }

    /** Resolves an entity type by id and spawns it, failing loudly on a rename. */
    private static DirewolfEntity spawnDirewolf(GameTestHelper helper, ResourceLocation typeId) {
        Optional<EntityType<?>> registered = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
        check(registered.isPresent(), typeId + " is missing from the entity registry");
        LivingEntity spawned = spawn(helper, registered.get());
        check(spawned instanceof DirewolfEntity,
                typeId + " no longer spawns a DirewolfEntity; this test needs updating");
        return (DirewolfEntity) spawned;
    }

    @SuppressWarnings("unchecked")
    private static LivingEntity spawn(GameTestHelper helper, EntityType<?> type) {
        return (LivingEntity) helper.spawn((EntityType<LivingEntity>) type, new BlockPos(4, 1, 4));
    }
}
