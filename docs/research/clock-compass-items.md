# 格式 43 → 44：时钟与指南针的物品模型

**结论：同一个输入值对应同一帧，但不能保留旧版完整的转动行为。** 三种物品都使用普通 `minecraft:model` 几何叶子，不使用 `minecraft:special`。仅替换原版单帧贴图的包无需改路径；自定义基础模型可能需要补到新的帧模型槽位。未知 override 继续保留并诊断，不自动重写组件或生成入口。

## 取证范围

采用[格式研究的相邻节点](modern-pack-formats.md#L57-L60)：本地只读源码 24w44a `7d9ca125f4d1ebdbdc412ed6fc43c5fc5b0672bd` 与 24w45a `cb3c8c664c147eada1465398f7170c09e7f1150b`。从官方版本 manifest 的记录核对版本 JSON 的 SHA-1，并核对其中 `downloads.client` 声明的字节数、SHA-1 与客户端 JAR；JAR 内 `version.json#pack_version.resource` 分别为 43、44。使用静态源码与 ZIP 条目验证，未启动客户端。

| 快照 | Mojang 版本 JSON | 官方客户端 SHA-1 / 大小 |
| --- | --- | --- |
| 24w44a | [版本 JSON](https://piston-meta.mojang.com/v1/packages/ed5e3fceeee75ec30e22537ae629c2a3a6f5abd9/24w44a.json) | [aee14fb6019ac3460574eaecdad3b66a96dfd6e6](https://piston-data.mojang.com/v1/objects/aee14fb6019ac3460574eaecdad3b66a96dfd6e6/client.jar) / 27,894,481 B |
| 24w45a | [版本 JSON](https://piston-meta.mojang.com/v1/packages/d5f78fe00f83d464ba69414b15b2447991b0c1f6/24w45a.json) | [4db71417912fdd7be8aed0792bc09dc70e505ff5](https://piston-data.mojang.com/v1/objects/4db71417912fdd7be8aed0792bc09dc70e505ff5/client.jar) / 28,300,237 B |

## 原版 JSON 与帧对应

下列路径相对于 `assets/minecraft/`。43 无这三个 `items/` 入口；`models/item/<物品>.json` 既是入口几何也是首尾两行覆盖用的 self ID，实写 `"parent":"item/generated"`、`"textures":{"layer0":"item/<物品>_<基础帧>"}` 和 `overrides`。44 不再附带这三个无后缀模型，改把基础帧几何放在下表位置，实写 `"parent":"minecraft:item/generated"`、`"textures":{"layer0":"minecraft:item/<物品>_<基础帧>"}`，不含 overrides。

| 物品 | 43 覆盖属性、行数 | 44 `items/<物品>.json` 中唯一顶层字段 `model` 的内容 | 44 基础帧几何 |
| --- | --- | --- | --- |
| `clock` | `time`，65 | `type:"minecraft:range_dispatch"`、`property:"minecraft:time"`、`scale:64.0`、`entries` | `models/item/clock_00.json` |
| `compass` | `angle`，33 | `type:"minecraft:condition"`、`property:"minecraft:has_component"`、`component:"minecraft:lodestone_tracker"`；`on_true` 与 `on_false` 各是 `type:"minecraft:range_dispatch"`、`property:"minecraft:compass"`、`scale:32.0`、`entries`，分别设 `target:"lodestone"` 和 `target:"spawn"`；两份 33 行的表相同 | `models/item/compass_16.json` |
| `recovery_compass` | `angle`，33 | `type:"minecraft:range_dispatch"`、`property:"minecraft:compass"`、`target:"recovery"`、`scale:32.0`、`entries` | `models/item/recovery_compass_16.json` |

精确行公式：`N=64`/`32`，基础帧 `p=0`/`16`，行号 `i=0…N`；`t₀=0`，`tᵢ=i−0.5`（`i>0`）；两端后缀均为两位数 `(i+p)%N`。43 行结构为 `{"predicate":{"time 或 angle":tᵢ/N},"model":"item/…"}`，第 0、N 行引用无后缀的根模型。44 行结构为 `{"threshold":tᵢ,"model":{"type":"minecraft:model","model":"minecraft:item/…"}}`，第 0、N 行直接引用基础帧。以下是两端实写数值（第 0 / 1 / N 行）：

| 物品 | 43 阈值 | 44 阈值 | 44 叶子后缀 |
| --- | --- | --- | --- |
| `clock` | `0.0 / 0.0078125 / 0.9921875` | `0.0 / 0.5 / 63.5` | `00 / 01 / 00` |
| `compass`、`recovery_compass` | `0.0 / 0.015625 / 0.984375` | `0.0 / 0.5 / 31.5` | `16 / 17 / 16` |

44 原版没有序列化 `fallback`、`wobble`、`natural_only`、`tints`，也没有 `ignore_default`。比对全部行与叶子，并把阈值及相邻 float32 输入交给两边的选择规则：时钟 198 点，指南针每分支 102 点，追溯指针 102 点；相同有限、非负的原版属性值（零为 `+0.0`）选择相同帧。倍率是 2 的幂，且各表阈值互异、严格递增；这不同于验证两代属性函数始终产生同值。三个新入口 SHA-1 分别为 `89a09e713ab791749467409972b626c9d52fd6cb`、`e0a870a3bcc775dcff2bdc52bcc62a709735d6f4`、`663e6fdb2598151b502c57af8a05a8026e682d22`。

除基础帧外，63 / 31 / 31 份 `models/item/<物品>_<帧>.json` 字节相同；`models/item/generated.json` 也字节相同（SHA-1 `0d70ccf45912e4a130e5990d6b4a1a6de963168f`）。64 / 32 / 32 张 `textures/item/<物品>_<帧>.png` 路径及字节均未变；两端这些原版 PNG 均无 `.png.mcmeta`。针的姿态来自选中的帧模型，不是新增旋转 `display` 或贴图动画。两端都先应用选中几何的 `display` 变换（含左右手），再平移 `(-0.5,-0.5,-0.5)`。指南针标签与时钟触发的特殊附魔光效不是 `minecraft:special` 入口：分别见 43 `7d9ca125f:client/net/minecraft/client/renderer/entity/ItemRenderer.java:106–152,191–205` 和 44 `cb3c8c664:client/net/minecraft/client/renderer/item/BlockModelWrapper.java:47–77`、`cb3c8c664:client/net/minecraft/client/renderer/item/ItemStackRenderState.java:139–151`。

## 属性、wobble 与 fallback

- **时钟**：43 `time` 是时钟专属属性。自然维度取 `getTimeOfDay(1.0f)`，其他维度取 `Math.random()`；双精度状态和阻尼 `0.9`，每世界 tick 至多推进一次。44 `minecraft:time` 的 `wobble` 与 `natural_only` 都默认 `true`；同样在自然维度取日时，其他维度改用该属性的 `RandomSource.nextFloat()`，状态和阻尼为 float / `0.9f`。`natural_only:false` 会让所有维度都取日时，不是原版迁移值。
- **指南针 / 追溯指针**：43 普通 `angle` 在存在 `lodestone_tracker` 时取其中的可选目标，否则在自然维度取世界出生点；组件存在但目标空时不退回出生点。追溯指针的独立 `angle` 只取玩家的上次死亡位置。44 的 `has_component` 实际调 `stack.has`，以同一条件取 `lodestone` 或 `spawn` 两个 `minecraft:compass` 属性，追溯指针取 `recovery`；`wobble` 默认 `true`。目标为空、异维或距离平方小于 `1.0E-5f` 时，有实体和客户端世界的两端都改为带渲染 seed 偏移的随机转动，而非走模型 fallback；偏移为 `(float)(seed * 1327217883) / 2.1474836E9f`，乘法按 Java int 溢出。有效目标时，仅本地玩家正常 tick 的路径平滑视觉偏航；其他实体或非正常 tick 路径不平滑。43 阻尼 `0.8`、双精度状态、`Math.random()`；44 新的 float 状态/`0.8f` 与独立 `RandomSource.nextFloat()`。对上述 44 属性设 `wobble:false` 会每次直接采样而不保留平滑延迟，不复现 43。
- **确定性的行为反例**：43 普通指南针的出生点和磁石共用一组有效/无效 target wobbler；44 条件两支各有一组，仅访问选中的分支。在状态归零、正常 tick 的本地玩家偏航 180°、两个有效目标均正东（方位角 0）的计算夹具中，tick 1 渲染无组件指南针时，两端属性均约 `0.019999981`、选 `compass_17`；tick 2 切换到此前未调用的磁石分支，43 延续到 `0.054399967`、选 `compass_18`，44 新分支约 `0.019999981`、仍选 `compass_17`。这是按源码舍入顺序与真实阈值重算的反例，不是游戏实测。时钟和追溯指针也不能声明旧随机序列或精度等价。
- **缺失上下文与 fallback 是两件事**：两端都优先用传入生物，缺省取 stack 表示实体；候选世界缺省时尝试实体的客户端世界。无实体或仍无客户端世界则属性返回 `0.0f`，命中阈值 0 的第一帧（`clock_00`、`compass_16`、`recovery_compass_16`），不是 44 的 fallback。43 从末行向前选第一个所有阈值通过的 override；未注册属性值是 `-Infinity`，无命中或结果为 null 才用基础几何，NaN 并不会被旧 `value < threshold` 拒绝，原版单谓词表因此会选最后一行（仍是基础帧）。44 排序后取不大于 `value * scale` 的最大阈值；NaN 或低于最小阈值才走 fallback，原版三入口未指定 fallback，实际烘焙为 missing item model，不是基础帧。任意自定义重复阈值、多谓词、异常值与 fallback 不可套用原版表的等价性。

属性与选择源码位置（在对应本地 Git ref 用 `git show <ref>:<path>` 复查）：

- 43 `7d9ca125f:client/net/minecraft/client/renderer/item/ItemProperties.java:121–169`、`7d9ca125f:client/net/minecraft/client/renderer/item/CompassItemPropertyFunction.java:23–122`、`7d9ca125f:client/net/minecraft/client/renderer/item/ClampedItemPropertyFunction.java:16–24`、`7d9ca125f:client/net/minecraft/client/renderer/block/model/BakedOverrides.java:42–74,92–99`、`7d9ca125f:client/net/minecraft/client/renderer/entity/ItemRenderer.java:221–225`。
- 44 `cb3c8c664:client/net/minecraft/client/data/models/ItemModelGenerators.java:105–136`、`cb3c8c664:client/net/minecraft/client/renderer/item/properties/numeric/Time.java:25–46`、`cb3c8c664:client/net/minecraft/client/renderer/item/properties/numeric/NeedleDirectionHelper.java:24–95`、`cb3c8c664:client/net/minecraft/client/renderer/item/properties/numeric/CompassAngleState.java:35–158`、`cb3c8c664:client/net/minecraft/client/renderer/item/properties/conditional/HasComponent.java:25–32`、`cb3c8c664:client/net/minecraft/client/renderer/item/ConditionalItemModel.java:43–57`、`cb3c8c664:client/net/minecraft/client/renderer/item/RangeSelectItemModel.java:56–78,116–143`。

## 可确认的包内操作与边界

1. **只替换已知帧贴图或同 ID 帧几何**：路径保持不变；不要移动贴图、重排帧、生成 sprite sheet、删包作者的侧车或把每帧变为物品入口。在目标原版入口与父模型未被其它层改变时，直接复用对应叶子。作者自定义侧车的视觉结果不在原版比对子集内。
2. **修改无后缀的基础几何且保留原版选帧表**：如果有效源视图的单谓词表与原版逐行一致、父链和被引用的帧可解析、没有冲突的有效 44 入口或作者定义的新基础帧，则可把旧根模型的几何副本（去掉 `overrides`，保留 `parent`、`textures`、`display` 等几何字段）放入上表唯一基础帧路径，沿用 44 原版入口。不要删除旧 ID 或覆盖已有新文件；此建议只保证把原先的几何交给相应的 44 帧，不保证旧时间序列。反之，对明确取消所有 overrides 的简单静态原版槽位，保留旧几何 ID、用已确认的 `items/<物品>.json` 指向一个 `minecraft:model` 叶子，可表达原先的单一几何选择；不能把它误作仍应转动的表。
3. **其余不动**：已有现代入口优先；多谓词、复杂 fallback、路径/命名空间或目标模型不明、出入视图的 filter/overlay、已有新帧冲突、复杂继承或渲染器与跨包合并无法解析时，保留并提示人工处理。43 与 44 的已有 `ITEM_MODEL` 组件 ID 分别由 `7d9ca125f:client/net/minecraft/client/renderer/ItemModelShaper.java:17–34` 与 `cb3c8c664:client/net/minecraft/client/renderer/item/ItemModelResolver.java:60–67` 读取；44 用 `cb3c8c664:client/net/minecraft/client/resources/model/ItemStackModelLoader.java:38` 的 `items/` 查入口，而非修改服务器组件。缺失整个 `ITEM_MODEL` 是另一条路径：43 返回 missing 基础模型，44 不添加渲染层，不经过上述 range fallback。此证据不支持自动生成其他物品定义，也不支持写入、清空或制造 `item_model` / `lodestone_tracker` / 自定义数据组件。
4. **只证明边界 43→44**：45 源码的 `HasComponent` 才新增默认 `false` 的 `ignore_default`（`6d0c6544f:client/net/minecraft/client/renderer/item/properties/conditional/HasComponent.java:25–40`）；46 的时钟改为必填 `source`，生成器用 `inOverworld` 分派 `daytime` / `random`（`09a2063f1:client/net/minecraft/client/renderer/item/properties/numeric/Time.java:26–76`、`09a2063f1:client/net/minecraft/client/data/models/ItemModelGenerators.java:127–136`）。不能把 44 时钟树原封不动地用于 46+。本项目的[现有格式 44 入口规则](../../src/main/java/top/syshub/viarp/convert/IntermediatePackUpgrades.java#L652-L705)对[非简单原版入口目录表](../../src/main/resources/viarp/migrations/intermediate/item-entrypoints-44.txt)标为 `-` 的这三项保留旧模型并诊断；本研究不修改这一路径。
