package org.bitwisemadness.tensurajade.gametest;

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

import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.race.api.Races;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.block.HipokuteGrass;
import io.github.manasmods.tensura.entity.multipart.LivingMultipartBody;
import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.Alignment;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import io.github.manasmods.tensura.storage.ability.IAbility;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.storage.player.ITensuraPlayer;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.tensurajade.TensuraJade;
import org.bitwisemadness.tensurajade.analysis.TensuraAnalysis;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import snownee.jade.api.config.IPluginConfig;

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

    // --- Block panel labels (Tensura's own keys). Only the rows Jade has no equivalent
    // for are emitted; see DEDUPLICATED_LABELS for what is deliberately left out.
    private static final String LABEL_EXPLOSION_RES = "tensura.attribute.block.explosion_resistance";
    private static final String LABEL_EGG_HATCH = "tensura.attribute.block.egg_hatch";
    private static final String LABEL_FAILED_GROWTH = "tensura.attribute.block.failed_growth";

    /**
     * Every block key the panel must <em>not</em> emit, because something else already
     * says it: Jade's harvest-tool provider for hardness and correct tool, More Overlays
     * for light level, and Jade's own redstone and crop-progress providers for the last
     * two - each of which reads the very state property Tensura used to read here.
     */
    private static final List<String> DEDUPLICATED_LABELS = List.of(
            "tensura.attribute.block.hardness",
            "tensura.attribute.block.correct_tool",
            "tensura.attribute.block.light_level",
            "tensura.attribute.block.age",
            "tensura.attribute.block.redstone_strength");

    /** Tensura's {@code 16733525}: the orange it draws the failed-growth row in. */
    private static final int FAILED_GROWTH_COLOUR = 0xFF5555;

    private AnalysisGameTests() {
    }

    // ---------------------------------------------------------------------
    // Gating
    // ---------------------------------------------------------------------

    /**
     * Tensura only lets you analyse when both analysis attributes are positive, then only
     * what the analysis mode allows - and the addon's override turns all of that off.
     * Reach is deliberately absent from that list; see
     * {@link TensuraAnalysis#allowedForEntity}. Every branch that remains is covered here.
     */
    @GameTest(template = "test_area")
    public static void gating_matrix(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        Cow target = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));

        // Override off: nothing else may be consulted, so this must hold for a viewer
        // that has never been given an analysis attribute at all.
        IPluginConfig relaxed = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false));
        check(TensuraAnalysis.allowedForEntity(viewer, relaxed),
                "with the override off, mob analysis must not be gated - that is the whole point of the toggle");
        check(TensuraAnalysis.allowedForBlock(viewer, relaxed),
                "with the override off, block analysis must not be gated");

        IPluginConfig gated = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, true));

        // Tensura refuses to analyse at all unless BOTH attributes are positive.
        setAttribute(viewer, TensuraAttributes.ANALYSIS_LEVEL, 0);
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 10);
        check(!TensuraAnalysis.allowedForEntity(viewer, gated),
                "analysis level 0 must deny mob analysis");
        check(!TensuraAnalysis.allowedForBlock(viewer, gated),
                "analysis level 0 must deny block analysis");

        setAttribute(viewer, TensuraAttributes.ANALYSIS_LEVEL, 2);
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 0);
        check(!TensuraAnalysis.allowedForEntity(viewer, gated),
                "an analysis distance of 0 must deny mob analysis");
        check(!TensuraAnalysis.allowedForBlock(viewer, gated),
                "an analysis distance of 0 must deny block analysis");

        // A viewer that satisfies both attributes, standing on top of the target.
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 10);
        ITensuraPlayer data = TensuraStorages.getPlayerDataFrom(viewer);
        check(data != null,
                "a viewer must carry Tensura player data; without it the mode reads -1 and nothing is ever analysed");
        viewer.setPos(target.getX(), target.getY(), target.getZ());

        data.setAnalysisMode(0);
        check(TensuraAnalysis.allowedForEntity(viewer, gated),
                "mode 0 analyses whatever is aimed at, so a mob in reach must be allowed");
        check(TensuraAnalysis.allowedForBlock(viewer, gated),
                "mode 0 analyses whatever is aimed at, so a block in reach must be allowed");

        data.setAnalysisMode(1);
        check(TensuraAnalysis.allowedForEntity(viewer, gated), "mode 1 analyses mobs");
        check(!TensuraAnalysis.allowedForBlock(viewer, gated), "mode 1 never analyses blocks");

        data.setAnalysisMode(2);
        check(!TensuraAnalysis.allowedForEntity(viewer, gated), "mode 2 never analyses mobs");
        check(TensuraAnalysis.allowedForBlock(viewer, gated), "mode 2 analyses blocks");

        // Any other mode analyses nothing. This is also the branch a viewer with no
        // player data takes, since a missing storage reports -1.
        data.setAnalysisMode(3);
        check(!TensuraAnalysis.allowedForEntity(viewer, gated),
                "an unknown analysis mode must deny mob analysis");
        check(!TensuraAnalysis.allowedForBlock(viewer, gated),
                "an unknown analysis mode must deny block analysis");

        // Reach plays no part in the decision, however far away the target is. Tensura's
        // own HUD would stop dead at analysis_distance; Jade does not, so the addon's rows
        // stay put rather than half-vanishing out of an otherwise complete tooltip.
        data.setAnalysisMode(0);
        setAttribute(viewer, TensuraAttributes.ANALYSIS_DISTANCE, 4);
        viewer.setPos(target.getX() + 64.0D, target.getY(), target.getZ());
        check(TensuraAnalysis.allowedForEntity(viewer, gated),
                "a mob 64 blocks beyond the analysis distance must still be allowed");
        check(TensuraAnalysis.allowedForBlock(viewer, gated),
                "a block 64 blocks beyond the analysis distance must still be allowed");

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

        // A freshly spawned mob's health is Tensura's business, not this panel's, so the input is
        // pinned rather than assumed. A fraction proves the line reports *current* health - not the
        // maximum - and that it rounds up as Tensura's does rather than truncating.
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

        Component expectedName = java.util.Objects.requireNonNull(
                races.getRace().get().getRace().getName(), "race name must resolve for comparison");
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
        // No placeholder assertion belongs here any more - the panel has no such row. That a
        // mastered-only list stays silent is pinned by
        // {@link #mastered_only_ability_list_is_silent}, directly below.

        helper.succeed();
    }

    /**
     * A mob whose only ability is mastered qualifies for nothing, and the panel must stop at
     * EP rather than pad itself out. Tensura's own ability widget prints its empty-slot key
     * ("None") for exactly this situation, because an inventory slot has to show something;
     * a tooltip row does not, and the panel used to print that placeholder here.
     */
    @GameTest(template = "test_area")
    public static void mastered_only_ability_list_is_silent(GameTestHelper helper) {
        Cow target = helper.spawn(EntityType.COW, new BlockPos(4, 1, 4));
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);

        IAbility ability = TensuraStorages.getAbilityFrom(target);
        check(ability != null, "every living mob must expose Tensura ability storage");
        List<AbilitySlot> slots = ability.getAbilitySlots();
        check(slots != null && !slots.isEmpty(),
                "a fresh mob must expose at least one ability slot");

        SkillStorage skills = SkillAPI.getSkillsFrom(target);
        ResourceLocation farsight = ResourceLocation.fromNamespaceAndPath("tensura", "farsight");
        check(learnFree(skills, farsight), "tensura:farsight must be learnable");

        // Mastered, so it is skipped - leaving nothing at all to list.
        ManasSkillInstance learnedFarsight = learned(skills, farsight);
        learnedFarsight.setMastery(1_000_000.0D);
        check(learnedFarsight.isMastered(target),
                "mastery must exceed the skill's cap before the ability can be expected to be skipped");

        ability.setAbilitySlot(0, learnedFarsight.getSkill(), 0);

        IPluginConfig toggles = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false,
                TensuraJadeConfig.SHOW_ABILITIES, true));
        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendEntity(panel.proxy(), entityAccessor(viewer, target), toggles, target);

        int ep = indexOfLabel(panel, LABEL_EP);
        check(ep >= 0, "the panel must reach the EP line: " + panel.texts());
        check(panel.size() == ep + 1,
                "a mob with nothing to list must end at EP, but it appended: " + panel.texts());
        check(!panel.texts().contains(Component.translatable("tensura.skill.empty").getString()),
                "no placeholder row may stand in for the abilities: " + panel.texts());

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
     * The block panel carries exactly the analysis Jade cannot report: explosion
     * resistance, always, and nothing else for an ordinary block.
     *
     * <p>Hardness, correct tool, light level, age and redstone strength are asserted
     * <em>absent</em> rather than merely left untested - see {@link #DEDUPLICATED_LABELS}.
     * Bedrock is the sharp case: its below-zero hardness used to become vanilla's
     * "Unbreakable" text here, which is now {@code jade:harvest_tool}'s to say.</p>
     */
    @GameTest(template = "test_area")
    public static void block_panel_lines(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        IPluginConfig toggles = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false));

        // Premise for the age row: a crop really does carry an "age" property, so its
        // absence below is a deliberate omission and not an accident of the block.
        BlockPos cropPos = new BlockPos(4, 1, 4);
        helper.setBlock(cropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        BlockState crop = helper.getBlockState(cropPos);
        check(crop.getBlock().getStateDefinition().getProperty("age") != null,
                "wheat must carry an \"age\" property for this test to mean anything");

        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(panel.proxy(),
                blockAccessor(viewer, helper.getLevel(), helper.absolutePos(cropPos), crop), toggles);
        check(panel.size() == 1,
                "a crop should report explosion resistance only, got " + panel.texts());
        expectStat(panel, 0, LABEL_EXPLOSION_RES, Float.toString(Blocks.WHEAT.getExplosionResistance()));
        expectNoDeduplicatedLines(panel);

        // Bedrock: hardness below zero, which the panel used to render as "Unbreakable".
        BlockPos bedrockPos = new BlockPos(2, 1, 2);
        helper.setBlock(bedrockPos, Blocks.BEDROCK);
        BlockState bedrock = helper.getBlockState(bedrockPos);
        check(bedrock.getBlock().defaultDestroyTime() < 0.0F,
                "bedrock must still be below zero hardness for this premise");
        TooltipRecorder unbreakable = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(unbreakable.proxy(),
                blockAccessor(viewer, helper.getLevel(), helper.absolutePos(bedrockPos), bedrock), toggles);
        check(unbreakable.size() == 1,
                "bedrock should report explosion resistance only, got " + unbreakable.texts());
        expectStat(unbreakable, 0, LABEL_EXPLOSION_RES, Float.toString(Blocks.BEDROCK.getExplosionResistance()));
        expectNoDeduplicatedLines(unbreakable);
        String unbreakableText = Component.translatable("item.unbreakable").getString();
        check(unbreakable.texts().stream().noneMatch(text -> text.contains(unbreakableText)),
                "the panel must not judge bedrock's own hardness; Jade's harvest-tool provider "
                        + "carries that marker: " + unbreakable.texts());

        helper.succeed();
    }

    /**
     * Egg hatching appears only where the block carries {@code hatch} - Tensura's own
     * condition, keyed on the very field it reads - and in the position it occupies
     * relative to the rest of the panel.
     */
    @GameTest(template = "test_area")
    public static void block_panel_hatching(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        IPluginConfig toggles = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false));

        BlockPos eggPos = new BlockPos(4, 1, 4);
        BlockState eggState = Blocks.TURTLE_EGG.defaultBlockState().setValue(BlockStateProperties.HATCH, 2);
        check(eggState.hasProperty(BlockStateProperties.HATCH),
                "Blocks.TURTLE_EGG must carry the hatch property this row is keyed on");
        helper.setBlock(eggPos, eggState);
        BlockState egg = helper.getBlockState(eggPos);

        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(panel.proxy(),
                blockAccessor(viewer, helper.getLevel(), helper.absolutePos(eggPos), egg), toggles);

        check(panel.size() == 2,
                "a turtle egg should report explosion resistance and hatching, got " + panel.texts());
        expectStat(panel, 0, LABEL_EXPLOSION_RES, Float.toString(Blocks.TURTLE_EGG.getExplosionResistance()));
        expectStat(panel, 1, LABEL_EGG_HATCH, Integer.toString(egg.getValue(BlockStateProperties.HATCH)));
        expectNoDeduplicatedLines(panel);

        helper.succeed();
    }

    /**
     * The row that stands in for Jade's growth percentage: bare - no label, no value
     * shape - in Tensura's own orange, and absent while the plant can still grow.
     *
     * <p>Both states are passed unplaced. The panel reads only the state the accessor
     * hands it, so placing the grass would fold the test area's floor and light into a
     * test about nothing but the state.</p>
     */
    @GameTest(template = "test_area")
    public static void failed_growth_replaces_growth_percentage(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        IPluginConfig toggles = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false));
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        Block hipokute = hipokuteBlock();

        BlockState growing = hipokute.defaultBlockState().setValue(HipokuteGrass.AGE, 1);
        TooltipRecorder early = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(early.proxy(),
                blockAccessor(viewer, helper.getLevel(), pos, growing), toggles);
        check(early.size() == 1,
                "an unfinished hipokute should report explosion resistance only, got " + early.texts());
        expectNoDeduplicatedLines(early);

        BlockState failed = hipokute.defaultBlockState().setValue(HipokuteGrass.AGE, 2);
        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(panel.proxy(),
                blockAccessor(viewer, helper.getLevel(), pos, failed), toggles);
        check(panel.size() == 2,
                "a failed hipokute should report explosion resistance and failed growth, got " + panel.texts());
        expectStat(panel, 0, LABEL_EXPLOSION_RES, Float.toString(hipokute.getExplosionResistance()));
        expectFailedGrowth(panel, 1);
        check(!labelKeys(panel).contains("tensura.attribute.block.age"),
                "failed growth replaces the growth row outright; no age line may stand beside it: "
                        + panel.texts());
        expectNoDeduplicatedLines(panel);

        helper.succeed();
    }

    /**
     * Which combinations withdraw Jade's growth percentage, pinned as a policy rather than
     * derived. The two switches that decide it are the addon's own {@code block_analysis}
     * and whether the plant has failed; analysis gating is deliberately not among them.
     *
     * <p>Each case pairs the mixin's decision with what the panel actually printed, because
     * the failures worth catching are the mismatches - cancelling a percentage that nothing
     * replaces leaves a viewer with no growth information in either form.</p>
     */
    @GameTest(template = "test_area")
    public static void crop_percentage_suppression_policy(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        Block hipokute = hipokuteBlock();
        BlockState failed = hipokute.defaultBlockState().setValue(HipokuteGrass.AGE, 2);
        BlockState growing = hipokute.defaultBlockState().setValue(HipokuteGrass.AGE, 1);

        // Panel off: the operator has opted the whole panel out, so Jade's own wording
        // stands untouched. (The provider is what holds the panel back in production -
        // appendBlock does not read the toggle - so only the withdrawal is asserted here.)
        IPluginConfig off = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false,
                TensuraJadeConfig.BLOCK_ANALYSIS, false));
        check(!TensuraAnalysis.suppressCropPercentage(blockAccessor(viewer, helper.getLevel(), pos, failed), off),
                "with block analysis off the panel is disabled, so Jade's percentage must stand");

        IPluginConfig on = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false,
                TensuraJadeConfig.BLOCK_ANALYSIS, true));

        // Panel on and the plant finished: the percentage is withdrawn and replaced.
        check(TensuraAnalysis.suppressCropPercentage(blockAccessor(viewer, helper.getLevel(), pos, failed), on),
                "a failed plant with the panel on must suppress the percentage");
        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(panel.proxy(), blockAccessor(viewer, helper.getLevel(), pos, failed), on);
        expectFailedGrowth(panel, 1);

        // Panel on but the plant can still grow: there is nothing to replace, so nothing is
        // withdrawn, and Jade's percentage stays.
        check(!TensuraAnalysis.suppressCropPercentage(blockAccessor(viewer, helper.getLevel(), pos, growing), on),
                "a plant that can still grow must keep Jade's percentage");

        // Gating denies this viewer every row - but the percentage is withdrawn all the same.
        // A viewer may be refused the analysis and still be shown a false number by default,
        // and declining to correct it would turn the denial into an endorsement of 67%.
        setAttribute(viewer, TensuraAttributes.ANALYSIS_LEVEL, 0);
        IPluginConfig denied = config(values(
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING, true,
                TensuraJadeConfig.BLOCK_ANALYSIS, true));
        check(!TensuraAnalysis.allowedForBlock(viewer, denied),
                "a viewer with no analysis level must be denied block analysis");
        check(TensuraAnalysis.suppressCropPercentage(blockAccessor(viewer, helper.getLevel(), pos, failed), denied),
                "analysis gating must not spare the percentage - it is still wrong for this plant");
        TooltipRecorder gated = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(gated.proxy(), blockAccessor(viewer, helper.getLevel(), pos, failed), denied);
        check(!labelKeys(gated).contains(LABEL_FAILED_GROWTH),
                "a denied viewer gets no rows from us at all: " + gated.texts());

        helper.succeed();
    }

    /**
     * The predicate both the block panel and the mixin that hides Jade's percentage
     * depend on, pinned to the exact comparison Tensura makes.
     *
     * <p>Tensura's {@code renderBlockAnalysis} tests {@code instanceof HipokuteGrass},
     * reads {@code getAgeProperty()} and compares it against the literal {@code 2}. That 2
     * is the last stage the plant can reach at all - its own {@code randomTick} returns as
     * soon as {@code age >= 2} - so it marks a finished plant even though the age property
     * runs one value higher. Nothing here may drift from that literal: the cancelled
     * percentage and the row that replaces it must switch on precisely one condition.</p>
     */
    @GameTest(template = "test_area")
    public static void reports_failed_growth_matches_tensura(GameTestHelper helper) {
        Block hipokute = hipokuteBlock();

        // Read out of Tensura's bytecode, deliberately not expressed as HipokuteGrass.MAX_AGE:
        // that constant is the age property's top value (3), while the plant's growth tick
        // stops at 2, and only 2 is what Tensura's overlay prints.
        final int tensuraFailedAge = 2;

        check("age".equals(HipokuteGrass.AGE.getName()),
                "Tensura locates growth by the property name \"age\", but it is now named "
                        + HipokuteGrass.AGE.getName());
        check(HipokuteGrass.MAX_AGE == 3,
                "hipokute's age property is expected to span 0..3, but HipokuteGrass.MAX_AGE is "
                        + HipokuteGrass.MAX_AGE + "; the literal " + tensuraFailedAge + " Tensura "
                        + "renders as Failed Growth may no longer be the plant's last reachable "
                        + "stage - revisit reportsFailedGrowth");

        for (int age = 0; age <= HipokuteGrass.MAX_AGE; age++) {
            BlockState state = hipokute.defaultBlockState().setValue(HipokuteGrass.AGE, age);
            boolean expected = age == tensuraFailedAge;
            check(TensuraAnalysis.reportsFailedGrowth(state) == expected,
                    "hipokute at age " + age + " reported growth failure as "
                            + TensuraAnalysis.reportsFailedGrowth(state) + " but expected " + expected);
        }

        // A mature crop also sits at the top of its range and also carries an "age"
        // property; Tensura only prints failed growth for HipokuteGrass, so neither may
        // the predicate.
        BlockState matureWheat = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3);
        check(!TensuraAnalysis.reportsFailedGrowth(matureWheat),
                "a mature crop is not a failed hipokute");
        check(!TensuraAnalysis.reportsFailedGrowth(Blocks.BEDROCK.defaultBlockState()),
                "a block with no age property at all cannot have failed to grow");

        helper.succeed();
    }

    /**
     * Redstone power used to be printed here too. It is left to Jade, which reads the
     * very same {@code power} property - so a block that carries it gets no line from
     * this panel at all.
     */
    @GameTest(template = "test_area")
    public static void redstone_line_is_omitted(GameTestHelper helper) {
        Player viewer = helper.makeMockPlayer(GameType.CREATIVE);
        IPluginConfig toggles = config(values(TensuraJadeConfig.RESPECT_ANALYSIS_GATING, false));

        // Premise: this block really does carry the property Tensura used to read, so
        // the absence below is an omission rather than an irrelevant block.
        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState();
        check(wire.hasProperty(BlockStateProperties.POWER),
                "redstone wire must carry the power property for this test to mean anything");

        TooltipRecorder panel = TooltipRecorder.create();
        TensuraAnalysis.appendBlock(panel.proxy(),
                blockAccessor(viewer, helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), wire),
                toggles);
        check(panel.size() == 1,
                "a redstone wire should report explosion resistance only, got " + panel.texts());
        expectNoDeduplicatedLines(panel);

        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Block panel helpers
    // ---------------------------------------------------------------------

    /** Asserts the panel says none of the things Jade or More Overlays already say. */
    private static void expectNoDeduplicatedLines(TooltipRecorder panel) {
        List<String> present = labelKeys(panel);
        for (String deduplicated : DEDUPLICATED_LABELS) {
            check(!present.contains(deduplicated),
                    "the panel emitted <" + deduplicated + ">, which another mod already reports: "
                            + panel.texts());
        }
    }

    /**
     * Asserts the failed-growth row: the Tensura translatable, unlabelled and unvalued,
     * in Tensura's own orange. Unlike every other row it is not a
     * {@code "<label>: <value>"} line, so {@code expectStat} cannot describe it.
     */
    private static void expectFailedGrowth(TooltipRecorder panel, int index) {
        Component line = panel.line(index);
        String expected = Component.translatable(LABEL_FAILED_GROWTH).getString();
        check(expected.equals(line.getString()),
                "line " + index + " was <" + line.getString() + "> but the failed-growth row is bare <"
                        + expected + "> (full panel: " + panel.texts() + ")");
        TextColor colour = line.getStyle().getColor();
        check(colour != null && colour.getValue() == FAILED_GROWTH_COLOUR,
                "failed growth must be Tensura's orange 0x" + Integer.toHexString(FAILED_GROWTH_COLOUR)
                        + " but the row's colour was " + colour);
    }

    /** Tensura's hipokute grass, failing loudly if the registry no longer has it. */
    private static Block hipokuteBlock() {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("tensura", "hipokute_grass"));
        check(block != null && block != Blocks.AIR,
                "tensura:hipokute_grass must be registered - the failed-growth row is keyed on it");
        check(block instanceof HipokuteGrass,
                "tensura:hipokute_grass is a " + block.getClass().getSimpleName()
                        + ", but failed growth is keyed on HipokuteGrass");
        return block;
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
