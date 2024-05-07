package me.falu.peepopractice.core.playerless;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import me.falu.peepopractice.PeepoPractice;
import me.falu.peepopractice.gui.screen.EditInventoryScreen;
import me.falu.peepopractice.gui.screen.EditShulkerBoxScreen;
import me.falu.peepopractice.gui.widget.LimitlessButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class PlayerlessHandledScreen extends Screen {
    protected final PlayerlessScreenHandler handler;
    protected final PlayerlessInventory playerInventory;
    protected final Set<Slot> cursorDragSlots;
    private final Map<Slot, List<ButtonWidget>> slotToButtonsMap;
    protected int backgroundWidth = 176;
    protected int backgroundHeight = 166;
    @Nullable protected Slot focusedSlot;
    protected int x;
    protected int y;
    protected boolean isCursorDragging;
    @Nullable private Slot touchDragSlotStart;
    @Nullable private Slot touchDropOriginSlot;
    @Nullable private Slot touchHoveredSlot;
    @Nullable private Slot lastClickedSlot;
    private boolean touchIsRightClickDrag;
    private ItemStack touchDragStack;
    private int touchDropX;
    private int touchDropY;
    private long touchDropTime;
    private ItemStack touchDropReturningStack;
    private long touchDropTimer;
    private int heldButtonType;
    private int heldButtonCode;
    private boolean cancelNextRelease;
    private int draggedStackRemainder;
    private long lastButtonClickTime;
    private int lastClickedButton;
    private boolean isDoubleClicking;
    private ItemStack quickMovingStack;

    public PlayerlessHandledScreen(PlayerlessScreenHandler handler, PlayerlessInventory inventory, Text title) {
        super(title);
        this.touchDragStack = ItemStack.EMPTY;
        this.touchDropReturningStack = ItemStack.EMPTY;
        this.cursorDragSlots = Sets.newHashSet();
        this.quickMovingStack = ItemStack.EMPTY;
        this.handler = handler;
        this.playerInventory = inventory;
        this.cancelNextRelease = true;
        this.slotToButtonsMap = new HashMap<>();
    }

    protected void init() {
        super.init();
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;
    }

    public PlayerlessScreenHandler getScreenHandler() {
        return this.handler;
    }

    @SuppressWarnings("deprecation")
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        int i = this.x;
        int j = this.y;
        this.drawBackground(matrices, delta, mouseX, mouseY);
        RenderSystem.disableRescaleNormal();
        RenderSystem.disableDepthTest();
        super.render(matrices, mouseX, mouseY, delta);
        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) i, (float) j, 0.0F);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableRescaleNormal();
        this.focusedSlot = null;
        RenderSystem.glMultiTexCoord2f(33986, 240.0F, 240.0F);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        int r;
        for (int m = 0; m < this.handler.slots.size(); ++m) {
            Slot slot = this.handler.slots.get(m);
            if (slot.doDrawHoveringEffect()) {
                this.drawSlot(matrices, slot);
            }

            if (this.isPointOverSlot(slot, mouseX, mouseY) && slot.doDrawHoveringEffect() && this.client != null) {
                this.focusedSlot = slot;
                if (
                        this.focusedSlot.inventory instanceof PlayerlessInventory
                        || this.client.currentScreen instanceof EditShulkerBoxScreen
                        || (
                                this.client.currentScreen instanceof EditInventoryScreen
                                && EditInventoryScreen.getSelectedTab() == ItemGroup.INVENTORY.getIndex()
                        )
                ) {
                    if (!this.slotToButtonsMap.containsKey(this.focusedSlot) && !this.focusedSlot.getStack().isEmpty()) {
                        List<ButtonWidget> buttons = new ArrayList<>();
                        buttons.add(
                                new LimitlessButtonWidget(
                                        this.x + this.focusedSlot.x,
                                        this.y + this.focusedSlot.y,
                                        4,
                                        4,
                                        new LiteralText("+"),
                                        button -> {
                                            ItemStack stack = this.focusedSlot.getStack();
                                            CompoundTag tag = stack.getTag() != null ? stack.getTag() : new CompoundTag();
                                            int min = tag.getInt("MinCount");
                                            int max = Math.min(tag.getInt("MaxCount"), stack.getMaxCount());
                                            if (min + 1 <= max) {
                                                tag.putInt("MinCount", min + 1);
                                                stack.setTag(tag);
                                            }
                                        }
                                )
                        );
                        buttons.add(
                                new LimitlessButtonWidget(
                                        this.x + this.focusedSlot.x,
                                        this.y + this.focusedSlot.y + 6,
                                        4,
                                        4,
                                        new LiteralText("-"),
                                        button -> {
                                            ItemStack stack = this.focusedSlot.getStack();
                                            CompoundTag tag = stack.getTag() != null ? stack.getTag() : new CompoundTag();
                                            int min = tag.getInt("MinCount");
                                            if (min - 1 >= 0) {
                                                tag.putInt("MinCount", min - 1);
                                                stack.setTag(tag);
                                            }
                                        }
                                )
                        );
                        buttons.add(
                                new LimitlessButtonWidget(
                                        this.x + this.focusedSlot.x + 16 - 4,
                                        this.y + this.focusedSlot.y,
                                        4,
                                        4,
                                        new LiteralText("+"),
                                        button -> {
                                            ItemStack stack = this.focusedSlot.getStack();
                                            CompoundTag tag = stack.getTag() != null ? stack.getTag() : new CompoundTag();
                                            int max = tag.getInt("MaxCount");
                                            if (max + 1 <= stack.getMaxCount()) {
                                                tag.putInt("MaxCount", max + 1);
                                                stack.setTag(tag);
                                            }
                                        }
                                )
                        );
                        buttons.add(
                                new LimitlessButtonWidget(
                                        this.x + this.focusedSlot.x + 16 - 4,
                                        this.y + this.focusedSlot.y + 6,
                                        4,
                                        4,
                                        new LiteralText("-"),
                                        button -> {
                                            ItemStack stack = this.focusedSlot.getStack();
                                            CompoundTag tag = stack.getTag() != null ? stack.getTag() : new CompoundTag();
                                            int min = tag.getInt("MinCount");
                                            int max = tag.getInt("MaxCount");
                                            if (max - 1 >= min) {
                                                tag.putInt("MaxCount", max - 1);
                                                stack.setTag(tag);
                                            }
                                        }
                                )
                        );
                        this.slotToButtonsMap.put(this.focusedSlot, buttons);
                        this.children.addAll(buttons);
                    }
                }
                RenderSystem.disableDepthTest();
                int n = slot.x;
                r = slot.y;
                RenderSystem.colorMask(true, true, true, false);
                this.fillGradient(matrices, n, r, n + 16, r + 16, -2130706433, -2130706433);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.enableDepthTest();
            }
        }

        Iterator<Map.Entry<Slot, List<ButtonWidget>>> iterator = this.slotToButtonsMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Slot, List<ButtonWidget>> entry = iterator.next();
            if (this.focusedSlot == null || entry.getKey().getStack().isEmpty() || entry.getKey().id != this.focusedSlot.id || !PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty()) {
                for (ButtonWidget button : entry.getValue()) {
                    this.buttons.remove(button);
                    this.children.remove(button);
                }
                iterator.remove();
            }
        }

        PlayerlessInventory playerInventory = PeepoPractice.PLAYERLESS_INVENTORY;
        ItemStack itemStack = this.touchDragStack.isEmpty() ? playerInventory.getCursorStack() : this.touchDragStack;
        if (!itemStack.isEmpty()) {
            String string = null;
            r = this.touchDragStack.isEmpty() ? 8 : 16;
            CompoundTag tag = itemStack.getTag();
            if (tag != null && (tag.contains("MinCount") || tag.contains("MaxCount"))) {
                int min = tag.getInt("MinCount");
                int max = tag.getInt("MaxCount");
                string = min + "/" + max;
            } else {
                if (!this.touchDragStack.isEmpty() && this.touchIsRightClickDrag) {
                    itemStack = itemStack.copy();
                    itemStack.setCount(MathHelper.ceil((float) itemStack.getCount() / 2.0F));
                } else if (this.isCursorDragging && this.cursorDragSlots.size() > 1) {
                    itemStack = itemStack.copy();
                    itemStack.setCount(this.draggedStackRemainder);
                    if (itemStack.isEmpty()) {
                        string = Formatting.YELLOW + "0";
                    }
                }
            }

            this.drawItem(itemStack, mouseX - i - 8, mouseY - j - r, string);
        }

        if (!this.touchDropReturningStack.isEmpty() && this.touchDropOriginSlot != null) {
            String string = null;
            CompoundTag tag = this.touchDropReturningStack.getTag();
            if (tag != null && (tag.contains("MinCount") || tag.contains("MaxCount"))) {
                int min = tag.getInt("MinCount");
                int max = tag.getInt("MaxCount");
                string = min + "/" + max;
            }

            float f = (float) (Util.getMeasuringTimeMs() - this.touchDropTime) / 100.0F;
            if (f >= 1.0F) {
                f = 1.0F;
                this.touchDropReturningStack = ItemStack.EMPTY;
            }

            r = this.touchDropOriginSlot.x - this.touchDropX;
            int s = this.touchDropOriginSlot.y - this.touchDropY;
            int t = this.touchDropX + (int) ((float) r * f);
            int u = this.touchDropY + (int) ((float) s * f);
            this.drawItem(this.touchDropReturningStack, t, u, string);
        }

        RenderSystem.popMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.pushMatrix();
        RenderSystem.translatef(0.0F, 0.0F, 400.0F);
        for (List<ButtonWidget> buttons : this.slotToButtonsMap.values()) {
            for (ButtonWidget button : buttons) {
                button.render(matrices, mouseX, mouseY, delta);
            }
        }
        RenderSystem.popMatrix();
    }

    @SuppressWarnings("deprecation")
    protected void drawMouseoverTooltip(MatrixStack matrices, int x, int y) {
        if (PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty() && this.focusedSlot != null && this.focusedSlot.hasStack()) {
            RenderSystem.translatef(0.0F, 0.0F, this.itemRenderer.zOffset + 5.0F);
            this.renderTooltip(matrices, this.focusedSlot.getStack(), x, y);
        }
    }

    @SuppressWarnings("deprecation")
    private void drawItem(ItemStack stack, int xPosition, int yPosition, String amountText) {
        RenderSystem.translatef(0.0F, 0.0F, 32.0F);
        this.setZOffset(200);
        this.itemRenderer.zOffset = 200.0F;
        this.itemRenderer.renderInGuiWithOverrides(stack, xPosition, yPosition);
        this.itemRenderer.renderGuiItemOverlay(this.textRenderer, stack, xPosition, yPosition - (this.touchDragStack.isEmpty() ? 0 : 8), amountText);
        this.setZOffset(0);
        this.itemRenderer.zOffset = 0.0F;
    }

    protected abstract void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY);

    private void drawSlot(MatrixStack matrices, Slot slot) {
        int i = slot.x;
        int j = slot.y;
        ItemStack itemStack = slot.getStack();
        boolean bl = false;
        boolean bl2 = slot == this.touchDragSlotStart && !this.touchDragStack.isEmpty() && !this.touchIsRightClickDrag;
        ItemStack itemStack2 = PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack();
        String string = null;
        if (slot == this.touchDragSlotStart && !this.touchDragStack.isEmpty() && this.touchIsRightClickDrag && !itemStack.isEmpty()) {
            itemStack = itemStack.copy();
            itemStack.setCount(itemStack.getCount() / 2);
        } else if (this.isCursorDragging && this.cursorDragSlots.contains(slot) && !itemStack2.isEmpty()) {
            if (this.cursorDragSlots.size() == 1) {
                return;
            }

            if (ScreenHandler.canInsertItemIntoSlot(slot, itemStack2, true)) {
                itemStack = itemStack2.copy();
                bl = true;
                ScreenHandler.calculateStackSize(this.cursorDragSlots, this.heldButtonType, itemStack, slot.getStack().isEmpty() ? 0 : slot.getStack().getCount());
                int k = Math.min(itemStack.getMaxCount(), slot.getMaxStackAmount(itemStack));
                if (itemStack.getCount() > k) {
                    string = Formatting.YELLOW.toString() + k;
                    itemStack.setCount(k);
                }
            } else {
                this.cursorDragSlots.remove(slot);
                this.calculateOffset();
            }
        }

        this.setZOffset(100);
        this.itemRenderer.zOffset = 100.0F;
        if (itemStack.isEmpty() && slot.doDrawHoveringEffect()) {
            Pair<Identifier, Identifier> pair = slot.getBackgroundSprite();
            if (pair != null && this.client != null) {
                Sprite sprite = this.client.getSpriteAtlas(pair.getFirst()).apply(pair.getSecond());
                this.client.getTextureManager().bindTexture(sprite.getAtlas().getId());
                drawSprite(matrices, i, j, this.getZOffset(), 16, 16, sprite);
                bl2 = true;
            }
        }

        if (!bl2) {
            if (bl) {
                fill(matrices, i, j, i + 16, j + 16, -2130706433);
            }

            RenderSystem.enableDepthTest();
            this.itemRenderer.renderInGui(itemStack, i, j);
            CompoundTag tag = itemStack.getTag();
            if (tag != null && (tag.contains("MinCount") || tag.contains("MaxCount"))) {
                int min = tag.getInt("MinCount");
                int max = tag.getInt("MaxCount");
                string = min + "/" + max;
            }
            this.itemRenderer.renderGuiItemOverlay(this.textRenderer, itemStack, i, j, string);
        }

        this.itemRenderer.zOffset = 0.0F;
        this.setZOffset(0);
    }

    private void calculateOffset() {
        ItemStack itemStack = PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack();
        if (!itemStack.isEmpty() && this.isCursorDragging) {
            if (this.heldButtonType == 2) {
                this.draggedStackRemainder = itemStack.getMaxCount();
            } else {
                this.draggedStackRemainder = itemStack.getCount();

                ItemStack itemStack2;
                int i;
                for (Iterator<Slot> var2 = this.cursorDragSlots.iterator(); var2.hasNext(); this.draggedStackRemainder -= itemStack2.getCount() - i) {
                    Slot slot = var2.next();
                    itemStack2 = itemStack.copy();
                    ItemStack itemStack3 = slot.getStack();
                    i = itemStack3.isEmpty() ? 0 : itemStack3.getCount();
                    ScreenHandler.calculateStackSize(this.cursorDragSlots, this.heldButtonType, itemStack2, i);
                    int j = Math.min(itemStack2.getMaxCount(), slot.getMaxStackAmount(itemStack2));
                    if (itemStack2.getCount() > j) {
                        itemStack2.setCount(j);
                    }
                }
            }
        }
    }

    @Nullable
    private Slot getSlotAt(double xPosition, double yPosition) {
        for (int i = 0; i < this.handler.slots.size(); ++i) {
            Slot slot = this.handler.slots.get(i);
            if (this.isPointOverSlot(slot, xPosition, yPosition) && slot.doDrawHoveringEffect()) {
                return slot;
            }
        }

        return null;
    }

    @SuppressWarnings("DuplicatedCode")
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.client == null) {
            return false;
        }
        if (!super.mouseClicked(mouseX, mouseY, button)) {
            boolean bl = this.client.options.keyPickItem.matchesMouse(button);
            Slot slot = this.getSlotAt(mouseX, mouseY);
            long l = Util.getMeasuringTimeMs();
            this.isDoubleClicking = this.lastClickedSlot == slot && l - this.lastButtonClickTime < 250L && this.lastClickedButton == button;
            this.cancelNextRelease = false;
            if (button != 0 && button != 1 && !bl) {
                this.method_30107(button);
            } else {
                int i = this.x;
                int j = this.y;
                boolean bl2 = this.isClickOutsideBounds(mouseX, mouseY, i, j);
                int k = -1;
                if (slot != null) {
                    k = slot.id;
                }

                if (bl2) {
                    k = -999;
                }

                if (this.client.options.touchscreen && bl2 && PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty()) {
                    this.client.openScreen(null);
                    return true;
                }

                if (k != -1) {
                    if (this.client.options.touchscreen) {
                        if (slot != null && slot.hasStack()) {
                            this.touchDragSlotStart = slot;
                            this.touchDragStack = ItemStack.EMPTY;
                            this.touchIsRightClickDrag = button == 1;
                        } else {
                            this.touchDragSlotStart = null;
                        }
                    } else if (!this.isCursorDragging) {
                        if (PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty()) {
                            if (this.client.options.keyPickItem.matchesMouse(button)) {
                                this.onMouseClick(slot, k, button, SlotActionType.CLONE);
                            } else {
                                boolean bl3 = k != -999 && Screen.hasShiftDown();
                                SlotActionType slotActionType = SlotActionType.PICKUP;
                                if (bl3) {
                                    this.quickMovingStack = slot.hasStack() ? slot.getStack().copy() : ItemStack.EMPTY;
                                    slotActionType = SlotActionType.QUICK_MOVE;
                                } else if (k == -999) {
                                    slotActionType = SlotActionType.THROW;
                                }

                                this.onMouseClick(slot, k, button, slotActionType);
                            }

                            this.cancelNextRelease = true;
                        } else {
                            this.isCursorDragging = true;
                            this.heldButtonCode = button;
                            this.cursorDragSlots.clear();
                            if (button == 0) {
                                this.heldButtonType = 0;
                            } else if (button == 1) {
                                this.heldButtonType = 1;
                            } else if (this.client.options.keyPickItem.matchesMouse(button)) {
                                this.heldButtonType = 2;
                            }
                        }
                    }
                }
            }

            this.lastClickedSlot = slot;
            this.lastButtonClickTime = l;
            this.lastClickedButton = button;
        }
        return true;
    }

    private void method_30107(int i) {
        if (this.focusedSlot != null && PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty() && this.client != null) {
            if (this.client.options.keySwapHands.matchesMouse(i)) {
                this.onMouseClick(this.focusedSlot, this.focusedSlot.id, 40, SlotActionType.SWAP);
                return;
            }
            for (int j = 0; j < 9; ++j) {
                if (this.client.options.keysHotbar[j].matchesMouse(i)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.id, j, SlotActionType.SWAP);
                }
            }
        }
    }

    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top) {
        return mouseX < (double) left || mouseY < (double) top || mouseX >= (double) (left + this.backgroundWidth) || mouseY >= (double) (top + this.backgroundHeight);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        Slot slot = this.getSlotAt(mouseX, mouseY);
        ItemStack itemStack = PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack();
        if (this.touchDragSlotStart != null && this.client != null && this.client.options.touchscreen) {
            if (button == 0 || button == 1) {
                if (this.touchDragStack.isEmpty()) {
                    if (slot != this.touchDragSlotStart && !this.touchDragSlotStart.getStack().isEmpty()) {
                        this.touchDragStack = this.touchDragSlotStart.getStack().copy();
                    }
                } else if (this.touchDragStack.getCount() > 1 && slot != null && ScreenHandler.canInsertItemIntoSlot(slot, this.touchDragStack, false)) {
                    long l = Util.getMeasuringTimeMs();
                    if (this.touchHoveredSlot == slot) {
                        if (l - this.touchDropTimer > 500L) {
                            this.onMouseClick(this.touchDragSlotStart, this.touchDragSlotStart.id, 0, SlotActionType.PICKUP);
                            this.onMouseClick(slot, slot.id, 1, SlotActionType.PICKUP);
                            this.onMouseClick(this.touchDragSlotStart, this.touchDragSlotStart.id, 0, SlotActionType.PICKUP);
                            this.touchDropTimer = l + 750L;
                            this.touchDragStack.decrement(1);
                        }
                    } else {
                        this.touchHoveredSlot = slot;
                        this.touchDropTimer = l;
                    }
                }
            }
        } else if (this.isCursorDragging && slot != null && !itemStack.isEmpty() && (itemStack.getCount() > this.cursorDragSlots.size() || this.heldButtonType == 2) && ScreenHandler.canInsertItemIntoSlot(slot, itemStack, true) && slot.canInsert(itemStack)) {
            this.cursorDragSlots.add(slot);
            this.calculateOffset();
        }

        return true;
    }

    @SuppressWarnings("DuplicatedCode")
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Slot slot = this.getSlotAt(mouseX, mouseY);
        int i = this.x;
        int j = this.y;
        boolean bl = this.isClickOutsideBounds(mouseX, mouseY, i, j);
        int k = -1;
        if (slot != null) {
            k = slot.id;
        }

        if (bl) {
            k = -999;
        }

        Slot slot3;
        Iterator<Slot> var13;
        if (this.isDoubleClicking && slot != null && button == 0) {
            if (hasShiftDown()) {
                if (!this.quickMovingStack.isEmpty()) {
                    for (Slot slot2 : this.handler.slots) {
                        if (slot2 == null || !slot2.hasStack() || slot2.inventory != slot.inventory || !ScreenHandler.canInsertItemIntoSlot(slot2, this.quickMovingStack, true)) {
                            continue;
                        }
                        this.onMouseClick(slot2, slot2.id, button, SlotActionType.QUICK_MOVE);
                    }
                }
            } else {
                this.onMouseClick(slot, k, button, SlotActionType.PICKUP_ALL);
            }

            this.isDoubleClicking = false;
            this.lastButtonClickTime = 0L;
        } else {
            if (this.isCursorDragging && this.heldButtonCode != button) {
                this.isCursorDragging = false;
                this.cursorDragSlots.clear();
                this.cancelNextRelease = true;
                return true;
            }

            if (this.cancelNextRelease) {
                this.cancelNextRelease = false;
                return true;
            }

            boolean bl3;
            if (this.touchDragSlotStart != null && this.client != null && this.client.options.touchscreen) {
                if (button == 0 || button == 1) {
                    if (this.touchDragStack.isEmpty() && slot != this.touchDragSlotStart) {
                        this.touchDragStack = this.touchDragSlotStart.getStack();
                    }

                    bl3 = ScreenHandler.canInsertItemIntoSlot(slot, this.touchDragStack, false);
                    if (k != -1 && !this.touchDragStack.isEmpty() && bl3) {
                        this.onMouseClick(this.touchDragSlotStart, this.touchDragSlotStart.id, button, SlotActionType.PICKUP);
                        this.onMouseClick(slot, k, 0, SlotActionType.PICKUP);
                        if (PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty()) {
                            this.touchDropReturningStack = ItemStack.EMPTY;
                        } else {
                            this.onMouseClick(this.touchDragSlotStart, this.touchDragSlotStart.id, button, SlotActionType.PICKUP);
                            this.touchDropX = MathHelper.floor(mouseX - (double) i);
                            this.touchDropY = MathHelper.floor(mouseY - (double) j);
                            this.touchDropOriginSlot = this.touchDragSlotStart;
                            this.touchDropReturningStack = this.touchDragStack;
                            this.touchDropTime = Util.getMeasuringTimeMs();
                        }
                    } else if (!this.touchDragStack.isEmpty()) {
                        this.touchDropX = MathHelper.floor(mouseX - (double) i);
                        this.touchDropY = MathHelper.floor(mouseY - (double) j);
                        this.touchDropOriginSlot = this.touchDragSlotStart;
                        this.touchDropReturningStack = this.touchDragStack;
                        this.touchDropTime = Util.getMeasuringTimeMs();
                    }

                    this.touchDragStack = ItemStack.EMPTY;
                    this.touchDragSlotStart = null;
                }
            } else if (this.isCursorDragging && !this.cursorDragSlots.isEmpty()) {
                this.onMouseClick(null, -999, ScreenHandler.packQuickCraftData(0, this.heldButtonType), SlotActionType.QUICK_CRAFT);
                var13 = this.cursorDragSlots.iterator();

                while (var13.hasNext()) {
                    slot3 = var13.next();
                    this.onMouseClick(slot3, slot3.id, ScreenHandler.packQuickCraftData(1, this.heldButtonType), SlotActionType.QUICK_CRAFT);
                }

                this.onMouseClick(null, -999, ScreenHandler.packQuickCraftData(2, this.heldButtonType), SlotActionType.QUICK_CRAFT);
            } else if (!PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty()) {
                if (this.client != null && this.client.options.keyPickItem.matchesMouse(button)) {
                    this.onMouseClick(slot, k, button, SlotActionType.CLONE);
                } else {
                    bl3 = k != -999 && (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), 340) || InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), 344));
                    if (bl3) {
                        this.quickMovingStack = slot != null && slot.hasStack() ? slot.getStack().copy() : ItemStack.EMPTY;
                    }

                    this.onMouseClick(slot, k, button, bl3 ? SlotActionType.QUICK_MOVE : SlotActionType.PICKUP);
                }
            }
        }

        if (PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty()) {
            this.lastButtonClickTime = 0L;
        }

        this.isCursorDragging = false;
        return true;
    }

    private boolean isPointOverSlot(Slot slot, double pointX, double pointY) {
        return this.isPointWithinBounds(slot.x, slot.y, 16, 16, pointX, pointY);
    }

    protected boolean isPointWithinBounds(int xPosition, int yPosition, int width, int height, double pointX, double pointY) {
        int i = this.x;
        int j = this.y;
        pointX -= i;
        pointY -= j;
        return pointX >= (double) (xPosition - 1) && pointX < (double) (xPosition + width + 1) && pointY >= (double) (yPosition - 1) && pointY < (double) (yPosition + height + 1);
    }

    protected void onMouseClick(Slot slot, int invSlot, int clickData, SlotActionType actionType) {
        this.handler.onSlotClick(slot == null ? invSlot : slot.id, clickData, actionType, this.playerInventory);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (keyCode != 256 && this.client != null && !this.client.options.keyInventory.matchesKey(keyCode, scanCode)) {
            this.handleHotbarKeyPressed(keyCode, scanCode);
            if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
                if (this.client.options.keyPickItem.matchesKey(keyCode, scanCode)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.id, 0, SlotActionType.CLONE);
                } else if (this.client.options.keyDrop.matchesKey(keyCode, scanCode)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.id, hasControlDown() ? 1 : 0, SlotActionType.THROW);
                }
            }

            return true;
        } else {
            return true;
        }
    }

    protected boolean handleHotbarKeyPressed(int keyCode, int scanCode) {
        if (this.client != null && PeepoPractice.PLAYERLESS_INVENTORY.getCursorStack().isEmpty() && this.focusedSlot != null) {
            if (this.client.options.keySwapHands.matchesKey(keyCode, scanCode)) {
                this.onMouseClick(this.focusedSlot, this.focusedSlot.id, 40, SlotActionType.SWAP);
                return true;
            }

            for (int i = 0; i < 9; ++i) {
                if (this.client.options.keysHotbar[i].matchesKey(keyCode, scanCode)) {
                    this.onMouseClick(this.focusedSlot, this.focusedSlot.id, i, SlotActionType.SWAP);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isPauseScreen() {
        return false;
    }
}
