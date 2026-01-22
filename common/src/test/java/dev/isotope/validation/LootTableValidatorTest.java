package dev.isotope.validation;

import dev.isotope.data.loot.*;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LootTableValidator.
 *
 * Note: These tests focus on validation logic that doesn't require
 * Minecraft registry access. Item existence checks are skipped in tests.
 */
class LootTableValidatorTest {

    private static final Identifier TEST_TABLE = Identifier.fromNamespaceAndPath("test", "table");

    // ===== Helper Methods =====

    private LootEntry createSimpleEntry(String item, int weight) {
        return new LootEntry(
            "minecraft:item",
            Optional.of(Identifier.fromNamespaceAndPath("test", item)),
            weight,
            0,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    private LootPool createPoolWithEntries(NumberProvider rolls, List<LootEntry> entries) {
        return new LootPool(
            "pool1",  // name
            rolls,
            NumberProvider.constant(0),  // bonusRolls
            entries,
            Collections.emptyList(),  // conditions
            Collections.emptyList()   // functions
        );
    }

    private LootTableStructure createTable(List<LootPool> pools) {
        return new LootTableStructure(
            TEST_TABLE,  // id
            "minecraft:chest",  // type
            pools,
            Collections.emptyList(),  // functions
            Optional.empty()  // randomSequence
        );
    }

    // ===== Null Structure Tests =====

    @Test
    @DisplayName("Null structure returns error")
    void nullStructureReturnsError() {
        var result = LootTableValidator.validate(TEST_TABLE, null);

        assertTrue(result.hasErrors());
        assertEquals(1, result.errorCount());
        assertEquals(LootTableValidator.IssueType.EMPTY_POOL, result.issues().get(0).type());
    }

    // ===== Empty Pool Tests =====

    @Test
    @DisplayName("Empty pool generates warning")
    void emptyPoolGeneratesWarning() {
        LootPool emptyPool = createPoolWithEntries(NumberProvider.constant(1), Collections.emptyList());
        LootTableStructure structure = createTable(List.of(emptyPool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertTrue(result.hasIssues());
        assertEquals(1, result.warningCount());
        assertEquals(LootTableValidator.IssueType.EMPTY_POOL, result.issues().get(0).type());
    }

    @Test
    @DisplayName("Valid pool with entries passes")
    void validPoolPasses() {
        LootEntry entry = createSimpleEntry("diamond", 1);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(1), List.of(entry));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertFalse(result.hasErrors());
    }

    // ===== Zero Rolls Tests =====

    @Test
    @DisplayName("Zero max rolls generates warning")
    void zeroMaxRollsGeneratesWarning() {
        LootEntry entry = createSimpleEntry("diamond", 1);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(0), List.of(entry));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertTrue(result.hasIssues());
        assertTrue(result.issues().stream()
            .anyMatch(i -> i.type() == LootTableValidator.IssueType.ZERO_ROLLS));
    }

    @Test
    @DisplayName("Uniform rolls with zero max generates warning")
    void uniformZeroMaxRollsGeneratesWarning() {
        LootEntry entry = createSimpleEntry("diamond", 1);
        LootPool pool = createPoolWithEntries(NumberProvider.uniform(0, 0), List.of(entry));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertTrue(result.issues().stream()
            .anyMatch(i -> i.type() == LootTableValidator.IssueType.ZERO_ROLLS));
    }

    // ===== Zero Weight Tests =====

    @Test
    @DisplayName("Zero weight entry generates warning")
    void zeroWeightGeneratesWarning() {
        LootEntry entry = createSimpleEntry("diamond", 0);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(1), List.of(entry));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertTrue(result.issues().stream()
            .anyMatch(i -> i.type() == LootTableValidator.IssueType.ZERO_WEIGHT));
    }

    @Test
    @DisplayName("All zero weight entries generates unreachable error")
    void allZeroWeightGeneratesUnreachableError() {
        LootEntry entry1 = createSimpleEntry("diamond", 0);
        LootEntry entry2 = createSimpleEntry("gold", 0);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(1), List.of(entry1, entry2));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertTrue(result.hasErrors());
        assertTrue(result.issues().stream()
            .anyMatch(i -> i.type() == LootTableValidator.IssueType.UNREACHABLE_ENTRY));
    }

    // ===== Duplicate Entry Tests =====

