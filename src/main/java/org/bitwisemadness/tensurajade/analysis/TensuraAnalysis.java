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
import io.github.manasmods.tensura.util.EnergyHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.bitwisemadness.mobinfocommon.analysis.AnalysisGating;
import org.bitwisemadness.tensurajade.TensuraJade;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;

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
 *
 * <p><strong>The gating rules and the number shortening are no longer this class's
 * own.</strong> They moved into the shared {@code mob_info_common} library so
 * Pseudocore's horn tooltip gates and blurs by the same source; what remains here
 * is the thin wiring from this addon's plugin config into that library.</p>
 */
public final class TensuraAnalysis {

    // --- Entity line labels: Tensura's own keys, so they read and localise identically.
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

    /** The silver the SHP readout colour-shifts to (owner: pack-proof). */
    private static final int SHP_SILVER = 0xC0C0C0;

    /**
     * The grey Jade prints HP numbers in when its theme cannot be asked:
     * Jade's own {@code ColorPalette.DEFAULT.normal} and the active
     * Excalibur theme's {@code text.colors.normal} both read {@code #a0a0a0}.
     */
    private static final int JADE_NORMAL_FALLBACK = 0xA0A0A0;

    /**
     * The heart font: vanilla's own heart shapes, flattened to white
     * silhouettes under {@code assets/tensura_jade/textures/font/} - the
     * silver arrives as the code-side tint, so no resource pack that
     * reskins vanilla's hearts can change this row.
     */
    private static final ResourceLocation SHP_HEART_FONT =
            ResourceLocation.fromNamespaceAndPath("tensura_jade", "shp_hearts");

    /** The font's three glyphs: full, half and empty heart. */
    private static final String HEART_FULL = "\uE000";
    private static final String HEART_HALF = "\uE001";
    private static final String HEART_EMPTY = "\uE002";

    /** Vanilla's heart scale: two points make one heart. */
    private static final double POINTS_PER_HEART = 2.0;

    /** Age at which {@code HipokuteGrass} counts as failed - the top of its {@code AGE_3}. */
    private static final int HIPROKUTE_FAILED_AGE = 2;

    /** One-shot guard: the glyph diagnostic logs at most once per JVM. */
    private static boolean glyphDiagnosticLogged;

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

        // SHP leads the panel as the bare readout - no "SHP: " label (owner's
        // brief) - so it pairs with Jade's own unlabelled hearts row. That row
        // already reports HP, so the panel does not repeat it (issues #42/#43).
        tooltip.add(shpValue(existence.getSpiritualHealth(), maxSpiritualHealth(entity), config));

        addRace(tooltip, entity, alignmentColour);

