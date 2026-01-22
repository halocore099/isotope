package dev.isotope.ui.screen;

import dev.isotope.data.StructureLootLink;
import dev.isotope.registry.EntityLootRegistry;
import dev.isotope.registry.StructureLootLinker;
import dev.isotope.editing.LootEditManager;
import dev.isotope.testing.DropStatistics;
import dev.isotope.testing.LootTestRunner;
import dev.isotope.testing.TestArenaManager;
import dev.isotope.testing.TestMobTools;
import dev.isotope.testing.TestModeState;
import dev.isotope.testing.TestWorldManager;
import dev.isotope.testing.TestingTools;
import dev.isotope.ui.HelpLinks;
import dev.isotope.ui.IsotopeColors;
import dev.isotope.ui.ScreenUtils;
import dev.isotope.ui.IsotopeToast;
import dev.isotope.ui.UIConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * In-game testing UI shown when in test mode.
 *
 * Simplified design with actual Button widgets for all interactions.
 */
@Environment(EnvType.CLIENT)
public class TestingScreen extends Screen {

    private static final int PANEL_WIDTH = UIConstants.TEST_PANEL_WIDTH;  // 450
    private static final int PANEL_HEIGHT = UIConstants.TEST_PANEL_HEIGHT;  // 400

    // Help panel persistence file path
    private static final String HELP_PREFS_FILE = "isotope/test_help_prefs.json";

    private final List<TableEntry> entries = new ArrayList<>();
    private int selectedArenaCount = 16;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Entry buttons (rebuilt on scroll)
    private final List<Button> entryButtons = new ArrayList<>();

    // Kill condition for mob testing
    private TestMobTools.KillCondition selectedKillCondition = TestMobTools.KillCondition.PLAYER_KILL;

    // Auto-collect drops to inventory
    private boolean autoCollect = false;

    // Test count for Stats/Compare (adjustable)
    private int selectedTestCount = 50;

    // Luck parameter for loot generation (0-5)
    private int selectedLuck = 0;

    // Looting level for mob loot (0-3)
    private int selectedLootingLevel = 0;

    // Collapsible help panel state
    private boolean helpPanelExpanded = true;
    private Button helpToggleButton;

    private record TableEntry(
        ResourceLocation tableId,
        Set<ResourceLocation> structures,
        boolean isEntityLoot,
        ResourceLocation entityId  // null if not entity loot
    ) {
        boolean isMobLoot() {
            return isEntityLoot && entityId != null;
        }
    }

    public TestingScreen() {
        super(Component.literal("ISOTOPE Test Mode"));
        loadHelpPanelPreference();
        loadEntries();
    }

    private void loadHelpPanelPreference() {
        try {
            Path prefsPath = Minecraft.getInstance().gameDirectory.toPath().resolve(HELP_PREFS_FILE);
            if (Files.exists(prefsPath)) {
                String content = Files.readString(prefsPath);
                helpPanelExpanded = content.contains("\"expanded\":true");
            }
        } catch (IOException e) {
            // Default to expanded for first-time users
            helpPanelExpanded = true;
        }
    }

    private void saveHelpPanelPreference() {
        try {
            Path prefsPath = Minecraft.getInstance().gameDirectory.toPath().resolve(HELP_PREFS_FILE);
            Files.createDirectories(prefsPath.getParent());
            String content = "{\"expanded\":" + helpPanelExpanded + "}";
            Files.writeString(prefsPath, content);
        } catch (IOException e) {
            // Ignore save errors
        }
    }

    private void toggleHelpPanel() {
        helpPanelExpanded = !helpPanelExpanded;
        saveHelpPanelPreference();
        rebuildWidgets();
    }

