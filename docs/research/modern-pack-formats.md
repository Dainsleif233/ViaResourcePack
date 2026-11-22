# 现代 Java 版资源包格式：1.20.1 交界到 26.4 Snapshot 10

## 可直接用于 ViaResourcePack 2.0 的结论

- 只用**客户端资源包**格式，不用数据包格式、世界版本、协议号或游戏版本字符串推定迁移步。节点是整数对 (major, minor)，按 major 再按 minor 比较；不用小数或浮点。老版整数提升为 (major, 0)。中间节点取下表实际出现的格式，不枚举所有整数。补丁版、快照、正式版共用格式时共用一个节点，但不能因此断言资产字节也相同。
- 1.20.2–1.21.8 输出单目标旧式声明，如 `{"pack":{"description":"…","pack_format":64}}`。从 25w31a（65.0；正式版从 1.21.9）使用现代声明。**单个现代目标两个边界都写显式二元组**；不要输出 `supported_formats`，也不必输出 `pack_format`。26.3 输出示例：

~~~json
{"pack":{"description":"Converted resource pack","min_format":[97,1],"max_format":[97,1]}}
~~~

- 26.3 运行目标为 **97.1**，26.4 Snapshot 10 识别目标为 **104.0**（运行支持仍只承诺到 26.3）。26.4 格式的包投向 26.3 不是升版：保留原输入，不降级或伪装兼容。
- 兼容区间不是作者格式。目标已在有效范围内则不转换；范围完全在目标之前才允许升级。真实起点对每条规则仍可能未知：不要把范围最小/最大或旧 `pack_format` 当作各 overlay 或所有文件的确切版；跨了不确定的结构跳变时，应依赖特征、明确选择来源或拒绝标注完整成功。无元数据/坏声明和未来未知格式不应混入有效节点。

## 证据和复验方式

本地引用仓库 `D:/Code/jsucraft/MC`；截至 `da28c03af`（26.4 Snapshot 10），默认工作树当前另有提交，不用它作锚点。表格取该提交的 first-parent 主线，另加 26.1.2 分支 `315aeb702`；不包含愚人节分支或根提交以前版本。到 25w32a，版本 JSON 未提交，取各 ref 中 `client/net/minecraft/DetectedVersion.java` 的内建版本，关键版本用官方说明交叉验证；25w33a 起取 `version.json` 的 `pack_version.resource_major/resource_minor`，绝非同文件的 `data_major/data_minor`。仓库不含 `assets/` 资产树、PNG/GLSL；`data/` 是数据包，不能作为替代。有发布说明和加载器证据时同时引用。下文 `ref:path:行` 指 Git blob 原行号；只核对可见行为，没有执行引用代码或复制反编译实现。

~~~sh
git -C D:/Code/jsucraft/MC log --first-parent --reverse --format='%h %s' da28c03af
git -C D:/Code/jsucraft/MC show 634debadb:version.json
git -C D:/Code/jsucraft/MC show da28c03af:client/net/minecraft/server/packs/metadata/pack/PackFormat.java | nl -ba
~~~

## 格式目录