        if (alignment != null) {
            addStat(tooltip, LABEL_ALIGNMENT, alignment.getName());
        }

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
     *
     * <p>The blur itself now lives in the shared library
     * ({@code mob_info_common}'s {@code AnalysisGating}), so this panel and Pseudocore's
     * horn tooltip blur EP by the same recipe from the same source.</p>
     */
    private static Component epValue(Player viewer, LivingEntity entity, IPluginConfig config) {
        return AnalysisGating.epReadout(viewer, EnergyHelper.getMaxEP(entity),
                config.get(TensuraJadeConfig.RESPECT_ANALYSIS_GATING));
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
        Player viewer = accessor.getPlayer();
        if (viewer == null) {
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
     * <p>The rules are Tensura's own {@code pre_renderAnalysis} conditions, minus the
     * reach limit - both now live in the shared library
     * ({@code mob_info_common}'s {@code AnalysisGating}), so this panel and
     * Pseudocore gate by the same source. The library's javadoc carries the full
     * rationale, including why the distance the target sits at plays no part.</p>
     *
     * <p>Which makes the parameter list a statement rather than an omission: the answer
     * depends only on the viewer, never on what is being looked at.</p>
     */
    public static boolean allowedForEntity(Player viewer, IPluginConfig config) {
        return AnalysisGating.allowedForEntity(viewer, config.get(TensuraJadeConfig.RESPECT_ANALYSIS_GATING));
    }

    /**
     * As {@link #allowedForEntity}, for blocks: analysis mode {@code 0} or {@code 2},
     * never {@code 1}.
     */
    public static boolean allowedForBlock(Player viewer, IPluginConfig config) {
        return AnalysisGating.allowedForBlock(viewer, config.get(TensuraJadeConfig.RESPECT_ANALYSIS_GATING));
    }

    // ---------------------------------------------------------------------
    // Formatting - Tensura's own recipes: "<label>: <value>"
    // ---------------------------------------------------------------------

    private static void addStat(ITooltip tooltip, String labelKey, Component value) {
        tooltip.add(Component.translatable(labelKey).append(Component.literal(": ")).append(value));
    }

    /**
     * The SHP readout in Jade's HP format (owner's brief): hearts while
     * the maximum sits inside Jade's own render threshold - the same
     * option that decides whether its health row draws hearts or a
     * fraction - and Jade's ceiled {@code current/max} above it. Either
     * form is colour-shifted to silver.
     */
    private static Component shpValue(double current, double max, IPluginConfig config) {
        String glyphs;
        Component row;
        if (max <= 0) {
            // no registered ceiling: the points known, never a faked maximum
            glyphs = hearts(current, 0, true);
            row = silver(Component.literal(glyphs)
                    .withStyle(style -> style.withFont(SHP_HEART_FONT)));
        } else if (max > config.getInt(JadeIds.MC_ENTITY_HEALTH_MAX_FOR_RENDER)) {
            // Jade labels its text form with one full heart before the value,
            // drawn tight against it (owner: no space) - its text mode shows
            // a single heart glyph, then the number
            String value = ((int) Math.ceil(current)) + "/" + ((int) Math.ceil(max));
            glyphs = HEART_FULL;
            // the value is the heart literal's CHILD: with no font of its own
            // it inherits shp_hearts, which has no digit glyphs - the in-game
            // "full heart then squares" row (owner's report)
            row = silver(Component.literal(HEART_FULL)
                    .withStyle(style -> style.withFont(SHP_HEART_FONT))
                    .append(Component.literal(value)
                            .withStyle(style -> style.withFont(Style.DEFAULT_FONT)
                                    .withColor(TextColor.fromRgb(jadeNumberColour())))));
        } else {
            glyphs = hearts(current, max, false);
            row = silver(Component.literal(glyphs)
                    .withStyle(style -> style.withFont(SHP_HEART_FONT)));
        }
        logGlyphDiagnostic(glyphs, row);
        return row;
    }

    /**
     * The heart string: full, half and empty slots up to the maximum in
     * vanilla's own arithmetic (two points a heart). Without a ceiling
     * only the filled slots appear - an unknown maximum draws no empties.
     */
    private static String hearts(double current, double max, boolean currentOnly) {
        int icons = currentOnly ? (int) Math.ceil(current / POINTS_PER_HEART)
                : (int) Math.ceil(max / POINTS_PER_HEART);
        int full = (int) Math.floor(current / POINTS_PER_HEART);
        boolean half = current / POINTS_PER_HEART - full >= 0.5;
        StringBuilder hearts = new StringBuilder(icons);
        for (int i = 0; i < icons; i++) {
            if (i < full) {
                hearts.append(HEART_FULL);
            } else if (i == full && half) {
                hearts.append(HEART_HALF);
            } else {
                hearts.append(HEART_EMPTY);
            }
        }
        return hearts.toString();
    }

    /** The colour-shift: the row's text (and white heart glyphs) read silver. */
    private static MutableComponent silver(MutableComponent text) {
        return text.withStyle(style -> style.withColor(TextColor.fromRgb(SHP_SILVER)));
    }

    /**
     * The colour Jade draws its HP numbers with - the active theme's normal
     * text colour, read live so a themed install pairs exactly (owner:
     * "Jade uses a greyish color for the HP numbers and we should do the
     * same"). Any failure - headless server, Jade absent - falls back to
     * Jade's own default normal.
     */
    private static int jadeNumberColour() {
        try {
            return IThemeHelper.get().getNormalColor() & 0xFFFFFF;
        } catch (Throwable t) {
            return JADE_NORMAL_FALLBACK;
        }
    }

    /**
     * One-shot ground truth for the tofu-box report: the built string's
     * codepoints, the row's style font, whether that font id is a loaded
     * FontSet, and what the renderer's own lookup returns per codepoint.
     * Reflection only - this class must stay loadable on a dedicated
     * server (the headless panel tests), so no client class may appear
     * at compile time.
     */
    private static void logGlyphDiagnostic(String glyphs, Component row) {
        if (glyphDiagnosticLogged) {
            return;
        }
        glyphDiagnosticLogged = true;
        try {
            Class<?> environment = Class.forName("net.neoforged.fml.loading.FMLEnvironment");
            Object dist = environment.getField("dist").get(null);
            String distName = (String) dist.getClass().getMethod("name").invoke(dist);
            if (!"CLIENT".equals(distName)) {
                TensuraJade.LOGGER.info("SHP glyph diagnostic: skipped (dist={})", distName);
                return;
            }
            Class<?> minecraftType = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftType.getMethod("getInstance").invoke(null);
            Field managerField = minecraftType.getDeclaredField("fontManager");
            managerField.setAccessible(true);
            Object fontManager = managerField.get(minecraft);
            Field setsField = fontManager.getClass().getDeclaredField("fontSets");
            setsField.setAccessible(true);
            Map<?, ?> fontSets = (Map<?, ?>) setsField.get(fontManager);
            StringBuilder report = new StringBuilder("SHP glyph diagnostic: dist=");
            report.append(distName).append(" styleFont=").append(row.getStyle().getFont());
            report.append(" codepoints=");
            glyphs.codePoints()
                    .forEach(cp -> report.append(String.format(Locale.ROOT, "U+%04X ", cp)));
            boolean loaded = fontSets.containsKey(SHP_HEART_FONT);
            report.append(" fontSetLoaded=").append(loaded);
            if (loaded) {
                appendFontSetProbes(report, fontManager, fontSets.get(SHP_HEART_FONT));
            } else {
                report.append(" knownFonts=").append(fontSets.keySet());
            }
            Object defaultSet = fontSets.get(Style.DEFAULT_FONT);
            report.append(" defaultE000=")
                    .append(defaultSet == null ? "absent" : glyphLookup(defaultSet, HEART_FULL));
            TensuraJade.LOGGER.info("{}", report);
        } catch (Throwable t) {
            TensuraJade.LOGGER.info("SHP glyph diagnostic: failed ({})", t.toString());
        }
    }

    /**
     * The deep probes: whether our FontSet is the renderer's shared
     * missing set, how many providers survived the reload, and the glyph
     * each codepoint resolves to. Any reflection miss degrades to one
     * "probeFailed=" note - the base report above is never lost.
     */
    private static void appendFontSetProbes(StringBuilder report, Object fontManager, Object fontSet) {
        try {
            Field missingField = fontManager.getClass().getDeclaredField("missingFontSet");
            missingField.setAccessible(true);
            Object missingSet = missingField.get(fontManager);
            report.append(" isMissingSet=").append(fontSet == missingSet);
            Method resolver = fontManager.getClass()
                    .getDeclaredMethod("getFontSetCached", ResourceLocation.class);
            resolver.setAccessible(true);
            report.append(" rendererResolvesMissing=")
                    .append(resolver.invoke(fontManager, SHP_HEART_FONT) == missingSet);
            Field activeField = fontSet.getClass().getDeclaredField("activeProviders");
            activeField.setAccessible(true);
            Object active = activeField.get(fontSet);
            report.append(" activeProviders=").append(active instanceof List<?> list
                    ? list.size() : String.valueOf(active));
            report.append(" full=").append(glyphLookup(fontSet, HEART_FULL));
            report.append(" half=").append(glyphLookup(fontSet, HEART_HALF));
            report.append(" empty=").append(glyphLookup(fontSet, HEART_EMPTY));
        } catch (ReflectiveOperationException | RuntimeException e) {
            report.append(" probeFailed=").append(e.toString());
        }
    }

    /** One glyph lookup as the renderer performs it, named for the log. */
    private static String glyphLookup(Object fontSet, String glyph) {
        try {
            Method lookup = fontSet.getClass().getMethod("getGlyphInfo", int.class, boolean.class);
            Object info = lookup.invoke(fontSet, glyph.codePointAt(0), false);
            if (info == null) {
                return "null";
            }
            String name = info.getClass().getSimpleName();
            return name + (name.contains("Missing") ? " (BOX)" : "");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "error:" + e;
        }
    }

    /** The target's SHP ceiling from Tensura's attribute, or 0 when unregistered. */
    private static double maxSpiritualHealth(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(TensuraAttributes.MAX_SPIRITUAL_HEALTH);
        return instance == null ? 0 : instance.getValue();
    }

    /**
     * The number shortening Tensura's overlay prints. The transcription itself
     * now lives in the shared library ({@code mob_info_common}'s
     * {@code AnalysisGating}), so both consumers shorten by the same recipe;
     * this delegate keeps the panel's call sites short.
     */
    private static Component shortenedNumber(double value) {
        return AnalysisGating.shortenedNumber(value);
    }
}