    @Test
    @DisplayName("Duplicate items in same pool generates info")
    void duplicateItemsGenerateInfo() {
        LootEntry entry1 = createSimpleEntry("diamond", 5);
        LootEntry entry2 = createSimpleEntry("diamond", 3);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(1), List.of(entry1, entry2));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertEquals(1, result.infoCount());
        assertTrue(result.issues().stream()
            .anyMatch(i -> i.type() == LootTableValidator.IssueType.DUPLICATE_ENTRY));
    }

    @Test
    @DisplayName("Different items in same pool is valid")
    void differentItemsNoIssue() {
        LootEntry entry1 = createSimpleEntry("diamond", 5);
        LootEntry entry2 = createSimpleEntry("gold", 3);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(1), List.of(entry1, entry2));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertFalse(result.issues().stream()
            .anyMatch(i -> i.type() == LootTableValidator.IssueType.DUPLICATE_ENTRY));
    }

    // ===== Multiple Pool Tests =====

    @Test
    @DisplayName("Multiple pools are all validated")
    void multiplePoolsAllValidated() {
        LootPool emptyPool = createPoolWithEntries(NumberProvider.constant(1), Collections.emptyList());
        LootEntry entry = createSimpleEntry("diamond", 0);
        LootPool zeroWeightPool = createPoolWithEntries(NumberProvider.constant(0), List.of(entry));

        LootTableStructure structure = createTable(List.of(emptyPool, zeroWeightPool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        // Should have warnings for both pools
        assertTrue(result.warningCount() >= 2);
    }

    // ===== Issue Indexing Tests =====

    @Test
    @DisplayName("Pool issues have correct pool index")
    void poolIssuesHaveCorrectIndex() {
        LootPool pool1 = createPoolWithEntries(NumberProvider.constant(1), List.of(createSimpleEntry("a", 1)));
        LootPool emptyPool = createPoolWithEntries(NumberProvider.constant(1), Collections.emptyList());

        LootTableStructure structure = createTable(List.of(pool1, emptyPool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        var emptyPoolIssue = result.issues().stream()
            .filter(i -> i.type() == LootTableValidator.IssueType.EMPTY_POOL)
            .findFirst();

        assertTrue(emptyPoolIssue.isPresent());
        assertEquals(1, emptyPoolIssue.get().poolIndex()); // Second pool (index 1)
    }

    @Test
    @DisplayName("Entry issues have correct entry index")
    void entryIssuesHaveCorrectIndex() {
        LootEntry entry1 = createSimpleEntry("diamond", 1);
        LootEntry entry2 = createSimpleEntry("gold", 0); // Zero weight
        LootPool pool = createPoolWithEntries(NumberProvider.constant(1), List.of(entry1, entry2));

        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        var zeroWeightIssue = result.issues().stream()
            .filter(i -> i.type() == LootTableValidator.IssueType.ZERO_WEIGHT)
            .findFirst();

        assertTrue(zeroWeightIssue.isPresent());
        assertEquals(0, zeroWeightIssue.get().poolIndex());
        assertEquals(1, zeroWeightIssue.get().entryIndex()); // Second entry (index 1)
        assertTrue(zeroWeightIssue.get().isEntrySpecific());
    }

    // ===== Severity Count Tests =====

    @Test
    @DisplayName("Error count is accurate")
    void errorCountIsAccurate() {
        // Create a pool with all zero-weight entries (causes ERROR)
        LootEntry entry1 = createSimpleEntry("a", 0);
        LootEntry entry2 = createSimpleEntry("b", 0);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(1), List.of(entry1, entry2));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertEquals(1, result.errorCount()); // Unreachable entry error
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("Warning count is accurate")
    void warningCountIsAccurate() {
        LootEntry entry = createSimpleEntry("diamond", 0); // Zero weight warning
        LootPool pool = createPoolWithEntries(NumberProvider.constant(0), List.of(entry)); // Zero rolls warning

        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertTrue(result.warningCount() >= 2);
        assertTrue(result.hasIssues());
    }

    // ===== ValidationResult Methods =====

    @Test
    @DisplayName("hasIssues returns false for valid table")
    void hasIssuesReturnsFalseForValidTable() {
        LootEntry entry = createSimpleEntry("diamond", 5);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(1), List.of(entry));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertFalse(result.hasIssues());
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("hasErrors returns true only for errors")
    void hasErrorsOnlyForErrors() {
        // Pool with warning only (zero rolls but not zero weight)
        LootEntry entry = createSimpleEntry("diamond", 5);
        LootPool pool = createPoolWithEntries(NumberProvider.constant(0), List.of(entry));
        LootTableStructure structure = createTable(List.of(pool));

        var result = LootTableValidator.validate(TEST_TABLE, structure);

        assertTrue(result.hasIssues());
        assertFalse(result.hasErrors());
    }

    // ===== Severity Enum Tests =====

    @Test
    @DisplayName("Severity enum has correct values")
    void severityEnumValues() {
        assertEquals("Error", LootTableValidator.Severity.ERROR.label);
        assertEquals("Warning", LootTableValidator.Severity.WARNING.label);
        assertEquals("Info", LootTableValidator.Severity.INFO.label);
    }

    // ===== IssueType Enum Tests =====

    @Test
    @DisplayName("IssueType enum has name and description")
    void issueTypeHasNameAndDescription() {
        for (LootTableValidator.IssueType type : LootTableValidator.IssueType.values()) {
            assertNotNull(type.name, "IssueType " + type + " should have a name");
            assertNotNull(type.description, "IssueType " + type + " should have a description");
            assertFalse(type.name.isEmpty());
            assertFalse(type.description.isEmpty());
        }
    }
}
