# 旧版字体：首个 provider 读者与格式 15 的保守迁移

## 锚点与无操作边

- 经 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 核对官方客户端 SHA-1：1.8.9、1.9、1.10.2、1.11、1.12.2 的原版读取器都使用 `textures/font/ascii.png`、`textures/font/ascii_sga.png`、`font/glyph_sizes.bin`、`textures/font/unicode_page_%02x.png`。对应类分别为 `ave/avn`、`bcf/bct`、`bcx/bdl`、`beq/bfe`、`bib/bip`。格式 1→2 和 2→3 没有字体路径或 provider 结构迁移；PNG 重编码、原版图案更新不等于自定义包格式变更。
- **格式 4 内部变化**：1.13-pre5（`f3262055c586a075fc84f9d4bc76b3cf1a72d69c`）仍由 `cep/cfg` 读取固定位置；pre6 处于过渡态；pre7（`0b5d9df7bc2d0e4fd00d0bf7cf4409b999567497`）首次确认原版提供 `font/default.json` 和 `font/alt.json`。1.13 正式版 `chu/chv/cih` 与 1.14.4 `dar$1/das/dbc` 均加载 provider 栈并以首个可用字形为准；强制 Unicode 时对默认字体只保留旧 Unicode provider。1.12.2→1.13 的 222 个 Unicode PNG 和 65536 字节宽度表逐字节相同，旧路径继续被引用。
- 1.12.2 硬编码 ASCII 字符表的全部 247 个非 NUL 项，在 1.13/1.14.4 的 ASCII provider 中仍处于同一槽位。裸 `ascii.png` 的普通字形不需搬运，例如 `A`（槽 65）。但旧版默认普通模式的 `é`（槽 130）、`α`（槽 224）在新默认字体分别由前置的 `accented.png`、`nonlatin_european.png` 遮蔽；1.13 共 76 项重叠，1.14.4 共 81 项。不应把 PNG 仍被读取说成所有字形都保留。仅在读者、顺序、实际修改槽和显式定义冲突都可证明时，才可能用一个引用原图、只暴露遮蔽槽的 bitmap provider 补充；本规则不做该推断。
- 裸 `ascii_sga.png` 的 52 个现行字母槽仍读取相同图；1.11 到 1.14.4 原版 PNG 字节相同。旧第二个 `bip` 仍用同一旧字符表，但 `alt.json` 只列 A–Z/a–z；普通字母无操作不代表自定义非字母槽无差异，尚未证实旧版有实际游戏调用会读这些非字母槽。1.13→1.14.4 仍同属格式 4，不造一个迁移边。

## 格式 14→15：原版读取器

