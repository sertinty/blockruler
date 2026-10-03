# BlockRuler 数格子

Minecraft **1.21.11** / Fabric 的**纯客户端**两点测距模组。用准星选中两个方块，立刻告诉你三轴格数、直线距离和体积。

- ✅ **纯客户端**：不注册任何网络包、不需要服务端装模组，进任何服务器都能用
- ✅ 只依赖 Fabric API，不修改原版任何行为
- ✅ 快捷键可在「选项 → 控制」里改

---

## 一、快速上手

进游戏后，左上角会出现 `数格子 BlockRuler` 面板。

| 操作 | 默认按键 | 说明 |
| --- | --- | --- |
| 设置点 A | `G` | 把**准星所指的方块**记为 A |
| 设置点 B | `H` | 把**准星所指的方块**记为 B |
| 清除两点 | `J` | 清空 A、B |
| 显示/隐藏面板 | `K` | 面板开关（设置会保存） |

选中第二个点的那一刻，聊天栏会同时打印一份完整结果。

> 注意：默认键位是 `G/H/J/K`。如果和别的模组冲突，去「选项 → 控制 → 数格子 BlockRuler」里改。

### 面板长这样

```
数格子 BlockRuler
A 120 64 -30
B 130 70 -30
Δ 10 × 6 × 0   格数 11 × 7 × 1
直线距离 11.66   体积 77 方块
维度 主世界
```

- **Δ**：两点坐标差的绝对值（跨了多少格）
- **格数**：`Δ + 1`，也就是**含首尾两端一共占了多少个方块**，这才是「数格子」要的数字
- **直线距离**：两点方块中心之间的欧氏距离，保留两位小数
- **体积**：以两点为对角线的长方体包含的方块总数 = `格数X × 格数Y × 格数Z`

---

## 二、聊天栏命令

不想用快捷键也可以用命令，都是**客户端命令**，服务端收不到：

```
/blockruler pos1                # 准星所指的方块设为 A
/blockruler pos1 here           # 脚下所站的方块设为 A
/blockruler pos1 120 64 -30     # 指定绝对坐标设为 A
/blockruler pos2                # 同上，设为 B
/blockruler clear               # 清空两点
/blockruler hud                 # 显示 / 隐藏面板
/blockruler info                # 把测量结果打印到聊天栏
```

短别名：把 `/blockruler` 换成 `/br` 即可，例如 `/br pos1`。

---

## 三、配置文件

首次运行后会生成 `config/blockruler.json`（在 `.minecraft/config/` 下）：

```json
{
  "hudEnabled": true,
  "hudX": 6,
  "hudY": 6,
  "showCoordinates": true,
  "showAxis": true,
  "showDistance": true,
  "showVolume": true,
  "chatOnSet": true,
  "dimension": "minecraft:overworld",
  "a": [120, 64, -30],
  "b": [130, 70, -30]
}
```

| 字段 | 作用 |
| --- | --- |
| `hudEnabled` | 面板开关 |
| `hudX` / `hudY` | 面板左上角位置（按 GUI 缩放后的像素，用来挪开别的 HUD） |
| `showCoordinates` | 是否显示两点坐标与维度 |
| `showAxis` | 是否显示 Δ 与格数 |
| `showDistance` | 是否显示直线距离 |
| `showVolume` | 是否显示体积 |
| `chatOnSet` | 设好第二点时是否自动把结果打到聊天栏 |

选点结果会跨次启动保存；**换维度时旧的点会自动清掉**，避免读出无意义的跨维度数字。

---

## 四、自己编译

需要 **JDK 21**。

```bash
cd blockruler

# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12
gradlew.bat build

# macOS / Linux
JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

产物：`build/libs/blockruler-1.0.0.jar`

把它丢进 `.minecraft/mods/`（连同 [Fabric API](https://modrinth.com/mod/fabric-api)）即可。

> wrapper 里的 `distributionUrl` 指向腾讯云 Gradle 镜像，因为 `services.gradle.org` 会 302 到 GitHub，
> 国内网络下会 `PKIX path building failed`。如果你在墙外，可以改回官方地址。

### 开发环境运行

```bash
gradlew.bat runClient
```

---

## 五、版本矩阵

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.141.4+1.21.11 |
| Yarn mappings | 1.21.11+build.6 |
| Fabric Loom | 1.17.20 |
| Gradle | 9.5.0（Loom 1.17.20 要求 ≥ 9.5.0） |
| Java | 21 |

---

## 六、实现说明

- **取点**：`ClientPlayerEntity#raycast` 用玩家真实触及距离（`getBlockInteractionRange()`，生存 4.5 / 创造 5.0）做方块射线检测，**忽略实体**，所以对着怪物也能取到它背后的方块；拿不到时回退到客户端本帧的 `crosshairTarget`。
- **HUD**：走 Fabric API 的 `HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, ...)`，没有 Mixin，挂在聊天栏之后所以不会被别的 HUD 盖住。
- **性能**：面板内容只在选点/清空/换维度时变化，因此按 tick 缓存，不会每帧都做十几次翻译查找与字符串拼接；面板再高也不会影响帧数。
- **纯客户端**：整个模组只有 `client` 入口点，`fabric.mod.json` 里 `"environment": "client"`，服务端装了也只会被忽略。

## License

MIT
