# 旧纹理包 → 格式 1：证据与自实现规则

本报告只讨论 ViaResourcePack 2.0 的 **输入兼容**，不新增旧游戏运行适配器。只升版：将没有数字格式的旧纹理包正规化成格式 1，再交给按资源包格式分层的迁移图。以下区分观察与产品建议；未运行任何被检查的 JAR，未复制上游代码、全量映射表或素材。

## 1. 节点与识别

| 内部节点 | 正向证据 | 建议 |
| --- | --- | --- |
| `LegacyAtlas` | 根 `terrain.png` 或 `gui/items.png` 可解码且每轴可分 16 格；`pack.txt`、`pack.png`、`misc/grasscolor.png` 仅辅助 | 识别图集容器与格子语义来源是两回事，另维护来源档案，不用游戏版本作为迁移节点。 |
| `LegacyTiles` | 无有效现代元数据；根 `textures/blocks/` 或 `textures/items/` 有已知、精确大小写的历史路径；同 stem PNG/TXT 是更强的佐证 | 独立图像不再裁切，先转动画，再改路径与根元数据。 |
| `PackFormat(1)` | 根 `pack.mcmeta` 声明 `pack.pack_format = 1`，`assets/minecraft/` 是佐证，不要求局部包必须有原版命名空间 | 走正常的格式迁移，不因根旧文件再拆图。 |

**官方实测。** 从 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 取各版本 JSON 与 `downloads.client`，分别验证清单的 JSON SHA-1、版本 JSON 的客户端 SHA-1；内存读取 ZIP、PNG 尺寸和元数据。`a1.2.6`、`b1.7.3`、`b1.8.1`、`1.0`、`1.2.5`、`1.4.7` 均有两张 256×256 atlas、根 `pack.txt`；`1.5`、`1.5.2`、`13w23b` 无两图集，有独立 PNG、动画 TXT、根 `pack.txt`；`13w24a`、`13w24b`、`1.6.1` 均有命名空间化的 PNG/PNG.mcmeta 与根 `pack.mcmeta`（格式 1）。清单将取样的 `1.5` 项标为 snapshot，正式 1.5.x 由 `1.5.2` 佐证；这不是逐个快照核验。可复查客户端 SHA-1：

| 版本 | 客户端 SHA-1 |
| --- | --- |
| a1.2.6 | `a68c817afd6c05c253ba5462287c2c19bbb57935` |
| b1.7.3 | `43db9b498cb67058d2e12d394e6507722e71bb45` |
| b1.8.1 | `6b562463ccc2c7ff12ff350a2b04a67b3adcd37b` |
| 1.0 | `b679fea27f2284836202e9365e13a82552092e5d` |
| 1.2.5 | `4a2fac7504182a97dcbcd7560c6392d7c8139928` |
| 1.4.7 | `53ed4b9d5c358ecfff2d8b846b4427b888287028` |
| 1.5.2 | `465378c9dc2f779ae1d6e8046ebc46fb53a57968` |
| 13w23b | `f374a05f667ed8509cea98ad8c905feb1bf5d9cf` |
| 13w24a | `440abbedd473bdf38821906db6ea825d042848ae` |
| 1.6.1 | `17e2c28fb54666df5640b2c822ea8042250ef592` |

**策略。** 先解析根现代元数据，区分声明较新、无法解析、缺失；较新不降级，损坏或未知现代结构不冒充 legacy。两节点用内部标识，不伪造数字 0 或负格式。没有根元数据时才依包内结构检测；仅 `pack.txt`/`pack.png`、文件名或低分辨率均不够。一种图集缺失不是错误；无图集又只有少量共享旧路径的局部包，在候选格式迁移有歧义时要求来源说明。ZIP/目录共用逻辑；允许折叠时只折叠一个无歧义公共包根。散图和图集可以同时存在（Unstitcher 会留下残图）：已确认散图优先于生成裁片，冲突诊断，不二次覆盖。无法确认年代时只迁移各候选档案已共同确认的语义；无可靠规则的资产保留原文、等待人工确认。完全未知包沿用占位/不猜测策略。

## 2. 图集、索引与未分配像素