- [23w16a 客户端](https://piston-data.mojang.com/v1/objects/10eb5a16d52959140ea2dba2fc4fb4bf177cacfa/client.jar)（格式 14）及[对应官方映射](https://piston-data.mojang.com/v1/objects/1bd8cf2208a00a93b2a02b1ebcf1e3b537865f33/client.txt)：`erd` / `LegacyUnicodeBitmapsProvider`，`erd$a` 读取至多 65536 字节；`erd$b` 源行 238/243/248 分别是逻辑 advance 与阴影、粗体偏移，内部类 `erd$b$1` 源行 256/261/266 是采样倍数和像素尺寸。宽度字节 `0` 表示不提供该字形；非零字节的高半位是左列、低半位是包含在内的右列。页为 256×256（16×16 个 16×16 字形）；采样倍数 2，advance 为整数除法 `(right - left + 1) / 2 + 1`，粗体和阴影偏移均为 0.5。原版只上传左右边界内的 16 行有效像素。
- Mojang [23w17a 发布说明](https://www.minecraft.net/en-us/article/minecraft-snapshot-23w17a) 和[客户端](https://piston-data.mojang.com/v1/objects/670d97ddfe62983a7bfa7c6c6f502d6157735bf8/client.jar)（首个格式 15）移除 `legacy_unicode`、宽度表和全部 222 张 Unicode 页。裸 `ascii.png` 与 `ascii_sga.png` 原样保留并继续被引用。`default` 的引用顺序为 `include/space`、`include/default`（三种 bitmap）、`include/unifont`；`uniform` 只引用 `include/space`、`include/unifont`。
- [1.20.1 客户端](https://piston-data.mojang.com/v1/objects/0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838/client.jar) 同属格式 15。JAR 内 `font/include/unifont.json` 为空定义，实际原版附加资源来自 [assets 索引 5](https://piston-meta.mojang.com/v1/packages/7397e41cb1f5b1b9e14bfbd27ba44fc98b75727b/5.json) 的 [provider 文件](https://resources.download.minecraft.net/f8/f8d4768707b20359f2f7660346bd3a84b6ee27b1) 与 [Unifont ZIP](https://resources.download.minecraft.net/a6/a661b5622172ea1ce1fa9ec78fad484d118e5689)。23w17a、1.20.1 当前清单都指向该索引，核验时 ZIP 的字体版本为 15.0.06，不把快照发布说明标注的 15.0.01 当作抓取结果。
- 额外读取官方 1.20.2、1.21.1、1.21.11、26.1、26.3 的 JAR：上述两个默认字体仍引用 `include/unifont`；所检 1.21.1 及更新版本的 `include/default` 引用增加 `uniform: false` 过滤器，但 `include/unifont` 不受该过滤器影响。这里只核对入口存在性，不据此宣称每个后续版本运行时完全相同。
- 本地 MC 仓库 1.20.1，ref `3015bd18b`：`client/net/minecraft/client/gui/font/FontManager.java:104–128,173–215,284–287` 读取同一字体定义的完整资源栈，按优先顺序展开引用；任一无法解码的 provider 会使该资源包贡献的整张列表无法加载。`client/net/minecraft/client/gui/font/FontSet.java:113–143` 在同一字体中首个非空字形胜出；`client/net/minecraft/server/packs/resources/FallbackResourceManager.java:84–98` 定位最高优先级的单一资源。
- 同 ref 的 `client/net/minecraft/client/gui/font/providers/UnihexProvider.java:88–104,324–367,407,433–465,527–533`：ZIP 中 `.hex` 的每行可含 16×16 点阵，输出仅有透明和不透明白色，像素高度、采样、advance 和偏移与旧 reader 一致。`size_overrides` 的左右列均包含边界；拒绝 `from == to`，会消费范围内**文件确实列出**且未被较早区间消费的字形。按码位降序生成；单点用 `[cp, cp+1]`（若相邻端点落于代理区则跨到 U+E000），既保持边界也不凭空生成相邻字形。

本地 MC 仓库 26.3 `634debadb` 的 `client/net/minecraft/client/gui/font/providers/UnihexProvider.java` 仍读取 `hex_file`、`size_overrides`，拒绝 `from >= to`；官方 26.3 客户端 JAR 的 `font/default.json`、`font/uniform.json` 仍引用 `minecraft:include/unifont`，普通模式在空白和 bitmap 之后查它。

## 当前规则的支持域

1. 显式 provider：仅在所需宽度表和全部非零宽度对应的页都由同一输入包明确供应、PNG 恰为 256×256、颜色模型样本不超过 8 位、有效裁切内可见像素全为不透明白色时原位替换，且保留兄弟 provider 的顺序。一个定义中有未通过预检的旧 provider 时不部分改写该列表。颜色或半透明不是调阈值可以保真的内容；透明像素的不可见 RGB 与裁切区外像素不编码。
2. 隐式原版覆盖：仅在原版路径下表和依赖的页均本地齐备、没有显式 `default`、`uniform` 或相同回退目标时，给 `include/unifont` 增加同一索引字体的一层 provider，不将其提前到默认 ASCII/空白之前，也不替换官方 Unifont。未列码位继续走目标 fallback，不把旧字宽为零的码位解释成作者要求在新版中屏蔽原版字形。
3. 不从运行中的其他包、下载缓存或旧版原版推断缺失尺寸/页。缺表/页、无效数据、预生成目标冲突不覆盖原文件。旧输入继续留在工作副本；生成 ZIP 只含输入包字形，不分发 Mojang 图案、字体或宽度表。其资源 ID 包含 HEX 内容的 SHA-256，避免不同迁移包栈中相同序号的资源互相覆盖。自定义 resource filter、其他包对路径/引用的覆盖、后续读者变动及完整运行时排版不从静态迁移证明。

## 对照样本

在内存中用真实格式 14 JAR、1.20.1 JAR、官方索引层和一张合成自定义页验证首字形命中。旧 U+4E2D（中）的表值 `0x0f` 选取 `unicode_page_4e.png` 的半开矩形 `(208,32)–(224,48)`，有 48 个不透明白点，宽度 advance 9；其 bitplane 与索引中的目标原版 Unifont 一致。在字形内原透明位置 (2,2) 填一白点，生成 ZIP 只列 U+4E2D；范围 `[U+4E2D,U+4E2E]` 保持 advance 9、49 白点。在默认/强制 Unicode 两组中，U+4E2D 都选入新增字形，未列的 U+4E2E 仍选官方 Unifont、无条目的 U+E000 不被生成，默认 `A` 仍选 ASCII，U+0020 仍选 space。另用不透明红色和 50% alpha 白色验证不可还原输入。证据是字节和查找顺序的对照，不是对游戏渲染器运行截图的比较。

另用隔离的正式客户端 26.3、Java 25 和 Fabric Loader 0.19.5 实测：选中一个只改 U+4E2D 的格式 14 包，运行时升级到 97.1；原版 `UnihexProvider` 同时读到自生成的 `glyphs.hex` 和原版 Unifont，资源重载及声音/图集初始化成功。未对游戏画面做字形逐像素比较。
