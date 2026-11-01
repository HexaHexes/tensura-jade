package org.bitwisemadness.tensurajade.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Test doubles for the Jade interfaces the analysis panel is written against.
 *
 * <p>Four Jade API types involved here - {@link ITooltip}, {@link IPluginConfig},
 * {@link EntityAccessor} and {@link BlockAccessor} - are interfaces whose methods the
 * panel reads, so a {@link Proxy} can stand in for each. Nothing in this class reflects
 * into Tensura or Jade internals: the doubles only implement the handful of methods the
 * production code actually calls, and deliberately throw on anything else so an
 * unexpected call fails loudly instead of being silently swallowed.</p>
 *
 * <p>{@link snownee.jade.api.IWailaClientRegistration} is the deliberate exception: it
 * cannot be proxied on a dedicated server, so it is stood in for by the hand-written
 * {@link JadeClientRegistrationStub} instead.</p>
 *
 * <p>These classes live in the {@code gametest} source set and are never packaged into
 * the released jar.</p>
 */
public final class TestSupport {

    private TestSupport() {
    }

    // ---------------------------------------------------------------------
    // Assertion helpers
    // ---------------------------------------------------------------------

    /** Fails the surrounding game test with {@code message} when {@code condition} is false. */
    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    // ---------------------------------------------------------------------
    // ITooltip - records the lines the production code adds, in order
    // ---------------------------------------------------------------------

    /** A recording {@link ITooltip}. Read the result back with {@link #lines()} / {@link #texts()}. */
    public static final class TooltipRecorder {
        private final List<Component> lines = new ArrayList<>();
        private final ITooltip proxy;

        private TooltipRecorder() {
            this.proxy = (ITooltip) Proxy.newProxyInstance(
                    ITooltip.class.getClassLoader(), new Class<?>[]{ITooltip.class}, handler());
        }

        public static TooltipRecorder create() {
            return new TooltipRecorder();
        }

        public ITooltip proxy() {
            return proxy;
        }

        /** The recorded components, in the order the production code added them. */
        public List<Component> lines() {
            return List.copyOf(lines);
        }

        /** The recorded components rendered to plain text. */
        public List<String> texts() {
            return lines.stream().map(Component::getString).toList();
        }

        public Component line(int index) {
            check(index < lines.size(), "expected at least " + (index + 1) + " line(s) but got " + lines.size()
                    + ": " + texts());
            return lines.get(index);
        }

        public String text(int index) {
            return line(index).getString();
        }

        public int size() {
            return lines.size();
        }

        /**
         * The translation key a {@code "<label>: <value>"} line is labelled with, or
         * {@code null} if the line is not a labelled translatable (a race or ability name).
         */
        public String labelKey(int index) {
            return labelKeyOf(line(index));
        }

        private InvocationHandler handler() {
            return (proxy, method, args) -> switch (method.getName()) {
                case "add" -> {
                    // ITooltip has several add(...) overloads; the panel only uses the
                    // Component ones, so anything else is an unexpected call.
                    if (args != null && args.length >= 1 && args[0] instanceof Component component) {
                        lines.add(component);
                        yield null;
                    }
                    throw new UnsupportedOperationException("unexpected ITooltip." + method.getName() + " arguments");
                }
                case "append" -> {
                    if (args != null && args.length >= 1 && args[0] instanceof Component component) {
                        lines.add(component);
                        yield null;
                    }
                    throw new UnsupportedOperationException("unexpected ITooltip." + method.getName() + " arguments");
                }
                case "size" -> lines.size();
                case "isEmpty" -> lines.isEmpty();
                case "clear" -> {
                    lines.clear();
                    yield null;
                }
                case "toString" -> "TooltipRecorder" + lines;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("unexpected ITooltip." + method.getName());
            };
        }
    }

