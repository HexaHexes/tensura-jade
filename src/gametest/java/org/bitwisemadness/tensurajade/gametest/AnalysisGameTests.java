package org.bitwisemadness.tensurajade.gametest;

import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.race.api.Races;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.entity.multipart.LivingMultipartBody;
import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.Alignment;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import io.github.manasmods.tensura.storage.ability.IAbility;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.storage.player.ITensuraPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.tensurajade.TensuraJade;
import org.bitwisemadness.tensurajade.analysis.TensuraAnalysis;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

import static org.bitwisemadness.tensurajade.gametest.TestSupport.TooltipRecorder;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.blockAccessor;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.check;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.config;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.entityAccessor;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.expectLabel;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.expectStat;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.indexOfLabel;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.labelKeys;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.setAttribute;
import static org.bitwisemadness.tensurajade.gametest.TestSupport.values;

/**
 * Exercises the analysis panel itself: the gating rules it mirrors, and the lines it
 * writes for entities, races, abilities and blocks.
 *
 * <p>The label keys are restated here rather than imported, because
 * {@link TensuraAnalysis} keeps its constants private. That is the point: these tests
 * pin the addon to Tensura's own wording and localisation keys, so a key that drifts
 * away from Tensura's fails here rather than showing a player a raw translation key.</p>
 *
 * <p>Assertions are resolution-neutral. Whether a mod's translation file happens to
 * have loaded on this headless server is not under test, so expected text is built with
 * the same {@code Component.translatable(...)} call production uses, and the stable
 * contract - the key itself - is checked structurally.</p>
 */
