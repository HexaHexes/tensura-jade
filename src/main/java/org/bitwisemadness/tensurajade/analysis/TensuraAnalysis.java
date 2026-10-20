package org.bitwisemadness.tensurajade.analysis;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.race.api.Races;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.block.HipokuteGrass;
import io.github.manasmods.tensura.block.entity.CharybdisCoreBlockEntity;
import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.Alignment;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import io.github.manasmods.tensura.storage.ability.IAbility;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.storage.player.ITensuraPlayer;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;

/**
 * Reproduces the content of Tensura's analysis overlay as Jade tooltip lines.
 *
 * <p><strong>Content parity, not renderer reuse.</strong> Tensura draws its panel from
 * {@code private static} methods inside {@code OverlayHandler} straight onto the HUD;
 * those cannot be called from a tooltip. Instead this class re-reads the same public
 * APIs the overlay reads, and rebuilds each line - including Tensura's own translation
 * keys, so wording and localisation match by construction rather than by copying
 * English strings.</p>
 *
 * <p>Every method signature here uses only vanilla or Jade types, the class holds no
 * Tensura-typed fields, and it references no client-only classes. All three are
 * deliberate: providers may be loaded while Tensura is absent, so the class must stay
 * loadable; and the panel is verified by headless game tests, which run on a dedicated
 * server where loading a client class aborts. Callers gate on
 * {@code ModList.isLoaded("tensura")} before touching this class at all.</p>
 */
public final class TensuraAnalysis {

    // --- Entity line labels: Tensura's own keys, so they read and localise identically.
    private static final String LABEL_HP = "tensura.vanilla_attribute.health.shortened_name";
    private static final String LABEL_SHP = "tensura.attribute.spiritual_health.shortened_name";
    private static final String LABEL_ARMOR = "attribute.name.generic.armor";
    private static final String LABEL_AURA = "tensura.attribute.aura.shortened_name";
    private static final String LABEL_MAGICULE = "tensura.attribute.magicule.shortened_name";
    private static final String LABEL_EP = "tensura.attribute.existence_points.shortened_name";
    private static final String LABEL_ALIGNMENT = "tensura_jade.alignment";

    // --- Block line labels. Only the lines Jade has no equivalent for; see appendBlock.
    private static final String LABEL_EXPLOSION_RES = "tensura.attribute.block.explosion_resistance";
    private static final String LABEL_BLOCK_EP = "tensura.attribute.existence_points";
    private static final String LABEL_EGG_HATCH = "tensura.attribute.block.egg_hatch";
    private static final String LABEL_FAILED_GROWTH = "tensura.attribute.block.failed_growth";

    /** Tensura paints an uncoloured ability name red; uncoloured means "not yet known". */
    private static final int DEFAULT_ABILITY_COLOUR = 0xFF0000;

    /** Tensura's {@code 16733525}: the orange it prints "Failed Growth" in. */
    private static final int FAILED_GROWTH_COLOUR = 0xFF5555;

    /** Age at which {@code HipokuteGrass} counts as failed - the top of its {@code AGE_3}. */
    private static final int HIPROKUTE_FAILED_AGE = 2;

    private TensuraAnalysis() {
    }

    // ---------------------------------------------------------------------
    // Target resolution
    // ---------------------------------------------------------------------