**静态证据。** 用户提供的 `unstitcher.jar` 13,352 字节，SHA-256 `2382b48482d5370390b82b843f1dce02cdc28a0a02202718caa62e78d8640e7d`。REA 清单 `ev_1002ba686695dc469abb233808fefe3d4fc57ee02ff95623b5977271fb1e5210`，成员视图 `ev_4ea1fdb287deec2c57af9cc92d4ed7ed6b387104c6ea16ac8d833d38c48c6400`；字节码由 JDK `javap -p -c -s -classpath <unstitcher.jar> net.minecraft.util.Unstitcher net.minecraft.util.gui.UnstitcherGui` 静态取得。归档里的 `blocks.txt`（**219** 命名格、37 未分配）和 `items.txt`（**196** 命名格、60 未分配）均无重复坐标和输出名，坐标每轴 0–15；不是所有早期游戏版本的语义表。

- `Unstitcher.loadPositions` 偏移 22–86：每行 `x,y - name`，0 起算，行优先 `i=x+16*y`。`load` 偏移 8–40、`unstitch(OutputStream)` 偏移 26–51：固定每轴 **16 个格**，单格 `tw=W/16`、`th=H/16`（旧工具整数除法），原点 `((i % 16)*tw, (i / 16)*th)`。256² 得 16²，512² 得 32²；不缩放或插值。
- `UnstitcherGui.unstitch(File, File)` 偏移 67–169/184–248：不拆的条目保留旧路径。流重载偏移 54–166：无名字跳过，两个图集分别输出 `textures/blocks/<name>.png`、`textures/items/<name>.png`。少量证据锚点：terrain (1,0)→`stone`，(2,0)→`dirt`；items (10,0)→`apple`，(6,3)→`compass`，(6,4)→`clock`。后两者的单格不是后期的整组帧。
- `Unstitcher.unstitch` 偏移 119–156：复制格内 ARGB，同时向工作图的已裁位置写 ARGB 整数 0；GUI 流重载偏移 338–358 把工作图写到 **原路径**。未命名格留在残图，不生成匿名现代纹理；输入无 alpha 时经原色彩模型写 0 未必透明。“未分配”是查表结果，不是透明、黑色或未修改。独立实现不需模仿清零：输入保持只读，保留未迁移格坐标与诊断；若产品允许单独导出残图，使用 RGBA 副本并显式清零，不将其安装成现代纹理。
- GUI 流重载偏移 169–324：晚期 terrain 将 `carrots_0..2` 各复制到同编号 `potatoes_0..2`；`potatoes_3` 独立在 (12,12)，不能复制 `carrots_3`。仅在已确认档案/目标缺失时应用这种有证据的一对多。

**RGBA 差分。** 把原版 1.4.7 按上述坐标规范裁切，219 个块名字、196 个物品名字在原版 1.5.2 都有同名散图；其中尺寸与 RGBA 逐字节一致 **218/219、152/196**。与 1.4.7 命名格的同坐标 RGBA 相比，alpha 1.2.6 有 **153/219、123/196** 格不同，beta 1.7.3 有 **103/219、112/196**，1.2.5 有 **31/219、90/196**；像素变化本身不能区分重画、占位、格重用。Alpha 有 **94/196** 个晚期已命名物品格全透明（包括晚期 sugar）；1.4.7 有 **25/37** 个未分配地形格全不透明，**32/60** 个未分配物品格有非零 alpha，且有两个已命名地形格全透明。因此既不能凭透明度判占用，也不能无条件把 1.4 表用于 alpha/beta。

**独立实现。** 条目用 `(atlas, sourceProfile, x, y, semanticId, destinations, evidence, status)` 记载，区分已证实、未使用、歧义、不支持。先扩少量确定格；用对应早期原版 atlas、下一期散图，必要时用对应早期版的渲染引用复核。精确像素匹配只作候选，重复图案、多名字、像素变化或变体保持歧义，不用相近色自动发布。已确认格保留有意的全透明；未使用/未知格不覆盖目标默认。正尺寸、两轴整除 16、像素/解码预算均验证，非整除不沿用旧工具截断；矩形 atlas 可无损裁成矩形，但不强行通过要求方帧的规则。报告按资产记录已晋升数、冲突、未知和各自的证据等级，不能用包级置信覆盖条目级未知。

## 3. 1.5 → 1.6：路径、描述、动画

