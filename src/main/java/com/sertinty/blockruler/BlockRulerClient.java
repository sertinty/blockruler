package com.sertinty.blockruler;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BlockRuler —— 纯客户端的两点测距 / 数格子工具。
 *
 * 所有逻辑都在客户端完成：读准星指向的方块、算距离、画 HUD、注册客户端命令，
 * 不发送任何自定义网络包，所以服务端装不装这个模组都能用。
 */
public class BlockRulerClient implements ClientModInitializer {

    public static final String MOD_ID = "blockruler";
    public static final Logger LOGGER = LoggerFactory.getLogger("BlockRuler");

    /** 1.21.9+ 起快捷键分组是独立对象，需要自己建一个，翻译键为 key.category.blockruler.main。 */
    public static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    public static KeyBinding keySetA;
    public static KeyBinding keySetB;
    public static KeyBinding keyClear;
    public static KeyBinding keyToggleHud;

    @Override
    public void onInitializeClient() {
        RulerState.load();

        keySetA = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockruler.set_a", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));
        keySetB = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockruler.set_b", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
        keyClear = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockruler.clear", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));
        keyToggleHud = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockruler.toggle_hud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of(MOD_ID, "hud"), new RulerHud());

        ClientCommandRegistrationCallback.EVENT.register(RulerCommands::register);

        LOGGER.info("[BlockRuler] 客户端初始化完成（G 设点 A / H 设点 B / J 清除 / K 开关 HUD）");
    }

    private void onEndTick(MinecraftClient client) {
        RulerState.nextTick();

        if (client.player == null || client.world == null) {
            return;
        }

        while (keySetA.wasPressed()) {
            setPointFromCrosshair(client, true);
        }
        while (keySetB.wasPressed()) {
            setPointFromCrosshair(client, false);
        }
        while (keyClear.wasPressed()) {
            RulerState.clear();
            feedback(client, Text.translatable("blockruler.msg.cleared"));
        }
        while (keyToggleHud.wasPressed()) {
            RulerState.toggleHud();
            feedback(client, Text.translatable(
                    RulerState.isHudEnabled() ? "blockruler.msg.hud_on" : "blockruler.msg.hud_off"));
        }
    }

    // ------------------------------------------------------------------
    // 共用工具
    // ------------------------------------------------------------------

    /** 当前维度 id，例如 minecraft:overworld。 */
    public static String currentDimension(MinecraftClient client) {
        if (client.world == null) {
            return "minecraft:overworld";
        }
        return client.world.getRegistryKey().getValue().toString();
    }

    /**
     * 准星所指的方块。实体会被忽略，只看方块碰撞箱，因此对着怪物也能取到它背后的方块。
     * 空手对着空气时返回 null。
     */
    public static BlockPos targetBlock(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        double reach = client.player.getBlockInteractionRange();
        HitResult hit = client.player.raycast(reach, 1.0F, false);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        // 兜底：直接用客户端本帧的准星结果（例如某些情况下 raycast 返回 MISS）
        HitResult crosshair = client.crosshairTarget;
        if (crosshair != null && crosshair.getType() == HitResult.Type.BLOCK && crosshair instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    /** 用准星取点，取不到就提示。 */
    public static boolean setPointFromCrosshair(MinecraftClient client, boolean pointA) {
        BlockPos pos = targetBlock(client);
        if (pos == null) {
            feedback(client, Text.translatable("blockruler.msg.no_target"));
            return false;
        }
        applyPoint(client, pointA, pos);
        return true;
    }

    public static void applyPoint(MinecraftClient client, boolean pointA, BlockPos pos) {
        RulerState.setPoint(pointA, pos, currentDimension(client));
        feedback(client, Text.translatable(pointA ? "blockruler.msg.set_a" : "blockruler.msg.set_b",
                RulerState.formatPos(pos)));
        if (RulerState.isChatOnSet() && RulerState.hasBoth()) {
            for (Text line : RulerState.chatText()) {
                feedback(client, line);
            }
        }
    }

    public static void feedback(MinecraftClient client, Text text) {
        if (client.player != null) {
            client.player.sendMessage(text, false);
        }
    }
}