    /**
     * Resolves whatever Jade aimed at into the {@link LivingEntity} whose data should be
     * shown, unwrapping Tensura's multipart heads the same way the overlay does.
     */
    public static LivingEntity resolveLiving(Entity raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ILivingPartEntity part) {
            Entity head = part.getHead();
            if (head instanceof LivingEntity living) {
                return living;
            }
        }
        return raw instanceof LivingEntity living ? living : null;
    }

    // ---------------------------------------------------------------------
    // Entity panel
    // ---------------------------------------------------------------------

    public static void appendEntity(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config, LivingEntity entity) {
        Player viewer = accessor.getPlayer();
        if (viewer == null || !allowedForEntity(viewer, config)) {
            return;
        }

        IExistence existence = TensuraStorages.getExistenceFrom(entity);
        if (existence == null) {
            // Data not synced for this target - fail soft rather than show a half-panel.
            return;
        }

        Alignment alignment = existence.getAlignment();
        int alignmentColour = alignment == null ? 0xFFFFFF : alignment.getColor() & 0xFFFFFF;

        addRace(tooltip, entity, alignmentColour);

        if (alignment != null) {
            addStat(tooltip, LABEL_ALIGNMENT, alignment.getName());
        }

        addStat(tooltip, LABEL_HP, Component.literal(Integer.toString(Mth.ceil(entity.getHealth()))));
        addStat(tooltip, LABEL_SHP, Component.literal(Integer.toString((int) Math.ceil(existence.getSpiritualHealth()))));
        addStat(tooltip, LABEL_ARMOR, Component.literal(Integer.toString(entity.getArmorValue())));
        addStat(tooltip, LABEL_AURA, shortenedNumber(existence.getAura()));
        addStat(tooltip, LABEL_MAGICULE, shortenedNumber(existence.getMagicule()));
        addStat(tooltip, LABEL_EP, epValue(viewer, entity, config));

        if (config.get(TensuraJadeConfig.SHOW_ABILITIES)) {
            addAbilities(tooltip, entity);
        }
    }

    /**
     * Tensura tints the race line with the target's alignment colour rather than
     * printing an alignment name - so the colouring is part of the parity, not decoration.
     */
    private static void addRace(ITooltip tooltip, LivingEntity entity, int alignmentColour) {
        Races races = RaceAPI.getRaceFrom(entity);
        if (races == null) {
            return;
        }
        Optional<ManasRaceInstance> instance = races.getRace();
        if (instance.isEmpty()) {
            return;
        }
        ManasRace race = instance.get().getRace();
        if (race == null) {
            return;
        }
        MutableComponent name = race.getName();
        if (name == null) {
            return;
        }
        MutableComponent line = name.copy();
        line.withStyle(style -> style.withColor(TextColor.fromRgb(alignmentColour)));
        tooltip.add(line);
    }

    /**
     * Maximum EP - except while gating, where Tensura blurs anything beyond the viewer's
     * grasp into {@code "12k+"} so analysis cannot be used to measure the unmeasurable.
     */
    private static Component epValue(Player viewer, LivingEntity entity, IPluginConfig config) {
        double maxEp = EnergyHelper.getMaxEP(entity);
        if (!config.get(TensuraJadeConfig.RESPECT_ANALYSIS_GATING)) {
            return shortenedNumber(maxEp);
        }

        double reach = EnergyHelper.getMaxEP(viewer) * (1.0 + (double) analysisLevel(viewer) * 0.5);
        if (maxEp > reach) {
            return concat(shortenedNumber(reach), Component.literal("+"));
        }
        return shortenedNumber(maxEp);
    }

    /**
     * Tensura cycles a single ability at a time on a timer, which is unusable in a static
     * tooltip - so every qualifying ability is listed instead. What qualifies matches the
     * overlay exactly: slots that are filled, learned, and not yet mastered, because
     * Tensura skips mastered abilities (they are already known).
     *
     * <p><strong>Nothing is written when nothing qualifies.</strong> Tensura's own ability
     * widget prints its empty-slot key ({@code tensura.skill.empty}, "None") because an
     * inventory slot has to render as something; a tooltip row does not. A placeholder here
     * would add a line to every mob that has no listable abilities - and that is not the
     * same set as mobs with no abilities at all, since an entirely mastered mob qualifies
     * for nothing. The panel simply ends after EP.</p>
     */
    private static void addAbilities(ITooltip tooltip, LivingEntity entity) {
        IAbility ability = TensuraStorages.getAbilityFrom(entity);
        if (ability == null) {
            return;
        }
        List<AbilitySlot> slots = ability.getAbilitySlots();
        if (slots == null || slots.isEmpty()) {
            return;
        }

        SkillStorage storage = SkillAPI.getSkillsFrom(entity);
        for (AbilitySlot slot : slots) {
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            ManasSkill skill = slot.getSkill();
            if (skill == null) {
                continue;
            }
            Optional<ManasSkillInstance> learned = storage.getSkill(skill);
            if (learned.isEmpty()) {
                continue;
            }
            ManasSkillInstance instance = learned.get();
            if (instance.isMastered(entity)) {
                continue;
            }

            MutableComponent name = instance.getDisplayName();
            TextColor declared = name.getStyle().getColor();
            TextColor colour = declared != null ? declared : TextColor.fromRgb(DEFAULT_ABILITY_COLOUR);
            MutableComponent line = name.copy();
            line.withStyle(style -> style.withColor(colour));
            tooltip.add(line);
        }
    }

    // ---------------------------------------------------------------------
    // Block panel
    // ---------------------------------------------------------------------

    /**
     * <p>{@code Block#getExplosionResistance()} is read straight off the block rather than
     * recomputed, because it is exactly what Tensura's own {@code renderBlockAnalysis}
     * reads - substituting any equivalent would risk a number that differs from the
     * overlay this panel replaces.</p>
     *
     * <p><strong>What is absent, and why.</strong> Hardness and correct tool are dropped
     * because Jade's harvest-tool provider already reports them (unbreakable blocks
     * included, once {@code minecraft:harvest_tool.show_unbreakable} is on); light level is
     * left to More Overlays; and crop age and redstone power are dropped because Jade's own
     * crop-progress and redstone providers read the very state properties Tensura reads, so
     * emitting them here would state each fact twice inside one tooltip. What remains is the
     * analysis Jade has no equivalent for.</p>
     *
     * <p>The viewer is accepted as {@link Player} rather than narrowed to the client's
     * {@code LocalPlayer} only to feed Tensura's own gating check, which takes a
     * {@code Player} - and accepting the wider type lets this method run under a headless
     * game test server. Jade tooltips only ever render client-side, so the widened check
     * behaves identically in production.</p>
     */
    public static void appendBlock(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        if (state == null || state.isAir()) {
            return;
        }
        if (!(accessor.getPlayer() instanceof Player viewer)) {
            return;
        }
        if (!allowedForBlock(viewer, config)) {
            return;
        }

        Block block = state.getBlock();
        addStat(tooltip, LABEL_EXPLOSION_RES, Component.literal(Float.toString(block.getExplosionResistance())));

        if (accessor.getBlockEntity() instanceof CharybdisCoreBlockEntity core) {
            addStat(tooltip, LABEL_BLOCK_EP, shortenedNumber(core.getEP()));
        }

        if (state.hasProperty(BlockStateProperties.HATCH)) {
            addStat(tooltip, LABEL_EGG_HATCH,
                    Component.literal(Integer.toString(state.getValue(BlockStateProperties.HATCH))));
        }

        // Bare, unlabelled: Tensura draws "Failed Growth" as the value under its "Age"
        // row, and the percentage this replaces has been cancelled for this block by
        // CropProgressProviderMixin - so there is no label on the other side to pair with.
        if (reportsFailedGrowth(state)) {
            tooltip.add(Component.translatable(LABEL_FAILED_GROWTH)
                    .withStyle(style -> style.withColor(TextColor.fromRgb(FAILED_GROWTH_COLOUR))));
        }
    }

    /**
     * Whether Tensura would report {@code Failed Growth} instead of a growth percentage for
     * this block - one test behind both this panel's line and the mixin that hides Jade's.
     *
     * <p>Mirrors {@code OverlayHandler#renderBlockAnalysis}, which tests the block against
     * {@code HipokuteGrass}, reads its own {@code getAgeProperty()} - vanilla {@code AGE_3},
     * named {@code "age"}, running 0..3 - and compares it against the literal {@code 2}.
     * That 2 is the last stage the plant can reach: its own {@code randomTick} returns as
     * soon as {@code age >= 2}, so 2 marks a plant that has stopped growing even though the
     * property permits one value more. Jade's percentage is suppressed on exactly this
     * condition because Jade scales the age by a constant chosen for the property - 3.0 for
     * {@code AGE_3} - rather than by how far the plant can actually grow, so it reports
     * {@code 2/3 = 67%} for a plant that will never grow again, and a green "67%" beside an
     * orange "Failed Growth" would contradict itself.</p>
     *
     * <p>Held here so the panel and the mixin cannot drift apart: the signature stays
     * vanilla-only even though the body needs Tensura, exactly as {@link #appendBlock} does
     * with {@code CharybdisCoreBlockEntity}.</p>
     */
    public static boolean reportsFailedGrowth(BlockState state) {
        if (state == null || !(state.getBlock() instanceof HipokuteGrass hipokute)) {
            return false;
        }
        IntegerProperty age = hipokute.getAgeProperty();
        return state.hasProperty(age) && state.getValue(age) == HIPROKUTE_FAILED_AGE;
    }

    /**
     * Whether Jade's crop-progress row should be cancelled for this block: true exactly
     * when the panel above has printed the {@code Failed Growth} line the percentage would
     * otherwise contradict.
     *
     * <p><strong>Only the addon's own switch suppresses.</strong> {@link #appendBlock} is
     * also asked {@link #allowedForBlock}, and a viewer whose analysis is gated out gets no
     * rows from us at all - yet their percentage is cancelled regardless. That asymmetry is
     * deliberate: gating decides what else Tensura is willing to reveal (existence points,
     * abilities), not whether {@code 67%} is a true statement about a plant that will never
     * grow again. Denying a viewer the analysis does not make the false number correct, so
     * the row is withdrawn from them too and nothing replaces it. {@code block_analysis} is
     * different in kind - it is the operator opting the panel out altogether - so with it
     * off Jade's content is left exactly as Jade writes it.</p>
     *
     * <p>Named for what the mixin does with it rather than for what it computes, so the
     * call site reads as the decision it is.</p>
     */
    public static boolean suppressCropPercentage(BlockAccessor accessor, IPluginConfig config) {
        return config.get(TensuraJadeConfig.BLOCK_ANALYSIS) && reportsFailedGrowth(accessor.getBlockState());
    }

    // ---------------------------------------------------------------------
    // Analysis gating - Tensura's pre_renderAnalysis() conditions, minus its reach limit
    // ---------------------------------------------------------------------

    /**
     * Whether the viewer is entitled to entity analysis at all.
     *
     * <p>Tensura gates its overlay on the {@code analysis_level} and
     * {@code analysis_distance} attributes both being positive, then on the analysis
     * mode: {@code 0} analyses whatever is aimed at (mobs win over blocks), {@code 1}
     * mobs only, {@code 2} blocks only. Missing player data means no analysis.</p>
     *
     * <p><strong>The reach limit is deliberately not mirrored.</strong> Tensura picks its
     * target with {@code getTargetingEntity(player, analysisDistance, ...)} and its block
     * with a raycast of the same length, so its own HUD stops dead at
     * {@code analysis_distance} - 5 blocks by default, with the attribute topping out at
     * 1024. Jade does not stop there: it validates entity data out to
     * {@code entityInteractionRange() + 21}, roughly 24. Comparing distances here would
     * leave a band in which Jade's own rows - name, health, effects, NBT - stay on screen
     * while ours vanish, and a tooltip that half-disappears is worse than analysis
     * reaching a little further than the HUD would. The attribute is still consulted:
     * {@code analysis_distance <= 0} is Tensura's own statement that this viewer has no
     * analysis to spend, and that is kept.</p>
     *
     * <p>Which makes the parameter list a statement rather than an omission: the answer
     * depends only on the viewer, never on what is being looked at.</p>
     */
    public static boolean allowedForEntity(Player viewer, IPluginConfig config) {
        if (!config.get(TensuraJadeConfig.RESPECT_ANALYSIS_GATING)) {
            return true;
        }
        if (analysisLevel(viewer) <= 0 || analysisDistance(viewer) <= 0) {
            return false;
        }
        int mode = analysisMode(viewer);
        return mode == 0 || mode == 1;
    }

    /**
     * As {@link #allowedForEntity}, for blocks: analysis mode {@code 0} or {@code 2},
     * never {@code 1}. The reach limit is dropped here for the same reason, and it is just
     * as visible - Jade keeps reporting a block long past the raycast Tensura would have
     * cast at it.
     */
    public static boolean allowedForBlock(Player viewer, IPluginConfig config) {
        if (!config.get(TensuraJadeConfig.RESPECT_ANALYSIS_GATING)) {
            return true;
        }
        if (analysisLevel(viewer) <= 0 || analysisDistance(viewer) <= 0) {
            return false;
        }
        int mode = analysisMode(viewer);
        return mode == 0 || mode == 2;
    }

    private static int analysisLevel(Player viewer) {
        return (int) viewer.getAttributeValue(TensuraAttributes.ANALYSIS_LEVEL);
    }

    private static int analysisDistance(Player viewer) {
        return (int) viewer.getAttributeValue(TensuraAttributes.ANALYSIS_DISTANCE);
    }

    /** Returns {@code -1} when the viewer has no Tensura player data, which never analyses. */
    private static int analysisMode(Player viewer) {
        ITensuraPlayer data = TensuraStorages.getPlayerDataFrom(viewer);
        return data == null ? -1 : data.getAnalysisMode();
    }

    // ---------------------------------------------------------------------
    // Formatting - Tensura's own recipes: "<label>: <value>"
    // ---------------------------------------------------------------------

    private static void addStat(ITooltip tooltip, String labelKey, Component value) {
        tooltip.add(Component.translatable(labelKey).append(Component.literal(": ")).append(value));
    }

    /**
     * The number shortening Tensura's overlay prints, reproduced rather than reused.
     *
     * <p>Tensura keeps this as {@code RenderHelper.getShortenedNumber}, a static method
     * inside a client-rendering utility whose neighbours take {@code GuiGraphics} and
     * {@code MultiBufferSource}. Loading <em>that</em> class on a dedicated server aborts
     * with {@code invalid dist DEDICATED_SERVER}, because verification resolves those
     * parameter types even for a pure method. This panel is therefore transcribed here
     * byte-for-byte from Tensura's implementation - the same two thresholds, the same
     * {@code DecimalFormat} patterns, and Tensura's own {@code million_index} /
     * {@code billion_index} keys - so values read identically while staying loadable on
     * both sides.</p>
     *
     * <p>Anything below a million prints as a bare integer, as {@code "14000"}; a million
     * or more becomes {@code "1.4M"}-style through Tensura's translation keys, so the
     * wording still comes from Tensura's language file rather than from here.</p>
     */
    private static Component shortenedNumber(double value) {
        if (value < 1_000_000.0D) {
            return Component.literal(new DecimalFormat("#").format(value));
        }
        DecimalFormat scaled = new DecimalFormat("#.##");
        if (value < 1_000_000_000.0D) {
            return Component.translatable("tensura.main_menu.million_index",
                    scaled.format(value / 1_000_000.0D));
        }
        return Component.translatable("tensura.main_menu.billion_index",
                scaled.format(value / 1_000_000_000.0D));
    }

    private static MutableComponent concat(Component... parts) {
        MutableComponent out = Component.empty();
        for (Component part : parts) {
            out.append(part);
        }
        return out;
    }
}
