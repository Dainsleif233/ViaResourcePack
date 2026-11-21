# 实体贴图迁移：26.2 床与告示牌

## 格式边

| entered | 原版两端（本地 Minecraft 参考仓库） | 实际变化 |
| --- | --- | --- |
| 86.0 | Snapshot 2（85.0，`434148b8e`）→ [Snapshot 3](https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-3)（`24ce64cf3`） | 床改用方块模型：16 个 `textures/entity/bed/<color>.png` 消失，出现 114 个逐面 block PNG（每色 7 张 + 黑色源派生 2 张共享图）；`atlases/beds.json` 仍在客户端，仍由 `AtlasManager` 注册。 |
| 86.1 | Snapshot 3 → [Snapshot 4](https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-4)（`57217e146`） | `atlases/beds.json` 及 `AtlasManager` 注册移除，床逐面 PNG 不在此边重新生成。 |
| 87.0 | Snapshot 6（86.2，`7bdbf43e9`）→ [Snapshot 7](https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-7)（`b4c0b4731`） | 两类告示牌改用方块模型：旧 24 个实体 PNG 消失并移除 `signs` atlas，增加 36 个目标。 |

[26.2 正式发行说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2) 汇总了这两次图集移除及新路径，并指向 [Mojang Slicer](https://github.com/Mojang/slicer/blob/main/26.2/src/main/java/com/mojang/slicer/Main.java)。只静态读取其 MIT 授权的坐标及方向事实作交叉参考，没有运行 Slicer 或复制它的图像处理实现；本规则独立用整数坐标拷贝像素。

## 两端模型、代码与 UV

下列材质路径均相对于 `assets/minecraft/`；原版代码路径均相对于参考仓库的 `client/net/minecraft/client/`。矩形为左上角 `(x,y,w,h)`，单位为原版像素。

- **床（86.0）。** 旧 `renderer/blockentity/BedRenderer.java` 在 `434148b8e` 的 79–96 行定义 64×64 实体图层；床身 UV 原点是 `(0,0)`、`(0,22)`，并有四个 3×3×3 床腿。在 `24ce64cf3` 客户端，`models/block/red_bed_head.json`/`red_bed_foot.json` 消费各色 `up/east/west` 及脚端 `south`，父模型 `models/block/bed_head.json`/`bed_foot.json` 消费共享 `block/bed_down.png` 与头部共享 `block/bed_head_north.png`。例如旧红图头顶 `(6,6,16,16)` 直接成为 `red_bed_head_up.png`；头东侧旧 `(22,6,6,16)` 顺时针 90° 成为新头东图的 `(0,7,16,6)`。新模型床框 UV `[0,7,16,13]`、腿部高度 `[13,16]`，解释了为什么侧面不是一次矩形裁剪。共享目标仅按黑色旧图派生；例如 `bed_down.png` 为黑图 `(28,6,16,16)` 旋转 180°。逐色的共享底/头端、以及新模型共用或省略的其它分面不会伪造为新模型。
- **普通木牌（87.0）。** 旧 `renderer/blockentity/StandingSignRenderer.java` 在 `7bdbf43e9` 的 136–143 行用 64×32 实体图层、24×12×2 牌面和 2×14×2 柱脚；新 `models/block/oak_sign_rot_0.json`/`oak_wall_sign.json` 的 `#all` 指向 `textures/block/oak_sign.png`，32×32。旧 `gui/screens/inventory/SignEditScreen.java` 在 35、53 行使用实体模型；新 `b4c0b4731` 的 29、41 行改为 24×26 的 `textures/gui/signs/oak.png`。GUI 牌面由旧 `(2,2,24,12)` 移至 `(0,0)`，柱面 `(2,16,2,14)` 移至 `(11,12)`。
- **悬挂木牌（87.0）。** 旧 `renderer/blockentity/HangingSignRenderer.java` 在 `7bdbf43e9` 的 137–154 行定义 64×32 实体图、牌面、横梁、直链与 V 链。新 `models/block/oak_hanging_sign_rot_0.json`/`oak_wall_hanging_sign.json` 的 `#all` 指向 `textures/block/oak_hanging_sign.png`，32×32；旧板身条 `(0,14,32,10)` 送到新 `(0,16)`，顶部、底部、梁与链则按各自新 UV 重排。旧、新 `gui/screens/inventory/HangingSignEditScreen.java` 的 29、41 行均使用早已有的 16×16 `textures/gui/hanging_signs/<wood>.png`；迁移既不生成也不修改该资源。

## 保留策略

- 只为已知的 16 个床色和 12 种原版木材的规范路径生成缺失目标；旧 PNG、图集、任意扩展源、模型和所有侧车都保留。已有新 PNG（不论内容是否合法）、单独存在的目标侧车、大小写碰撞和文件/目录占位优先，逐目标提示冲突，其他不冲突目标仍可生成。
- 只接受原版 64×64 / 64×32 画布的等比整数放大（宽度上限 4096）。畸形、非 PNG、非整数或不等比尺寸、超过 8-bit 的组件、含有源侧车的输入（如动画和未知扩展节）跳过并提示。保留透明像素中的 RGB；不插值、不混合、不推断自定义 UV。若自定义 UV 恰好与原版等尺寸且无标识，无法从 PNG 单独判别。
- 迁移输入 atlas 若仅使用原版 `directory` 来源，不改变默认精灵查找；若工作树包含非规范 `beds.json` / `signs.json`（包括 `single` 指向另一路径、过滤器或不明扩展），保留并保守跳过整个相应切片族：不能从同名 PNG 推断旧渲染真正看到的源。对 86.1/87.0 移除边，保留但单独提示迁移自定义 atlas sources。转换看不到其他叠加的资源包，不承诺它们之间的覆盖关系。

## 实测与复现

原版输入来自 [Mojang 客户端版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 中各版本的 `downloads.client`，只作为 ZIP 读取；下载后校验如下 SHA-1（没有执行客户端）：

| 客户端 | SHA-1 |
| --- | --- |
| 26.2 Snapshot 2 | `9578fe329bdf486d5f99896749a5f85c94d93b6a` |
| 26.2 Snapshot 3 | `b2fc8a4c04fc5984fe408aba387e316a8784d79b` |
| 26.2 Snapshot 4 | `08ff2a0b68a3eda26cb73abafe87cd8c9b5a838f` |
| 26.2 Snapshot 6 | `7926cea1792ec1065f006056dbd29d842a58c827` |
| 26.2 Snapshot 7 | `8a35666eb5c6d625dd90662bdb7259ec67aaf26f` |
| 26.2 正式版 | `2dc72797acbc1b63fc16a11c4ac393605f453754` |

将两种旧目录内的 PNG 分别解压到临时目录（保留 `assets/minecraft/` 前缀），用 JDK 8 `javac -encoding UTF-8` 编译同包临时入口、迁移类、`UpgradeWorkspace`、`PackFormat`、`PathIo`、`Json` 和 `JsonException`，分别调用 `apply(PackFormat.of(86, 0), workspace)` 或 `apply(PackFormat.of(87, 0), workspace)`。比较输出严格文件集合、宽高和 PNG 解码后的自顶向下 RGBA 字节，不比较编码或压缩块。实测 16 个旧床 PNG → **114/114** 输出与 Snapshot 3 完全相同；24 个旧木牌 PNG → **36/36** 输出与 Snapshot 7 完全相同。两套目标也分别与 26.2 正式版逐像素相同；输入字节无改变。

| 版本/贴图（相对 `textures/`） | 尺寸 | RGBA SHA-256 |
| --- | --- | --- |
| Snapshot 2 `entity/bed/red.png` | 64×64 | `0e6af475d7e2bc3f21fa0c3fa2496180304e5d9913d89e1e514e7586038feff6` |
| Snapshot 3 / 产物 `block/red_bed_head_east.png` | 16×16 | `f66e43ddea9be8eb328c0602b1599b5dcac4e177b09a710d89ab581ae2417ae4` |
| Snapshot 6 `entity/signs/oak.png` | 64×32 | `4e0120e91c7bbdf4359efc9abc0054cf42fb04f86dcfc0e856c73f050b11a90f` |
| Snapshot 7 / 产物 `gui/signs/oak.png` | 24×26 | `a21b769fcaf07d1714d7787fce29d46a2c6b1624dbff95ac067888889bf2fc18` |
| Snapshot 6 `entity/signs/hanging/oak.png` | 64×32 | `737d82e4a9cdc3581d384d1ceff0c9eaa4e4c42e76000845bd91dc96f0ad12d7` |
| Snapshot 7 / 产物 `block/oak_hanging_sign.png` | 32×32 | `e310918c1e17617a5937d61089f78bd7ade7bcb521854b994b6681edb4689671` |

另用临时合成图复验 2× 非对称逐像素图案（含 alpha=0 的有色像素）、旋转和翻转、普通牌 GUI 的 48×52 尺寸、悬挂牌旧 GUI 字节保留、重复运行字节确定性、明确目标/孤立侧车优先、大小写父路径、源侧车、坏 PNG、错误宽高、16-bit、扩展路径、非原版 atlas source，以及 86.1 仅警告和其它 entered 不变。未运行 Gradle，未添加长期测试。
