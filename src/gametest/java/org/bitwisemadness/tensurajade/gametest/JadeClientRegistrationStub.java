package org.bitwisemadness.tensurajade.gametest;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.Accessor;
import snownee.jade.api.AccessorClientHandler;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.callback.JadeAfterRenderCallback;
import snownee.jade.api.callback.JadeBeforeRenderCallback;
import snownee.jade.api.callback.JadeBeforeTooltipCollectCallback;
import snownee.jade.api.callback.JadeItemModNameCallback;
import snownee.jade.api.callback.JadeRayTraceCallback;
import snownee.jade.api.callback.JadeTooltipCollectedCallback;
import snownee.jade.api.platform.CustomEnchantPower;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ProgressView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A hand-written stand-in for Jade's client registration, used by the config game tests.
 *
 * <p><strong>Why not a {@link java.lang.reflect.Proxy}?</strong> {@code Proxy} builds its
 * class by enumerating the interface's methods, which forces every parameter and return
 * type to be loaded - including {@code net.minecraft.client.gui.screens.Screen}, which
 * Jade's {@code createPluginConfigScreen} takes and returns. On a dedicated server that
 * aborts with {@code invalid dist DEDICATED_SERVER} before a single line of test code
 * runs. A real class is verified lazily instead: the JVM resolves a method's types only
 * when that method is reached, so the 43 methods this panel never calls - including
 * {@code createPluginConfigScreen} - stay unlinked and {@code Screen} is never loaded.</p>
 *
 * <p>Only the two methods the addon's registration actually touches do anything:
 * {@link #addConfig(ResourceLocation, boolean)} records what was declared, and
 * {@link #setConfigCategoryOverride(ResourceLocation, Component)} reproduces Jade's own
 * precondition so the shipped crash regresses loudly here rather than in front of a
 * player. Everything else throws {@link UnsupportedOperationException}, so an unexpected
 * call fails instead of being silently swallowed.</p>
 */
final class JadeClientRegistrationStub implements IWailaClientRegistration {

    /** The ids declared via {@code addConfig}, in call order. */
    private final Set<ResourceLocation> registeredIds = new LinkedHashSet<>();

    /** Every {@code addConfig} call, so defaults can be asserted per key. */
    private final List<RegistrationCall> calls = new ArrayList<>();

    /** Keys {@code setConfigCategoryOverride} was called with; empty by design. */
    private final List<ResourceLocation> overrideCalls = new ArrayList<>();

    /** A single {@code addConfig(ResourceLocation, boolean)} declaration. */
    record RegistrationCall(ResourceLocation id, Object value) {
    }

    // ---------------------------------------------------------------------
    // The two methods the addon's registration uses
    // ---------------------------------------------------------------------

    @Override
    public void addConfig(ResourceLocation id, boolean value) {
        registeredIds.add(id);
        calls.add(new RegistrationCall(id, value));
    }

    /**
     * Reproduces Jade's precondition exactly as {@code ClientRegistrationSession} enforces
     * it: the key must be one already declared this pass (or already known to
     * {@code PluginConfig}), and it must be a primary key - one whose path carries no dot.
     *
     * <p>The addon once called this with the group id {@code tensura_jade:tensura_jade}
     * during registration. That id is primary, but it is not a config key, so Jade threw
     * {@code Unknown config key} and the game refused to start. Grouping is instead
     * derived from {@code getPrimaryKey}, which is why the line was deleted - and why
     * calling this again fails here.</p>
     */
    @Override
    public void setConfigCategoryOverride(ResourceLocation key, Component override) {
        overrideCalls.add(key);
        boolean known = registeredIds.contains(key) || jadeAlreadyKnows(key);
        if (!known) {
            // Jade's own wording; the pre-fix code reached exactly here and threw.
            throw new IllegalArgumentException("Unknown config key: " + key);
        }
        if (!registeredIds.isEmpty() && !key.getPath().contains(".")) {
            // Jade requires an override target to be a primary key, and only a dotted
            // key is ever a config entry - a bare group id can never be one.
            throw new IllegalArgumentException("Only primary config key can be overridden: " + key);
        }
    }

    /**
     * Mirrors the second half of Jade's own check. Jade consults its live config table as
     * well as the ids registered in this pass; in a game test that table is empty, so
     * this is consulted defensively rather than relied upon.
     */
    private static boolean jadeAlreadyKnows(ResourceLocation key) {
        try {
            return snownee.jade.impl.config.PluginConfig.INSTANCE.containsKey(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    Set<ResourceLocation> registeredIds() {
        return Set.copyOf(registeredIds);
    }

    List<RegistrationCall> calls() {
        return List.copyOf(calls);
    }

    List<ResourceLocation> overrideCalls() {
        return List.copyOf(overrideCalls);
    }

    // ---------------------------------------------------------------------
    // Everything else: never called, deliberately loud
    // ---------------------------------------------------------------------

    private static UnsupportedOperationException unused(String method) {
        return new UnsupportedOperationException("these tests never call IWailaClientRegistration." + method);
    }

    @Override
    public <T extends Enum<T>> void addConfig(ResourceLocation id, T value) {
        throw unused("addConfig(ResourceLocation, Enum)");
    }

    @Override
    public void addConfig(ResourceLocation id, String value, Predicate<String> validator) {
        throw unused("addConfig(ResourceLocation, String, Predicate)");
    }

    @Override
    public void addConfig(ResourceLocation id, int value, int min, int max, boolean requiresRestart) {
        throw unused("addConfig(ResourceLocation, int, int, int, boolean)");
    }

    @Override
    public void addConfig(ResourceLocation id, float value, float min, float max, boolean requiresRestart) {
        throw unused("addConfig(ResourceLocation, float, float, float, boolean)");
    }

    @Override
    public void addConfigListener(ResourceLocation id, Consumer<ResourceLocation> listener) {
        throw unused("addConfigListener");
    }

    @Override
    public void setConfigCategoryOverride(ResourceLocation key, List<Component> override) {
        setConfigCategoryOverride(key, override.isEmpty() ? Component.empty() : override.get(0));
    }

    @Override
    public void registerBlockIcon(IComponentProvider<BlockAccessor> provider, Class<? extends Block> block) {
        throw unused("registerBlockIcon");
    }

    @Override
    public void registerBlockComponent(IComponentProvider<BlockAccessor> provider, Class<? extends Block> block) {
        throw unused("registerBlockComponent");
    }

    @Override
    public void registerEntityIcon(IComponentProvider<EntityAccessor> provider, Class<? extends Entity> entity) {
        throw unused("registerEntityIcon");
    }

    @Override
    public void registerEntityComponent(IComponentProvider<EntityAccessor> provider, Class<? extends Entity> entity) {
        throw unused("registerEntityComponent");
    }

    @Override
    public void hideTarget(Block block) {
        throw unused("hideTarget(Block)");
    }

    @Override
    public void hideTarget(EntityType<?> type) {
        throw unused("hideTarget(EntityType)");
    }

    @Override
    public void usePickedResult(Block block) {
        throw unused("usePickedResult(Block)");
    }

    @Override
    public void usePickedResult(EntityType<?> type) {
        throw unused("usePickedResult(EntityType)");
    }

    @Override
    public BlockAccessor.Builder blockAccessor() {
        throw unused("blockAccessor");
    }

    @Override
    public EntityAccessor.Builder entityAccessor() {
        throw unused("entityAccessor");
    }

    @Override
    public boolean shouldHide(Entity entity) {
        throw unused("shouldHide(Entity)");
    }

    @Override
    public boolean shouldHide(BlockState state) {
        throw unused("shouldHide(BlockState)");
    }

    @Override
    public boolean shouldPick(Entity entity) {
        throw unused("shouldPick(Entity)");
    }

    @Override
    public boolean shouldPick(BlockState state) {
        throw unused("shouldPick(BlockState)");
    }

    @Override
    public void addAfterRenderCallback(int priority, JadeAfterRenderCallback callback) {
        throw unused("addAfterRenderCallback");
    }

    @Override
    public void addBeforeRenderCallback(int priority, JadeBeforeRenderCallback callback) {
        throw unused("addBeforeRenderCallback");
    }

    @Override
    public void addRayTraceCallback(int priority, JadeRayTraceCallback callback) {
        throw unused("addRayTraceCallback");
    }

    @Override
    public void addTooltipCollectedCallback(int priority, JadeTooltipCollectedCallback callback) {
        throw unused("addTooltipCollectedCallback");
    }

    @Override
    public void addItemModNameCallback(int priority, JadeItemModNameCallback callback) {
        throw unused("addItemModNameCallback");
    }

    @Override
    public void addBeforeTooltipCollectCallback(int priority, JadeBeforeTooltipCollectCallback callback) {
        throw unused("addBeforeTooltipCollectCallback");
    }

    /**
     * The method that makes a {@code Proxy} unusable here: its parameter and return type
     * are client-only, so a proxy would load {@code Screen} while enumerating methods.
     * As a real class this body is simply never verified, and {@code Screen} stays
     * unloaded on the dedicated server.
     */
    @Override
    public Screen createPluginConfigScreen(Screen parent, Component title) {
        throw unused("createPluginConfigScreen");
    }

    @Override
    public void registerItemStorageClient(IClientExtensionProvider<ItemStack, ItemView> provider) {
        throw unused("registerItemStorageClient");
    }

    @Override
    public void registerFluidStorageClient(IClientExtensionProvider<CompoundTag, FluidView> provider) {
        throw unused("registerFluidStorageClient");
    }

    @Override
    public void registerEnergyStorageClient(IClientExtensionProvider<CompoundTag, EnergyView> provider) {
        throw unused("registerEnergyStorageClient");
    }

    @Override
    public void registerProgressClient(IClientExtensionProvider<CompoundTag, ProgressView> provider) {
        throw unused("registerProgressClient");
    }

    @Override
    public boolean isServerConnected() {
        throw unused("isServerConnected");
    }

    @Override
    public boolean isShowDetailsPressed() {
        throw unused("isShowDetailsPressed");
    }

    @Override
    public boolean maybeLowVisionUser() {
        throw unused("maybeLowVisionUser");
    }

    @Override
    public CompoundTag getServerData() {
        throw unused("getServerData");
    }

    @Override
    public void setServerData(CompoundTag data) {
        throw unused("setServerData");
    }

    @Override
    public ItemStack getBlockCamouflage(LevelAccessor level, BlockPos pos) {
        throw unused("getBlockCamouflage");
    }

    @Override
    public void markAsClientFeature(ResourceLocation id) {
        throw unused("markAsClientFeature");
    }

    @Override
    public void markAsServerFeature(ResourceLocation id) {
        throw unused("markAsServerFeature");
    }

    @Override
    public boolean isClientFeature(ResourceLocation id) {
        throw unused("isClientFeature");
    }

    @Override
    public <T extends Accessor<?>> void registerAccessorHandler(Class<T> type, AccessorClientHandler<T> handler) {
        throw unused("registerAccessorHandler");
    }

    @Override
    public AccessorClientHandler<Accessor<?>> getAccessorHandler(Class<? extends Accessor<?>> type) {
        throw unused("getAccessorHandler");
    }

    @Override
    public void registerCustomEnchantPower(Block block, CustomEnchantPower power) {
        throw unused("registerCustomEnchantPower");
    }
}