    /** The translation key behind a line's label, or {@code null} when it is not a translatable. */
    public static String labelKeyOf(Component line) {
        return line.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    /**
     * Asserts line {@code index} is labelled with {@code labelKey}.
     *
     * <p>Checked structurally rather than against rendered text: whether a mod's own
     * translation file has loaded on this headless server is not what these tests are
     * about, and the key is the stable contract either way.</p>
     */
    public static void expectLabel(TooltipRecorder lines, int index, String labelKey) {
        String actual = labelKeyOf(lines.line(index));
        check(labelKey.equals(actual), "line " + index + " was labelled <" + actual + "> but expected <"
                + labelKey + "> (full panel: " + lines.texts() + ")");
    }

    /** The label key of every line that carries one, in order; unlabelled lines become {@code null}. */
    public static List<String> labelKeys(TooltipRecorder lines) {
        List<String> keys = new ArrayList<>();
        for (Component line : lines.lines()) {
            keys.add(labelKeyOf(line));
        }
        return keys;
    }

    /** Index of the (first) line labelled {@code labelKey}, or {@code -1}. */
    public static int indexOfLabel(TooltipRecorder lines, String labelKey) {
        List<String> keys = labelKeys(lines);
        return keys.indexOf(labelKey);
    }

    /**
     * Asserts that {@code lines[index]} is exactly the {@code "<label>: <value>"} line the
     * panel builds for {@code labelKey}, with the label resolved through the live language
     * map rather than matched against a hardcoded English string.
     */
    public static void expectStat(TooltipRecorder lines, int index, String labelKey, String expectedValue) {
        String expected = Component.translatable(labelKey).getString() + ": " + expectedValue;
        check(expected.equals(lines.text(index)),
                "line " + index + " was <" + lines.text(index) + "> but expected <" + expected + ">"
                        + " (full panel: " + lines.texts() + ")");
    }

    // ---------------------------------------------------------------------
    // IPluginConfig - a map lookup, nothing more
    // ---------------------------------------------------------------------

    /** Builds an {@link IPluginConfig} backed by {@code values}; an unset key is an error. */
    public static IPluginConfig config(Map<ResourceLocation, Boolean> values) {
        return (IPluginConfig) Proxy.newProxyInstance(
                IPluginConfig.class.getClassLoader(), new Class<?>[]{IPluginConfig.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "get" -> {
                        if (args != null && args.length == 1 && args[0] instanceof ResourceLocation key) {
                            Boolean value = values.get(key);
                            check(value != null, "game test did not configure " + key);
                            yield value;
                        }
                        throw new UnsupportedOperationException("unexpected IPluginConfig.get arguments");
                    }
                    case "toString" -> "TestPluginConfig" + values;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("unexpected IPluginConfig." + method.getName());
                });
    }

    /** An empty map, to be filled with the toggles a given test cares about. */
    public static Map<ResourceLocation, Boolean> configValues() {
        return new HashMap<>();
    }

    // ---------------------------------------------------------------------
    // EntityAccessor / BlockAccessor - the narrow slice the panel reads
    // ---------------------------------------------------------------------

    public static EntityAccessor entityAccessor(Player viewer, Entity entity) {
        return (EntityAccessor) Proxy.newProxyInstance(
                EntityAccessor.class.getClassLoader(), new Class<?>[]{EntityAccessor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayer" -> viewer;
                    case "getEntity", "getRawEntity" -> entity;
                    case "getLevel" -> entity.level();
                    case "toString" -> "TestEntityAccessor(" + entity + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("unexpected EntityAccessor." + method.getName());
                });
    }

    public static BlockAccessor blockAccessor(Player viewer, Level level, BlockPos pos, BlockState state) {
        return blockAccessor(viewer, level, pos, state, null);
    }

    public static BlockAccessor blockAccessor(Player viewer, Level level, BlockPos pos, BlockState state,
                                              BlockEntity blockEntity) {
        return (BlockAccessor) Proxy.newProxyInstance(
                BlockAccessor.class.getClassLoader(), new Class<?>[]{BlockAccessor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayer" -> viewer;
                    case "getBlockState" -> state;
                    case "getBlock" -> state.getBlock();
                    case "getPosition" -> pos;
                    case "getLevel" -> level;
                    case "getBlockEntity" -> blockEntity;
                    case "toString" -> "TestBlockAccessor(" + state + " @ " + pos + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("unexpected BlockAccessor." + method.getName());
                });
    }

    // ---------------------------------------------------------------------
    // IWailaClientRegistration - see JadeClientRegistrationStub, in its own file
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // Attribute helpers
    // ---------------------------------------------------------------------

    /**
     * Sets an attribute's base value, failing loudly if the entity type does not carry it.
     *
     * <p>Attributes are not added to a live entity by hand - the supplier decides - so an
     * entity whose type lacks {@code attribute} cannot be given it. Everything this panel
     * reads is registered by Tensura either for {@code EntityType.PLAYER} or for every
     * entity type, so a failure here means the premise changed rather than a flaky test.</p>
     */
    public static void setAttribute(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        check(instance != null, entity.getType().getDescriptionId() + " does not carry "
                + attribute.getRegisteredName() + "; the panel's attribute premise needs revisiting");
        instance.setBaseValue(value);
    }

    /** Reads an attribute, failing loudly if the entity type does not carry it. */
    public static double attribute(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        check(instance != null, entity.getType().getDescriptionId() + " does not carry "
                + attribute.getRegisteredName() + "; the panel's attribute premise needs revisiting");
        return instance.getValue();
    }

    /** Whether an entity type carries an attribute; used to prove the premise, not to guess it. */
    public static boolean typeCarries(EntityType<? extends LivingEntity> type, Holder<Attribute> attribute) {
        AttributeSupplier supplier = DefaultAttributes.getSupplier(type);
        return supplier != null && supplier.hasAttribute(attribute);
    }

    /** A live {@link ResourceLocation} for a namespaced key, for readable test setup. */
    public static ResourceLocation key(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** Convenience: the panel's own config keys, as a mutable map. */
    public static Map<ResourceLocation, Boolean> values(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("key/value pairs expected");
        }
        Map<ResourceLocation, Boolean> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((ResourceLocation) keyValues[i], (Boolean) keyValues[i + 1]);
        }
        return map;
    }
}
