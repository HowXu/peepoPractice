package me.falu.peepopractice.mixin.world.entity.ai;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.falu.peepopractice.core.category.preferences.CategoryPreferences;
import me.falu.peepopractice.core.loot.PiglinBarterState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Random;

@Mixin(PiglinBrain.class)
public abstract class PiglinBrainMixin {
    @ModifyReturnValue(method = "method_29276", at = @At("RETURN"))
    private static boolean peepoPractice$danceOnPlayerKill(boolean bl, LivingEntity livingEntity, LivingEntity livingEntity2) {
        return bl || livingEntity2.getType().equals(EntityType.PLAYER);
    }

    @ModifyReturnValue(method = "getBarteredItem", at = @At("RETURN"))
    private static List<ItemStack> peepoPractice$guaranteeTrades(List<ItemStack> barteredItems, PiglinEntity piglin) {
        if (CategoryPreferences.RANKED_LOOT_TABLE.getBoolValue()) {
            MinecraftServer server = piglin.getServer();
            if (server == null || barteredItems.isEmpty()) {
                return barteredItems;
            }
            ServerWorld overworld = server.getOverworld();
            PiglinBarterState piglinBarterState = overworld.getPersistentStateManager().getOrCreate(PiglinBarterState::new, "piglin_barters");
            return Lists.newArrayList(piglinBarterState.guaranteeItem(piglin, barteredItems.get(0), new Random()));
        }
        return barteredItems;
    }
}