    private void loadEntries() {
        entries.clear();
        Set<ResourceLocation> tested = TestModeState.getInstance().getTestedTables();
        StructureLootLinker linker = StructureLootLinker.getInstance();
        EntityLootRegistry entityRegistry = EntityLootRegistry.getInstance();

        for (ResourceLocation tableId : tested) {
            // Check if this is an entity loot table
            var entityInfo = entityRegistry.getByLootTable(tableId);
            boolean isEntityLoot = entityInfo.isPresent();
            ResourceLocation entityId = entityInfo.map(e -> e.entityId()).orElse(null);

            // Get linked structures (for non-entity loot)
            List<StructureLootLink> links = linker.getLinksForLootTable(tableId);
            Set<ResourceLocation> structures = new HashSet<>();
            for (var link : links) {
                structures.add(link.structureId());
            }

            entries.add(new TableEntry(tableId, structures, isEntityLoot, entityId));
        }

        // Sort: entity loot first, then structures
        entries.sort((a, b) -> {
            if (a.isEntityLoot != b.isEntityLoot) {
                return a.isEntityLoot ? -1 : 1;
            }
            return a.tableId.compareTo(b.tableId);
        });

        // Recalculate max scroll (done in init() to account for help panel)
    }

    private void recalculateMaxScroll() {
        int entryHeight = UIConstants.TEST_ENTRY_HEIGHT;  // 80px per entry
        int contentHeight = entries.size() * entryHeight;
        int headerHeight = getContentStartY() - ((height - PANEL_HEIGHT) / 2);
        int footerHeight = UIConstants.TEST_FOOTER_HEIGHT + 10;
        int viewHeight = PANEL_HEIGHT - headerHeight - footerHeight;
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    private int getContentStartY() {
        int panelY = (height - PANEL_HEIGHT) / 2;
        // Header (30) + Settings row (24) + Modifiers row (24) + Help panel (if expanded: ~50, collapsed: ~20)
        int helpHeight = helpPanelExpanded ? 50 : 20;
        return panelY + UIConstants.TEST_HEADER_HEIGHT + UIConstants.TEST_SETTINGS_ROW_HEIGHT + UIConstants.TEST_SETTINGS_ROW_HEIGHT + helpHeight;
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // === Header Row (30px) ===
        // Help button
        addRenderableWidget(Button.builder(
            Component.literal("?"),
            b -> HelpLinks.open(HelpLinks.TEST_MODE)
        ).pos(panelX + PANEL_WIDTH - 140, panelY + 5).size(20, 20)
            .tooltip(Tooltip.create(Component.literal("Open help documentation")))
            .build());

        // Exit button
        addRenderableWidget(Button.builder(
            Component.literal("Exit"),
            b -> onExitTestMode()
        ).pos(panelX + PANEL_WIDTH - 55, panelY + 5).size(45, 20)
            .tooltip(Tooltip.create(Component.literal("Exit test mode and delete test world")))
            .build());

        // === Settings Row (24px) ===
        int row1Y = panelY + UIConstants.TEST_HEADER_HEIGHT;

        // Ground/Inventory toggle
        addRenderableWidget(Button.builder(
            Component.literal(autoCollect ? "Inventory▼" : "Ground▼"),
            b -> {
                autoCollect = !autoCollect;
                rebuildWidgets();
            }
        ).pos(panelX + 10, row1Y).size(70, 20)
            .tooltip(Tooltip.create(Component.literal("Where items appear.\nGround = near player\nInventory = in hotbar")))
            .build());

        // Test count label and buttons
        addRenderableWidget(Button.builder(
            Component.literal("Tests:"),
            b -> {}
        ).pos(panelX + 85, row1Y).size(40, 20).build()).active = false;

        int[] testPresets = {10, 50, 100, 500};
        int testX = panelX + 128;
        for (int preset : testPresets) {
            final int count = preset;
            addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(count)),
                b -> selectedTestCount = count
            ).pos(testX, row1Y).size(30, 20)
                .tooltip(Tooltip.create(Component.literal("Run " + count + " rolls for statistics")))
                .build());
            testX += 32;
        }

        // === Modifiers Row (24px) ===
        int row2Y = row1Y + UIConstants.TEST_SETTINGS_ROW_HEIGHT;

        // Luck selector
        addRenderableWidget(Button.builder(
            Component.literal("Luck:"),
            b -> {}
        ).pos(panelX + 10, row2Y).size(35, 18)
            .tooltip(Tooltip.create(Component.literal("Affects bonus_rolls.\n0 = normal, 5 = max bonus")))
            .build()).active = false;

        int[] luckPresets = {0, 1, 2, 3, 5};
        int luckX = panelX + 48;
        for (int preset : luckPresets) {
            final int luck = preset;
            addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(luck)),
                b -> selectedLuck = luck
            ).pos(luckX, row2Y).size(20, 18).build());
            luckX += 22;
        }

        // Looting selector (for mob loot)
        addRenderableWidget(Button.builder(
            Component.literal("Looting:"),
            b -> {}
        ).pos(panelX + 165, row2Y).size(45, 18)
            .tooltip(Tooltip.create(Component.literal("Enchantment level for mob drops")))
            .build()).active = false;

        int[] lootingPresets = {0, 1, 2, 3};
        int lootingX = panelX + 213;
        for (int preset : lootingPresets) {
            final int looting = preset;
            addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(looting)),
                b -> selectedLootingLevel = looting
            ).pos(lootingX, row2Y).size(20, 18).build());
            lootingX += 22;
        }

        // Kill condition dropdown button
        addRenderableWidget(Button.builder(
            Component.literal("Kill:"),
            b -> {}
        ).pos(panelX + 305, row2Y).size(30, 18).build()).active = false;

        addRenderableWidget(Button.builder(
            Component.literal(selectedKillCondition.displayName + " ▼"),
            b -> cycleKillCondition(1)
        ).pos(panelX + 338, row2Y).size(100, 18)
            .tooltip(Tooltip.create(Component.literal("Player Kill = rare drops available\nNon-Player = guaranteed drops only\nClick to cycle")))
            .build());

        // === Collapsible Help Panel ===
        int helpY = row2Y + UIConstants.TEST_SETTINGS_ROW_HEIGHT;
        String helpLabel = helpPanelExpanded ? "▼ Quick Guide (click to hide)" : "▶ Quick Guide (click to show)";
        helpToggleButton = addRenderableWidget(Button.builder(
            Component.literal(helpLabel),
            b -> toggleHelpPanel()
        ).pos(panelX + 10, helpY).size(180, 16).build());

        // === Footer (30px) ===
        int footerY = panelY + PANEL_HEIGHT - UIConstants.TEST_FOOTER_HEIGHT;

        // Arena size buttons
        addRenderableWidget(Button.builder(
            Component.literal("Arena:"),
            b -> {}
        ).pos(panelX + 10, footerY).size(42, 20)
            .tooltip(Tooltip.create(Component.literal("Number of structures in arena grid")))
            .build()).active = false;

        int[] presets = {4, 9, 16, 25, 36};
        int presetX = panelX + 55;
        for (int preset : presets) {
            final int count = preset;
            addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(count)),
                b -> selectedArenaCount = count
            ).pos(presetX, footerY).size(28, 20)
                .tooltip(Tooltip.create(Component.literal("Create " + count + " structure copies")))
                .build());
            presetX += 30;
        }

        // Clear ground items button
        addRenderableWidget(Button.builder(
            Component.literal("Clear"),
            b -> onClearGroundItems()
        ).pos(panelX + PANEL_WIDTH - 110, footerY).size(45, 20)
            .tooltip(Tooltip.create(Component.literal("Remove all dropped items nearby")))
            .build());

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal(UIConstants.LABEL_CLOSE),
            b -> onClose()
        ).pos(panelX + PANEL_WIDTH - 60, footerY).size(50, 20).build());

        // Recalculate scroll and build entry buttons
        recalculateMaxScroll();
        rebuildEntryButtons();
    }

    private void cycleKillCondition(int direction) {
        TestMobTools.KillCondition[] conditions = TestMobTools.KillCondition.values();
        int currentIdx = selectedKillCondition.ordinal();
        int newIdx = (currentIdx + direction + conditions.length) % conditions.length;
        selectedKillCondition = conditions[newIdx];
    }

    private void rebuildEntryButtons() {
        // Remove old entry buttons
        for (Button btn : entryButtons) {
            removeWidget(btn);
        }
        entryButtons.clear();

        int panelX = (width - PANEL_WIDTH) / 2;
        int contentY = getContentStartY();
        int footerY = (height - PANEL_HEIGHT) / 2 + PANEL_HEIGHT - UIConstants.TEST_FOOTER_HEIGHT;
        int contentHeight = footerY - contentY - 5;
        int entryHeight = UIConstants.TEST_ENTRY_HEIGHT;  // 80px

        int entryY = contentY - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            TableEntry entry = entries.get(i);

            // Only create buttons for visible entries
            if (entryY + entryHeight - 10 > contentY && entryY < contentY + contentHeight) {
                int buttonY = entryY + 45;  // Adjusted for taller cards

                if (entry.isMobLoot()) {
                    // Mob loot: Spawn, Kill, and Stats buttons
                    final ResourceLocation entityId = entry.entityId;

                    Button spawnBtn = Button.builder(
                        Component.literal("Spawn 1"),
                        b -> onSpawnMob(entityId)
                    ).pos(panelX + 15, buttonY).size(50, 18)
                        .tooltip(Tooltip.create(Component.literal("Spawn one mob (AI disabled)")))
                        .build();
                    entryButtons.add(addRenderableWidget(spawnBtn));

                    Button spawn5Btn = Button.builder(
                        Component.literal("x5"),
                        b -> onSpawnMobGrid(entityId, 5)
                    ).pos(panelX + 68, buttonY).size(25, 18)
                        .tooltip(Tooltip.create(Component.literal("Spawn 5 mobs in a grid")))
                        .build();
                    entryButtons.add(addRenderableWidget(spawn5Btn));

                    Button killBtn = Button.builder(
                        Component.literal("Kill"),
                        b -> onKillMobs(entityId)
                    ).pos(panelX + 96, buttonY).size(35, 18)
                        .tooltip(Tooltip.create(Component.literal("Remove all mobs of this type nearby")))
                        .build();
                    entryButtons.add(addRenderableWidget(killBtn));

                    Button testBtn = Button.builder(
                        Component.literal("Kill x10"),
                        b -> onTestMobDrops(entityId, 10)
                    ).pos(panelX + 134, buttonY).size(55, 18)
                        .tooltip(Tooltip.create(Component.literal("Spawn and kill 10, drops on ground")))
                        .build();
                    entryButtons.add(addRenderableWidget(testBtn));

                    Button statsBtn = Button.builder(
                        Component.literal("Stats"),
                        b -> onMobStats(entityId, selectedTestCount)
                    ).pos(panelX + 192, buttonY).size(40, 18)
                        .tooltip(Tooltip.create(Component.literal("Run analysis, show drop rates")))
                        .build();
                    entryButtons.add(addRenderableWidget(statsBtn));

                    // Compare button - only show if edits exist
                    ResourceLocation lootTableId = ResourceLocation.fromNamespaceAndPath(
                        entityId.getNamespace(), "entities/" + entityId.getPath());
                    if (LootEditManager.getInstance().hasEdits(lootTableId)) {
                        Button compareBtn = Button.builder(
                            Component.literal("Compare"),
                            b -> onMobCompare(entityId, selectedTestCount)
                        ).pos(panelX + 235, buttonY).size(55, 18)
                            .tooltip(Tooltip.create(Component.literal("Original vs edited side-by-side")))
                            .build();
                        entryButtons.add(addRenderableWidget(compareBtn));
                    }

                } else {
                    // Structure/chest loot: Teleport, Arena, Generate, Stats buttons
                    final ResourceLocation structureId = entry.structures.isEmpty()
                        ? null
                        : entry.structures.iterator().next();
                    final ResourceLocation tableId = entry.tableId;

                    if (structureId != null) {
                        Button teleportBtn = Button.builder(
                            Component.literal("Teleport"),
                            b -> onTeleport(structureId)
                        ).pos(panelX + 15, buttonY).size(60, 18)
                            .tooltip(Tooltip.create(Component.literal("Find nearest structure and teleport")))
                            .build();
                        entryButtons.add(addRenderableWidget(teleportBtn));

                        Button arenaBtn = Button.builder(
                            Component.literal("Arena"),
                            b -> onSpawnArena(structureId)
                        ).pos(panelX + 78, buttonY).size(45, 18)
                            .tooltip(Tooltip.create(Component.literal("Create grid of structure copies")))
                            .build();
                        entryButtons.add(addRenderableWidget(arenaBtn));
                    }

                    // Generate and Stats buttons for chest loot (always shown)
                    int genX = structureId != null ? panelX + 126 : panelX + 15;

                    Button genBtn = Button.builder(
                        Component.literal("Roll x10"),
                        b -> onGenerateChestLoot(tableId, 10)
                    ).pos(genX, buttonY).size(55, 18)
                        .tooltip(Tooltip.create(Component.literal("Generate 10 loot rolls")))
                        .build();
                    entryButtons.add(addRenderableWidget(genBtn));

                    Button statsBtn = Button.builder(
                        Component.literal("Stats"),
                        b -> onChestStats(tableId, selectedTestCount)
                    ).pos(genX + 58, buttonY).size(40, 18)
                        .tooltip(Tooltip.create(Component.literal("Run analysis, show drop rates")))
                        .build();
                    entryButtons.add(addRenderableWidget(statsBtn));

                    // Compare button - only show if edits exist
                    if (LootEditManager.getInstance().hasEdits(tableId)) {
                        Button compareBtn = Button.builder(
                            Component.literal("Compare"),
                            b -> onChestCompare(tableId, selectedTestCount)
                        ).pos(genX + 101, buttonY).size(55, 18)
                            .tooltip(Tooltip.create(Component.literal("Original vs edited side-by-side")))
                            .build();
                        entryButtons.add(addRenderableWidget(compareBtn));
                    }
                }
            }

            entryY += entryHeight;
        }
    }

    private void onExitTestMode() {
        if (minecraft == null) return;
        // skipCloseAfterConfirm=true because exitTestWorld() handles the screen transition
        minecraft.setScreen(new ConfirmDialog(
            this,
            "Exit Test Mode",
            "This will delete the test world\nand return to the main menu.",
            "Exit",
            () -> TestWorldManager.getInstance().exitTestWorld(),
            true,
            true  // Don't call onClose() after confirm - exitTestWorld handles screen
        ));
    }

    private void onClearGroundItems() {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        minecraft.execute(() -> {
            var server = minecraft.getSingleplayerServer();
            var player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (player == null) return;

            var level = (net.minecraft.server.level.ServerLevel) player.level();
            var pos = player.position();
            int radius = 100;

            // Find and remove all item entities within radius
            var items = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                player.getBoundingBox().inflate(radius)
            );

            int count = items.size();
            for (var item : items) {
                item.discard();
            }

            minecraft.execute(() -> {
                if (count > 0) {
                    IsotopeToast.success("Cleared", "Removed " + count + " items");
                } else {
                    IsotopeToast.info("Clear", "No items to clear");
                }
            });
        });
    }

    private void onTeleport(ResourceLocation structureId) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        onClose();
        minecraft.execute(() -> {
            var result = TestingTools.locateStructure(
                minecraft.getSingleplayerServer(),
                structureId,
                100
            );

            if (result.found()) {
                boolean success = TestingTools.teleportPlayer(
                    minecraft.getSingleplayerServer(),
                    result.position()
                );
                if (success) {
                    IsotopeToast.success("Teleported", "Found " + structureId.getPath());
                } else {
                    IsotopeToast.error("Failed", "Could not teleport");
                }
            } else {
                IsotopeToast.error("Not Found", "Structure not found within range");
            }
        });
    }

    private void onSpawnArena(ResourceLocation structureId) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        onClose();
        IsotopeToast.info("Creating Arena", "Spawning " + selectedArenaCount + " structures...");
        TestArenaManager.getInstance().createArena(structureId, msg -> {
            minecraft.execute(() -> IsotopeToast.info("Arena", msg));
        });
    }

    // === Mob Testing Methods ===

    private void onSpawnMob(ResourceLocation entityId) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        minecraft.execute(() -> {
            var result = TestMobTools.spawnMob(
                minecraft.getSingleplayerServer(),
                entityId,
                new net.minecraft.core.BlockPos(3, 0, 3)
            );

            if (result.success()) {
                IsotopeToast.success("Spawned", TestMobTools.getEntityDisplayName(entityId));
            } else {
                IsotopeToast.error("Failed", result.error());
            }
        });
    }

    private void onSpawnMobGrid(ResourceLocation entityId, int count) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        minecraft.execute(() -> {
            var results = TestMobTools.spawnMobGrid(
                minecraft.getSingleplayerServer(),
                entityId,
                count
            );

            long successCount = results.stream().filter(r -> r.success()).count();
            IsotopeToast.success("Spawned", successCount + "/" + count + " " + TestMobTools.getEntityDisplayName(entityId));
        });
    }

    private void onKillMobs(ResourceLocation entityId) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        minecraft.execute(() -> {
            int removed = TestMobTools.clearMobs(
                minecraft.getSingleplayerServer(),
                entityId,
                50  // 50 block radius
            );

            if (removed > 0) {
                IsotopeToast.success("Cleared", removed + " " + TestMobTools.getEntityDisplayName(entityId));
            } else {
                IsotopeToast.info("None Found", "No mobs to clear");
            }
        });
    }

    private void onTestMobDrops(ResourceLocation entityId, int count) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        onClose();
        String lootingInfo = selectedLootingLevel > 0 && selectedKillCondition.isPlayerKill()
            ? " + Looting " + selectedLootingLevel : "";
        IsotopeToast.info("Testing Drops", "Spawning and killing " + count + " mobs with " + selectedKillCondition.displayName + lootingInfo);

        minecraft.execute(() -> {
            int successful = TestMobTools.batchSpawnAndKill(
                minecraft.getSingleplayerServer(),
                entityId,
                count,
                selectedKillCondition,
                selectedLootingLevel
            );

            minecraft.execute(() -> {
                IsotopeToast.success("Test Complete",
                    successful + "/" + count + " mobs tested\nCheck ground for drops!");
            });
        });
    }

    private void onMobStats(ResourceLocation entityId, int count) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        String lootingInfo = selectedLootingLevel > 0 && selectedKillCondition.isPlayerKill()
            ? " + Looting " + selectedLootingLevel : "";
        IsotopeToast.info("Running Test", "Testing " + count + " mobs with " + selectedKillCondition.displayName + lootingInfo + "...");

        minecraft.execute(() -> {
            var result = LootTestRunner.runMobTest(
                minecraft.getSingleplayerServer(),
                entityId,
                count,
                selectedKillCondition,
                selectedLootingLevel,
                null
            );

            minecraft.execute(() -> {
                if (result.success()) {
                    minecraft.setScreen(new DropStatisticsDialog(this, result.statistics()));
                } else {
                    IsotopeToast.error("Test Failed", result.error());
                }
            });
        });
    }

    // === Chest Loot Testing Methods ===

    private void onGenerateChestLoot(ResourceLocation tableId, int count) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        minecraft.execute(() -> {
            int totalItems;
            if (autoCollect) {
                totalItems = LootTestRunner.collectLootToInventory(
                    minecraft.getSingleplayerServer(),
                    tableId,
                    count
                );
            } else {
                totalItems = LootTestRunner.spawnLootOnGround(
                    minecraft.getSingleplayerServer(),
                    tableId,
                    count
                );
            }

            final String destination = autoCollect ? "inventory" : "ground";
            minecraft.execute(() -> {
                if (totalItems > 0) {
                    IsotopeToast.success("Generated", totalItems + " items to " + destination);
                } else {
                    IsotopeToast.error("Failed", "Could not generate loot");
                }
            });
        });
    }

    private void onChestStats(ResourceLocation tableId, int count) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        String luckInfo = selectedLuck > 0 ? " (Luck " + selectedLuck + ")" : "";
        IsotopeToast.info("Running Test", "Generating " + count + " chest rolls" + luckInfo + "...");

        minecraft.execute(() -> {
            var result = LootTestRunner.runChestTest(
                minecraft.getSingleplayerServer(),
                tableId,
                count,
                selectedLuck,
                null
            );

            minecraft.execute(() -> {
                if (result.success()) {
                    minecraft.setScreen(new DropStatisticsDialog(this, result.statistics()));
                } else {
                    IsotopeToast.error("Test Failed", result.error());
                }
            });
        });
    }

    // === Compare Methods ===

    private void onMobCompare(ResourceLocation entityId, int count) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        String lootingInfo = selectedLootingLevel > 0 && selectedKillCondition.isPlayerKill()
            ? " + Looting " + selectedLootingLevel : "";
        IsotopeToast.info("Comparing", "Testing original vs edited (" + count + " each)" + lootingInfo + "...");

        minecraft.execute(() -> {
            var result = LootTestRunner.runMobCompare(
                minecraft.getSingleplayerServer(),
                entityId,
                count,
                selectedKillCondition,
                selectedLootingLevel,
                null
            );

            minecraft.execute(() -> {
                if (result.success()) {
                    minecraft.setScreen(new CompareStatisticsDialog(this, result.originalStats(), result.editedStats()));
                } else {
                    IsotopeToast.error("Compare Failed", result.error());
                }
            });
        });
    }

    private void onChestCompare(ResourceLocation tableId, int count) {
        if (minecraft == null || minecraft.getSingleplayerServer() == null) {
            IsotopeToast.error("Error", "Not in singleplayer world");
            return;
        }

        String luckInfo = selectedLuck > 0 ? " (Luck " + selectedLuck + ")" : "";
        IsotopeToast.info("Comparing", "Testing original vs edited (" + count + " each)" + luckInfo + "...");

        minecraft.execute(() -> {
            var result = LootTestRunner.runChestCompare(
                minecraft.getSingleplayerServer(),
                tableId,
                count,
                selectedLuck,
                null
            );

            minecraft.execute(() -> {
                if (result.success()) {
                    minecraft.setScreen(new CompareStatisticsDialog(this, result.originalStats(), result.editedStats()));
                } else {
                    IsotopeToast.error("Compare Failed", result.error());
                }
            });
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. Background first (no blur)
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // 2. Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, IsotopeColors.BORDER_OUTER_DARK);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);

        // Header (30px)
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + UIConstants.TEST_HEADER_HEIGHT, IsotopeColors.BACKGROUND_SOLID);
        graphics.fill(panelX + 8, panelY + 8, panelX + 12, panelY + 22, IsotopeColors.TEST_INDICATOR); // Green indicator
        graphics.drawString(font, "ISOTOPE TEST MODE", panelX + 18, panelY + 10, IsotopeColors.ACCENT_GOLD, false);

        // World type
        String worldType = TestModeState.getInstance().getWorldType().displayName;
        graphics.drawString(font, worldType, panelX + 150, panelY + 10, IsotopeColors.TEXT_MUTED, false);

        // Settings row background
        int row1Y = panelY + UIConstants.TEST_HEADER_HEIGHT;
        graphics.fill(panelX, row1Y, panelX + PANEL_WIDTH, row1Y + UIConstants.TEST_SETTINGS_ROW_HEIGHT, 0x20FFFFFF);

        // Modifiers row
        int row2Y = row1Y + UIConstants.TEST_SETTINGS_ROW_HEIGHT;

        // Help panel (collapsible)
        int helpY = row2Y + UIConstants.TEST_SETTINGS_ROW_HEIGHT;
        if (helpPanelExpanded) {
            // Draw help content
            graphics.fill(panelX + 5, helpY + 18, panelX + PANEL_WIDTH - 5, helpY + 48, IsotopeColors.BACKGROUND_DARK);
            graphics.drawString(font, "Chest: Teleport → Arena → Roll → Stats", panelX + 12, helpY + 22, IsotopeColors.TEXT_SECONDARY, false);
            graphics.drawString(font, "Mob: Spawn → Kill x10 → Stats", panelX + 12, helpY + 34, IsotopeColors.TEXT_SECONDARY, false);
        }

        // Content area
        int contentY = getContentStartY();
        int footerY = panelY + PANEL_HEIGHT - UIConstants.TEST_FOOTER_HEIGHT;
        int contentHeight = footerY - contentY - 5;

        // Scissor for scrolling
        graphics.enableScissor(panelX, contentY, panelX + PANEL_WIDTH, contentY + contentHeight);

        // Render entries
        int entryHeight = UIConstants.TEST_ENTRY_HEIGHT;
        int entryY = contentY - scrollOffset;
        for (TableEntry entry : entries) {
            if (entryY + entryHeight - 10 > contentY && entryY < contentY + contentHeight) {
                renderEntry(graphics, panelX + 10, entryY, PANEL_WIDTH - 20, entry);
            }
            entryY += entryHeight;
        }

        graphics.disableScissor();

        // Footer
        graphics.fill(panelX, footerY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, IsotopeColors.BACKGROUND_SOLID);

        // 3. Render widgets (buttons) on top
        super.render(graphics, mouseX, mouseY, partialTick);

        // Selection highlights (rendered after buttons)
        // Selected arena highlight
        int[] presets = {4, 9, 16, 25, 36};
        int presetX = panelX + 55;
        int presetButtonY = panelY + PANEL_HEIGHT - UIConstants.TEST_FOOTER_HEIGHT;
        for (int preset : presets) {
            if (preset == selectedArenaCount) {
                ScreenUtils.renderOutline(graphics, presetX - 1, presetButtonY - 1, 30, 22, IsotopeColors.ACCENT_GOLD);
            }
            presetX += 30;
        }

        // Selected test count highlight (settings row)
        int[] testPresets = {10, 50, 100, 500};
        int testX = panelX + 128;
        int testButtonY = panelY + UIConstants.TEST_HEADER_HEIGHT;
        for (int preset : testPresets) {
            if (preset == selectedTestCount) {
                ScreenUtils.renderOutline(graphics, testX - 1, testButtonY - 1, 32, 22, IsotopeColors.ACCENT_AQUA);
            }
            testX += 32;
        }

        // Selected luck highlight (modifiers row)
        int[] luckPresets = {0, 1, 2, 3, 5};
        int luckX = panelX + 48;
        int luckButtonY = row2Y;
        for (int preset : luckPresets) {
            if (preset == selectedLuck) {
                ScreenUtils.renderOutline(graphics, luckX - 1, luckButtonY - 1, 22, 20, IsotopeColors.ACCENT_GREEN);
            }
            luckX += 22;
        }

        // Selected looting highlight (modifiers row)
        int[] lootingPresets = {0, 1, 2, 3};
        int lootingX = panelX + 213;
        int lootingButtonY = row2Y;
        for (int preset : lootingPresets) {
            if (preset == selectedLootingLevel) {
                ScreenUtils.renderOutline(graphics, lootingX - 1, lootingButtonY - 1, 22, 20, IsotopeColors.SOURCE_MOB);
            }
            lootingX += 22;
        }
    }

    private void renderEntry(GuiGraphics graphics, int x, int y, int entryWidth, TableEntry entry) {
        int entryHeight = UIConstants.TEST_ENTRY_HEIGHT - 10;  // 70px visible (10px margin)

        // Entry background - purple tint for mob loot
        int bgColor = entry.isMobLoot() ? IsotopeColors.MOB_LOOT_BG : IsotopeColors.BACKGROUND_DARK;
        int borderColor = entry.isMobLoot() ? IsotopeColors.MOB_LOOT_BORDER : IsotopeColors.BORDER_INNER;
        graphics.fill(x, y, x + entryWidth, y + entryHeight, bgColor);
        ScreenUtils.renderOutline(graphics, x, y, entryWidth, entryHeight, borderColor);

        if (entry.isMobLoot()) {
            // Mob loot entry
            String entityName = TestMobTools.getEntityDisplayName(entry.entityId);
            graphics.drawString(font, "⚔ " + entityName, x + 8, y + 8, IsotopeColors.SOURCE_MOB, false);

            // Type badge
            int badgeX = x + entryWidth - 70;
            graphics.fill(badgeX, y + 6, badgeX + 62, y + 18, IsotopeColors.MOB_BADGE_BG);
            graphics.drawString(font, "Mob Loot", badgeX + 4, y + 8, IsotopeColors.SOURCE_MOB, false);

            // Table path (smaller, dimmer)
            String tablePath = entry.tableId.getPath();
            if (tablePath.length() > 50) {
                tablePath = "..." + tablePath.substring(tablePath.length() - 47);
            }
            graphics.drawString(font, tablePath, x + 8, y + 24, IsotopeColors.TEXT_MUTED, false);

        } else {
            // Structure loot entry
            String name = entry.tableId.toString();
            if (name.length() > 55) {
                name = "..." + name.substring(name.length() - 52);
            }
            graphics.drawString(font, name, x + 8, y + 8, IsotopeColors.TEXT_PRIMARY, false);

            // Structure info
            if (entry.structures.isEmpty()) {
                graphics.drawString(font, "No linked structures", x + 8, y + 24, IsotopeColors.TEXT_MUTED, false);
            } else {
                String info = entry.structures.size() + " structure" + (entry.structures.size() != 1 ? "s" : "");
                graphics.drawString(font, info, x + 8, y + 24, IsotopeColors.STRUCTURE_INFO, false);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int oldOffset = scrollOffset;
        recalculateMaxScroll();
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 25)));

        if (oldOffset != scrollOffset) {
            rebuildEntryButtons();
        }

        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Don't call super - just fill with solid color to prevent blur
        graphics.fill(0, 0, this.width, this.height, IsotopeColors.BACKGROUND_MEDIUM);
    }
}
