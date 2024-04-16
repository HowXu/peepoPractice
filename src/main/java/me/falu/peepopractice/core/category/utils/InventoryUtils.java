package me.falu.peepopractice.core.category.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.falu.peepopractice.PeepoPractice;
import me.falu.peepopractice.core.category.preferences.CategoryPreferences;
import me.falu.peepopractice.core.category.PracticeCategory;
import me.falu.peepopractice.core.item.RandomToolItem;
import me.falu.peepopractice.core.playerless.PlayerlessInventory;
import me.falu.peepopractice.core.writer.DefaultFileWriter;
import me.falu.peepopractice.core.writer.PracticeWriter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.collection.DefaultedList;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class InventoryUtils {
    public static final List<ItemStack> PREVIOUS_INVENTORY = new ArrayList<>();

    public static int getSelectedInventory() {
        return CategoryPreferences.SELECTED_INVENTORY.getValue().ordinal();
    }

    public static void putItems(Inventory inventory, PracticeCategory category, int profileIndex) {
        JsonObject config = PracticeWriter.INVENTORY_WRITER.get();
        if (config.has(category.getId())) {
            JsonElement profilesElement = config.get(category.getId());
            if (profilesElement.isJsonArray()) {
                JsonArray profiles = config.has(category.getId()) ? profilesElement.getAsJsonArray() : new JsonArray();
                if (profiles.size() > profileIndex) {
                    JsonObject object = profiles.get(profileIndex).getAsJsonObject();
                    object.entrySet().forEach(set -> {
                        try {
                            CompoundTag tag = StringNbtReader.parse(set.getValue().getAsString());
                            ItemStack stack = ItemStack.fromTag(tag);
                            if (!(inventory instanceof PlayerlessInventory)) {
                                CompoundTag blockEntityTag = stack.getSubTag("BlockEntityTag");
                                if (blockEntityTag != null && stack.getTag() != null) {
                                    if (blockEntityTag.contains("Items")) {
                                        DefaultedList<ItemStack> containerStacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
                                        DefaultedList<ItemStack> newContainerStacks = DefaultedList.ofSize(27, ItemStack.EMPTY);
                                        Inventories.fromTag(blockEntityTag, containerStacks);
                                        for (int i = 0; i < containerStacks.size(); i++) {
                                            newContainerStacks.set(i, addItemModifiers(containerStacks.get(i)));
                                        }
                                        Inventories.toTag(blockEntityTag, newContainerStacks);
                                        CompoundTag stackTag = stack.getTag();
                                        stackTag.put("BlockEntityTag", blockEntityTag);
                                        stack.setTag(stackTag);
                                    }
                                } else {
                                    stack = addItemModifiers(stack);
                                }
                            }
                            inventory.setStack(Integer.parseInt(set.getKey()), stack);
                        } catch (CommandSyntaxException e) {
                            PeepoPractice.LOGGER.error(String.format("Couldn't parse inventory contents for inventory '%s'.", category.getId()), e);
                        } catch (NumberFormatException e) {
                            PeepoPractice.LOGGER.error(String.format("Couldn't parse slot index: '%s' is not a valid number.", set.getKey()), e);
                        }
                    });
                }
            }
        }
    }

    public static ItemStack addItemModifiers(ItemStack stack) {
        CompoundTag stackTag = stack.getTag();
        if (stack.getItem() instanceof RandomToolItem) {
            stack = ((RandomToolItem) stack.getItem()).convert();
        }
        if (stackTag != null && stackTag.contains("MaxCount")) {
            int minCount = stackTag.getInt("MinCount");
            int maxCount = stackTag.getInt("MaxCount");
            stackTag.remove("MinCount");
            stackTag.remove("MaxCount");
            int count = minCount == maxCount ? minCount : new Random().nextInt(maxCount - minCount + 1) + minCount;
            stack.setCount(count);
            stack.setTag(stackTag.isEmpty() ? null : stackTag);
        }
        return stack;
    }

    public static void saveCurrentPlayerInventory() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && !PeepoPractice.CATEGORY.isFillerCategory()) {
            PREVIOUS_INVENTORY.clear();
            for (int i = 0; i < client.player.inventory.size(); i++) {
                PREVIOUS_INVENTORY.add(i, client.player.inventory.getStack(i).copy());
            }
            client.player.inventory.clear();
        }
    }

    public static void resetToDefault(PracticeCategory category, int profileIndex) {
        JsonObject defaultObject = DefaultFileWriter.INSTANCE.getResourceJson("writer/inventories.json").getAsJsonObject();
        JsonArray defaultProfiles = defaultObject.has(category.getId()) ? defaultObject.getAsJsonArray(category.getId()) : new JsonArray();
        if (defaultProfiles.size() > 0) {
            JsonObject defaultProfile = defaultProfiles.get(0).getAsJsonObject();
            JsonObject object = PracticeWriter.INVENTORY_WRITER.get();
            JsonArray profiles = object.getAsJsonArray(category.getId());
            if (profiles.size() > profileIndex) {
                profiles.set(profileIndex, defaultProfile);
            } else {
                profiles.add(defaultProfile);
            }
            PracticeWriter.INVENTORY_WRITER.put(category.getId(), profiles);
            PracticeWriter.INVENTORY_WRITER.write();
        }
    }
}
