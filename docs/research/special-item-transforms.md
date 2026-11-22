# 82.0 → 83.0：物品特殊渲染器的内建变换

**结论：盾牌、三叉戟、潮涌核心可以保守恢复同一局部仿射变换；不保证跨版本的位级或画面逐像素一致。** 三种渲染器的 MapCodec.unit 没有要求新增字段，旧 JSON 仍能解码；但 83.0 移除了渲染器内的变换，未经迁移时姿态或位置实际改变，并非只有警告。

## 版本证据

[Mojang Snapshot 11 发布说明](https://www.minecraft.net/en-us/article/minecraft-26-1-snapshot-11) 将资源格式升为 83，说明局部变换与父节点变换组合，并在 `display` 变换之后应用。通过 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 核验版本 JSON 的 SHA-1，再根据各 JSON 核验完整客户端 JAR 的字节数及 SHA-1，最后读取 JAR 内的 `version.json`。历史源码采用与 [现代资源包格式研究](modern-pack-formats.md) 相同的本地 MC Git 参考库。

| Minecraft / 资源格式 | MC Git ref | 版本 JSON | 客户端 JAR SHA-1 / 字节数 |
| --- | --- | --- | --- |
| 26.1 Snapshot 10 / 82.0 | `db1c61c3951398e1e133675ef908688880e02b95` | [Mojang](https://piston-meta.mojang.com/v1/packages/f40345925167461c107d49f8c1edbac0315d1c74/26.1-snapshot-10.json) | [70285c18b62ba311a18842ff54e282b75b88612f](https://piston-data.mojang.com/v1/objects/70285c18b62ba311a18842ff54e282b75b88612f/client.jar) / 37,937,134 |
| 26.1 Snapshot 11 / 83.0 | `fad18303d05655bd335bee66b2c71e67ed27e6dc` | [Mojang](https://piston-meta.mojang.com/v1/packages/f41f75459903a59b7a3f73a12c5000a78b7c8bcc/26.1-snapshot-11.json) | [632526298395b8483b608e9488d4787bfed9e3f6](https://piston-data.mojang.com/v1/objects/632526298395b8483b608e9488d4787bfed9e3f6/client.jar) / 38,064,482 |
| 26.1 / 84.0 | `097c9cc6f` | [Mojang](https://piston-meta.mojang.com/v1/packages/224ef4d5ccb0a181b750765f2fdc591e0072bd3a/26.1.json) | [191771837687b766537a8c4607cb6fad79c533a1](https://piston-data.mojang.com/v1/objects/191771837687b766537a8c4607cb6fad79c533a1/client.jar) / 38,113,398 |
| 26.3 / 97.1 | `634debadb` | [Mojang](https://piston-meta.mojang.com/v1/packages/ead839cbccf0ddf013c3f935f2840080b5cb0d26/26.3.json) | [e877b6a07acd633fb3bb475002175cec036e7b87](https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar) / 41,483,720 |

## 精确差分

素材路径相对 `assets/minecraft/`。下面五个 `base` 的 `models/<base>.json` 在四个官方 JAR 中逐字节相同。83.0 变换的 `left_rotation`、`right_rotation` 均使用单位四元数 `[0,0,0,1]`；84.0、97.1 中这三个物品沿用同一姿态。

| 物品定义 | 82.0 的叶子及内建操作 | 83.0 原版变换存放位置 |
| --- | --- | --- |
| `items/shield.json` | `using_item` 条件两支的 `base` 为 `item/shield` 和 `item/shield_blocking`；`scale(1,-1,-1)` | 两支共同的 `condition`，`scale:[1,-1,-1]`，`translation:[0,0,0]` |
| `items/trident.json` | `display_context` 的 fallback 内 `using_item` 条件两支为 `item/trident_in_hand` 和 `item/trident_throwing`；`scale(1,-1,-1)` | 同样写在 fallback 下的 `condition`；`gui/ground/fixed/on_shelf` 的普通平面模型不动 |
| `items/conduit.json` | 唯一叶子 `item/conduit`；`translate(0.5,0.5,0.5)` | 直接写在同一叶子上，`scale:[1,1,1]`，`translation:[0.5,0.5,0.5]` |

- **82.0 原版操作同时见于提交和 extents**：`db1c61c39:client/net/minecraft/client/renderer/special/ShieldSpecialRenderer.java:51–77`、`db1c61c39:client/net/minecraft/client/renderer/special/TridentSpecialRenderer.java:29–46`、`db1c61c39:client/net/minecraft/client/renderer/special/ConduitSpecialRenderer.java:35–59`。1.21.4 的 `178543a33` 中同三个文件第 51、32、34 行已有同样常量，但没有逐一验证中间每个快照。
- **83.0 移出来而非更换方向**：常量见 `fad18303d:client/net/minecraft/client/renderer/special/ShieldSpecialRenderer.java:39`、`fad18303d:client/net/minecraft/client/renderer/special/TridentSpecialRenderer.java:26`、`fad18303d:client/net/minecraft/client/renderer/blockentity/ConduitRenderer.java:47`；移入定义的生成代码见 `fad18303d:client/net/minecraft/client/data/models/ItemModelGenerators.java:254–275`、`fad18303d:client/net/minecraft/client/data/models/BlockModelGenerators.java:2257`。潮涌核心仍烘焙 `CONDUIT_SHELL`。
- **乘法顺序**：旧端先应用 display，再由特殊渲染器应用局部操作（`db1c61c39:client/net/minecraft/client/renderer/item/ItemStackRenderState.java:217–225`）。新叶子使用可选的 `Transformation.EXTENDED_CODEC`，以 `parent × local` 组合（`fad18303d:client/net/minecraft/client/renderer/item/SpecialModelWrapper.java:74,82–99`、`fad18303d:client/com/mojang/math/Transformation.java:45–46,168–169`），Layer 再于 display 后乘组合矩阵（`fad18303d:client/net/minecraft/client/renderer/item/ItemStackRenderState.java:103–116,229–244`）。没有既有局部变换时，把原版共同父变换下推到两支叶子，得到同一 `display × 局部变换`，且不影响其他兄弟支路。旧 extents 先对顶点应用内建操作，新 extents 使用组合矩阵；数学等价不要求浮点运算顺序、带符号零或 GPU 输出位级一致。

## 自动修复边界

[SpecialItemTransforms.java](../../src/main/java/top/syshub/viarp/convert/SpecialItemTransforms.java) 在工作副本的 `assets/<namespace>/items/<id>.json` 中从 `model` 出发，仅走原版 condition、select、range_dispatch、composite 的子模型槽。仅匹配恰有 `type/base/model` 的 vanilla special 叶子、仅有 `type` 的渲染器对象以及上表对应 `base`，接受显式 `minecraft:` 或省略前缀。若叶子或任一祖先已有 `transformation` 键（即使 null 或无效），保留作者定义。非证据 base、模组渲染器、未知字段、不认识的包装、重复键或坏 JSON 不自动改写。

- **床：不仅是姿态变化。** 82.0 一片叶子渲染整个床，83.0 的 codec 要求 `part`，原定义缺键不能照旧解码，合法新叶子也只绘制一半。83.0 的 16 个原版床定义都改为两叶子的 composite，97.1 的床不再使用该特殊渲染器。见 `db1c61c39:client/net/minecraft/client/renderer/special/BedSpecialRenderer.java:40–52`、`fad18303d:client/net/minecraft/client/renderer/special/BedSpecialRenderer.java:44–55`。留待人工迁移。
- **潜影盒：显式旧字段已失去作用。** 82.0 的 `orientation` 参与提交和 extents，83.0 codec 不再读取它。17 个原版叶子添加了变换，但其旧定义没有显式方向，不能从原版默认值推出任意旧方向的补偿。见 `db1c61c39:client/net/minecraft/client/renderer/special/ShulkerBoxSpecialRenderer.java:48–59`、`fad18303d:client/net/minecraft/client/renderer/special/ShulkerBoxSpecialRenderer.java:44–56`。其他显示结构或渲染状态有变化的类型继续人工诊断，不套用上述常量。
- **箱子与饰纹陶罐是反例。** `items/chest.json`、`items/decorated_pot.json` 在四个官方样本中逐字节相同，不因缺少 transform 发姿态警告；这不代表其他自定义渲染行为均不变。

## 隔离验证

用 Zulu Java 8 `1.8.0_504` 编译运行，只引入转换器及其工作树、JSON 和路径辅助类。在 82.0 JAR 的全部 1,506 个原版物品定义加 11 个合成对照上，只有盾牌、三叉戟、潮涌核心和一个嵌套正例修改。将官方父节点变换等价下推到两支后，三个迁移结果分别与官方 83.0、84.0、97.1 的 JSON 树数值相等。对照覆盖普通兄弟叶子、tint、未知包装、模组 ID/base、未知字段、既有祖先或叶子变换、重复键和诊断；第二次执行 1,517 个文件全部逐字节不变。未启动游戏、未运行共享 Gradle、未验证像素输出。