每行记**本地首次观察**到该格式的版本、提交。源码里缺失的值不能从递增规则填入；`23.0` 和 `27.0` 不在该序列：`23w51a/b` 的 `e811dffbc/b0080f518` 仍为资源 22，`24w07a` 的 `6a7d7e093` 仍为资源 26；官方 [23w51a](https://www.minecraft.net/en-us/article/minecraft-snapshot-23w51a) 的 27 和 [24w07a](https://www.minecraft.net/en-us/article/minecraft-snapshot-24w07a) 的 32 均是数据包版本。[1.20.5](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-5) 汇总标题范围 “23 through 32” 不能证明 23 已发布。

~~~text
格式    首见 ref     首见版本
15.0    3015bd18b   1.20.1（与早期研究的交界）
16.0    10bf742e1   23w31a
17.0    a03b2cd92   23w32a
18.0    141f21719   1.20.2-pre2
19.0    4be0aab46   23w42a
20.0    68af0a6a5   23w43a
21.0    a4e972d41   23w45a
22.0    683e0e30f   1.20.3-pre1
24.0    07a9fef3a   24w03a
25.0    361215e45   24w04a
26.0    213ffdce7   24w06a
28.0    a3077e147   24w09a
29.0    7f4772956   24w11a
30.0    7c54fd7b0   24w12a
31.0    a13ccbf01   24w13a
32.0    456a7ba27   1.20.5-pre4
33.0    2e65ef7c9   24w18a
34.0    66faacceb   24w21a
35.0    03d2adcb8   24w33a
36.0    03f395516   24w34a
37.0    bad12edcc   24w36a
38.0    d6ba94fce   24w37a
39.0    ad3b201eb   24w38a
40.0    66c7215b5   24w40a
41.0    1b24bb662   1.21.2-pre1
42.0    a0eb34e89   1.21.2-pre3
43.0    7d9ca125f   24w44a
44.0    cb3c8c664   24w45a
45.0    6d0c6544f   24w46a
46.0    09a2063f1   1.21.4-pre1
47.0    9d861cd57   25w02a
48.0    e13daa1e0   25w03a
49.0    dcce7af21   25w04a
50.0    4c224ddb2   25w05a
51.0    e7c83fcbc   25w06a
52.0    02027c155   25w07a
53.0    e8d206390   25w08a
54.0    4344bdb6b   25w10a
55.0    bebc844b1   1.21.5-pre1
56.0    034755801   25w15a
57.0    b781aae7d   25w16a
58.0    6196aeb61   25w17a
59.0    8c43d0b4c   25w18a
60.0    c48962f90   25w19a
61.0    dff09d60c   25w20a
62.0    731a4c53d   25w21a
63.0    d7086969a   1.21.6-pre1
64.0    090e8b998   1.21.7-rc2
65.0    f9a89e68a   25w31a
65.1    77454a9ad   25w32a
65.2    529cdd358   25w33a
66.0    b97eb18d1   25w34a
67.0    1342b2c95   25w35a
68.0    ac74788ed   25w36a
69.0    c3aa46a49   25w37a
70.0    b8d5db91c   25w41a
70.1    08a234d00   25w42a
71.0    baeea7b6c   25w43a
72.0    e7226d2e3   25w44a
73.0    a083a66d7   25w45a
74.0    7ec078240   25w46a
75.0    73c816e57   1.21.11-pre1
76.0    3d78fd122   26.1 Snapshot 1
77.0    c09beab17   26.1 Snapshot 2
78.0    74ed35eb0   26.1 Snapshot 3
78.1    90a37ed99   26.1 Snapshot 4
79.0    25320fb7b   26.1 Snapshot 5
80.0    f78a8fca0   26.1 Snapshot 6
81.0    406bf5244   26.1 Snapshot 7
81.1    083c626fe   26.1 Snapshot 8
82.0    db1c61c39   26.1 Snapshot 10
83.0    fad18303d   26.1 Snapshot 11
84.0    c3bb482ee   26.1 Pre-Release 1
85.0    1d3d3caa1   26.2 Snapshot 1
86.0    24ce64cf3   26.2 Snapshot 3
86.1    57217e146   26.2 Snapshot 4
86.2    ddf88512e   26.2 Snapshot 5
87.0    b4c0b4731   26.2 Snapshot 7
88.0    88fe5875f   26.2 Pre-Release 1
89.0    92ec35563   26.3 Snapshot 1
90.0    f32bd2293   26.3 Snapshot 2
91.0    87e8628cf   26.3 Snapshot 3
92.0    e79868855   26.3 Snapshot 4
93.0    3a325c395   26.3 Snapshot 5
94.0    cfa124a02   26.3 Snapshot 6
95.0    70b7687d3   26.3 Snapshot 7
96.0    c581feaf7   26.3 Snapshot 8
97.0    6c8b8ec87   26.3 Snapshot 10
97.1    b1fe809fa   26.3 Pre-Release 1
98.0    b74180e7d   26.4 Snapshot 1
99.0    0247b4c84   26.4 Snapshot 2
100.0   31432c0b8   26.4 Snapshot 3
100.1   911ae0f55   26.4 Snapshot 4
100.2   2781b32d5   26.4 Snapshot 5
101.0   a6ccc5d75   26.4 Snapshot 6
102.0   bc3c96918   26.4 Snapshot 7
103.0   f2b434ae0   26.4 Snapshot 8
104.0   b75a64e11   26.4 Snapshot 9（Snapshot 10 da28c03af 同格式）
~~~

正式版的格式复用及目标映射：

| 格式 | 游戏版本及对应 ref |
| --- | --- |
| 18.0 | 1.20.2 `913dbbb4c` |
| 22.0 | 1.20.3 `d73b604bb`、1.20.4 `9e2d2eebd` |
| 32.0 | 1.20.5 `ac39e1ebb`、1.20.6 `bc80c0e3b` |
| 34.0 | 1.21 `98fde7be9`、1.21.1 `2f9b4a847` |
| 42.0 | 1.21.2 `cc8ca3ae1`、1.21.3 `95bd2c463` |
| 46.0 | 1.21.4 `178543a33` |
| 55.0 | 1.21.5 `bbff168ef` |
| 63.0 | 1.21.6 `855bcfe23` |
| 64.0 | 1.21.7 `689a91bb0`、1.21.8 `c454270bb` |
| 69.0 | 1.21.9 `4536d1392`、1.21.10 `bbceceaee` |
| 75.0 | 1.21.11 `02189ae7c` |
| 84.0 | 26.1 `097c9cc6f`、26.1.1 `d08dd652d`、26.1.2 `315aeb702` |
| 88.0 | 26.2 `894728dcf` |
| 97.1 | 26.3 `634debadb` |

## 原版 pack.mcmeta 读取、范围和输出

**整数时代。** [1.20.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-2) 和 `913dbbb4c:client/net/minecraft/server/packs/repository/Pack.java:74–107`：根对象 `pack` 中需要 `description` 和整型 `pack_format`；可选 `supported_formats` 支持整型、双元素数组、含 `min_inclusive/max_inclusive` 的对象。范围缺失视为单版本；声明范围未包含 `pack_format` 时该版本原版记录警告并回退到单版本。16.0 的 23w31a 已有 overlay 和 GUI 图集；不能把稳定版 18.0 当成首引入，更不能把旧整数范围按现代数组解析。

**major/minor 时代。** [25w31a](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w31a)、[1.21.9](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9)，以及 `4536d1392:client/net/minecraft/server/packs/metadata/pack/PackFormat.java:38–100,119–234`、同文件在 `da28c03af:38–101,114–229`：

- `min_format` 和 `max_format` 都在 `pack` 中，必须成对。边界接受非负整数或数组。原版解码器接受 1–256 个非负整数的数组，但只取前两项；产物只写两项（也可在输入诊断中标记多余元素）。整数与一元数组：min 的缺省 minor 为 0，max 的缺省 minor 为 2147483647；如 min=97、max=97 覆盖整个 97.x，而不是只覆盖 97.0。新范围是闭区间，min > max 无效。
- 对 client 当 min major > 64 时，旧 `supported_formats` 禁用；若仍声明 `pack_format`，则必须落在 major 区间内。对服务端 data 对应阈值是 81，与本项目资源包迁移无关。只写旧式 `pack_format`：<=64 解为单点 (major,0)，>64 缺现代边界为无效。只写旧范围时上界也不能 >64，且原版需要主 `pack_format` 在范围内、>=15；旧范围每端按 minor=0。
- 同时声称旧式格式和现代格式（min major <=64 且存在现代边界）需要两个旧键。旧范围下界必须等于现代 min 的 major，上界必须是现代 max 的 major 或 64；`pack_format` 必须落在现代 major 区间且 >=15。这是解码条件，不代表该包实际可被所有旧客户端识别，更不保证资产兼容。建议转换产物只声明单一目标，不制造混合范围。原版编码是范围序列化，不是输入 JSON 分支的无损重放：`fromRange` 总是生成现代两端，只在 major 区间**包含** 64 时生成旧键（主格式取下界）；`compactListCodec` 则把等于缺省 minor 的单元素边界写为整数。完全低于 64 的范围据此不会补上旧键，不能把原版 encoder 当作旧式包 writer。见 `da28c03af:client/net/minecraft/server/packs/metadata/pack/PackFormat.java:41–45,118–121,174–194` 和 `da28c03af:client/net/minecraft/util/ExtraCodecs.java:437–443`。
- `description` 走文本 Component codec；留存有效原值，缺失时生成合法字符串；不要把任意 JSON 对象当描述。严格 section 不能读时，原版可能退到仅 description 的 fallback。fallback 在兼容性界面的状态是 **UNKNOWN**，不是新格式或成功兼容。同样，只有 description 能读并不证明 overlay 或资产正常。锚点：`4536d1392:client/net/minecraft/server/packs/metadata/pack/PackMetadataSection.java:23–38`，`4536d1392:client/net/minecraft/server/packs/repository/Pack.java:59–80`；`da28c03af:client/net/minecraft/server/packs/repository/PackCompatibility.java:30–40`。

## Overlay 与 filter：不要用一个 ZIP 路径重命名替代原版语义

- Overlay 格式为顶层 `overlays.entries`：旧条目有 `directory` + `formats` 整数范围，现代条目有 `directory` + `min_format` + `max_format`。命中对应格式的条目按声明顺序激活，**同路径最后声明的生效 overlay 胜过前条，overlay 胜过根**；只叠加各自之下的 `assets/` 等类型资源；根资源、元数据仍由主包读。目录正则为 `[-_a-zA-Z0-9.]+`；转换器还必须拒绝 `.`、`..`、绝对路径、逃逸与 symlink，不能拿此正则作磁盘安全边界。
- 整个列表只要有条目的最低支持 major <=64，所有参与校验的条目都需要旧 `formats`（现代条目也需要，受双范围一致性规则约束）；纯现代列表则不得给新条目附旧 `formats`。既没有两个边界也没有旧范围的条目在原版被警告后跳过；只缺一端等验证错误可使 overlay 整节失效。锚点：`da28c03af:client/net/minecraft/server/packs/metadata/pack/PackFormat.java:48–64,114–216`，`da28c03af:client/net/minecraft/server/packs/OverlayMetadataSection.java:27–68`，`4536d1392:client/net/minecraft/server/packs/CompositePackResources.java:31–67`，`da28c03af:client/net/minecraft/server/packs/OverlayedPackResources.java:32–75`。
- `filter.block` 每项为可选 `namespace` / `path` 的 Java 正则（`Pattern.asPredicate` 是查找子串，不是全串匹配；如需精确模式使用锚点；缺字段相当于任意）。静态核对表明：原版独立对整个列表计算“任一 namespace 匹配”和“任一 path 匹配”，不是按条目的成对 AND 之后再 OR；两条规则可能交叉组合。它屏蔽较低优先级包的资源（路径包含类型下的相对路径及扩展名）和可单独匹配的 `.mcmeta`，不删除本包已有资源；无该命名空间资源的包也可能作为 filter-only 条目插入。见 `da28c03af:client/net/minecraft/server/packs/resources/ResourceFilterSection.java:20–35`、`da28c03af:client/net/minecraft/util/IdentifierPattern.java:22–47`、`da28c03af:client/net/minecraft/server/packs/resources/MultiPackResourceManager.java:38–74`、`da28c03af:client/net/minecraft/server/packs/resources/FallbackResourceManager.java:80–94,108–134,151–195`。
- 建议：对明确来源版本，先按**来源**格式选择 overlay 一次，再按原版优先级生成有效资产视图；只有成功投影且确定语义时，才将结果 materialize 为单目标包并删除旧 overlay 声明。如果来源不明确且激活集合不同，不能擅自扁平化；未知节与任意 filter 正则原样保留并出诊断，不做字符串级推测改写。命中同一资源路径时刻意保留冲突日志。filter 迁移只能处理可证明的精确路径 / 单条命名空间规则；不可把正则当字面路径重命名。
- `language` 与 `filter` 是独立根节；新接管 writer 不应丢掉原包提供的语言注册。`LanguageManager` 从每个资源包的 `language` 读取注册（语言代码对应 `region`、`name`、可选 `bidirectional`），实际字符串从 `assets/<namespace>/lang/<code>.json` 资源栈读取；与 104.0 的受保护前缀规则分开处理。见 `da28c03af:client/net/minecraft/client/resources/language/LanguageManager.java:49–63`、`da28c03af:client/net/minecraft/client/resources/language/LanguageInfo.java:20`、`da28c03af:client/net/minecraft/client/resources/language/ClientLanguage.java:42–75`。

## 优先可落地的文件和引用迁移

路径以 `assets/minecraft/` 开头，除非单列 namespace。以下仅是可证候选，不能重写同名的任意自定义 namespace、备份或外部 mod 子域。迁移时用确定的源→目标映射，检查目的路径冲突；等价字节才可去重。重命名 PNG 时同移动 `.png.mcmeta`，代码只改已知 JSON 语义槽（texture / parent / sprite / atlas 等各自独立），不全文替换。官方汇总窗口没逐快照验证的条目不冒充精确首见；生产规则应先补逐 ref fixture 或明确检测旧结构。

| 首见/窗口 | 可确定的转换 | 条件与证据 |
| --- | --- | --- |
| 22.0 | `textures/{block,item}/grass.png` → `…/short_grass.png`，以及对应 vanilla `models/{block,item}/grass.json` 和 `blockstates/grass.json` 中实际存在的旧路径/引用 | 只匹配 `grass`，不动 `grass_block`、`tall_grass`；[1.20.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-3) |
| 24–32 | `textures/item/scute.png` / `models/item/scute.json` → `…/turtle_scute` | 只改旧海龟鳞甲；[1.20.5](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-5) |
| 37.0 | `textures/models/armor/<material>_layer_1.png` → `textures/entity/equipment/humanoid/<material>.png`；`_layer_2` 对应 `humanoid_leggings`；皮革 `_layer_N_overlay` 映射为对应层的 `leather_overlay`；`textures/entity/elytra.png` → `textures/entity/equipment/wings/elytra.png` | 只转换已知原版材质；狼甲也有明确映射（`textures/entity/wolf/wolf_armor{,_overlay}.png` → `textures/entity/equipment/wolf_body/armadillo_scute{,_overlay}.png`）；见 [24w36a](https://www.minecraft.net/en-us/article/minecraft-snapshot-24w36a) 和 [1.21.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-2) |
| 43–46 | `assets/<namespace>/models/equipment/<id>.json` → `assets/<namespace>/equipment/<id>.json` | 只有已知为旧装备 asset/形状通过的文件；新版与旧版 schema 改动独立验证，不能改自定义同路径的几何模型；[1.21.4](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4) |
| 47–55 | `textures/misc/enchanted_glint_entity.png` → `textures/misc/enchanted_glint_armor.png`；`textures/entity/pig/pig_saddle.png` → `textures/entity/equipment/pig_saddle/saddle.png`；同结构迁移 `strider_saddle`；`creaking_heart_active` / `creaking_heart_top_active` → `…_awake`，显式改 `creaking_heart` blockstate 对应值 | 并不覆盖“从合图拆出马鞍”这类像素变换；[1.21.5](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-5) |
| 88.0 | `textures/block/{quartz_pillar,purpur_pillar}.png` → 同目录 `{quartz_pillar,purpur_pillar}_side.png` | 保持顶面 / 其他引用；[26.2 Pre-Release 1](https://www.minecraft.net/en-us/article/minecraft-26-2-pre-release-1)（`88fe5875f`） |
| 89.0 | vanilla 调色板 `textures/trims/color_palettes/<id>.png` → `textures/palettes/trim/<id>.png`；特例源 `trim_palette.png` → `textures/palettes/trim_base.png`；atlas 的 `palette_key` / `permutations` 从旧 ID `trims/color_palettes/<id>` 改为相对 `textures/palettes/` 的 ID `trim/<id>`，base 则为 `trim_base` | [26.3 Snapshot 1](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-1)（`92ec35563`）引入。公告写 `trim/color_palettes`，旧版生成器 `894728dcf:client/net/minecraft/client/data/AtlasProvider.java:38–39` 明确为 **`trims`**，以实际路径为准；新版去向见 `634debadb:client/net/minecraft/client/data/AtlasProvider.java:31,59` 和 `634debadb:client/net/minecraft/client/resources/palette/Palette.java:32`。只对路径与引用都确认的调色板改写，不重写任意自定义 palette。 |
| 91.0 | 在 `models/` 方块几何的 `elements[]` 中，将已知 `shade:false` 改为 `shade_direction_override:"up"`，旧 `shade:true` 使用新缺省；已有合法新字段不覆盖 | 仅限布尔旧值及正确元素槽，遇冲突诊断；[26.3 Snapshot 3](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-3)（`87e8628cf`）明示此等价映射 |
| 96.0 | `ancient_city_map`→`buried_ancient_city_map`，`mineshaft_map`→`buried_mineshaft_map`，`abandoned_campsite_map`→`abandoned_camp_map` | [26.3 Snapshot 8](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-8)；入口、几何、贴图和 `item.minecraft.*` 语言键随注册 ID 改名。见下方制品核对。 |
| 97.0 | 五个探险地图注册 ID 改名；旧贴图与旧入口不一定同名 | [26.3 Snapshot 10](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-10)；原始贴图 ID 和模型 ID 各用独立映射。 |
| 102.0 | `textures/block/icicle_down_{base,frustum,middle,tip,tip_merge}.png` → 只去掉 `_down` | 适用于 26.4 目标或迁移夹具，不改变 26.3 运行边界；[26.4 Snapshot 7](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-7) |

### 26.3 探险地图资源边界

[格式 95 客户端](https://piston-data.mojang.com/v1/objects/16169fc68cc553b82f495bb2896bde6170aa51a7/client.jar)、[格式 96 客户端](https://piston-data.mojang.com/v1/objects/dd75cb26bf71099ae196080606dbaaf6eb77d59a/client.jar)和[格式 97 客户端](https://piston-data.mojang.com/v1/objects/b36eac90fae6cce3c83c5cb42e11d3ea8cd04f19/client.jar)均按各自 Mojang profile 的 SHA-1 检查。三加五组的旧注册入口在对应新制品中消失；两端各自的 `items/<id>.json` 均指向 `models/item/<id>.json`。对照两个几何的 `textures.layer0`，八对 PNG 字节完全相同，明确证实旧图可以原样复制。97.0 中的 `trial_explorer_map` 原本引用 `trial_chamber_map`，目标两处都是 `buried_trial_chambers_map`；丛林地图也区分 `jungle_explorer_map`、`jungle_temple_map`、`jungle_pyramid_map`。证据代码：`c581feaf7:client/net/minecraft/references/ItemIds.java:204–207` 与 `c581feaf7:client/net/minecraft/client/data/models/ItemModelGenerators.java:636–644`；下一边界 `6c8b8ec87` 同类。

迁移只给可确认的原版资源补新路径；包内明确的新版文件、侧车和语言值优先。语言层仅在 `assets/minecraft/lang/<locale>.json` 内为字符串补充新 `item.minecraft.<id>` 键，保留旧键和值。本例不迁移地图装饰或物品数据，不把原版新名称复制进用户资源包。

## 不能只靠移动或改版本号的变化

| 格式边界/窗口 | 可观测资产规则、无法静态保证的部分 |
| --- | --- |
| 16–18 | [1.20.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-2)：旧 GUI 大图被分离到 `textures/gui/sprites/`；GUI 图集接受普通动画以及 `gui.scaling`（stretch/tile/nine_slice）。只在 vanilla 图尺寸、比例和槽位有可复验映射时切割；任意拼图、偏移、动画侧车不能靠文件名推断。17–18 也有图块/资产增量，不能因为发现主要 GUI 步就宣称整个窗口覆盖。 |
| 24–32 | 地图装饰从 `textures/map/map_icons.png` 拆成 `textures/map/decorations/` 的图集；定标后逐格裁切，非整倍数/自定义图版报告未知。TTF provider 的 shift/EM/baseline 行为变更，font provider 的 `filter`（`uniform`、`jp`）不是包级 filter。Shader 删除 `IViewRotMat` 等时涉及坐标空间，不能靠改标识符维持效果。见上面的 [1.20.5 技术说明](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-5)。 |
| 36–42 | [24w34a](https://www.minecraft.net/en-us/article/minecraft-snapshot-24w34a) 的 36.0 已把 post effect 配置移到 `post_effect/`、post shader 源移到 `shaders/post/`；命名空间化 vertex/fragment/include，统一 program schema，并改变 pass 输入和 targets。裸文件移动只有在同时转换对应 schema 且保留关联引用时有效。37.0 装备模型和贴图布局独立；自定义染色、多层、纹理映射不可凭旧文件名合成。 |
| 44–46 | [24w45a](https://www.minecraft.net/en-us/article/minecraft-snapshot-24w45a)、`cb3c8c664:client/net/minecraft/client/renderer/item/ClientItem.java:18–21`、`178543a33:client/net/minecraft/client/resources/model/ClientItemInfoLoader.java:33–38`：物品入口改为 `assets/<namespace>/items/<id>.json`，顶层 `model` 树；`models/` 几何模型仍在原处但必须被入口引用。可以在已知物品入口和基础模型路径下为简单静态模型生成 `minecraft:model`；不要把每个 `models/item` 文件提升为独立物品。旧 `overrides` 是依序选择满足一组阈值的模型（后项优先；见 `3015bd18b:client/net/minecraft/client/renderer/block/model/ItemOverrides.java:44–90`）。`custom_model_data` 单属性阈值使用保留后项优先级的 `range_dispatch`；[弓与钓竿](classic-item-predicates.md) 限原版默认入口和已核实的 `pulling` / `pull` / `cast`。其他多谓词交叠、特殊姿态及服务器物品组件输入不能套用这一规则。把旧数字分派迁入现代组件 `floats[0]` 不会改服务器提供的物品。 |
| 47–55 | `paletted_permutations` 支持自定义分隔符；出生蛋拆成各实体精灵，鞍可从实体基础图拆分；无法替任意包推断其模板、染色或 UV。post pass 的 `program` → `vertex_shader` / `fragment_shader`，uniform 也有布局变化；需要看具体定义，不能只改一个字段名。 |
| 60.0 | [1.21.6](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6)：59.0 `8c43d0b4c` 的 `MobEffectTextureManager` 使用独立 `atlases/mob_effects.json`；60.0 `c48962f90` 的 `AtlasProvider.java:97` 将 `textures/mob_effect/` 加入 GUI，`Gui.java:477` 按 `mob_effect/<效果路径>` 查找并保留命名空间。因此只给 `directory.prefix` 或 `single` 输出的 sprite 路径加前缀，不动 PNG 或 `single.resource`。已有 `gui.json`、filter、未知结构留人工合并，不将旧过滤范围扩大至全部 GUI。 |
| 65–69 | 元数据换代之外，文本组件的 `atlas` 图像对象由 atlas ID/sprite ID 引用精灵（不是 `font/*.json` 的 provider 类型；`4536d1392:client/net/minecraft/network/chat/contents/objects/AtlasSprite.java:21–27`）。[1.21.9](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9) 列出此 schema。GUI 与其他图集之间不能全局替换；透传不等于证明文本渲染相同。 |
| 70–75 | 26.1 之前的 [1.21.11](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11) 引入独立 items atlas（无 mipmaps）；一个 item 模型的所有精灵必须同图集，一个 block 模型只用 blocks atlas。加上独立 celestials atlas 和 GPU 动画/UBO 变化。`02189ae7c:client/net/minecraft/client/resources/model/AtlasManager.java:43–51` 列出两独立 atlas。若旧 item 路径与 block 共用，必须检查完整的 parent/texture 引用图，不能见到 `item/` 就重分配或删除 blocks atlas 条目。 |
| 76–84 | [26.1](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1)：部分 `minecraft:special` 模型的内建变换抽到 item 模型，另有幼体装备/实体纹理。不能从静态旧模型唯一推断新渲染意图；仅迁移明确尺寸/姿态映射。 |
| 85–88 | [26.2 Snapshot 3](https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-3)（86.0）列出逐面的床纹理；[Snapshot 4](https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-4)（86.1）移除 beds atlas；[Snapshot 7](https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-7)（87.0）将告示牌/悬挂告示牌改成方块模型并移除 signs atlas。旧实体贴图到新 block face 贴图或 UI 背景是裁剪、旋转、合成，不是重命名；任意自定义 UV 或旧实体模型不可推断。官方 [26.2](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2) 提供 slicer 以核对结果，算法需自实现。 |
| 89–97.1 | 从 89.0 起 entity trim 在运行时配色，单纹理侧车用 `palette.base_palette`，item trim 仍经 `paletted_permutations`。早期 `trim_palette_replacements` 以旧 Palette ID 为键；[26.3 Snapshot 6](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-6)（94.0，`cfa124a02`）改成 `trim_overrides`，`when.material` 是 Trim Material ID，非 Palette ID；支持 material/pattern 首命中，`texture` / `palette` 至少其一。任意旧 atlas override 不保证能自动还原。此窗口还涉及 OIT core shader/post-effect 及透明破坏 overlay；不能泛化为 shader rename 或透传即兼容。 |

## 26.4 快照增量（只纳入识别/研究）

| 版本、格式 | 能验证的主要资源变化与当前边界 |
| --- | --- |
| [S1](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-1) 98.0 | OIT 从 wavelet 到深度分箱；删除 `OIT_WAVELET_RANK`，以 `OIT_NUMBER_OF_DEPTH_BINS` 替代 `OIT_COEFF_COUNT`，`OIT_COEFF_ATTACHMENT_COUNT` 改名 `OIT_TRANSMITTANCE_TARGET_COUNT`；宏语义/算法变更，非普适自动修复。 |
| [S2](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-2) 99.0 | `screenquad.vsh` → `screentriangle.vsh`；新增 `blit_clouds.fsh`、`sky_occluder.{vsh,fsh}`，`clouds.fsh` 不再承担 OIT。rename 候选只适用于确知旧 shader 行为可用于新目标，且依赖图可重写。 |
| [S3](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-3) 100.0 | 冰晶/冰柱块和物品 sprite、冰冻效果及好友 UI 资产；`31432c0b8` 合入许多 texture provider 实现变更，但类重构不证明格式字段或自定义内容要重写。 |
| [S4](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-4) 100.1 | `block/brittle_ice_{0–3}.png`、新唱片机底纹及更新其它面，冰类音效；保留不同的 minor，不能和 S3/S5 合并。 |
| [S5](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-5) 100.2 | 新 `block/chiseled_packed_ice.png`、`block/packed_ice_bricks.png`，修复原版冰柱模型 shade/template 问题。不据此改所有文件。 |
| [S6](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-6) 101.0 | 移除 `block/cube_top` 模型；引用此 vanilla parent 的自定义包需要证据化等价变换或人工处理，不能盲选 `cube_all`。新增雪砖 sprite 和冰晶音效；源码 item/sprite codec 内部注册表改造（`a6ccc5d75`），没有证据说明旧字段因重构本身失效。 |
| S7 102.0 | 冰柱五个贴图重命名见上，同时新增方解石相关 sprite 与唱片。 |
| [S8](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-8) 103.0 | `terrain.vsh` 顶点打包变更；OIT 用 subpass；水掩码成为独立目标；移除 `core/blit_depth.fsh`。用户 GLSL 需要适配接口/流水线，不能正则替换宣称正常。 |
| [S9/S10](https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-9) 104.0 | 新物品槽高亮 UI、`core/terrain.fsh` 支持独立 Improved Fog；`lang/<locale>.json` 中所有以 `menu.custom_options` 或 `menu.custom_screen_info` 开头的键仅 vanilla 包可覆盖，按 `startsWith` 而非按词分隔。`b75a64e11` 引入这一规则；`da28c03af:client/net/minecraft/client/resources/language/ClientLanguage.java:33,42–74` 仍在。应报告忽略项；删键或移命名空间无法恢复预期。官方 S9 页面顶部更新说明 S10 修复多人游戏崩溃，无独立技术格式改动；两提交的客户端上述资源加载目录无差异，`version.json` 资源格式相同，不代表所有游戏资产字节已核对。 |

## 验证清单与未知

- 元数据夹具：用最小空包分别走 zip/目录；精确 `[97,1]`、整数 97、`[97]`、`[97,0]` 与 97.1 的正反测试；65.0/65.1/65.2 及 100.0/100.1/100.2 各自隔离；拒绝旧 `pack_format`=97 且缺范围、现代-only 声明旧 `supported_formats`、缺单端、反序、负值、非法描述。对混合旧范围测试上界 64 vs 现代 max、主格式 14 和 15、旧客户端是否能解析生成产物。
- Overlay/过滤夹具：两条并行命中相同路径，检查 order、`.png.mcmeta` 独立覆盖、基础图回落、旧/现代混合、坏目录、zip/目录一致。filter 验证正则子串、两条规则交叉匹配、屏蔽低包但保留本包、元数据与内容分开筛选。
- 内容夹具：从人工造的彩色视频帧/纹理或最小几何开始，比对哈希、像素或资源解析结果；图库拆分、UV、Shader 编译、字体及自定义 namespace 必须用目标客户端视觉回归，JSON 结构判断不等于渲染成功。缓存键至少包含完整源/目标 format、规则版本与来源 overlay 选择。
- 未知：本地 Git 能确定格式节点和已列加载行为，但不含完整原版客户端资产。这份清单没有证明所有实际 schema 差异、资产改名或每个快照按内容无变化；未定位的边界不可当 verified identity。补齐时应以 Mojang 官方版本 manifest 固定客户端资产及 asset index，按相邻格式取路径/内容 diff 后结合对应发布说明和真实客户端验证；不要从未证明的两张近似 PNG 或文件名推导几何、像素、用途。未知资源可以原样保存在升级产物并显式报告，不能承诺完整更新。
