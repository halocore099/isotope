package dev.isotope.data;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.isotope.Isotope;
import dev.isotope.data.loot.LootCondition;
import dev.isotope.data.loot.LootFunction;
import dev.isotope.data.loot.NumberProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages custom entry templates with persistence.
 * Persists templates to .minecraft/isotope/templates.json
 */
public final class TemplateManager {

    private static final TemplateManager INSTANCE = new TemplateManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int CURRENT_VERSION = 1;

    private final List<EntryTemplate> customTemplates = new ArrayList<>();
    private final List<TemplateListener> listeners = new CopyOnWriteArrayList<>();
    private boolean loaded = false;

    private TemplateManager() {}

    public static TemplateManager getInstance() {
        return INSTANCE;
    }

    /**
     * Save or update a template.
     * If a template with the same ID exists, it will be replaced.
     */
    public void save(EntryTemplate template) {
        ensureLoaded();

        // Remove existing template with same ID
        customTemplates.removeIf(t -> t.id().equals(template.id()));
        customTemplates.add(template);

        saveToDisk();
        notifyListeners();

        Isotope.LOGGER.info("Saved custom template: {}", template.name());
    }

    /**
     * Delete a template by ID.
     */
    public void delete(String templateId) {
        ensureLoaded();

        boolean removed = customTemplates.removeIf(t -> t.id().equals(templateId));
        if (removed) {
            saveToDisk();
            notifyListeners();
            Isotope.LOGGER.info("Deleted custom template: {}", templateId);
        }
    }

    /**
     * Get a template by ID (searches both built-in and custom).
     */
    public Optional<EntryTemplate> getById(String templateId) {
        ensureLoaded();

        // Check built-in first
        for (EntryTemplate t : EntryTemplate.BUILTIN_TEMPLATES) {
            if (t.id().equals(templateId)) {
                return Optional.of(t);
            }
        }

        // Check custom
        for (EntryTemplate t : customTemplates) {
            if (t.id().equals(templateId)) {
                return Optional.of(t);
            }
        }

        return Optional.empty();
    }

    /**
     * Get all custom templates.
     */
    public List<EntryTemplate> getCustomTemplates() {
        ensureLoaded();
        return new ArrayList<>(customTemplates);
    }

    /**
     * Get all templates (built-in + custom).
     */
    public List<EntryTemplate> getAllTemplates() {
        ensureLoaded();

        List<EntryTemplate> all = new ArrayList<>();
        all.addAll(EntryTemplate.BUILTIN_TEMPLATES);
        all.addAll(customTemplates);
        return all;
    }

    /**
     * Get the count of custom templates.
     */
    public int getCustomCount() {
        ensureLoaded();
        return customTemplates.size();
    }

    /**
     * Check if a template ID is already used.
     */
    public boolean isIdTaken(String templateId) {
        return getById(templateId).isPresent();
    }

    /**
     * Generate a unique template ID from a name.
     */
    public String generateId(String name) {
        String baseId = name.toLowerCase()
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");

        if (baseId.isEmpty()) {
            baseId = "custom_template";
        }

        String id = baseId;
        int counter = 1;
        while (isIdTaken(id)) {
            id = baseId + "_" + counter;
            counter++;
        }

        return id;
    }

    /**
     * Check if a template is built-in (not custom).
     */
    public boolean isBuiltIn(EntryTemplate template) {
        return EntryTemplate.BUILTIN_TEMPLATES.contains(template);
    }

    /**
     * Add a listener for template changes.
     */
    public void addListener(TemplateListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     */
    public void removeListener(TemplateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (TemplateListener listener : listeners) {
            listener.onTemplatesChanged();
        }
    }

    /**
     * Ensure templates are loaded from disk.
     */
    private void ensureLoaded() {
        if (!loaded) {
            loadFromDisk();
            loaded = true;
        }
    }

    /**
     * Get the templates file path.
     */
    private Path getTemplatesPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("isotope")
            .resolve("templates.json");
    }

    /**
     * Save templates to disk.
     */
    private void saveToDisk() {
        try {
            Path path = getTemplatesPath();
            Files.createDirectories(path.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);

            JsonArray templatesArray = new JsonArray();
            for (EntryTemplate template : customTemplates) {
                templatesArray.add(templateToJson(template));
            }
            root.add("templates", templatesArray);

            String json = GSON.toJson(root);
            Files.writeString(path, json);

            Isotope.LOGGER.debug("Saved {} custom templates", customTemplates.size());
        } catch (IOException e) {
            Isotope.LOGGER.error("Failed to save templates", e);
        }
    }

    /**
     * Load templates from disk.
     */
    private void loadFromDisk() {
        Path path = getTemplatesPath();
        if (!Files.exists(path)) {
            Isotope.LOGGER.debug("No templates file found");
            return;
        }

        try {
            String json = Files.readString(path);
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            int version = root.has("version") ? root.get("version").getAsInt() : 1;

            customTemplates.clear();
            if (root.has("templates")) {
                JsonArray templatesArray = root.getAsJsonArray("templates");
                for (JsonElement element : templatesArray) {
                    try {
                        EntryTemplate template = templateFromJson(element.getAsJsonObject());
                        customTemplates.add(template);
                    } catch (Exception e) {
                        Isotope.LOGGER.warn("Failed to parse template: {}", e.getMessage());
                    }
                }
            }

            Isotope.LOGGER.info("Loaded {} custom templates", customTemplates.size());
        } catch (Exception e) {
            Isotope.LOGGER.error("Failed to load templates", e);
        }
    }

    // ===== JSON Serialization =====

