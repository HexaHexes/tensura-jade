package org.bitwisemadness.tensurajade.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.tensurajade.TensuraJade;
import org.bitwisemadness.tensurajade.config.TensuraJadeConfig;
import org.bitwisemadness.tensurajade.gametest.JadeClientRegistrationStub.RegistrationCall;
import org.bitwisemadness.tensurajade.provider.TensuraBlockProvider;
import org.bitwisemadness.tensurajade.provider.TensuraEntityProvider;
import snownee.jade.impl.config.PluginConfig;

import java.util.List;
import java.util.Set;

import static org.bitwisemadness.tensurajade.gametest.TestSupport.check;

/**
 * Covers the addon's configuration contract with Jade.
 *
 * <p>Two of these are regression tests for real shipped crashes. The first: the
 * addon once called {@code setConfigCategoryOverride(CATEGORY, ...)} during registration,
 * but Jade only accepts that for keys it has already been told about, and the group id is
 * not one of them - so the game refused to start with {@code Unknown config key}. The
 * stub reproduces Jade's precondition exactly, so re-adding that line fails here instead
 * of in front of a player. The second: {@code register()} used to declare all five
 * toggles, including the two Jade derives from the providers' {@code getUid()} during
 * {@code ClientRegistrationSession.end()} - and because that pass runs before the queued
 * entries are flushed, the second copy was rejected with {@code Duplicate config key},
 * the flush was abandoned, and the remaining toggles never existed.</p>
 *
 * <p>These use a hand-written stub rather than a {@link java.lang.reflect.Proxy}: a proxy
 * enumerates the interface's methods, which loads the client-only {@code Screen} type Jade
 * returns from {@code createPluginConfigScreen} and aborts a dedicated server before any
 * test code runs. See {@link JadeClientRegistrationStub}.</p>
 */
