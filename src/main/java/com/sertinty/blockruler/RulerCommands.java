package com.sertinty.blockruler;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 客户端命令，服务端不会有任何感知。
 *
 * <pre>
 *   /blockruler pos1            用准星所指的方块设点 A
 *   /blockruler pos1 here       用脚下所站的方块设点 A
 *   /blockruler pos1 100 64 -30 用指定绝对坐标设点 A
 *   /blockruler pos2 ...        同上，设点 B
 *   /blockruler clear           清空两点
 *   /blockruler hud             显示 / 隐藏 HUD 面板
 *   /blockruler info            把测量结果打印到聊天栏
 * </pre>
 *
 * 另注册了短别名 /br。
 */
public final class RulerCommands {

    private RulerCommands() {
    }

    private static final SimpleCommandExceptionType NO_TARGET = new SimpleCommandExceptionType(
            Text.translatable("blockruler.msg.no_target"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess) {
        dispatcher.register(ClientCommandManager.literal("blockruler")
                .executes(ctx -> {
                    printHelp(ctx.getSource());
                    return 1;
                })
                .then(pointNode("pos1", true))
                .then(pointNode("pos2", false))
                .then(ClientCommandManager.literal("clear")
                        .executes(ctx -> {
                            RulerState.clear();
                            ctx.getSource().sendFeedback(Text.translatable("blockruler.msg.cleared"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("hud")
                        .executes(ctx -> {
                            RulerState.toggleHud();
                            ctx.getSource().sendFeedback(Text.translatable(
                                    RulerState.isHudEnabled() ? "blockruler.msg.hud_on" : "blockruler.msg.hud_off"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("info")
                        .executes(ctx -> {
                            for (Text line : RulerState.chatText()) {
                                ctx.getSource().sendFeedback(line);
                            }
                            return 1;
                        })));

        // 短别名：直接重定向到 blockruler 节点，不重复构建整棵命令树
        var root = dispatcher.getRoot().getChild("blockruler");
        if (root != null) {
            dispatcher.register(ClientCommandManager.literal("br").redirect(root));
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> pointNode(
            String name, boolean pointA) {
        return ClientCommandManager.literal(name)
                .executes(ctx -> setFromCrosshair(ctx.getSource(), pointA))
                .then(ClientCommandManager.literal("here")
                        .executes(ctx -> setHere(ctx.getSource(), pointA)))
                .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                        .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> setFromCoords(ctx, pointA)))));
    }

    private static int setFromCrosshair(FabricClientCommandSource source, boolean pointA)
            throws CommandSyntaxException {
        BlockPos pos = BlockRulerClient.targetBlock(source.getClient());
        if (pos == null) {
            throw NO_TARGET.create();
        }
        return apply(source, pointA, pos);
    }

    private static int setHere(FabricClientCommandSource source, boolean pointA) {
        BlockPos pos = source.getPlayer().getBlockPos();
        return apply(source, pointA, pos);
    }

    /**
     * 用三个整数指定绝对坐标。
     *
     * 这里刻意不用 BlockPosArgumentType：它的解析结果 PosArgument 需要一个
     * ServerCommandSource 才能转成绝对坐标，而 FabricClientCommandSource 只实现了
     * CommandSource 接口，运行时强转会炸。用纯 Brigadier 的整数参数最稳。
     */
    private static int setFromCoords(CommandContext<FabricClientCommandSource> ctx, boolean pointA) {
        int x = ctx.getArgument("x", Integer.class);
        int y = ctx.getArgument("y", Integer.class);
        int z = ctx.getArgument("z", Integer.class);
        return apply(ctx.getSource(), pointA, new BlockPos(x, y, z));
    }

    private static int apply(FabricClientCommandSource source, boolean pointA, BlockPos pos) {
        // applyPoint 内部已经负责在两点都就绪时把结果打到聊天栏
        BlockRulerClient.applyPoint(source.getClient(), pointA, pos);
        return 1;
    }

    private static void printHelp(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("§b[BlockRuler] §7" + Text.translatable("blockruler.hud.title").getString()));
        source.sendFeedback(Text.literal("§7  /blockruler pos1 §8[here|坐标]  §7" + tr("blockruler.cmd.pos1")));
        source.sendFeedback(Text.literal("§7  /blockruler pos2 §8[here|坐标]  §7" + tr("blockruler.cmd.pos2")));
        source.sendFeedback(Text.literal("§7  /blockruler clear   §7" + tr("blockruler.cmd.clear")));
        source.sendFeedback(Text.literal("§7  /blockruler hud     §7" + tr("blockruler.cmd.hud")));
        source.sendFeedback(Text.literal("§7  /blockruler info    §7" + tr("blockruler.cmd.info")));
        source.sendFeedback(Text.literal("§8  " + tr("blockruler.cmd.alias")));
    }

    private static String tr(String key) {
        return Text.translatable(key).getString();
    }
}