[Mojang/TextureEnder 固定提交](https://github.com/Mojang/TextureEnder/tree/3c5c95c0ca0c76c12b5c45b64b0b9c98aa815684) 是资源包化工具，不是图集拆分器。[ConvertTxtToMcmetaTask](https://github.com/Mojang/TextureEnder/blob/3c5c95c0ca0c76c12b5c45b64b0b9c98aa815684/src/main/java/com/mojang/minecraft/textureender/tasks/ConvertTxtToMcmetaTask.java#L41-L200) 找到根 `pack.txt` 时，在 TXT 转换之后加入 **重命名 → 预设侧车 → 命名空间化**。我们的输入检测不用照搬工具的触发条件；不复制它在空/失败输入时的删除与整包搬运行为。

1. **包根。** 取 `pack.txt` 第一行，按 JSON 转义得到 `{"pack":{"pack_format":1,"description":"..."}}`；保留根 `pack.png`。上游文件无行则不生成；建议格式已确立却缺描述时可回退包名。默认按 UTF-8（处理 BOM），文本解码坏输入警告，不能依赖机器默认编码；现有合法元数据不要覆盖。官方 1.6.1 根还有 `language`，不是 `pack.txt` 能推导，不伪造语言注册。
2. **先语义重命名再加命名空间。** [RenameFilesTask](https://github.com/Mojang/TextureEnder/blob/3c5c95c0ca0c76c12b5c45b64b0b9c98aa815684/src/main/java/com/mojang/minecraft/textureender/tasks/RenameFilesTask.java#L27-L94) 允许一对多、同名不动、`purged/` 代表无目标、原文件的 `.mcmeta` 跟随；[MoveToNamespacesTask](https://github.com/Mojang/TextureEnder/blob/3c5c95c0ca0c76c12b5c45b64b0b9c98aa815684/src/main/java/com/mojang/minecraft/textureender/tasks/MoveToNamespacesTask.java#L14-L25) 移到 `assets/minecraft/`，包元数据/图标留根。下表是 [官方映射表](https://github.com/Mojang/TextureEnder/blob/3c5c95c0ca0c76c12b5c45b64b0b9c98aa815684/src/main/resources/rename_lists/texpack_to_respack.txt) 的少量事实锚点，目标省略命名空间前缀：

   | 1.5 源 | 格式 1 目标 |
   | --- | --- |
   | `textures/blocks/stone.png`、`textures/items/apple.png` | 同相对路径，目录依然复数 |
   | `textures/blocks/fire_0.png`、`textures/blocks/water.png` | `textures/blocks/fire_layer_0.png`、`textures/blocks/water_still.png`；`lava.png` 同理变 `lava_still.png` |
   | `textures/items/helmetCloth.png` | `textures/items/leather_helmet.png`；匹配源时不要先小写 |
   | `textures/blocks/stem_bent.png` | `textures/blocks/pumpkin_stem_connected.png`、`textures/blocks/melon_stem_connected.png` |
   | `mob/char.png`、`font/default.png` | `textures/entity/steve.png`、`textures/font/ascii.png` |
   | `gui/gui.png`、`misc/grasscolor.png` | `textures/gui/widgets.png`、`textures/colormap/grass.png` |
   | `environment/moon.png` | 无目标；不要覆盖 `moon_phases.png` |

   建议独立从 1.5.2/13w23b 与 13w24a/1.6.1 的原版实际资源做差分并审查例外；不用上游表作为运行依赖。相同目的路径同内容去重，异内容不能按顺序覆盖；优先明确已有的可信版本资源，否则报告冲突。未知根路径不能一律搬成原版资源；MCPatcher/OptiFine 的早期动图、CTM 等另作格式与回退，不用通用 PNG/TXT 规则误吃。
3. **动画。** 同 stem 有 PNG 的 TXT 才是候选。上游格式是行内逗号、跨行保序，`i` 默认 **1 tick**、`i*t` 指定持续 ticks；负索引和非正时间拒绝（内部 `-1` 是未知时间哨兵，不应视作用户可填）。上游用时长众数作 `frametime`；[序列化器](https://github.com/Mojang/TextureEnder/blob/3c5c95c0ca0c76c12b5c45b64b0b9c98aa815684/src/main/java/net/minecraft/client/resources/metadata/animation/AnimationMetadataSectionSerializer.java#L57-L86) 中帧为整数或 `{"index":i,"time":t}`，默认时长 1/未知宽高/空帧数组可省。独立实现可固定默认 1，只发非默认时长对象，省去众数并列的不确定性；保留序列重复帧。标准竖条以宽作一帧边长、高为整数倍；限制帧数/整数范围，拒绝越界索引，不能把任意非方 PNG 或同名文本都当动画。合法既有侧车优先，保留其他节；坏 TXT 不删除、不出坏侧车。1.5.2 的 `clock.txt`、`compass.txt` 实测 0 字节，PNG 分别 16×1024/16×512，13w24a/1.6.1 相应 `{"animation":{}}` 代表缺省帧序，未知空 TXT 不泛化。1.5.2 火焰存在序列、熔岩存在显式时长；13w24a 对应侧车分别有 `frames` 和 `frametime,frames`。单格 atlas 无法反推出火、水、岩浆、指南针/钟的程序化动画或角度语义：缺完整帧源时注明缺失并让目标回落原版，不能用一张静态裁片覆盖动画。后续格式的模型语义交由各自迁移处理。
4. **非动画侧车。** [PopulateDefaultMcMetaTask](https://github.com/Mojang/TextureEnder/blob/3c5c95c0ca0c76c12b5c45b64b0b9c98aa815684/src/main/java/com/mojang/minecraft/textureender/tasks/PopulateDefaultMcMetaTask.java#L42-L62) 只在目标纹理存在、侧车缺失时写入；[预设清单](https://github.com/Mojang/TextureEnder/blob/3c5c95c0ca0c76c12b5c45b64b0b9c98aa815684/src/main/resources/mcmeta_conversion_lists/texture_txt_to_mcmeta.txt) 对光效、南瓜遮罩、暗角有 `blur`，阴影有 `clamp`。不能看颜色猜采样元数据。

## 4. 测试与发布门槛

| 自合成夹具 | 预期断言 |
| --- | --- |
| 256²、512² 的 16×16 布局，异色角标，半透明与全透明，另有 257×256、矩形、无 alpha、索引色与超限 PNG | 15/16 跨行、四角与 255 不转置；格尺寸随比例；已映射全透明保留，未映射的非透明格也不发出；原输入不变。 |
| atlas-only，单图集，两图集，1.5 局部散图，混合散图+残图，只有 pack.txt，根格式 1 与未知现代包 | ZIP/目录同一分类；不造缺失图集；弱证据不打格式；混合显式资源优先；较新不降级。 |
| 同坐标在两档案分别“未使用”和“已确认”，以及重复图案不同语义 | 不能以颜色反推占用；来源不明仅执行共同确认的规则；冲突与跳过有诊断。 |
| 大小写重命名、一对多、无目标路径、图像旁侧车、多行描述/引号/非 ASCII | 路径在正确根；一对多不改像素；保留已有合法侧车；输出元数据 JSON 可解析；碰撞策略确定。 |
| 16×64 PNG 与两行 `0,1*3` / `0,2*2`；已确认的钟/指南针空 TXT；再测负数、0 ticks、越界 4、溢出、孤立 TXT 及既有合法侧车 | 播放序列为 `[(0,1),(1,3),(0,1),(2,2)]`（索引、ticks），仅已核验空 TXT 写空 animation；坏条目不会产出坏侧车。 |
| 用验证过摘要的官方归档作本地核对，只断言边界、路径和像素差统计 | 不向仓库提交官方 JAR、全图或导出的切片；测试预期不从实现规则自我生成。 |

先交付高置信检测、最小无损裁图、格式 1 的显式路径和动画，再扩版本档案与坐标。拒绝越界/重复规范路径/符号链接，限制输入图像和 ZIP 解码预算；输出独立新包，确定性写入，缓存键包括规则/档案版本。遵守 [AGENTS.md](../../AGENTS.md) 的 Java 8、纯 core 与路径替换约束。**尚未完成验证：** alpha/beta 的完整格占用和重用矩阵、每个过渡快照、旧运行时程序化动画/渲染行为、模组私有格式、格式 1→现代各步规则。本报告不是通盘兼容声明；不能仅改 `pack_format` 就标记后续格式的资产已迁移。

## 5. 首批 atlas 白名单：原版客户端独立证据

以下只用上文 Mojang 清单的客户端归档验证，未读 Unstitcher 的映射表，也未执行任何客户端。表中的类名为该版本 JAR 中的类条目（加 `.class`），偏移从相应 JVM 方法 `Code` 属性的指令数组开头计，不是归档文件偏移。

| 版本 | 方块注册与草方块分面 | 物品注册 |
| --- | --- | --- |
| a1.2.6 | `nq.<clinit>()V` 184–257：方块 ID 1 的构造纹理参数为 1，ID 3 为 2，ID 2 调用 `os.<init>(I)V`；`nq.<init>(IILhb;)V` 偏移 8 写入纹理字段。`os.a(Lpk;IIII)I` 0–14 将面 1/0 选为 0/2，15–49 选择特殊侧面 68 或普通侧面 3。 | `dx.<clinit>()V` 85–99 注册内部物品 ID 4，设图标索引 10；`dx.<init>(I)V` 21–27 确认 ID 加 256，`dx.a(I)Ldx;` 偏移 2 写图标字段。该注册未见字符串 `apple`，名称由 Beta 同 ID/图标及像素跨证。 |
| b1.7.3 | `uu.<clinit>()V` 192–280 绑定 `stone`=1、`dirt`=2，`grass` 调用 `wp`；`wp.a(Lxp;IIII)I` 0–49 的分面与 Alpha 相同。 | `gm.<clinit>()V` 114–135 把 `apple`（内部 ID 4）设成 (10,0)，2234–2299 把 `compass`、`clock` 设成 (6,3)、(6,4)；`gm.a(II)Lgm;` 1–7 将坐标写成 `x + 16*y`。 |
| b1.8.1 | `lr.<clinit>()V` 192–280 绑定相同的石/草/泥；`nf.a(II)I` 0–14 普通表面：面 1→0，面 0→2，其余→3。 | `sv.<clinit>()V` 114–137 的 `apple` 仍是 (10,0)；2260–2325 钟/指南针坐标不变，`sv.a(II)Lsv;` 1–7 同公式。 |
| 1.4.7 | `amq.<clinit>()V` 227 起以 ID 1/纹理 1 注册 `stone`；`akj.a(II)I` 的 15 字节与 b1.8.1 对应方法完全一致。 | `up.<clinit>()V` 偏移 132 的 `apple` 常量紧跟 (10,0) 图标设置；`up.b(II)Lup;` 1–7 同公式。 |

无需任何表数据即可独立复验：从每个样本的原版图集裁 (x,y) 对应 16×16 区，按从上到下、从左到右的 8 位 RGBA 字节序（不是 PNG 文件字节）计算 SHA-256。Alpha 1.2.6、Beta 1.7.3 和 Beta 1.8.1 在以下每一格各自同字节，但比 1.0 起的样本换过画。后期的四个 terrain 锚点在 **1.0、1.2.5、1.4.7** 源图集各 256 格以及 **1.5.2** 同域 16×16 散图中均唯一精确相等；苹果只在 1.4.7 和 1.5.2 的完全透明 RGB 归零后为唯一命中。归零只用于取证，不应改动迁移输出的像素。

| 图集坐标 → 1.5.2 文件名 | Alpha/Beta 三个早期样本的 SHA-256 | 1.5.2 目标 SHA-256 |
| --- | --- | --- |
| terrain (0,0) → `grass_top.png` | `3e64eba2afc69e8ea18e97945cc821f2988fd324112d4020df5c87816506ca0e` | `31bb5d5d3c5516aadfd1fd5c605fa46252c0f0612091d753c0621ca6890549fd` |
| terrain (1,0) → `stone.png` | `d7575ff3841532edb545c47b9228d3a27bbc2a1b353f6fca62790944f947f34c` | `0810ce3b5d5dac6a5a28afc2336921d76e28d9e72660da69e1b57deecf68d28d` |
| terrain (2,0) → `dirt.png` | `362d2f5f78a01425d9069b6273a5e4656ce23dd5a27f3c1f742a50006c89d683` | `327b802852d3662bb8bded6acd1cfbd559be990d521d2609ec1a91de6f27b4c9` |
| terrain (3,0) → `grass_side.png` | `a1230a3537ed3ae24f0d599496d142359401a0798451160bac72c7d054e22b08` | `62c47cebea3514034a031ca1199a2ce0dfcedf4be0d3d669f0067087ffe95419` |
| items (10,0) → `apple.png` | `a9f2b11860345f0b4cb57bd788198270dcb560d2bcb4e381328534c9118f28cf` | `adf1ea3dbda4d81e92d945f80a6311bd94a7ab913c7f33e7bc99354a91e1e94b` |

1.0、1.2.5 苹果该格仍是早期哈希；1.4.7 原始 RGBA 的 SHA-256 是 `7c5ac3dbdbe44d502821dc8db10448874f00f10ebeb21b998ebc36ac952149f4`，归零后才相等。已核验的五个静态格可作为首批共同规则；钟和指南针的明确坐标是动画例外证据，不能当静态可迁移格；草方块特殊侧面只证明早期有另一格，未在此证明其现代路径/合成规则。

### 扩展候选：六个原版图集样本的唯一像素交集

先对 a1.2.6、b1.7.3、b1.8.1、1.0、1.2.5、1.4.7 按原尺寸裁 256 格，在各自同域图集计算严格 RGBA（不归零）频数。只考虑至少一像素 alpha 非零、六个版本均同坐标同字节且在各图集中只出现一次的格，再从 1.5.2 同域的 16×16 散图找唯一精确对端，得到 65 个方块、70 个物品候选。判据不是运行时使用证明：例如对端有未采用的 `quiver`，多个树种共用木段截面像素，不能由 1.6.1 同像素自动判定一对多。以下仅选明确的普通方块/物品，另外要求在 1.6.1 同域 16×16 PNG 中也能唯一精确命中；未覆盖未抽样的快照。两端文件均加 `.png`，目录分别为 `textures/{blocks,items}/` 与 `assets/minecraft/textures/{blocks,items}/`。

| 图集格 | 1.5.2 文件 stem | 1.6.1 文件 stem | 全部八个版本的共同 RGBA SHA-256 |
| --- | --- | --- | --- |
| terrain (1,1) | `bedrock` | `bedrock` | `0fddd326cd10bd8795da2e9206bd13fdc5bb2e814a2ff6b40e9aef90f8477ea3` |
| terrain (0,2) | `oreGold` | `gold_ore` | `772f949014a04a5e20313748138449c40fc578619bf685c1e454cf53d9dce796` |
| terrain (1,2) | `oreIron` | `iron_ore` | `f8b6e228ba9ee3e33dcf58d7b15e00ec7a4f5a1736605bb85dccd69aa18779b1` |
| terrain (2,2) | `oreCoal` | `coal_ore` | `90f2a1dcef6bb9cdc6c2b4933fa187d629cf626f22b2de3c05139babba37bbb8` |
| terrain (5,2) | `obsidian` | `obsidian` | `e20270bfdf7f1230211d04fe5b0ca744917c57c3e62b6c501e6cdfca6a53e7ca` |
| terrain (1,3) | `glass` | `glass` | `cc2b59ea27804a28c5f1f9f5f24c811b0d886a7e7735c576bec93d11af7593d6` |
| terrain (2,3) | `oreDiamond` | `diamond_ore` | `6c92df1cbdca8e471fb4f399e5ba8c26d51ecc4660777aa4cb4e5a29c4757c93` |
| items (6,1) | `brick` | `brick` | `bdcd48a1e17b6cc1e3effb5abc86e7c2fdafd9988c7f9271fe6f8f1380acad29` |
| items (7,1) | `ingotIron` | `iron_ingot` | `e3eefaf73595ec23ebce498c33b971f3783251a28637c3abe1ebc5f2f32f4993` |
| items (8,1) | `feather` | `feather` | `cd65e0d4fd6cecb7d6e08872725aba693e9a1635b3c79f5ec61096075689bd02` |
| items (9,1) | `wheat` | `wheat` | `3456a2c44da86164c9ac47bf219fff6895abc44111f8a3c579dd5123b0aaaba5` |
| items (14,1) | `slimeball` | `slimeball` | `8cc7f2088fa39ad97cded41638947a967ae2cd5c115012584fb6c5ceb59f2a15` |
| items (5,2) | `arrow` | `arrow` | `ff0a24e1fdb5a9e391d27dcdd866bf4289dff9fbc96488a09af3683927158da0` |
| items (7,2) | `ingotGold` | `gold_ingot` | `88067a3aa67491ada7fa42096f6d364dd4f114a9d0b70df47984c551ae38d2ed` |