@GameTestHolder(TensuraJade.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigGameTests {

    private ConfigGameTests() {
    }

    /**
     * Registering the toggles completes without Jade refusing any key, declares the three
     * settings this addon alone owns with all of them on by default - and leaves
     * {@code entity_analysis} and {@code block_analysis} for Jade to create.
     *
     * <p>The last clause is the regression test for a real shipped crash. Jade registers
     * those two keys itself from {@code TensuraEntityProvider} and
     * {@code TensuraBlockProvider} while ending its registration session, and it does so
     * <em>before</em> flushing the entries queued here. Naming them here as well therefore
     * reached {@code PluginConfig.addConfig} a second time, which threw
     * {@code Duplicate config key} and aborted the flush - leaving
     * {@code respect_analysis_gating}, read on every analysis line, unregistered, so every
     * read of it was an NPE.</p>
     */
    @GameTest(template = "test_area")
    public static void config_registration_completes(GameTestHelper helper) {
        JadeClientRegistrationStub registration = new JadeClientRegistrationStub();
        TensuraJadeConfig.register(registration);

        Set<ResourceLocation> expected = Set.of(
                TensuraJadeConfig.SHOW_ABILITIES,
                TensuraJadeConfig.HIDE_TENSURA_OVERLAY,
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING);
        check(registration.registeredIds().equals(expected),
                "registered ids were " + registration.registeredIds() + " but expected " + expected);

        // The shipped NPE: these two belong to Jade's tryAddConfig, not to this method.
        for (ResourceLocation providerOwned : List.of(
                TensuraJadeConfig.ENTITY_ANALYSIS, TensuraJadeConfig.BLOCK_ANALYSIS)) {
            check(!registration.registeredIds().contains(providerOwned),
                    providerOwned + " must be left to Jade's tryAddConfig; registering it here too "
                            + "duplicates it and aborts the session with 'Duplicate config key'");
        }

        for (RegistrationCall call : registration.calls()) {
            check(Boolean.TRUE.equals(call.value()),
                    "toggle " + call.id() + " defaults to " + call.value() + " but every addon toggle ships on");
        }

        // The crash fix: registration never names a category, because Jade derives it.
        check(registration.overrideCalls().isEmpty(),
                "register() called setConfigCategoryOverride(" + registration.overrideCalls()
                        + "); grouping is derived from getPrimaryKey, not declared");

        helper.succeed();
    }

    /**
     * Jade derives a setting's category by truncating its dotted path at the first dot -
     * so all five toggles must resolve to the addon's own group id, and that group id
     * must be a primary key itself, because it has no dot to truncate.
     *
     * <p>Deliberately <em>not</em> asserted: that {@code getPrimaryKey} of the group id
     * returns the group id. Jade's implementation truncates the path at the first dot
     * without checking one exists, so it throws on a dotless key - and Jade only ever
     * derives a category <em>from</em> a dotted config key, never from a category. The
     * group id is a label, not something Jade re-derives; the toggles above are what it
     * actually derives, and they are checked here.</p>
     */
    @GameTest(template = "test_area")
    public static void category_keys_derive(GameTestHelper helper) {
        ResourceLocation category = TensuraJadeConfig.CATEGORY;
        List<ResourceLocation> keys = List.of(
                TensuraJadeConfig.ENTITY_ANALYSIS,
                TensuraJadeConfig.BLOCK_ANALYSIS,
                TensuraJadeConfig.SHOW_ABILITIES,
                TensuraJadeConfig.HIDE_TENSURA_OVERLAY,
                TensuraJadeConfig.RESPECT_ANALYSIS_GATING);

        check(PluginConfig.isPrimaryKey(category),
                category + " has no dot and must therefore be its own primary key");

        for (ResourceLocation key : keys) {
            ResourceLocation derived = PluginConfig.getPrimaryKey(key);
            check(derived.equals(category),
                    key + " derives to " + derived + " but the toggles are grouped under " + category);
            check(!PluginConfig.isPrimaryKey(key),
                    key + " must not be a primary key: it carries a dot, which is what groups it");
        }

        helper.succeed();
    }

    /** The floating-overlay suppression is one of the documented toggles and ships enabled. */
    @GameTest(template = "test_area")
    public static void overlay_config_default(GameTestHelper helper) {
        JadeClientRegistrationStub registration = new JadeClientRegistrationStub();
        TensuraJadeConfig.register(registration);

        check(registration.calls().stream().anyMatch(
                        call -> call.id().equals(TensuraJadeConfig.HIDE_TENSURA_OVERLAY)
                                && Boolean.TRUE.equals(call.value())),
                "hiding Tensura's own overlay must be registered on by default, got "
                        + registration.calls());
        check(registration.registeredIds().contains(TensuraJadeConfig.HIDE_TENSURA_OVERLAY),
                "the suppression toggle must be exposed in Jade's settings");

        helper.succeed();
    }

    /**
     * The provider-owned keys must keep matching the providers Jade derives them from.
     *
     * <p>This is the invariant the duplicate-key fix rests on, and nothing else checks it.
     * {@code register()} deliberately does <em>not</em> declare {@code entity_analysis} or
     * {@code block_analysis}, because Jade creates those two from the providers' own
     * {@code getUid()}. That only holds while the uid <em>is</em> the constant: change
     * either provider's return value and Jade registers a key nobody declared, while the
     * constant the panel and the settings screen still read is left with no entry at all -
     * so {@code config.get(BLOCK_ANALYSIS)} throws an NPE again, with every test in this
     * project still green.</p>
     */
    @GameTest(template = "test_area")
    public static void provider_uids_own_their_keys(GameTestHelper helper) {
        ResourceLocation entityUid = new TensuraEntityProvider().getUid();
        ResourceLocation blockUid = new TensuraBlockProvider().getUid();

        check(entityUid.equals(TensuraJadeConfig.ENTITY_ANALYSIS),
                "TensuraEntityProvider reports " + entityUid + " but the panel and settings read "
                        + TensuraJadeConfig.ENTITY_ANALYSIS);
        check(blockUid.equals(TensuraJadeConfig.BLOCK_ANALYSIS),
                "TensuraBlockProvider reports " + blockUid + " but the panel and settings read "
                        + TensuraJadeConfig.BLOCK_ANALYSIS);

        // Both must also stay undeclared here - that is what makes leaving them to Jade
        // safe rather than merely what makes the previous test pass.
        JadeClientRegistrationStub registration = new JadeClientRegistrationStub();
        TensuraJadeConfig.register(registration);
        check(!registration.registeredIds().contains(TensuraJadeConfig.ENTITY_ANALYSIS),
                "entity_analysis must stay undeclared so Jade's own copy is the only one");
        check(!registration.registeredIds().contains(TensuraJadeConfig.BLOCK_ANALYSIS),
                "block_analysis must stay undeclared so Jade's own copy is the only one");

        helper.succeed();
    }
}