@GameTestHolder(TensuraJade.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnalysisGameTests {

    // --- Entity panel labels (Tensura's own keys).
    private static final String LABEL_ALIGNMENT = "tensura_jade.alignment";
    private static final String LABEL_HP = "tensura.vanilla_attribute.health.shortened_name";
    private static final String LABEL_SHP = "tensura.attribute.spiritual_health.shortened_name";
    private static final String LABEL_ARMOR = "attribute.name.generic.armor";
    private static final String LABEL_AURA = "tensura.attribute.aura.shortened_name";
    private static final String LABEL_MAGICULE = "tensura.attribute.magicule.shortened_name";
    private static final String LABEL_EP = "tensura.attribute.existence_points.shortened_name";

    // --- Block panel labels (Tensura's own keys).
    private static final String LABEL_HARDNESS = "tensura.attribute.block.hardness";
    private static final String LABEL_EXPLOSION_RES = "tensura.attribute.block.explosion_resistance";
    private static final String LABEL_LIGHT = "tensura.attribute.block.light_level";
    private static final String LABEL_CORRECT_TOOL = "tensura.attribute.block.correct_tool";
    private static final String LABEL_AGE = "tensura.attribute.block.age";
    private static final String LABEL_REDSTONE = "tensura.attribute.block.redstone_strength";

    private AnalysisGameTests() {
    }

    // ---------------------------------------------------------------------
    // Gating
    // ---------------------------------------------------------------------

    /**
     * Tensura only lets you analyse when both analysis attributes are positive, then only
     * what the analysis mode allows, then only within reach - and the addon's override
     * turns all of that off. Every branch of {@link TensuraAnalysis#allowedForEntity}
     * and {@link TensuraAnalysis#allowedForBlock} is covered here.
     */
    @GameTest(template = "test_area")
    public static void gating_matrix(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        Cow target = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));
        BlockPos targetBlock = helper.absolutePos(new BlockPos(4, 1, 4));

        // Override off: nothing else may be consulted, so this must hold for a viewer
        // that has never been given an analysis attribute at all.
        IPluginConfig relaxed = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false));
        check(TensuraAnalysis.allowedForEntity(viewer, target, relaxed),
                "with the override off, mob analysis must not be gated - that is the whole point of the toggle");
        check(TensuraAnalysis.allowedForBlock(viewer, targetBlock, relaxed),
                "with the override off, block analysis must not be gated");

        IPluginConfig gated = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, true));

        // Tensura refuses to analyse at all unless BOTH attributes are positive.
        setAttribute(viewer, TensuraAttributes.ANALYSIS_LEVEL, 0);
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 10);
        check(!TensuraAnalysis.allowedForEntity(viewer, target, gated),
                "analysis level 0 must deny mob analysis");
        check(!TensuraAnalysis.allowedForBlock(viewer, targetBlock, gated),
                "analysis level 0 must deny block analysis");

        setAttribute(viewer, TensuraAttributes.ANALYSIS_LEVEL, 2);
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 0);
        check(!TensuraAnalysis.allowedForEntity(viewer, target, gated),
                "an analysis distance of 0 must deny mob analysis");
        check(!TensuraAnalysis.allowedForBlock(viewer, targetBlock, gated),
                "an analysis distance of 0 must deny block analysis");

        // A viewer that satisfies both attributes, standing on top of the target.
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 10);
        ITensuraPlayer data = TensuraStorages.getPlayerDataFrom(viewer);
        check(data != null,
                "a viewer must carry Tensura player data; without it the mode reads -1 and nothing is ever analysed");
        viewer.setPos(target.getX(), target.getY(), target.getZ());

        data.setAnalysisMode(0);
        check(TensuraAnalysis.allowedForEntity(viewer, target, gated),
                "mode 0 analyses whatever is aimed at, so a mob in reach must be allowed");
        check(TensuraAnalysis.allowedForBlock(viewer, targetBlock, gated),
                "mode 0 analyses whatever is aimed at, so a block in reach must be allowed");

        data.setAnalysisMode(1);
        check(TensuraAnalysis.allowedForEntity(viewer, target, gated), "mode 1 analyses mobs");
        check(!TensuraAnalysis.allowedForBlock(viewer, targetBlock, gated), "mode 1 never analyses blocks");

        data.setAnalysisMode(2);
        check(!TensuraAnalysis.allowedForEntity(viewer, target, gated), "mode 2 never analyses mobs");
        check(TensuraAnalysis.allowedForBlock(viewer, targetBlock, gated), "mode 2 analyses blocks");

        // Any other mode analyses nothing. This is also the branch a viewer with no
        // player data takes, since a missing storage reports -1.
        data.setAnalysisMode(3);
        check(!TensuraAnalysis.allowedForEntity(viewer, target, gated),
                "an unknown analysis mode must deny mob analysis");
        check(!TensuraAnalysis.allowedForBlock(viewer, targetBlock, gated),
                "an unknown analysis mode must deny block analysis");

        // Reach is measured from the viewer, so stepping out of range denies both.
        data.setAnalysisMode(0);
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 4);
        viewer.setPos(target.getX() + 64.0D, target.getY(), target.getZ());
        check(!TensuraAnalysis.allowedForEntity(viewer, target, gated),
                "a mob beyond the analysis distance must be denied");
        check(!TensuraAnalysis.allowedForBlock(viewer, targetBlock, gated),
                "a block beyond the analysis distance must be denied");

        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Entity panel
    // ---------------------------------------------------------------------

    /**
     * The entity panel is a fixed sequence of {@code "<label>: <value>"} lines - race
     * first (absent here), then alignment, HP, spiritual health, armor, aura, magicule
     * and maximum EP - each labelled with Tensura's own key.
     *
     * <p>The mob is deliberately a vanilla one: the panel is supposed to work on
     * anything you aim Jade at, and Tensura registers its energy attributes for every
     * entity type, so a plain cow must produce a complete panel rather than fail soft.</p>
     */
    @GameTest(template = "test_area")
    public static void entity_panel_lines(GameTestHelper helper) {
        Cow target = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);

        // Caps first, so the readings below are not clamped to an unregistered maximum.
        setAttribute(target, TensuraAttributes.MAX_SPIRITUAL_HEALTH, 1000.0D);
        setAttribute(target, TensuraAttributes.MAX_AURA, 5000.0D);
        setAttribute(target, TensuraAttributes.MAX_MAGICULE, 9000.0D);

        IExistence existence = TensuraStorages.getExistenceFrom(target);
        check(existence != null,
                "a spawned mob must carry Tensura existence storage (created on demand); without it "
                        + "the panel fails soft and would show nothing at all");
        existence.setSpiritualHealth(55.0D);
        existence.setAura(1234.0D);
        existence.setMagicule(4321.0D);
        existence.setAlignment(Alignment.MAJIN);

        // A freshly spawned mob's health is Tensura's business, not this panel's, so the
        // input is pinned rather than assumed. A fraction does double duty: it proves the
        // line reports *current* health - not the maximum - and that it rounds up as
        // Tensura's does, rather than truncating.
        target.setHealth(4.5F);

        IPluginConfig toggles = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false,
                TensuraJadeConfig.SHOW_ABILITIES, false));
        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendEntity(panel.proxy(), entityAccessor(viewer, target), toggles, target);

        check(panel.size() == 7,
                "expected alignment/HP/SHP/armor/aura/magicule/EP for a raceless mob with abilities hidden, "
                        + "got " + panel.texts());
        expectStat(panel, 0, LABEL_ALIGNMENT, Alignment.MAJIN.getName().getString());
        expectStat(panel, 1, LABEL_HP, "5");
        expectStat(panel, 2, LABEL_SHP, "55");
        expectStat(panel, 3, LABEL_ARMOR, "0");
        expectStat(panel, 4, LABEL_AURA, "1234");
        expectStat(panel, 5, LABEL_MAGICULE, "4321");
        // Maximum EP is max magicule + max aura, both pinned above.
        expectStat(panel, 6, LABEL_EP, "14000");

        // Ordering is part of the parity, not incidental.
        check(labelKeys(panel).subList(0, 7).equals(List.of(
                        LABEL_ALIGNMENT, LABEL_HP, LABEL_SHP, LABEL_ARMOR,
                        LABEL_AURA, LABEL_MAGICULE, LABEL_EP)),
                "panel label order was " + labelKeys(panel));

        helper.succeed();
    }

    /**
     * The race line leads the panel, carries the race's own name with no label prefix,
     * and is tinted with the target's alignment colour - because that colouring is how
     * Tensura communicates alignment, so it is part of the parity rather than decoration.
     */
    @GameTest(template = "test_area")
    public static void race_line(GameTestHelper helper) {
        Cow target = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);

        Races races = RaceAPI.getRaceFrom(target);
        check(races != null, "every living mob must expose ManasCore race storage");
        check(races.setRace(ResourceLocation.fromNamespaceAndPath("tensura", "goblin"), false),
                "tensura:goblin must be a registered race this addon can attach");
        check(races.getRace().isPresent(), "the race must have been attached to the mob");

        IExistence existence = TensuraStorages.getExistenceFrom(target);
        check(existence != null, "a spawned mob must carry Tensura existence storage");
        existence.setAlignment(Alignment.MAJIN);
        // Pin what the race hands the mob, so the assertions below describe this panel
        // rather than Tensura's balance values for a goblin.
        existence.setAura(7.0D);
        existence.setMagicule(8.0D);
        target.setHealth(1.1F);

        IPluginConfig toggles = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false,
                TensuraJadeConfig.SHOW_ABILITIES, false));
        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendEntity(panel.proxy(), entityAccessor(viewer, target), toggles, target);

        check(panel.size() == 8,
                "expected race + alignment + six stats, got " + panel.texts());

        Component expectedName = races.getRace().get().getRace().getName();
        Component raceLine = panel.line(0);
        check(raceLine.getString().equals(expectedName.getString()),
                "line 0 was <" + raceLine.getString() + "> but the race name is <"
                        + expectedName.getString() + ">");
        check(!panel.text(0).contains(": "),
                "the race line is a name, not a '<label>: <value>' stat: " + panel.text(0));

        int tint = raceLine.getStyle().getColor() == null
                ? -1
                : raceLine.getStyle().getColor().getValue();
        check(tint == (Alignment.MAJIN.getColor() & 0xFFFFFF),
                "the race line must be tinted with the alignment colour, got #"
                        + Integer.toHexString(tint));

        // And everything after it falls into the documented stat order.
        expectLabel(panel, 1, LABEL_ALIGNMENT);
        expectStat(panel, 1, LABEL_ALIGNMENT, Alignment.MAJIN.getName().getString());
        expectStat(panel, 2, LABEL_HP, "2");
        expectStat(panel, 5, LABEL_AURA, "7");
        expectStat(panel, 6, LABEL_MAGICULE, "8");
        expectLabel(panel, 7, LABEL_EP);

        check(labelKeys(panel).subList(1, 8).equals(List.of(
                        LABEL_ALIGNMENT, LABEL_HP, LABEL_SHP, LABEL_ARMOR,
                        LABEL_AURA, LABEL_MAGICULE, LABEL_EP)),
                "the race line must lead into the stat lines in order, got " + labelKeys(panel));

        helper.succeed();
    }

    /**
     * While gating is on, Tensura blurs any EP beyond the viewer's grasp into
     * {@code "<reach>+"} so analysis cannot be used to measure the unmeasurable - and
     * with the override off the same target reads exactly. Both readings are asserted,
     * so the {@code "+"} is provably the gate's doing rather than a formatting quirk.
     */
    @GameTest(template = "test_area")
    public static void ep_blur(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        setAttribute(viewer, TensuraAttributes.ANALYSIS_LEVEL, 2);
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 64);
        // A cap above the attribute's registered minimum of 10.0 - asking for 0 is read
        // back as 10, which would silently throw every figure below off by 10.
        setAttribute(viewer, TensuraAttributes.MAX_AURA, 50.0D);
        setAttribute(viewer, TensuraAttributes.MAX_MAGICULE, 1000.0D);

        ITensuraPlayer data = TensuraStorages.getPlayerDataFrom(viewer);
        check(data != null, "a viewer must carry Tensura player data");
        data.setAnalysisMode(0);

        Cow cloudy = helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        Cow clear = helper.spawn(EntityType.COW, new BlockPos(5, 1, 5));
        setAttribute(cloudy, TensuraAttributes.MAX_AURA, 50.0D);
        setAttribute(cloudy, TensuraAttributes.MAX_MAGICULE, 99999.0D);
        setAttribute(clear, TensuraAttributes.MAX_AURA, 50.0D);
        setAttribute(clear, TensuraAttributes.MAX_MAGICULE, 1500.0D);
        viewer.setPos(cloudy.getX(), cloudy.getY(), cloudy.getZ());

        IPluginConfig gated = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, true,
                TensuraJadeConfig.SHOW_ABILITIES, false));
        IPluginConfig relaxed = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false,
                TensuraJadeConfig.SHOW_ABILITIES, false));

        // Reach = the viewer's max EP (1000 magicule + 50 aura = 1050)
        //       * (1 + level 2 * 0.5) = 2100.
        TooltipRecorder blurred = TooltipRecorder.create();
        TensuraAnalysis.appendEntity(blurred.proxy(), entityAccessor(viewer, cloudy), gated, cloudy);
        int blurredEp = indexOfLabel(blurred, LABEL_EP);
        check(blurredEp >= 0, "a gated panel must still reach an EP line: " + blurred.texts());
        expectStat(blurred, blurredEp, LABEL_EP, "2100+");

        // 1550 sits inside that same reach, so the true reading survives gating.
        TooltipRecorder inReach = TooltipRecorder.create();
        TensuraAnalysis.appendEntity(inReach.proxy(), entityAccessor(viewer, clear), gated, clear);
        int inReachEp = indexOfLabel(inReach, LABEL_EP);
        check(inReachEp >= 0, "a gated panel must still reach an EP line: " + inReach.texts());
        expectStat(inReach, inReachEp, LABEL_EP, "1550");

        // The same cloud read without gating shows its real figure.
        TooltipRecorder ungated = TooltipRecorder.create();
        TensuraAnalysis.appendEntity(ungated.proxy(), entityAccessor(viewer, cloudy), relaxed, cloudy);
        int ungatedEp = indexOfLabel(ungated, LABEL_EP);
        check(ungatedEp >= 0, "an ungated panel must still reach an EP line: " + ungated.texts());
        expectStat(ungated, ungatedEp, LABEL_EP, "100049");

        helper.succeed();
    }

    /**
     * Tensura cycles a single ability at a time on a timer, which is unusable in a
     * static tooltip - so every qualifying ability gets its own line instead. A filled,
     * learned, unmastered slot qualifies; a mastered one does not (Tensura skips them,
     * because they are already known).
     *
     * <p>Skills are learned through Tensura's own {@code NoMagiculeCost} path rather than
     * paid for, because a fresh mob has almost no {@code max_magicule} and would be
     * refused every skill for a reason that has nothing to do with this panel.</p>
     */
    @GameTest(template = "test_area")
    public static void ability_listing(GameTestHelper helper) {
        Cow target = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);

        IAbility ability = TensuraStorages.getAbilityFrom(target);
        check(ability != null, "every living mob must expose Tensura ability storage");
        List<AbilitySlot> slots = ability.getAbilitySlots();
        check(slots != null && slots.size() >= 3,
                "a fresh mob must expose at least three ability slots, got "
                        + (slots == null ? "null" : slots.size()));

        SkillStorage skills = SkillAPI.getSkillsFrom(target);
        ResourceLocation coercion = ResourceLocation.fromNamespaceAndPath("tensura", "coercion");
        ResourceLocation corrosion = ResourceLocation.fromNamespaceAndPath("tensura", "corrosion");
        ResourceLocation farsight = ResourceLocation.fromNamespaceAndPath("tensura", "farsight");

        check(learnFree(skills, coercion), "tensura:coercion must be learnable");
        check(learnFree(skills, corrosion), "tensura:corrosion must be learnable");
        check(learnFree(skills, farsight), "tensura:farsight must be learnable");

        ManasSkillInstance learnedCoercion = learned(skills, coercion);
        ManasSkillInstance learnedCorrosion = learned(skills, corrosion);
        ManasSkillInstance learnedFarsight = learned(skills, farsight);

        // Push mastery past the skill's cap so this one counts as mastered.
        learnedFarsight.setMastery(1_000_000.0D);
        check(learnedFarsight.isMastered(target),
                "mastery must exceed the skill's cap before this ability can be expected to be skipped");

        ability.setAbilitySlot(0, learnedCoercion.getSkill(), 0);
        ability.setAbilitySlot(1, learnedCorrosion.getSkill(), 0);
        ability.setAbilitySlot(2, learnedFarsight.getSkill(), 0);

        IPluginConfig toggles = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false,
                TensuraJadeConfig.SHOW_ABILITIES, true));
        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendEntity(panel.proxy(), entityAccessor(viewer, target), toggles, target);

        int ep = indexOfLabel(panel, LABEL_EP);
        check(ep >= 0, "the panel must reach the EP line: " + panel.texts());
        List<String> abilityLines = panel.texts().subList(ep + 1, panel.size());

        check(abilityLines.size() == 2,
                "expected one line per qualifying ability rather than a single cycling one, got "
                        + abilityLines);
        check(abilityLines.contains(learnedCoercion.getDisplayName().getString()),
                "tensura:coercion must be listed: " + abilityLines);
        check(abilityLines.contains(learnedCorrosion.getDisplayName().getString()),
                "tensura:corrosion must be listed: " + abilityLines);
        check(!abilityLines.contains(learnedFarsight.getDisplayName().getString()),
                "a mastered ability must be skipped, but it was listed: " + abilityLines);
        check(!abilityLines.contains(Component.translatable("tensura.skill.empty").getString()),
                "the 'no abilities' fallback must not appear when abilities were listed: " + abilityLines);

        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Target resolution
    // ---------------------------------------------------------------------

    /**
     * Jade may aim at any entity, so resolution has to unwrap Tensura's multipart heads
     * to the creature whose data belongs on screen. The part itself is a test double
     * implementing Tensura's own {@link ILivingPartEntity} contract, so the unwrapping is
     * exercised deterministically instead of depending on when a live mob builds its
     * segments - that timing is covered separately by
     * {@link #live_multipart_resolution(GameTestHelper)}.
     */
    @GameTest(template = "test_area")
    public static void multipart_resolution(GameTestHelper helper) {
        check(TensuraAnalysis.resolveLiving(null) == null,
                "an empty target resolves to nothing");

        Cow plain = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));
        check(TensuraAnalysis.resolveLiving(plain) == plain,
                "an ordinary mob resolves to itself");

        PartProbe part = new PartProbe(helper.getLevel(), plain);
        check(TensuraAnalysis.resolveLiving(part) == plain,
                "a multipart part must resolve to the head it belongs to");

        PartProbe orphan = new PartProbe(helper.getLevel(), null);
        check(TensuraAnalysis.resolveLiving(orphan) == orphan,
                "a part whose head is unavailable is still a living entity and resolves to itself");

        helper.succeed();
    }

    /**
     * The same unwrapping against a real Tensura multipart mob: the centipede builds its
     * body parts asynchronously, so this waits for them and then requires resolution to
     * land on the head that was spawned.
     */
    @GameTest(template = "test_area")
    public static void live_multipart_resolution(GameTestHelper helper) {
        EntityType<?> registered = BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("tensura", "evil_centipede"));
        check(registered != null, "tensura:evil_centipede must be registered");

        @SuppressWarnings("unchecked")
        EntityType<LivingEntity> centipede = (EntityType<LivingEntity>) registered;
        LivingEntity head = helper.spawn(centipede, new BlockPos(4, 1, 4));

        helper.succeedWhen(() -> {
            LivingMultipartBody body = firstBody(helper.getLevel(), head);
            check(body != null, "the centipede must build its body parts");
            check(TensuraAnalysis.resolveLiving(body) == head,
                    "a live multipart body must resolve to its head");
        });
    }

    // ---------------------------------------------------------------------
    // Block panel
    // ---------------------------------------------------------------------

    /**
     * The block panel reports hardness, explosion resistance, light, whether the viewer
     * holds the right tool, and any integer state property the block has - in that
     * order, with Tensura's own keys. A crop's age appears; its lack of a power property
     * means no redstone line may be emitted at all.
     */
    @GameTest(template = "test_area")
    public static void block_panel_lines(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        IPluginConfig toggles = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false));

        BlockPos cropPos = new BlockPos(4, 1, 4);
        helper.setBlock(cropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        BlockState crop = helper.getBlockState(cropPos);
        BlockPos cropWorldPos = helper.absolutePos(cropPos);

        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(panel.proxy(),
                blockAccessor(viewer, helper.getLevel(), cropWorldPos, crop), toggles);

        check(panel.size() == 5,
                "expected hardness/explosion/light/correct-tool/age and no redstone line, got " + panel.texts());
        expectStat(panel, 0, LABEL_HARDNESS, Float.toString(Blocks.WHEAT.defaultDestroyTime()));
        expectStat(panel, 1, LABEL_EXPLOSION_RES, Float.toString(Blocks.WHEAT.getExplosionResistance()));
        int light = Math.max(
                helper.getLevel().getBrightness(LightLayer.BLOCK, cropWorldPos),
                helper.getLevel().getBrightness(LightLayer.SKY, cropWorldPos));
        expectStat(panel, 2, LABEL_LIGHT, Integer.toString(light));
        expectStat(panel, 3, LABEL_CORRECT_TOOL,
                Component.translatable(viewer.hasCorrectToolForDrops(crop) ? "gui.yes" : "gui.no").getString());
        expectStat(panel, 4, LABEL_AGE, "3");
        check(!labelKeys(panel).contains(LABEL_REDSTONE),
                "wheat has no power property, so no redstone line may be emitted: " + panel.texts());
        check(labelKeys(panel).equals(List.of(
                        LABEL_HARDNESS, LABEL_EXPLOSION_RES, LABEL_LIGHT, LABEL_CORRECT_TOOL, LABEL_AGE)),
                "block label order was " + labelKeys(panel));

        // Below-zero hardness is vanilla's "Unbreakable" text, exactly as the overlay prints it.
        BlockPos bedrockPos = new BlockPos(2, 1, 2);
        helper.setBlock(bedrockPos, Blocks.BEDROCK);
        BlockState bedrock = helper.getBlockState(bedrockPos);
        TooltipRecorder unbreakable = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(unbreakable.proxy(),
                blockAccessor(viewer, helper.getLevel(), helper.absolutePos(bedrockPos), bedrock), toggles);
        expectStat(unbreakable, 0, LABEL_HARDNESS, Component.translatable("item.unbreakable").getString());
        check(unbreakable.size() == 4,
                "bedrock has neither an age nor a power property, so four lines are expected: "
                        + unbreakable.texts());

        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static ManasSkillInstance learned(SkillStorage skills, ResourceLocation id) {
        return skills.getSkill(id).orElseThrow(() -> new GameTestAssertException(
                id + " must be retrievable once learned"));
    }

    /**
     * Learns a skill on {@code skills}, waiving Tensura's magicule cost for it.
     *
     * <p>Tensura's unlock handler refuses a skill whose acquiring cost exceeds the
     * entity's {@code max_magicule}, and a freshly spawned mob has almost none - so a
     * plain {@code learnSkill} is refused for a reason unrelated to what this panel does
     * with a learned skill. The handler honours a {@code NoMagiculeCost} tag on the
     * instance and skips the cost entirely, which is the same path Tensura itself uses to
     * grant skills freely; taking it keeps this test about listing abilities rather than
     * about the magicule economy.</p>
     *
     * <p>The instance is built through {@link ManasSkillInstance#fromNBT} rather than by
     * looking the skill up in the registry directly, because that registry is
     * Architectury's {@code Registrar}, which is not on the game test compile classpath.
     * {@code fromNBT} resolves it inside ManasCore, where Architectury is available.</p>
     *
     * @return whether the skill is now learned
     */
    private static boolean learnFree(SkillStorage skills, ResourceLocation id) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("skill", id.toString());
        ManasSkillInstance instance = ManasSkillInstance.fromNBT(nbt);
        CompoundTag waiver = new CompoundTag();
        waiver.putBoolean("NoMagiculeCost", true);
        instance.setTag(waiver);
        return skills.learnSkill(instance);
    }

    /** The first multipart body near {@code head}, or {@code null} if none exists yet. */
    private static LivingMultipartBody firstBody(Level level, Entity head) {
        AABB search = head.getBoundingBox().inflate(32.0D);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, search)) {
            if (entity != head && entity instanceof LivingMultipartBody body) {
                return body;
            }
        }
        return null;
    }

    /**
     * A multipart body part whose head is supplied directly rather than looked up from
     * the level, so the unwrap can be asserted without waiting on a live mob.
     */
    private static final class PartProbe extends Cow implements ILivingPartEntity {

        private final LivingEntity head;

        private PartProbe(Level level, LivingEntity head) {
            super(EntityType.COW, level);
            this.head = head;
        }

        @Override
        public Entity getHead() {
            return head;
        }

        @Override
        public void onServerHurt(LivingEntity attacker) {
            // Not a live part: nothing listens for damage to it.
        }
    }
}