    /**
     * Convert an EntryTemplate to JSON.
     */
    public static JsonObject templateToJson(EntryTemplate template) {
        JsonObject json = new JsonObject();

        json.addProperty("id", template.id());
        json.addProperty("name", template.name());
        json.addProperty("description", template.description());
        json.addProperty("category", template.category());

        if (template.defaultItem().isPresent()) {
            json.addProperty("defaultItem", template.defaultItem().get().toString());
        }

        json.addProperty("defaultWeight", template.defaultWeight());
        json.add("defaultCount", numberProviderToJson(template.defaultCount()));

        JsonArray functionsArray = new JsonArray();
        for (LootFunction func : template.functions()) {
            functionsArray.add(functionToJson(func));
        }
        json.add("functions", functionsArray);

        return json;
    }

    /**
     * Parse an EntryTemplate from JSON.
     */
    public static EntryTemplate templateFromJson(JsonObject json) {
        String id = json.get("id").getAsString();
        String name = json.get("name").getAsString();
        String description = json.has("description") ? json.get("description").getAsString() : "";
        String category = json.has("category") ? json.get("category").getAsString() : "Custom";

        Optional<ResourceLocation> defaultItem = Optional.empty();
        if (json.has("defaultItem") && !json.get("defaultItem").isJsonNull()) {
            ResourceLocation itemId = ResourceLocation.tryParse(json.get("defaultItem").getAsString());
            if (itemId != null) {
                defaultItem = Optional.of(itemId);
            }
        }

        int defaultWeight = json.has("defaultWeight") ? json.get("defaultWeight").getAsInt() : 10;

        NumberProvider defaultCount = NumberProvider.constant(1);
        if (json.has("defaultCount")) {
            defaultCount = numberProviderFromJson(json.get("defaultCount"));
        }

        List<LootFunction> functions = new ArrayList<>();
        if (json.has("functions")) {
            JsonArray functionsArray = json.getAsJsonArray("functions");
            for (JsonElement element : functionsArray) {
                functions.add(functionFromJson(element.getAsJsonObject()));
            }
        }

        return new EntryTemplate(id, name, description, category, defaultItem, defaultWeight, defaultCount, functions);
    }

    /**
     * Convert a NumberProvider to JSON.
     */
    private static JsonObject numberProviderToJson(NumberProvider provider) {
        JsonObject json = new JsonObject();

        if (provider instanceof NumberProvider.Constant c) {
            json.addProperty("type", "constant");
            json.addProperty("value", c.value());
        } else if (provider instanceof NumberProvider.Uniform u) {
            json.addProperty("type", "uniform");
            json.addProperty("min", u.min());
            json.addProperty("max", u.max());
        } else if (provider instanceof NumberProvider.Binomial b) {
            json.addProperty("type", "binomial");
            json.addProperty("n", b.n());
            json.addProperty("p", b.p());
        }

        return json;
    }

    /**
     * Parse a NumberProvider from JSON.
     */
    private static NumberProvider numberProviderFromJson(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return NumberProvider.constant(element.getAsFloat());
        }

        JsonObject json = element.getAsJsonObject();
        String type = json.has("type") ? json.get("type").getAsString() : "constant";

        if (type.equals("constant")) {
            float value = json.has("value") ? json.get("value").getAsFloat() : 1;
            return NumberProvider.constant(value);
        } else if (type.equals("uniform")) {
            float min = json.has("min") ? json.get("min").getAsFloat() : 0;
            float max = json.has("max") ? json.get("max").getAsFloat() : 1;
            return NumberProvider.uniform(min, max);
        } else if (type.equals("binomial")) {
            int n = json.has("n") ? json.get("n").getAsInt() : 1;
            float p = json.has("p") ? json.get("p").getAsFloat() : 0.5f;
            return NumberProvider.binomial(n, p);
        }

        return NumberProvider.constant(1);
    }

    /**
     * Convert a LootFunction to JSON.
     */
    private static JsonObject functionToJson(LootFunction func) {
        JsonObject json = new JsonObject();
        json.addProperty("function", func.function());
        json.add("parameters", func.parameters().deepCopy());

        if (!func.conditions().isEmpty()) {
            JsonArray conditionsArray = new JsonArray();
            for (LootCondition cond : func.conditions()) {
                conditionsArray.add(conditionToJson(cond));
            }
            json.add("conditions", conditionsArray);
        }

        return json;
    }

    /**
     * Parse a LootFunction from JSON.
     */
    private static LootFunction functionFromJson(JsonObject json) {
        String function = json.get("function").getAsString();
        JsonObject parameters = json.has("parameters") ? json.getAsJsonObject("parameters") : new JsonObject();

        List<LootCondition> conditions = new ArrayList<>();
        if (json.has("conditions")) {
            JsonArray conditionsArray = json.getAsJsonArray("conditions");
            for (JsonElement element : conditionsArray) {
                conditions.add(conditionFromJson(element.getAsJsonObject()));
            }
        }

        return new LootFunction(function, parameters, conditions);
    }

    /**
     * Convert a LootCondition to JSON.
     */
    private static JsonObject conditionToJson(LootCondition cond) {
        JsonObject json = new JsonObject();
        json.addProperty("condition", cond.condition());
        json.add("parameters", cond.parameters().deepCopy());
        return json;
    }

    /**
     * Parse a LootCondition from JSON.
     */
    private static LootCondition conditionFromJson(JsonObject json) {
        String condition = json.get("condition").getAsString();
        JsonObject parameters = json.has("parameters") ? json.getAsJsonObject("parameters") : new JsonObject();
        return new LootCondition(condition, parameters);
    }

    /**
     * Listener for template changes.
     */
    @FunctionalInterface
    public interface TemplateListener {
        void onTemplatesChanged();
    }
}
