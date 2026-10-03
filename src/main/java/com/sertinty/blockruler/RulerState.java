package com.sertinty.blockruler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模组的全部状态：选中的两个点 + HUD 配置。
 * 纯客户端，保存在 config/blockruler.json。
 */
public final class RulerState {

    private RulerState() {
    }

    /** 单个点的显示名，例如 A / B。 */
    public static final String LABEL_A = "A";
    public static final String LABEL_B = "B";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "blockruler.json";

    // ------------------------------------------------------------------
    // 可序列化的数据
    // ------------------------------------------------------------------

    private static final class Data {
        boolean hudEnabled = true;
        int hudX = 6;
        int hudY = 6;
        boolean showCoordinates = true;
        boolean showAxis = true;
        boolean showDistance = true;
        boolean showVolume = true;
        boolean chatOnSet = true;
        String dimension;
        int[] a;
        int[] b;
    }

    private static Data D = new Data();

    // ------------------------------------------------------------------
    // 配置读写
    // ------------------------------------------------------------------

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Data loaded = GSON.fromJson(json, Data.class);
            if (loaded != null) {
                D = loaded;
                if (D.hudX < 0) D.hudX = 6;
                if (D.hudY < 0) D.hudY = 6;
            }
        } catch (Exception e) {
            BlockRulerClient.LOGGER.warn("[BlockRuler] 读取配置失败，使用默认值: {}", e.toString());
        }
    }

    public static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(D), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BlockRulerClient.LOGGER.warn("[BlockRuler] 保存配置失败: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // 配置项访问器
    // ------------------------------------------------------------------

    public static boolean isHudEnabled() {
        return D.hudEnabled;
    }

    public static void toggleHud() {
        D.hudEnabled = !D.hudEnabled;
        save();
    }

    public static boolean isChatOnSet() {
        return D.chatOnSet;
    }

    public static int getHudX() {
        return D.hudX;
    }

    public static int getHudY() {
        return D.hudY;
    }

    // ------------------------------------------------------------------
    // 选点
    // ------------------------------------------------------------------

    public static BlockPos getA() {
        return toPos(D.a);
    }

    public static BlockPos getB() {
        return toPos(D.b);
    }

    public static boolean hasA() {
        return D.a != null;
    }

    public static boolean hasB() {
        return D.b != null;
    }

    public static boolean hasBoth() {
        return hasA() && hasB();
    }

    public static String getDimension() {
        return D.dimension;
    }

    /**
     * 写下某个点。切换维度时旧的点已经没有意义，直接清掉，避免误读出跨维度的数字。
     */
    public static void setPoint(boolean pointA, BlockPos pos, String currentDimension) {
        if (!currentDimension.equals(D.dimension)) {
            D.dimension = currentDimension;
            D.a = null;
            D.b = null;
        }
        int[] value = {pos.getX(), pos.getY(), pos.getZ()};
        if (pointA) {
            D.a = value;
        } else {
            D.b = value;
        }
        save();
    }

    public static void clear() {
        D.a = null;
        D.b = null;
        save();
    }

    private static BlockPos toPos(int[] arr) {
        if (arr == null || arr.length != 3) {
            return null;
        }
        return new BlockPos(arr[0], arr[1], arr[2]);
    }

    // ------------------------------------------------------------------
    // 测量
    // ------------------------------------------------------------------

    public static int dx() {
        return Math.abs(getB().getX() - getA().getX());
    }

    public static int dy() {
        return Math.abs(getB().getY() - getA().getY());
    }

    public static int dz() {
        return Math.abs(getB().getZ() - getA().getZ());
    }

    /** 含首尾两端，沿 X 轴一共跨了多少格方块。 */
    public static int spanX() {
        return dx() + 1;
    }

    public static int spanY() {
        return dy() + 1;
    }

    public static int spanZ() {
        return dz() + 1;
    }

    /** 两点之间的直线距离（以方块中心计）。 */
    public static double distance() {
        double dx = dx();
        double dy = dy();
        double dz = dz();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 以两点为对角线的长方体内包含的方块总数。 */
    public static long volume() {
        return (long) spanX() * (long) spanY() * (long) spanZ();
    }

    public static String formatDistance() {
        return String.format(Locale.ROOT, "%.2f", distance());
    }

    public static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String tr(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    /** 维度显示名，例如 minecraft:overworld -> 主世界。 */
    public static String dimensionName(String dimensionId) {
        if (dimensionId == null) {
            return "-";
        }
        return Text.translatable("dimension." + dimensionId).getString();
    }

    // ------------------------------------------------------------------
    // 文本输出（HUD 与聊天栏共用同一套排版）
    // ------------------------------------------------------------------

    /**
     * HUD 面板内容。使用 § 格式化代码，单行一条。
     *
     * 面板内容只在选点/清空/切换维度时变化，但 render() 每帧都会被调用，
     * 所以这里按 tick 缓存，避免每帧都做十几次翻译查找和字符串拼接。
     */
    public static List<String> hudLines() {
        if (hudCache != null && hudCacheTick == currentTick) {
            return hudCache;
        }
        List<String> built = buildHudLines();
        hudCache = built;
        hudCacheTick = currentTick;
        return built;
    }

    private static List<String> hudCache;
    private static int hudCacheTick = -1;
    private static int currentTick;

    /** 由客户端 tick 事件每 tick 调用一次。 */
    public static void nextTick() {
        currentTick++;
    }

    private static List<String> buildHudLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§b§l" + tr("blockruler.hud.title"));

        if (!hasA() && !hasB()) {
            lines.add("§7" + tr("blockruler.hint.empty"));
            return lines;
        }

        if (D.showCoordinates) {
            lines.add(pointLine("§a" + LABEL_A, getA()));
            lines.add(pointLine("§c" + LABEL_B, getB()));
        }

        if (!hasBoth()) {
            lines.add("§7" + tr(hasA() ? "blockruler.hint.only_a" : "blockruler.hint.only_b"));
            return lines;
        }

        if (D.showAxis) {
            lines.add("§7" + tr("blockruler.label.delta") + " §f" + dx() + " §7× §f" + dy() + " §7× §f" + dz()
                    + "   §7" + tr("blockruler.label.span") + " §f" + spanX() + " §7× §f" + spanY() + " §7× §f" + spanZ());
        }

        StringBuilder summary = new StringBuilder();
        if (D.showDistance) {
            summary.append("§7").append(tr("blockruler.label.distance")).append(" §f").append(formatDistance());
        }
        if (D.showVolume) {
            if (summary.length() > 0) {
                summary.append("   ");
            }
            summary.append("§7").append(tr("blockruler.label.volume")).append(" §f").append(volume())
                    .append(" §7").append(tr("blockruler.unit.blocks"));
        }
        if (summary.length() > 0) {
            lines.add(summary.toString());
        }

        if (D.showCoordinates && D.dimension != null) {
            lines.add("§7" + tr("blockruler.label.dimension") + " §f" + dimensionName(D.dimension));
        }
        return lines;
    }

    private static String pointLine(String label, BlockPos pos) {
        if (pos == null) {
            return label + " §8" + tr("blockruler.hud.unset");
        }
        return label + " §f" + formatPos(pos);
    }

    /**
     * 两点都选好之后，输出到聊天栏的详细结果。
     */
    public static List<String> chatLines() {
        List<String> lines = new ArrayList<>();
        if (!hasBoth()) {
            if (hasA() || hasB()) {
                lines.add("§b[BlockRuler] §7" + tr(hasA() ? "blockruler.hint.only_a" : "blockruler.hint.only_b"));
            } else {
                lines.add("§b[BlockRuler] §7" + tr("blockruler.hint.empty"));
            }
            return lines;
        }

        lines.add("§b[BlockRuler] §a" + LABEL_A + " §f" + formatPos(getA())
                + " §7→ §c" + LABEL_B + " §f" + formatPos(getB())
                + (D.dimension != null ? " §8(" + dimensionName(D.dimension) + ")" : ""));
        lines.add("§7  " + tr("blockruler.label.delta") + " §f" + dx() + " §7× §f" + dy() + " §7× §f" + dz()
                + "   §7" + tr("blockruler.label.span") + " §f" + spanX() + " §7× §f" + spanY() + " §7× §f" + spanZ()
                + "   §7" + tr("blockruler.label.distance") + " §f" + formatDistance());
        lines.add("§7  " + tr("blockruler.label.volume") + " §f" + spanX() + " §7× §f" + spanY() + " §7× §f"
                + spanZ() + " §7= §f" + volume() + " §7" + tr("blockruler.unit.blocks"));
        return lines;
    }

    /** chatLines() 的 Text 版本，方便直接丢给聊天栏或命令反馈。 */
    public static List<Text> chatText() {
        List<String> raw = chatLines();
        List<Text> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(Text.literal(line));
        }
        return out;
    }
}
