package org.bitwisemadness.tensurajade.analysis;

import io.github.manasmods.manascore.race.api.ManasRace;
import io.github.manasmods.manascore.race.api.ManasRaceInstance;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.race.api.Races;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
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

    // --- Block line labels.
    private static final String LABEL_HARDNESS = "tensura.attribute.block.hardness";
    private static final String LABEL_CORRECT_TOOL = "tensura.attribute.block.correct_tool";
    private static final String LABEL_EXPLOSION_RES = "tensura.attribute.block.explosion_resistance";
    private static final String LABEL_LIGHT = "tensura.attribute.block.light_level";
    private static final String LABEL_BLOCK_EP = "tensura.attribute.existence_points";
    private static final String LABEL_REDSTONE = "tensura.attribute.block.redstone_strength";
    private static final String LABEL_AGE = "tensura.attribute.block.age";

    /** Tensura paints an uncoloured ability name red; uncoloured means "not yet known". */
    private static final int DEFAULT_ABILITY_COLOUR = 0xFF0000;

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
        if (viewer == null || !allowedForEntity(viewer, entity, config)) {
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
        int shown = 0;
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
            shown++;
        }

        if (shown == 0) {
            tooltip.add(Component.translatable("tensura.skill.empty"));
        }
    }

    // ---------------------------------------------------------------------
    // Block panel
    // ---------------------------------------------------------------------

    /**
     * <p>Two calls here are deprecated in the vanilla mappings, and are kept
     * deliberately: {@code Block#getExplosionResistance()} and
     * {@code Player#hasCorrectToolForDrops(BlockState)} are exactly the methods
     * Tensura's own {@code renderBlockAnalysis()} uses. Substituting their successors
     * would risk computing numbers that differ from the overlay this panel replaces.</p>
     *
     * <p>The viewer is accepted as {@link Player} rather than narrowed to the client's
     * {@code LocalPlayer}: only the tool-drop check is needed, which lives on
     * {@code Player}, and accepting the wider type lets this method run under a headless
     * game test server. Jade tooltips only ever render client-side, so the widened check
     * behaves identically in production.</p>
     */
    @SuppressWarnings("deprecation")
    public static void appendBlock(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        if (state == null || state.isAir()) {
            return;
        }
        if (!(accessor.getPlayer() instanceof Player viewer)) {
            return;
        }
        BlockPos pos = accessor.getPosition();
        if (pos == null || !allowedForBlock(viewer, pos, config)) {
            return;
        }

        Block block = state.getBlock();
        Level level = accessor.getLevel();

        // Hardness: Tensura substitutes the vanilla "Unbreakable" text below zero.
        float hardness = block.defaultDestroyTime();
        if (hardness < 0.0F) {
            addStat(tooltip, LABEL_HARDNESS, Component.translatable("item.unbreakable"));
        } else {
            addStat(tooltip, LABEL_HARDNESS, Component.literal(Float.toString(hardness)));
        }

        addStat(tooltip, LABEL_EXPLOSION_RES, Component.literal(Float.toString(block.getExplosionResistance())));

        if (level != null) {
            int light = Math.max(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
            addStat(tooltip, LABEL_LIGHT, Component.literal(Integer.toString(light)));
        }

        addStat(tooltip, LABEL_CORRECT_TOOL,
                Component.translatable(viewer.hasCorrectToolForDrops(state) ? "gui.yes" : "gui.no"));

        addIntegerProperty(tooltip, state, "age", LABEL_AGE);
        addIntegerProperty(tooltip, state, "power", LABEL_REDSTONE);

        if (accessor.getBlockEntity() instanceof CharybdisCoreBlockEntity core) {
            addStat(tooltip, LABEL_BLOCK_EP, shortenedNumber(core.getEP()));
        }
    }

    private static void addIntegerProperty(ITooltip tooltip, BlockState state, String name, String labelKey) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property instanceof IntegerProperty integer && state.hasProperty(integer)) {
            addStat(tooltip, labelKey, Component.literal(Integer.toString(state.getValue(integer))));
        }
    }

    // ---------------------------------------------------------------------
    // Analysis gating - mirrors Tensura's own pre_renderAnalysis() conditions
    // ---------------------------------------------------------------------

    /**
     * Whether the viewer is entitled to this reading at all.
     *
     * <p>Tensura gates its overlay on the {@code analysis_level} and
     * {@code analysis_distance} attributes both being positive, then on the analysis
     * mode: {@code 0} analyses whatever is aimed at (mobs win over blocks), {@code 1}
     * mobs only, {@code 2} blocks only. Missing player data means no analysis.</p>
     */
    public static boolean allowedForEntity(Player viewer, LivingEntity target, IPluginConfig config) {
        if (!config.get(TensuraJadeConfig.RESPECT_ANALYSIS_GATING)) {
            return true;
        }
        int distance = analysisDistance(viewer);
        if (analysisLevel(viewer) <= 0 || distance <= 0) {
            return false;
        }
        int mode = analysisMode(viewer);
        if (mode != 0 && mode != 1) {
            return false;
        }
        return viewer.distanceTo(target) <= (float) distance;
    }

    public static boolean allowedForBlock(Player viewer, BlockPos pos, IPluginConfig config) {
        if (!config.get(TensuraJadeConfig.RESPECT_ANALYSIS_GATING)) {
            return true;
        }
        int distance = analysisDistance(viewer);
        if (analysisLevel(viewer) <= 0 || distance <= 0) {
            return false;
        }
        int mode = analysisMode(viewer);
        if (mode != 0 && mode != 2) {
            return false;
        }
        double dx = pos.getX() + 0.5D - viewer.getX();
        double dy = pos.getY() + 0.5D - viewer.getEyeY();
        double dz = pos.getZ() + 0.5D - viewer.getZ();
        return dx * dx + dy * dy + dz * dz <= (double) distance * (double) distance;
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
