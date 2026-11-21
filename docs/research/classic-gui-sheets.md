# 格式 16：经典 GUI 拼图的逐像素证据

## 来源与验证口径

- [Mojang 的 23w31a 发布说明](https://www.minecraft.net/en-us/article/minecraft-snapshot-23w31a) 明确把资源包格式升至 16、把界面精灵迁到 `textures/gui/sprites/`、并声明 sprite 的 `gui.scaling`（默认 stretch，另有 tile、nine_slice）。说明链接了官方 Slicer；本实现未运行、复制或采用该工具的代码或图像内容。
- [Mojang 客户端版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 中没有 23w30a；本地 Minecraft Git 中 23w31a 的直接父提交是 1.20.1。因此取 1.20.1（格式 15）为旧端、23w31a（格式 16）为首个新端，并用 1.20.2（格式 18）复核正式发布。Git 提交分别为 `3015bd18b21c5f345f0c9b294a687e7a17dd36c3`、`10bf742e1a8fe3cabcfb8ee752279e05328075ee`、`913dbbb4c6775bbd333f70570a8bbadc1aabb506`。

| 原版元数据 | 发布的客户端 JAR SHA-1（实测一致） |
|---|---|
| [1.20.1](https://piston-meta.mojang.com/v1/packages/fb730d2829f057fe26a41e5ecdc9e3307333bef5/1.20.1.json) | `0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838` |
| [23w31a](https://piston-meta.mojang.com/v1/packages/91264ab59d2c6c94df140fbecef1e90cc4de1f25/23w31a.json) | `ca51bf36913a7333c055096a52a3a96fbdb11813` |
| [1.20.2](https://piston-meta.mojang.com/v1/packages/2de0c0ee516b632aab03cd5ea4d34e5aafcdf130/1.20.2.json) | `82d1974e75fc984c5ed4b038e764e50958ac61a0` |

在各自 JAR 内读取 `assets/minecraft/textures/gui/`。先用旧端渲染 UV + 新端 sprite ID 配对（不凭相似度猜状态），再将旧端 PNG 裁成相同尺寸，与两个新端分别比较**全部 RGBA 分量，包含 alpha=0 的 RGB**。结果：六张 256×256 旧图无侧车，95 个矩形在两个新端均无任何像素差异。新端所列五个 sprite 子目录的 PNG 清单恰为这 95 个；只有 `advancements/title_box.png` 在两个新端都另有侧车。

## 落地的矩形与调用证据

下列相对路径以 `assets/minecraft/textures/gui/` 为根，目标统一加 `sprites/` 前缀与 `.png`。坐标均为 1× 的整数 (x, y, width, height)。源码锚点通过 `git show <commit>:client/net/minecraft/<path>` 可复查。

| 旧图 → 新资源 | 数 | 取值依据与槽位 |
|---|---:|---|
| `bars.png` → `boss_bar/` | 22 | 旧 `client/gui/components/BossHealthOverlay.java:53–66` 按颜色和分段绘制，新 `client/gui/components/BossHealthOverlay.java:24–27,55–68` 给出独立 ID。固定宽 182、高 5。颜色按 pink、blue、red、green、yellow、purple、white，y=0、10…60；notched_6、10、12、20 从 y=80 起每组 10；同组 progress 相对于 background +5。 |
| `advancements/widgets.png` → `advancements/{box_*,*_frame_*,title_box}` | 9 | 旧 `client/gui/screens/advancements/AdvancementWidget.java:148–154,207–217`、`client/gui/screens/advancements/AdvancementWidgetType.java:7–8`、`advancements/FrameType.java:10–12`；新 `client/gui/screens/advancements/AdvancementWidgetType.java:10–11` 与 `client/gui/screens/advancements/AdvancementWidget.java:207–217`。obtained/unobtained 的 box 为 (0,0/26,200,26)；task/challenge/goal 的 frame x=0/26/52，y=128/154，宽高 26。title_box 为 (0,52,200,26)，与旧九宫格逻辑及新 PNG 侧车一致：`{"gui":{"scaling":{"type":"nine_slice","width":200,"height":26,"border":10}}}`。 |
| `advancements/tabs.png` → `advancements/tab_*` | 24 | 两端 `client/gui/screens/advancements/AdvancementTabType.java:10–43`。above/below 起点 (0/84,0)，大小 28×32；left/right 起点 (0/96,64)，大小 32×28。每种方向三个位置依次右移一个宽度，selected 下移一个高度。 |
| `container/beacon.png` → `container/beacon/` | 6 | 旧 `client/gui/screens/inventory/BeaconScreen.java:150–170,273,286–302`；新同路径 :38–44,276,295–296。button、selected、disabled、highlighted 是 y=219、x=0/22/44/66 的 22×22；confirm/cancel 分别是 (90/112,220,18,18)。背景仍以旧文件路径读取，不能删除。 |
| `container/creative_inventory/tabs.png` → `container/creative_inventory/` | 30 | 旧 `client/gui/screens/inventory/CreativeModeInventoryScreen.java:618,658–676`；新同路径 :63–68,620–621,667–668。四行 top_unselected/top_selected/bottom_unselected/bottom_selected，y=0/32/64/96；每行 7 个 26×32 槽，列从零开始取像素、从一开始命名。两种 scroller 用 (232/244,0,12,15)。 |
| `container/horse.png` → `container/horse/` | 4 | 旧 `client/gui/screens/inventory/HorseInventoryScreen.java:28–45`，且 `client/gui/screens/inventory/AbstractContainerScreen.java:43–44` 的背景高度为 166；新同 screen :18–22,37–48。chest_slots (0,166,90,54)；armor/saddle/llama_armor_slot 为 (0/18/36,220,18,18)。背景仍以旧文件路径读取。 |

## 未覆盖与保留

- 遮掉映射矩形及原版仍绘制的背景（beacon 230×219、horse 176×166）之后，旧端 `container/beacon.png` 另有 50 个 alpha>0 像素，边界框 `[88,238,132,242)`；不扩大 confirm/cancel 的旧 18×18 UV。旧端 `container/creative_inventory/tabs.png` 的第八列 `[182,0,208,128)` 另有 3328 个 alpha>0 像素；新端只声明 7 列，不制造第 8 列 ID。其余四张旧图在上述区域之外没有 alpha>0 像素。旧 PNG 本身始终逐字节保留。
- 23w31a 的 BossHealthOverlay 对 notched 进度使用只传目标宽度的 `blitSprite`，未传裁剪区域；1.20.2 的同调用已变为显式 182×5 源尺寸及裁剪参数。素材等价不等于修复快照自身的绘制差异。
- 不重做 [现有 GUI 图集规则](../../src/main/java/top/syshub/viarp/convert/IntermediatePackUpgrades.java) 已处理的根 `widgets.png` 和 `icons.png`；本模块也未验证/处理原版更新说明里的 bundle、Realms、设置按钮、其他 GUI、后续版本的名称或第三方资源布局。窗口背景继续用原路径；不猜新的动画、布局或资源命名。RGBA 等价是这两组原版素材的实测；不承诺保留任意 PNG 压缩字节/内部元数据或逐帧画面一致。
- 只从精确原版输入路径切出缺失 sprite。显式目标 PNG 或侧车（含大小写别名）优先，跳过且不混配；旧 PNG 如带侧车，不推导其动画/其他含义。非正方形、非 256 的整数倍和畸形 PNG 只警告。通过 [共用 PNG helper](../../src/main/java/top/syshub/viarp/convert/UpgradePng.java) 预检边长 ≤4096、面积 ≤4096²、文件 ≤32 MiB、非 IDAT 块与块数量，再进行无重采样裁切。title_box 的九宫格数值是逻辑尺寸，不乘 2× 等像素倍率。

## 探针

1. 本地临时 Pillow 探针从三个 SHA-1 已核对的 JAR 提取成员；每槽比较尺寸、逐像素 RGBA 与侧车。独立保留检测遮掉映射、背景矩形后统计 alpha>0 像素的个数和最小外接矩形。
2. 把六张官方输入 PNG 放进独立的临时 `UpgradeWorkspace`，调用 [补切入口](../../src/main/java/top/syshub/viarp/convert/ClassicGuiSheets.java)，Java ImageIO 对两个新端的 95 张输出分别做逐 ARGB 数组对比，并检查精确路径清单、旧图字节不变和 title_box 的 JSON。合成 512×512 夹具含 alpha=0/127/255 及非零隐藏 RGB，用于检查 2× 裁切、九宫格逻辑尺寸、case-variant PNG/单独侧车保护、带旧侧车、非 PNG、非方形输入的拒绝；重复调用验证清单及选定输出 mtime 不变。
3. Zulu 8u504 编译并运行探针、Zulu 25.0.4.1 用 `-source 8 -target 8` 编译后在 Zulu 8u504 运行同一探针，均通过（每次 23w31a 95 对 + 1.20.2 95 对）。没有运行 Minecraft 客户端、Gradle 或把官方素材纳入仓库。
