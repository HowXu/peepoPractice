package me.falu.peepopractice.core.category;

import com.google.gson.*;
import me.falu.peepopractice.PeepoPractice;
import me.falu.peepopractice.core.category.properties.PlayerProperties;
import me.falu.peepopractice.core.category.properties.StructureProperties;
import me.falu.peepopractice.core.category.properties.WorldProperties;
import me.falu.peepopractice.core.category.properties.event.*;
import me.falu.peepopractice.core.exception.InvalidCategorySyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import net.minecraft.world.gen.feature.StructureFeature;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomCategoryResourceManager {
    public static final List<PracticeCategory> CUSTOM_CATEGORIES = new ArrayList<>();
    private static final File CATEGORIES_FOLDER = FabricLoader.getInstance().getConfigDir().resolve(PeepoPractice.MOD_NAME).resolve("categories").toFile();

    @SuppressWarnings({ "DuplicatedCode" })
    public static void register() throws InvalidCategorySyntaxException {
        try {
            if (CATEGORIES_FOLDER.exists()) {
                if (CATEGORIES_FOLDER.isFile()) {
                    Files.delete(CATEGORIES_FOLDER.toPath());
                }
            }
            boolean ignored = CATEGORIES_FOLDER.mkdirs();

            DirectoryStream.Filter<Path> filter = entry -> entry.toFile().isFile() && entry.getFileName().toString().endsWith(".json");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(CATEGORIES_FOLDER.toPath(), filter)) {
                StringBuilder categories = new StringBuilder("Registered custom categories: ");
                for (Path path : stream) {
                    Object obj;
                    try {
                        FileReader reader = new FileReader(path.toFile());
                        JsonParser parser = new JsonParser();
                        obj = parser.parse(reader);
                        reader.close();
                    } catch (JsonParseException e) {
                        throw new InvalidCategorySyntaxException(path.toString());
                    }

                    if (obj != null && !obj.equals(JsonNull.INSTANCE)) {
                        JsonObject main = (JsonObject) obj;

                        PracticeCategory category = new PracticeCategory();
                        category = category.setFillerCategory(true);

                        if (main.has("id")) {
                            category = category.setId(main.get("id").getAsString());
                        }
                        if (main.has("can_have_empty_inventory")) {
                            category = category.setCanHaveEmptyInventory(main.get("can_have_empty_inventory").getAsBoolean());
                        }
                        if (main.has("hidden")) {
                            category = category.setHidden(main.get("hidden").getAsBoolean());
                        }
                        if (main.has("player_properties")) {
                            JsonObject playerProperties = main.get("player_properties").getAsJsonObject();
                            PlayerProperties properties = new PlayerProperties();
                            if (playerProperties.has("spawn_pos")) {
                                JsonArray spawnPos = playerProperties.get("spawn_pos").getAsJsonArray();
                                properties = properties.setSpawnPos(new BlockPos(spawnPos.get(0).getAsDouble(), spawnPos.get(1).getAsDouble(), spawnPos.get(2).getAsDouble()));
                            }
                            if (playerProperties.has("spawn_angle")) {
                                JsonArray spawnAngle = playerProperties.get("spawn_angle").getAsJsonArray();
                                properties = properties.setSpawnAngle(spawnAngle.get(0).getAsFloat(), spawnAngle.get(1).getAsFloat());
                            }
                            if (playerProperties.has("vehicle")) {
                                String vehicleId = playerProperties.get("vehicle").getAsString();
                                properties = properties.setVehicle(Registry.ENTITY_TYPE.get(new Identifier(vehicleId)));
                            }
                            if (playerProperties.has("commands")) {
                                JsonArray commandsArray = playerProperties.get("commands").getAsJsonArray();
                                for (JsonElement element : commandsArray) {
                                    properties = properties.runCommand(element.getAsString());
                                }
                            }
                            if (playerProperties.has("potion_effects")) {
                                JsonArray potionEffectsArray = playerProperties.get("potion_effects").getAsJsonArray();
                                for (JsonElement element : potionEffectsArray) {
                                    JsonObject potionEffectObject = element.getAsJsonObject();
                                    if (potionEffectObject.has("effect")) {
                                        PlayerProperties.PotionEffect potionEffect = new PlayerProperties.PotionEffect();
                                        potionEffect.setEffect(Registry.STATUS_EFFECT.get(new Identifier(potionEffectObject.get("effect").getAsString())));
                                        if (potionEffectObject.has("amplifier")) {
                                            potionEffect = potionEffect.setAmplifier(potionEffectObject.get("amplifier").getAsInt());
                                        }
                                        if (potionEffectObject.has("duration")) {
                                            potionEffect = potionEffect.setDuration(potionEffectObject.get("duration").getAsInt());
                                        }
                                        properties = properties.addPotionEffect(potionEffect);
                                    }
                                }
                            }
                            category = category.setPlayerProperties(properties);
                        }
                        if (main.has("structure_properties")) {
                            JsonArray array = main.get("structure_properties").getAsJsonArray();
                            for (JsonElement element : array) {
                                if (element instanceof JsonObject) {
                                    JsonObject structureProperties = (JsonObject) element;
                                    StructureProperties properties = new StructureProperties();
                                    if (structureProperties.has("feature")) {
                                        StructureFeature<?> feature = Registry.STRUCTURE_FEATURE.get(new Identifier(structureProperties.get("feature").getAsString()));
                                        ConfiguredStructureFeature<?, ?> configuredFeature = FlatChunkGeneratorConfig.STRUCTURE_TO_FEATURES.get(feature);
                                        properties.setStructure(configuredFeature);
                                    }
                                    if (structureProperties.has("chunk_pos")) {
                                        JsonArray chunkPos = structureProperties.get("chunk_pos").getAsJsonArray();
                                        properties = properties.setChunkPos(new ChunkPos(chunkPos.get(0).getAsInt(), chunkPos.get(1).getAsInt()));
                                    }
                                    if (structureProperties.has("orientation")) {
                                        String orientationName = structureProperties.get("orientation").getAsString();
                                        properties = properties.setOrientation(Direction.byName(orientationName));
                                    }
                                    if (structureProperties.has("rotation")) {
                                        String rotationName = structureProperties.get("rotationName").getAsString();
                                        properties = properties.setRotation(BlockRotation.valueOf(rotationName.toUpperCase(Locale.ROOT)));
                                    }
                                    if (structureProperties.has("structure_top_y")) {
                                        properties = properties.setStructureTopY(structureProperties.get("structure_top_y").getAsInt());
                                    }
                                    if (structureProperties.has("generatable")) {
                                        properties = properties.setGeneratable(structureProperties.get("generatable").getAsBoolean());
                                    }
                                    category = category.addStructureProperties(properties);
                                }
                            }
                        }
                        if (main.has("world_properties")) {
                            JsonObject worldProperties = main.get("world_properties").getAsJsonObject();
                            WorldProperties properties = new WorldProperties();
                            if (worldProperties.has("world_registry_key")) {
                                properties = properties.setWorldRegistryKey(parseDimensionKey(worldProperties.get("world_registry_key")));
                            }
                            if (worldProperties.has("spawn_chunks_disabled")) {
                                properties = properties.setSpawnChunksDisabled(worldProperties.get("spawn_chunks_disabled").getAsBoolean());
                            }
                            if (worldProperties.has("anti_biomes")) {
                                JsonArray array = worldProperties.get("anti_biomes").getAsJsonArray();
                                for (JsonElement element : array) {
                                    JsonObject antiBiomeInfo = element.getAsJsonObject();
                                    if (antiBiomeInfo.has("biome") && antiBiomeInfo.has("range") && antiBiomeInfo.has("replacement") && antiBiomeInfo.has("valid_dimensions")) {
                                        Biome biome = Registry.BIOME.get(new Identifier(antiBiomeInfo.get("biome").getAsString()));
                                        Biome replacement = Registry.BIOME.get(new Identifier(antiBiomeInfo.get("replacement").getAsString()));
                                        Integer range = antiBiomeInfo.get("range").getAsInt();
                                        range = range > 0 ? range : null;
                                        List<RegistryKey<World>> validDimensions = new ArrayList<>();
                                        JsonArray validDimensionsArray = antiBiomeInfo.get("valid_dimensions").getAsJsonArray();
                                        for (JsonElement element1 : validDimensionsArray) {
                                            validDimensions.add(parseDimensionKey(element1));
                                        }
                                        properties = properties.addAntiBiome(new WorldProperties.BiomeModification()
                                                                                     .setBiome(biome)
                                                                                     .setReplacement(replacement)
                                                                                     .setRange(new WorldProperties.Range()
                                                                                                       .setRange(range)
                                                                                                       .addValidDimensions(validDimensions)
                                                                                     )
                                        );
                                    }
                                }
                            }
                            if (worldProperties.has("pro_biomes")) {
                                JsonArray array = worldProperties.get("pro_biomes").getAsJsonArray();
                                for (JsonElement element : array) {
                                    JsonObject proBiomeInfo = element.getAsJsonObject();
                                    if (proBiomeInfo.has("biome") && proBiomeInfo.has("range") && proBiomeInfo.has("valid_dimensions")) {
                                        Biome biome = Registry.BIOME.get(new Identifier(proBiomeInfo.get("biome").getAsString()));
                                        Integer range = proBiomeInfo.get("range").getAsInt();
                                        range = range > 0 ? range : null;
                                        List<RegistryKey<World>> validDimensions = new ArrayList<>();
                                        JsonArray validDimensionsArray = proBiomeInfo.get("valid_dimensions").getAsJsonArray();
                                        for (JsonElement element1 : validDimensionsArray) {
                                            validDimensions.add(parseDimensionKey(element1));
                                        }
                                        properties = properties.addProBiome(new WorldProperties.BiomeModification()
                                                                                    .setBiome(biome)
                                                                                    .setRange(new WorldProperties.Range()
                                                                                                      .setRange(range)
                                                                                                      .addValidDimensions(validDimensions)
                                                                                    )
                                        );
                                    }
                                }
                            }
                            if (worldProperties.has("start_difficulty")) {
                                properties = properties.setStartDifficulty(Difficulty.byName(worldProperties.get("start_difficulty").getAsString().toLowerCase()));
                            }
                            category = category.setWorldProperties(properties);
                        }
                        if (main.has("split_event")) {
                            JsonObject splitEvent = main.get("split_event").getAsJsonObject();
                            String eventId = splitEvent.get("id").getAsString().toLowerCase(Locale.ROOT);
                            switch (eventId) {
                                case "change_dimension":
                                    if (splitEvent.has("to_dimension")) {
                                        ChangeDimensionSplitEvent event = new ChangeDimensionSplitEvent();
                                        event = event.setToDimension(parseDimensionKey(splitEvent.get("to_dimension")));
                                        if (splitEvent.has("from_dimension")) {
                                            event = event.setFromDimension(parseDimensionKey(splitEvent.get("from_dimension")));
                                        }
                                        category = category.setSplitEvent(event);
                                    }
                                    break;
                                case "enter_vehicle":
                                    if (splitEvent.has("vehicle")) {
                                        EnterVehicleSplitEvent event = new EnterVehicleSplitEvent();
                                        if (splitEvent.has("keep_item")) {
                                            event = event.setKeepItem(splitEvent.get("keep_item").getAsBoolean());
                                        }
                                        EntityType<?> type = Registry.ENTITY_TYPE.get(new Identifier(splitEvent.get("vehicle").getAsString()));
                                        event = event.setVehicle(type);
                                        category = category.setSplitEvent(event);
                                    }
                                    break;
                                case "get_advancement":
                                    if (splitEvent.has("advancement")) {
                                        GetAdvancementSplitEvent event = new GetAdvancementSplitEvent();
                                        event = event.setAdvancement(new Identifier(splitEvent.get("advancement").getAsString()));
                                        category = category.setSplitEvent(event);
                                    }
                                    break;
                                case "interact_loot_chest":
                                    if (splitEvent.has("loot_table")) {
                                        InteractLootChestSplitEvent event = new InteractLootChestSplitEvent();
                                        Identifier lootTable = new Identifier(splitEvent.get("loot_table").getAsString());
                                        event = event.setLootTable(lootTable);
                                        if (splitEvent.has("on_close")) {
                                            event = event.setOnClose(splitEvent.get("on_close").getAsBoolean());
                                        }
                                        category = category.setSplitEvent(event);
                                    }
                                    break;
                                case "throw_entity":
                                    if (splitEvent.has("item")) {
                                        ThrowEntitySplitEvent event = new ThrowEntitySplitEvent();
                                        Item item = Registry.ITEM.get(new Identifier(splitEvent.get("item").getAsString()));
                                        event = event.setItem(item);
                                        category = category.setSplitEvent(event);
                                    }
                                    break;
                            }
                        }
                        CUSTOM_CATEGORIES.add(category);
                        categories.append(category.getSimpleName()).append(", ");
                    }
                }
                PeepoPractice.log(categories);
            }
        } catch (IOException e) {
            throw new InvalidCategorySyntaxException();
        }
    }

    public static RegistryKey<World> parseDimensionKey(JsonElement string) {
        switch (string.getAsString().toUpperCase()) {
            default:
            case "OVERWORLD":
                return World.OVERWORLD;
            case "NETHER":
                return World.NETHER;
            case "END":
                return World.END;
        }
    }
}