# ViaResourcePack 2.0：经典资源包迁移（1–15）

范围是 Java 客户端 **资源包格式**，不是游戏版本或数据包格式。入口从 1.6 的 `assets/<namespace>/…` + `pack.mcmeta` 起；早期 `terrain.png`、`pack.txt` 到格式 1 的桥接另行处理。只升版；同格式不是一条迁移边，不因启发式跳过显式声明。最低运行客户端 1.14.4 的目标是格式 4；1.20.1 的交接节点是 15；本机只读游戏参考仓库 tag `1.20.1`（`3015bd18`）的 `client/net/minecraft/SharedConstants.java:42` 声明 `RESOURCE_PACK_FORMAT = 15`，可用 [1.20.1 官方客户端制品](https://piston-data.mojang.com/v1/objects/0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838/client.jar) 独立复验。下文的 **规则** 指可独立实现的已证实子集，**人工** 指原信息不足或需要设计选择，**未知** 指尚无足够证据，不等于无需处理。

## 一、格式节点和边界

范围来自 [Minecraft Wiki 资源包格式表](https://minecraft.wiki/w/Pack_format#Resource_pack_format_history)（定位索引）；有官方发布说明或制品的边界另外附证。正式版和快照仅用于选择证据或夹具，不作为转换器分派键。

| 格式 | 快照至该段末尾；代表正式版 | 入边的最小实现与限制 |
| --- | --- | --- |
| 1 | 13w24a–1.8.9；1.6.1–1.8.9 | **规则：**识别根元数据与命名空间，不把无元数据或缺目录直接猜为格式 1。已校验官方 [1.6.1 客户端 JAR](https://launcher.mojang.com/v1/objects/17e2c28fb54666df5640b2c822ea8042250ef592/client.jar)：根 `pack.mcmeta` 的 `pack.pack_format` 为 1，内有 `language.en_US`；资源已有 `textures/blocks`、`textures/items` 与 `lang/en_US.lang`。1.6–1.7 与 1.8 同号，但后者才有 1.8 式模型/方块状态，不给输入凭空补模型。|
| 2 | 15w31a–1.10.2；1.9–1.10.2 | **规则：**生成类父模型搬迁、旧手持变换字段、经证实的原版动态道具覆盖以及钟表／指南针的标准帧条（见下）；**人工：**自定义左右手视角、动态模型和 multipart 语义。|
| 3 | 16w32a–17w47b；1.11–1.12.2 | **规则：**小写资源路径与对应引用，及 locale 键；冲突拒绝。索引可追到 [16w32a 变更](https://minecraft.wiki/w/Java_Edition_16w32a#General_2)，制品证据见下。|
| 4 | 17w48a–19w46b；1.13–1.14.4 | **规则：**按资源类型/命名空间的 flatten 表、纹理目录单数化、逐字段 JSON 与语言转换；**人工：**一对多或不再等价的模型/方块状态及旧配方。注意 17w47a/b 已破坏内容但没 bump；1.14 的若干资源又在同号内改名。|
| 5 | 1.15-pre1–1.16.2-pre3；1.15–1.16.1 | [Mojang 1.15](https://www.minecraft.net/en-us/article/buzzy-bees-out-now-in-java) 确认 5 及原版尽量适配旧资源包。**规则（需像素夹具锁定后启用）：**部分木箱/陷阱箱/末影箱拆图及 UV、旗帜/盾牌遮罩、潮涌核心眼、铁傀儡路径；**人工/未知：**任意 HD 非标准布局、末影龙不同翼面信息、无像素证据的其他图。|
| 6 | 1.16.2-rc1–1.16.5；1.16.2–1.16.5 | [Mojang 1.16.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-16-2) 明示 6 和 MC-197275。**规则：**仅当确认真值型旧墙状态时迁移；方向属性由 `false/true` 到 `none/low/tall`，不触碰 `up`。**人工：**自定义高墙几何/匹配组合；早已使用 `low/tall` 的包不得再次改。|
| 7 | 20w45a–21w38a；1.17–1.17.1 | [Mojang 20w45a](https://www.minecraft.net/en-us/article/minecraft-snapshot-20w45a) 说明客户端 `version.json.pack_version` 分离，JAR 实测 `{"resource":7,"data":6}`；修复溺尸手臂镜像。**规则：**`grass_path`→`dirt_path` 的已核准 vanilla 路径和引用（[1.16.5 旧模型](https://assets.mcasset.cloud/1.16.5/assets/minecraft/models/block/grass_path.json) 与 [1.17 新模型](https://assets.mcasset.cloud/1.17/assets/minecraft/models/block/dirt_path.json) 实证 `top`、`side` 贴图改名，`bottom`、`particle` 仍为 `block/dirt`）。**人工/未知：**溺尸 UV 及游戏模式 GUI 插槽对齐，待像素样本。|
| 8 | 21w39a–1.18.2；1.18–1.18.2 | [Mojang 21w39a](https://www.minecraft.net/en-us/article/minecraft-snapshot-21w39a) 明示 8，`textures/gui/container/inventory.png` 增加窄布局效果列表 sprite。**人工：**没有作者像素的新增区域不能自动补画；不覆盖该图的包无需改图。|
| 9 | 22w11a–1.19.2；1.19–1.19.2 | [Mojang 22w11a](https://www.minecraft.net/en-us/article/minecraft-snapshot-22w11a) 明示资源包 9、数据包 10。**规则：**原样保留可选 `filter.block`（正则定义、缺省通配；仅屏蔽低优先级包），无过滤器不用新增。如已知路径改名使正则匹配集改变且不能等价翻译，发诊断，不做文本替换。|
| 11 | 22w42a–22w44a；无正式版 | [Mojang 22w42a](https://www.minecraft.net/en-us/article/minecraft-snapshot-22w42a) 明示 11 并移除格式 3、4 懒适配器。**规则：**对内容已正确的 9 包，不另造路径改名；此前边的变换应写入文件，不依赖原版包装器。无证据不能称此段资产绝对不变。|
| 12 | 22w45a–23w07a；1.19.3 | [Mojang 22w45a](https://www.minecraft.net/en-us/article/minecraft-snapshot-22w45a) bump 至 12；[22w46a](https://www.minecraft.net/en-us/article/minecraft-snapshot-22w46a) 同号更改图集加载次序。**规则：**检查可解析且能确定用途的自定义模型纹理实际是否在 `blocks` 图集内，补缺失的 `single` 源（见下）；**人工：**恼鬼旧皮肤/几何与有自定义绘制的 `tabs.png` 需要单独处理。|
| 13 | 1.19.4-pre1–23w13a；1.19.4 | [Mojang 1.19.4](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-19-4) 确认旧 [textures/misc/enchanted_item_glint.png](https://assets.mcasset.cloud/1.19.3/assets/minecraft/textures/misc/enchanted_item_glint.png) 分为 `textures/misc/enchanted_glint_{entity,item}.png`，旧锻造 GUI 从 `smithing.png` 移到 `legacy_smithing.png`；实作可把前者旧资源复制到两个未显式提供的目标，不保证视觉相同；后者只迁移旧界面，不把它覆盖到有新槽位的 GUI。增加可选 `paletted_permutations` 图集源，不对旧包自动生成盔甲纹饰。|
| 14 | 23w14a–23w16a；无正式版 | [Mojang 23w14a](https://www.minecraft.net/en-us/article/minecraft-snapshot-23w14a) 明示 14，重排主菜单 logo 与 Realms 邀请图；移除 Programmer Art 对旧 logo 的覆盖。**规则候选：**允许对已验证旧坐标/倍率的布局重排；**人工：**不知 UV 或原图没有对应状态时给出诊断，不能只改文件名或强行拉伸。|
| 15 | 23w17a–1.20.1；1.20–1.20.1 | [Mojang 23w17a](https://www.minecraft.net/en-us/article/minecraft-snapshot-23w17a) 明示移除 `legacy_unicode`、用 Unifont 15.0.01、引入 `unihex` 与 `reference`、credits 增 `disciplines`。**规则：**平铺旧 `titles` 可明确采用一层空名 discipline 包裹（需验证 GUI 对空标题的渲染）；**人工：**旧 glyph 页/宽度到 HEX 需已知字形布局/度量，否则不假装翻译；纯依赖 vanilla 默认字体的包可免改。|

迁移序列为 `1→2→3→4→5→6→7→8→9→11→12→13→14→15`。**没有资源包格式 10 节点**；不要按整数盲递增，更不要把 `version.json.pack_version.data` 或资产索引编号当成资源包格式。早于 schema 分离的 JAR 有的没有 `version.json`；单次查不到根元数据不是缺失节点。元数据宣告范围内也不是完全相同的资源布局：

- [Mojang 17w47a/b](https://www.minecraft.net/en-us/article/minecraft-snapshot-17w47a) 宣布 flatten 破坏旧包；[17w48a](https://www.minecraft.net/en-us/article/minecraft-snapshot-17w48a) 明说上一快照漏改格式号。这是格式 3 内已出现新式资源的已知反例。若来源为已知快照且包内已有混合新旧结构，只对明确旧资产应用有源签名的规则；不能据内容重新标定上传包的格式。
- 对比 [1.15 JAR](https://piston-data.mojang.com/v1/objects/7b07fd09d1e3aae1bc7a1304fedc73bfe5d81800/client.jar) 与 [1.16.1 JAR](https://piston-data.mojang.com/v1/objects/c9abbe8ee4fa490751ca70635340b7cf00db83ff/client.jar)：两份 `version.json.pack_version` 都为 5，但 `blockstates/cobblestone_wall.json` 的 `multipart.when.north` 已由 `"true"` 变为 `"low"`；[1.16.2-rc1 JAR](https://piston-data.mojang.com/v1/objects/dd4982bb447c1d2c7c03419b90dbe8f017c47311/client.jar) 才是 6。把布尔连接到两种高度的回退视作可能失真；没有高墙模型不能靠简单替换保留外观。严格格式路由不能承诺 5→5，能识别时给出同号不兼容诊断。跨到 6 时按旧状态结构检查，不按版本号猜测。
- 格式 4 还包含 [Mojang 18w43a](https://www.minecraft.net/en-us/article/minecraft-snapshot-18w43a) 的统一染料命名：1.13 `rose_red`，1.14.4 [为 `red_dye.png`](https://assets.mcasset.cloud/1.14.4/assets/minecraft/textures/item/red_dye.png)。对**升入** 4 可把 1.14.4 用作唯一受支持的格式 4 目标基准；也可在穿过 4 时接受两个有证据的旧别名。不能保证把一个已经宣告 4 的 1.13 包静默改成 1.14.4 样式。

## 二、可优先实现的三条迁移边

### 1→2：模型字段、动态道具覆盖和 multipart 分开做

直接比较 [1.8.9 客户端 JAR](https://launcher.mojang.com/v1/objects/3870888a6c3d349d3771a3e9d16c9bf5e076b908/client.jar) 与 [1.9 客户端 JAR](https://piston-data.mojang.com/v1/objects/2f67dfe8953299440d1902f9124f0f2c3a2c940f/client.jar) 中的 `assets/minecraft/models/item/bow.json` 与 `assets/minecraft/blockstates/fence.json`：

- 原版弓的 `parent` 从 `builtin/generated` 到 `item/generated`，`textures.layer0` 仍是 `items/bow_standby`，不应在此边提前单数化；拿不到目标的最高层父模型时应告警，不能把所有 `builtin/*` 混同。`display.thirdperson`、`display.firstperson` 变为分别的 `*_righthand`、`*_lefthand`，且原版弓变换数值也变了。对任意自定义变换只在有明确策略时迁移（保留已提供的新键；没有左手设计时用经目标解析器验证的回退并标告警）；不要宣称直接复制旧数值等同于 1.9 渲染。
- 1.9 弓的根模型增加 3 个 `overrides`，原版顺序：`pulling=1` → `item/bow_pulling_0`；`pulling=1,pull=0.65` → `item/bow_pulling_1`；`pulling=1,pull=0.9` → `item/bow_pulling_2`。对已替换 vanilla 弓根模型而未提供 `overrides` 的包，可以按查证的模型路径、谓词与顺序补齐；不能由任意拉弓 PNG 名推断动态意图，也不应覆盖现成的条件列表。原版 [1.9 钓竿](https://assets.mcasset.cloud/1.9/assets/minecraft/models/item/fishing_rod.json) 从 1.8.9 的 `builtin/generated` 转到 `item/handheld_rod`，唯一覆盖为 `cast=1` → `item/fishing_rod_cast`。[钻石剑](https://assets.mcasset.cloud/1.9/assets/minecraft/models/item/diamond_sword.json) 从 `builtin/generated` 改到 `item/handheld`。[钟表](https://assets.mcasset.cloud/1.9/assets/minecraft/models/item/clock.json) 与 [指南针](https://assets.mcasset.cloud/1.9/assets/minecraft/models/item/compass.json) 由 `builtin/clock`、`builtin/compass` 改用 `item/generated`，但分别有 65、33 个动态覆盖，单换父模型不会保留动态效果。`builtin/entity` 仍由例如 [1.9 skull](https://assets.mcasset.cloud/1.9/assets/minecraft/models/item/skull.json) 使用。
- 仅覆盖贴图的包可把原版形状的 `clock.png` / `compass.png` 纵向帧条按顺序拆成 64 / 32 张编号的正方形 PNG。1.8.9 帧条与 1.9 分帧的 96 张 PNG 逐 RGBA 像素相同；1.14.4 原版模型仍引用全部输出。新目标或侧车、自定义根／帧模型以及非默认帧侧车不自动混合；保留原图。
- 1.8.9 栅栏用 `variants` 枚举连接位；1.9 改为 `multipart`：桩无条件 `apply`、边按连接 `when` 追加且带旋转和 `uvlock`。**multipart 是新增表达能力，不表示所有 variants 必须重写。** 保留仍可解析的 variants；只有明确的原版型规则做结构变换，带权变体、重复边与非独立条件保持原意或报告人工。

### 2→3：小写路径是全局约束，替换范围不是所有字符串


a. 读取 ZIP/目录原始相对路径，以 `Locale.ROOT` 做 ASCII 小写计划，先验证 `namespace` 与逻辑路径（目标文法分别为 `[a-z0-9_.-]+` / `[a-z0-9/._-]+`；原版较早版本与现代严格校验不要混称）。防止路径穿越、驱动器路径、根外 symlink、重复条目及大小写折叠。优先检测 `A.png` / `a.png` 这样的不同内容冲突，不能取第一条或最后一条，不能依赖 Windows 枚举结果。只在明确同源且内容相同的情况下合并重复。目录命名也属于路径。

b. 作为同一批计划更新资源 ID：例如模型父引用、纹理引用、方块状态 `model`、`sounds.json` 的事件 ID/音频 ID，以及 `pack.mcmeta.language` 的 locale 键；不要把 JSON 语言文本、树内任意 key、大小写有意义的 mod 字段、regex、显示名称小写。`en_US.lang`→`en_us.lang` 可由 1.9 与 [1.12.2 JAR](https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar) 验证。贴图元数据 `.png.mcmeta` 与 PNG 一起迁移，保留动画/采样字段。

### 3→4：把 flatten 当成类型化重写，不当成字符串替换

用 1.12.2 与 [1.13 JAR](https://piston-data.mojang.com/v1/objects/c0b970952cdd279912da384cdbfc0c26e6c6090b/client.jar) 的实物对，不把 [Wiki 1.13 资源文件变更表](https://minecraft.wiki/w/Java_Edition_1.13/Resource_pack_changes) 直接作为未经审核的数据资产。下面路径均相对 `assets/minecraft/`，显式给出资源类型；搬迁表不表示所有同名字符串都可以替换。

| 类型 | 旧路径 | 目标路径（1.13 基准） |
| --- | --- | --- |
| 纹理 | `textures/blocks/grass_top.png` | `textures/block/grass_block_top.png` |
| 纹理 | `textures/blocks/planks_oak.png` | `textures/block/oak_planks.png` |
| 纹理 | `textures/blocks/wool_colored_red.png` | `textures/block/red_wool.png` |
| 动画纹理与侧车 | `textures/blocks/water_still.png[.mcmeta]` | `textures/block/water_still.png[.mcmeta]`；官方样本两边 `animation.frametime=2` |
| 纹理 | `textures/items/record_13.png` | `textures/item/music_disc_13.png` |
| 纹理 | `textures/items/dye_powder_red.png` | `textures/item/rose_red.png`（格式 4 的 1.14.4 基准用 `red_dye.png`）|
| 模型及状态 | `models/block/grass.json`、`blockstates/grass.json` | `models/block/grass_block.json`、`blockstates/grass_block.json`（短草也曾叫 `grass`；不能笼统改所有 `grass`）|
| 语言文件 | `lang/en_us.lang` | `lang/en_us.json`；语法和 vanilla 翻译键均变 |

1. 将物理路径、模型 ID、逻辑纹理 ID、音效事件 ID、音频 ID、翻译键分成不同映射域；逐条记录旧→新、一对多分支、证据、引入/终止格式与基准。只对 `minecraft`（包括默认命名空间）套用 vanilla 名称表，自定义命名空间仅对包内确证同一资源的路径/引用做一致搬迁，不按裸文件名撞表。例：已知纹理值 `minecraft:blocks/grass_top` → `minecraft:block/grass_block_top`；
   - `models`：读 `parent`、`textures` 中的资源值、`overrides[*].model`、必要时 `elements[*].faces.*.texture`；`#top` 等变量名是引用模型内部键，应保留，沿父链解析后再检查纹理是否存在。原版 1.13 草方块模型同时有 `top: block/grass_block_top` 与面槽 `texture: #top`。动模型名不等于动其贴图名。
   - `blockstates`：遍历 `variants` 值与 `multipart[*].apply` 的对象/数组内的 `model`，并看目标加载器的 ID 约定（1.9 栅栏写 `oak_fence_post`，并非贴图路径）。状态键、`when` 字段与值是独立 grammar，仅按特定方块的状态变换表改；保留权重、旋转、`uvlock` 与数组顺序。不能用数值 ID/data value 全局重建作者的多变种语义。
   - `sounds.json`：事件对象的键按事件表，`sounds[*]` 字符串或对象 `name` 按音频表；对象 `type:event` 的 `name` 则按事件表。`subtitle` 是翻译键，只走独立键表。没有精确映射的自定义音效不改；外置 OGG 可按 launcher `assetIndex.objects` 校验，并非都在客户端 JAR 中。
2. `.lang` 按旧加载器线路解析，不用 Java `Properties.load` 代替。最小子集：逐行、跳过注释/空行、只取第一个 `=` 作分隔、UTF-8、保留值里的 `=`/空白/引号并 JSON 序列化；数字格式占位符归一行为随解析器验证，先保留字符串，不发明对 `%s`/`%1$s` 的改写。逻辑翻译键用精确表，例如 [1.12.2 的 `tile.grass.name`](https://assets.mcasset.cloud/1.12.2/assets/minecraft/lang/en_us.lang) → [1.13 的 `block.minecraft.grass_block`](https://assets.mcasset.cloud/1.13/assets/minecraft/lang/en_us.json)；未知定制键保留；遇重复、不同旧键合并同目标、畸形 UTF-8 或旧解析器宽松语法未证实时报告诊断。语言元数据中的显示名不是键表输入。可用 [1.20.1 Language 映射](https://mappings.dev/1.20.1/net/minecraft/locale/Language.html) 对照较新 `loadFromJson`，并用真实制品比对两代 loader 的有效文本，不能只承诺 JSON 可 parse。
3. **人工/未知：**旧 `assets` 下的配方等转入数据包是另一产品面（见 [Mojang 17w48a 数据包配方](https://www.minecraft.net/en-us/article/minecraft-snapshot-17w48a)），不要自动把它们包装成世界数据包。需要将多个旧 blockstate 拆到新方块、移除模组粒子/着色器/OptiFine CTM/CIT 特有语法、或有无法唯一恢复的内容，一律保持原文件并报告；不能根据 DataFixer 的世界/NBT 映射推导任意资源语义。

## 三、后续边的落地判据

- **4→5 兼容层不是完整迁移器。** [1.19.2 LegacyPackResourcesAdapter（Yarn Format3ResourcePack）](https://mappings.dev/1.19.2/net/minecraft/client/resources/LegacyPackResourcesAdapter.html) 是查询 ID/元数据的适配层；[PackResourcesAdapterV4（Yarn Format4ResourcePack）](https://mappings.dev/1.19.2/net/minecraft/client/resources/PackResourcesAdapterV4.html) 有箱子映射、左/右/单箱裁图、盾与旗遮罩、潮涌眼裁剪和铁傀儡纹理相关方法/字段。映射页证明范围而非精确像素算法；铁傀儡基础图有例外：1.14.4 [textures/entity/iron_golem.png](https://assets.mcasset.cloud/1.14.4/assets/minecraft/textures/entity/iron_golem.png) → 1.15 [textures/entity/iron_golem/iron_golem.png](https://assets.mcasset.cloud/1.15/assets/minecraft/textures/entity/iron_golem/iron_golem.png)，原版两份 128×128 PNG 的 SHA-256 同为 `dd01e35ca9ff8968ee817c9c7f32a787486e08616f774f6023fd17e1390509ba`，是可直接落地的纯改路径。其他像素规则本次没有提取 UV/混合公式。分别用官方成对 PNG 和大小倍率的非对称哨兵像素验证原位、翻转、裁剪、alpha、纹理侧车策略。1.19.3 发布说明称移除了 3 和 4 的兼容层；其“pre-flattening”措辞不意味着格式 4 是 flatten 前。
- **6→7 土径分开映射模型、纹理和翻译。** 在 `assets/minecraft/` 下，`blockstates/grass_path.json`、`models/block/grass_path.json`、`models/item/grass_path.json`、`textures/block/grass_path_top.png`、`textures/block/grass_path_side.png` 的 `grass_path` 段都变为 `dirt_path`；已检查 [20w45a 官方客户端 JAR](https://piston-data.mojang.com/v1/objects/cf2cd62dfc63fc1a79061a3964d82bf26e0fd208/client.jar) 含五条新路径、不含旧路径。翻译键 [1.16.5](https://assets.mcasset.cloud/1.16.5/assets/minecraft/lang/en_us.json) `block.minecraft.grass_path` → [1.17](https://assets.mcasset.cloud/1.17/assets/minecraft/lang/en_us.json) `block.minecraft.dirt_path`，只改键，保留包作者值。
- **6→7 普通鱿鱼保留新旧路径。** [1.16.5 客户端](https://piston-data.mojang.com/v1/objects/37fd3c903861eeff3bc24b71eed48f828b5269c8/client.jar) 渲染器读取 `textures/entity/squid.png`；[1.17 客户端](https://piston-data.mojang.com/v1/objects/1cf89c77ed5e72401b869f66410934804f3d6f52/client.jar) 读取 `textures/entity/squid/squid.png`。两张 PNG SHA-256 均为 `70ab1cf798e1ca29f8b59db7a7b4751b87229c0c9b298bad35739ce8fbf465c4`。早期格式 7 仍读旧路径，所以只补副本及其侧车，不覆盖显式目标，也不派生发光鱿鱼纹理。
- **11→12 补图集不能照模型名猜。** [1.19.3 原版 `atlases/blocks.json`](https://assets.mcasset.cloud/1.19.3/assets/minecraft/atlases/blocks.json) 的默认源包含 `block/`、`item/`、`entity/conduit/` 目录和铃体/附魔台书的 `single`；不能把所有非 block/item 一概标缺失。对于已解析的自定义模型引用 `example:custom/stone`、且包含 `assets/example/textures/custom/stone.png` 并确认不会误触用户 filter 时，将 `{"type":"single","resource":"example:custom/stone"}` 增入该贴图应使用的图集，例如 `assets/minecraft/atlases/blocks.json`。不把所有 PNG 加入所有图集；图集文件按包顺序合并，`sources` 依次执行，缺失贴图也可能由低优先级包提供；已有用户排除规则和跨包引用无法静态等价时发诊断。22w45a 的部分格式 12 包可无此图集配置；这不应成为版本支路。
- **13→14 需要坐标表，不按总尺寸缩放。** Mojang 公告确证布局变化；资产镜像对比 logo [23w13a](https://assets.mcasset.cloud/23w13a/assets/minecraft/textures/gui/title/minecraft.png) 256×256 → [23w14a](https://assets.mcasset.cloud/23w14a/assets/minecraft/textures/gui/title/minecraft.png) 1024×256；邀请图相对 `assets/realms/` 为 `textures/gui/realms/invite_icon.png`，[旧 31×25](https://assets.mcasset.cloud/23w13a/assets/realms/textures/gui/realms/invite_icon.png) → [新 18×30](https://assets.mcasset.cloud/23w14a/assets/realms/textures/gui/realms/invite_icon.png)。实作要另核对渲染端/采样区域；尺寸本身只证实布局变动。
- **14→15 可按内容谓词选择。** 1.20.1 原版 [uniform 字体](https://assets.mcasset.cloud/1.20.1/assets/minecraft/font/uniform.json) 用 `reference` 引入 `minecraft:include/space` 与 `minecraft:include/unifont`；`unihex` 的 `hex_file` 指向含 HEX 的 ZIP，和 legacy PNG/glyph widths 不是换一个 type 就能兼容。原版 [credits.json](https://assets.mcasset.cloud/1.20.1/assets/minecraft/texts/credits.json) 的结构为 `section→disciplines[{discipline,titles}]`；已经有 `disciplines` 的不重复包裹，旧 credits 自定义展示不能只校验字段可读，还需比较页面呈现。

## 四、建议的最小实现与验收样本

1. **读/计划/验证/写分离。** 保留源路径和原字节，标记每条规则处理过的文件与源签名；构建全包的 rename graph（同目录文件、sidecar、关联引用、显式目标优先级）后再写，避免 `A→B→C` 在同边被反复套用；ZIP 与目录使用一致且区分大小写的入口索引。按已登记格式邻边运行，不能借递增元数据掩盖缺失边。对已声明同格式或较新源包不转换；结构可疑、冲突、未知或人工依赖应报告机器可读的 `from/to、路径、规则、原因`。仅当必要迁移及目标格式校验通过才原子发布并改元数据；没有可判明输入是否关键的影响应标「不确定」，默认不要报告完全兼容。勿静默丢弃不可迁移文件，更不要修改输入包。保持 core Java 8/JDK-only；对客户端仍仅交付文件路径，不用原版 `PackResources` 包装器。
2. **官方可复取的成对夹具。** 从 [Mojang Launcher manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) → 每版本元数据 → `downloads.client` 与 `assetIndex` 取样，记录版本、SHA-1、条目路径。1.6.1、1.8.9、1.9 的 JAR 已全量校验 SHA-1；其余用于本次比对的 JAR 是从内容寻址 URL 定向读取 ZIP 目录/条目，不宣称全体哈希校验。不要提交整包或未获授权的 Mojang PNG；本仓测试可提交自行绘的等比例哨兵 PNG、根据结构手写最小 JSON，另做可联网的官方只读集成样本（全量制品下载时校验散列）。推荐：

   | 样本 | 最小断言 |
   | --- | --- |
   | 1.8.9 → 1.9 弓、栅栏 | 框定 `parent` 旧新关系、纹理还用复数、四手视角、override 顺序/保留现有值；variants 可保留，multipart 有权重和旋转测试。 |
   | 1.9 → 1.12.2 语言、另造大小写冲突 ZIP | locale 文件与 metadata 键同步、内容不变；ZIP 大小写冲突与目标文件遮盖可诊断，同一夹具在 Linux/Windows 一致。 |
   | 1.12.2 → 1.13 草模型/状态、羊毛、木板、唱片、动态水、`en_us.lang` | 验证最终引用图、动画侧车，`#top` 不改；另测同名自定义命名空间、带 `=` 的文本、旧/新同存以及翻译目标碰撞。 |
   | 1.14.4 → 1.15 箱子、旗帜；1.15 → 1.16.1 → 1.16.2-rc1 墙 | 对 PNG 只比较可证实区域；边界分别验证 5→5 不动、有 5→6 路由但不重复处理早已使用 `low/tall` 的墙。 |
   | 17w47a/17w48a 与 22w42a/22w45a/22w46a | 只以格式字段分类，允许已迁移路径，同号结构差异有诊断；补自定义 atlas `single` 只触及目标图集，不覆盖既有筛选。 |
   | 23w13a → 23w14a → 23w17a logo/邀请、字体、credits | 保留 14 精确节点；不把旧 glyph provider 无警告吞掉；已符合 `disciplines` 的 credits 不重复升级。 |

3. **真实第三方夹具候选，不宣称已跑。** [Faithful 下载归档](https://faithfulpack.net/downloads) 可定位维护方公开发布的 1.12.2 Faithful 32x（页面中有 2025-06 正式包和实验包）；选择固定一份，核对 ZIP 内实际根元数据、许可、SHA-256 后用于目录/ZIP 对照，不随仓分发原包。它偏纹理层，不代替包含自定义模型链、声音/字幕、非默认 atlas 和多 namespace 的小型自制集成包。至少在运行下限 1.14.4 检查 1→4 的客户端资源重载、并在 1.19.3/1.20.1 验证没有依赖旧兼容层、模型/字体加载无新警告；本次调研未运行游戏或测试套件。
