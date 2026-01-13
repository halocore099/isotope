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
import dev.isotope.ui.IsotopeToast;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 350;

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
        loadEntries();
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

        // Calculate max scroll
        int contentHeight = entries.size() * 70;
        int viewHeight = PANEL_HEIGHT - 120; // header + footer
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // Header buttons
        addRenderableWidget(Button.builder(
            Component.literal("Exit Test Mode"),
            b -> onExitTestMode()
        ).pos(panelX + PANEL_WIDTH - 115, panelY + 8).size(105, 20).build());

        addRenderableWidget(Button.builder(
            Component.literal("?"),
            b -> HelpLinks.open(HelpLinks.TEST_MODE)
        ).pos(panelX + PANEL_WIDTH - 140, panelY + 8).size(20, 20).build());

        // Auto-collect toggle in header
        addRenderableWidget(Button.builder(
            Component.literal(autoCollect ? "📦 Inventory" : "📦 Ground"),
            b -> {
                autoCollect = !autoCollect;
                rebuildWidgets();
            }
        ).pos(panelX + 10, panelY + 8).size(80, 20).build());

        // Test count selector in header (for Stats/Compare)
        addRenderableWidget(Button.builder(
            Component.literal("Tests:"),
            b -> {}
        ).pos(panelX + 95, panelY + 8).size(40, 20).build()).active = false;

        int[] testPresets = {10, 50, 100, 500};
        int testX = panelX + 138;
        for (int preset : testPresets) {
            final int count = preset;
            addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(count)),
                b -> selectedTestCount = count
            ).pos(testX, panelY + 8).size(28, 20).build());
            testX += 30;
        }

        // Second row of header - Luck selector
        int row2Y = panelY + 30;
        addRenderableWidget(Button.builder(
            Component.literal("Luck:"),
            b -> {}
        ).pos(panelX + 10, row2Y).size(35, 16).build()).active = false;

        int[] luckPresets = {0, 1, 2, 3, 5};
        int luckX = panelX + 48;
        for (int preset : luckPresets) {
            final int luck = preset;
            addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(luck)),
                b -> selectedLuck = luck
            ).pos(luckX, row2Y).size(20, 16).build());
            luckX += 22;
        }

        // Footer - Arena size buttons (for structures)
        int footerY = panelY + PANEL_HEIGHT - 35;
        int[] presets = {4, 9, 16, 25, 36};
        int presetX = panelX + 10;
        addRenderableWidget(Button.builder(
            Component.literal("Arena:"),
            b -> {}
        ).pos(presetX, footerY).size(45, 20).build()).active = false;
        presetX += 50;
        for (int preset : presets) {
            final int count = preset;
            addRenderableWidget(Button.builder(
                Component.literal(String.valueOf(count)),
                b -> selectedArenaCount = count
            ).pos(presetX, footerY).size(30, 20).build());
            presetX += 32;
        }

        // Kill condition buttons (for mobs)
        int killX = presetX + 15;
        addRenderableWidget(Button.builder(
            Component.literal("Kill:"),
            b -> {}
        ).pos(killX, footerY).size(35, 20).build()).active = false;
        killX += 38;

        // Cycle through kill conditions
        addRenderableWidget(Button.builder(
            Component.literal("◀"),
            b -> cycleKillCondition(-1)
        ).pos(killX, footerY).size(20, 20).build());
        killX += 22;

        addRenderableWidget(Button.builder(
            Component.literal("▶"),
            b -> cycleKillCondition(1)
        ).pos(killX + 70, footerY).size(20, 20).build());

        // Clear ground items button
        addRenderableWidget(Button.builder(
            Component.literal("Clear Items"),
            b -> onClearGroundItems()
        ).pos(panelX + PANEL_WIDTH - 130, footerY).size(70, 20).build());

        // Close button
        addRenderableWidget(Button.builder(
            Component.literal("Close"),
            b -> onClose()
        ).pos(panelX + PANEL_WIDTH - 55, footerY).size(50, 20).build());

        // Build entry buttons
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
        int panelY = (height - PANEL_HEIGHT) / 2;
        int contentY = panelY + 45;
        int contentHeight = PANEL_HEIGHT - 120;

        int entryY = contentY - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            TableEntry entry = entries.get(i);

            // Only create buttons for visible entries
            if (entryY + 60 > contentY && entryY < contentY + contentHeight) {
                if (entry.isMobLoot()) {
                    // Mob loot: Spawn, Kill, and Stats buttons
                    final ResourceLocation entityId = entry.entityId;

                    Button spawnBtn = Button.builder(
                        Component.literal("Spawn"),
                        b -> onSpawnMob(entityId)
                    ).pos(panelX + 15, entryY + 35).size(50, 18).build();
                    entryButtons.add(addRenderableWidget(spawnBtn));

                    Button spawn5Btn = Button.builder(
                        Component.literal("×5"),
                        b -> onSpawnMobGrid(entityId, 5)
                    ).pos(panelX + 68, entryY + 35).size(25, 18).build();
                    entryButtons.add(addRenderableWidget(spawn5Btn));

                    Button killBtn = Button.builder(
                        Component.literal("Kill"),
                        b -> onKillMobs(entityId)
                    ).pos(panelX + 96, entryY + 35).size(35, 18).build();
                    entryButtons.add(addRenderableWidget(killBtn));

                    Button testBtn = Button.builder(
                        Component.literal("Test ×10"),
                        b -> onTestMobDrops(entityId, 10)
                    ).pos(panelX + 134, entryY + 35).size(55, 18).build();
                    entryButtons.add(addRenderableWidget(testBtn));

                    Button statsBtn = Button.builder(
                        Component.literal("Stats"),
                        b -> onMobStats(entityId, selectedTestCount)
                    ).pos(panelX + 192, entryY + 35).size(40, 18).build();
                    entryButtons.add(addRenderableWidget(statsBtn));

                    // Compare button - only show if edits exist
                    ResourceLocation lootTableId = ResourceLocation.fromNamespaceAndPath(
                        entityId.getNamespace(), "entities/" + entityId.getPath());
                    if (LootEditManager.getInstance().hasEdits(lootTableId)) {
                        Button compareBtn = Button.builder(
                            Component.literal("Compare"),
                            b -> onMobCompare(entityId, selectedTestCount)
                        ).pos(panelX + 235, entryY + 35).size(55, 18).build();
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
                        ).pos(panelX + 15, entryY + 35).size(65, 18).build();
                        entryButtons.add(addRenderableWidget(teleportBtn));

                        Button arenaBtn = Button.builder(
                            Component.literal("Arena"),
                            b -> onSpawnArena(structureId)
                        ).pos(panelX + 83, entryY + 35).size(45, 18).build();
                        entryButtons.add(addRenderableWidget(arenaBtn));
                    }

                    // Generate and Stats buttons for chest loot (always shown)
                    int genX = structureId != null ? panelX + 131 : panelX + 15;

                    Button genBtn = Button.builder(
                        Component.literal("Gen ×10"),
                        b -> onGenerateChestLoot(tableId, 10)
                    ).pos(genX, entryY + 35).size(50, 18).build();
                    entryButtons.add(addRenderableWidget(genBtn));

                    Button statsBtn = Button.builder(
                        Component.literal("Stats"),
                        b -> onChestStats(tableId, selectedTestCount)
                    ).pos(genX + 53, entryY + 35).size(40, 18).build();
                    entryButtons.add(addRenderableWidget(statsBtn));

                    // Compare button - only show if edits exist
                    if (LootEditManager.getInstance().hasEdits(tableId)) {
                        Button compareBtn = Button.builder(
                            Component.literal("Compare"),
                            b -> onChestCompare(tableId, selectedTestCount)
                        ).pos(genX + 96, entryY + 35).size(55, 18).build();
                        entryButtons.add(addRenderableWidget(compareBtn));
                    }
                }
            }

            entryY += 70;
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

            var level = player.serverLevel();
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
        IsotopeToast.info("Testing Drops", "Spawning and killing " + count + " mobs with " + selectedKillCondition.displayName);

        minecraft.execute(() -> {
            int successful = TestMobTools.batchSpawnAndKill(
                minecraft.getSingleplayerServer(),
                entityId,
                count,
                selectedKillCondition
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

        IsotopeToast.info("Running Test", "Testing " + count + " mobs with " + selectedKillCondition.displayName + "...");

        minecraft.execute(() -> {
            var result = LootTestRunner.runMobTest(
                minecraft.getSingleplayerServer(),
                entityId,
                count,
                selectedKillCondition,
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

        IsotopeToast.info("Comparing", "Testing original vs edited (" + count + " each)...");

        minecraft.execute(() -> {
            var result = LootTestRunner.runMobCompare(
                minecraft.getSingleplayerServer(),
                entityId,
                count,
                selectedKillCondition,
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
        // Render widgets first (this calls renderBackground internally)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Now draw our panel content ON TOP of everything
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, 0xFF000000);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, IsotopeColors.BACKGROUND_MEDIUM);

        // Header
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 40, IsotopeColors.BACKGROUND_SOLID);
        graphics.fill(panelX + 8, panelY + 10, panelX + 12, panelY + 30, 0xFF44aa44); // Green indicator
        graphics.drawString(font, "ISOTOPE TEST MODE", panelX + 18, panelY + 14, IsotopeColors.ACCENT_GOLD, false);

        // World type
        String worldType = TestModeState.getInstance().getWorldType().displayName;
        graphics.drawString(font, worldType, panelX + 150, panelY + 14, IsotopeColors.TEXT_MUTED, false);

        // Content area (adjusted for second header row)
        int contentY = panelY + 52;
        int contentHeight = PANEL_HEIGHT - 127;

        // Scissor for scrolling
        graphics.enableScissor(panelX, contentY, panelX + PANEL_WIDTH, contentY + contentHeight);

        // Render entries
        int entryY = contentY - scrollOffset;
        for (TableEntry entry : entries) {
            if (entryY + 65 > contentY && entryY < contentY + contentHeight) {
                renderEntry(graphics, panelX + 10, entryY, PANEL_WIDTH - 20, entry);
            }
            entryY += 70;
        }

        graphics.disableScissor();

        // Footer
        int footerY = panelY + PANEL_HEIGHT - 45;
        graphics.fill(panelX, footerY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, IsotopeColors.BACKGROUND_SOLID);

        // Draw current kill condition in the middle area
        int killCondX = panelX + 230;
        graphics.drawString(font, selectedKillCondition.displayName, killCondX, footerY + 12,
            IsotopeColors.SOURCE_MOB, false);

        // Re-render buttons on top of panel
        for (var widget : this.children()) {
            if (widget instanceof Button btn) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        // Selected arena highlight
        int[] presets = {4, 9, 16, 25, 36};
        int presetX = panelX + 100;
        int presetButtonY = panelY + PANEL_HEIGHT - 35;
        for (int preset : presets) {
            if (preset == selectedArenaCount) {
                graphics.renderOutline(presetX - 1, presetButtonY - 1, 42, 22, IsotopeColors.ACCENT_GOLD);
            }
            presetX += 45;
        }

        // Selected test count highlight (in header)
        int[] testPresets = {10, 50, 100, 500};
        int testX = panelX + 138;
        int testButtonY = panelY + 8;
        for (int preset : testPresets) {
            if (preset == selectedTestCount) {
                graphics.renderOutline(testX - 1, testButtonY - 1, 30, 22, IsotopeColors.ACCENT_AQUA);
            }
            testX += 30;
        }

        // Selected luck highlight (second row)
        int[] luckPresets = {0, 1, 2, 3, 5};
        int luckX = panelX + 48;
        int luckButtonY = panelY + 30;
        for (int preset : luckPresets) {
            if (preset == selectedLuck) {
                graphics.renderOutline(luckX - 1, luckButtonY - 1, 22, 18, IsotopeColors.ACCENT_GREEN);
            }
            luckX += 22;
        }
    }

    private void renderEntry(GuiGraphics graphics, int x, int y, int entryWidth, TableEntry entry) {
        // Entry background - purple tint for mob loot
        int bgColor = entry.isMobLoot() ? 0xFF2a1a3a : IsotopeColors.BACKGROUND_DARK;
        int borderColor = entry.isMobLoot() ? 0xFF4a3a5a : 0xFF383838;
        graphics.fill(x, y, x + entryWidth, y + 60, bgColor);
        graphics.renderOutline(x, y, entryWidth, 60, borderColor);

        if (entry.isMobLoot()) {
            // Mob loot entry
            String entityName = TestMobTools.getEntityDisplayName(entry.entityId);
            graphics.drawString(font, "⚔ " + entityName, x + 8, y + 6, IsotopeColors.SOURCE_MOB, false);

            // Table path (smaller, dimmer)
            String tablePath = entry.tableId.getPath();
            if (tablePath.length() > 45) {
                tablePath = "..." + tablePath.substring(tablePath.length() - 42);
            }
            graphics.drawString(font, tablePath, x + 8, y + 20, IsotopeColors.TEXT_MUTED, false);

        } else {
            // Structure loot entry
            String name = entry.tableId.toString();
            if (name.length() > 50) {
                name = "..." + name.substring(name.length() - 47);
            }
            graphics.drawString(font, name, x + 8, y + 6, IsotopeColors.TEXT_PRIMARY, false);

            // Structure info
            if (entry.structures.isEmpty()) {
                graphics.drawString(font, "No linked structures", x + 8, y + 20, IsotopeColors.TEXT_MUTED, false);
            } else {
                String info = entry.structures.size() + " structure" + (entry.structures.size() != 1 ? "s" : "");
                graphics.drawString(font, info, x + 8, y + 20, 0xFF88aa88, false);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int oldOffset = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollY * 20)));

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
        graphics.fill(0, 0, this.width, this.height, 0xFF1a1a1a);
    }
}
