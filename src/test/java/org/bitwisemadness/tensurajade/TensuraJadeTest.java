package org.bitwisemadness.tensurajade;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Keeps the junit CI check non-vacuous: the suite must always assert something
 * real, so the check cannot pass by running zero tests.
 */
class TensuraJadeTest {
    @Test
    void modIdIsPlainLowercaseIdentifier() {
        assertTrue(TensuraJade.MOD_ID.matches("[a-z][a-z0-9_]*"),
                "MOD_ID must be a plain lowercase identifier, got: " + TensuraJade.MOD_ID);
    }
}
