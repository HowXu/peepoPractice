package me.falu.peepopractice.mixin.compat.timer;

// import com.redlimerl.speedrunigt.timer.PracticeTimerManager;
import me.falu.peepopractice.PeepoPractice;
import me.falu.peepopractice.core.category.PracticeCategoriesAny;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 这里不用inject因为新版本timer没有practice
// @Mixin(value = PracticeTimerManager.class, remap = false)
public abstract class PracticeTimerManagerMixin {
    // @Inject(method = "startPractice", at = @At("HEAD"), cancellable = true)
    private static void peepoPractice$disablePracticeTimer(CallbackInfo ci) {
        if (!PeepoPractice.CATEGORY.equals(PracticeCategoriesAny.EMPTY)) {
            ci.cancel();
        }
    }
}
