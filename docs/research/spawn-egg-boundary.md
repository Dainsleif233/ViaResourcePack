# 刷怪蛋（出生蛋）：资源包格式 52 → 53 的静态证据

**实际首变是 25w08a 的资源包格式 53.0。** 上一快照 25w07a（52.0）还是双层共享灰度 PNG + 每种蛋的两色 tint；53.0 起原版每种蛋使用一张独立 sprite。正式发行版两端是 1.21.4（46.0）和 1.21.5（55.0），不要把发行节点 55 误当首变格式。新版默认是有意重画；只为用户包实际覆盖的旧资源保留外观，不让未改蛋的包覆盖新版默认。若要求保真，先考虑保持旧双层、原 PNG 与原 tint；单张 8-bit PNG 在限定条件下可近似保留正/背面的 texel 外观，不能普遍保持侧面或逐像素完全一致。

## 1. 快照边界与制品

[既有格式研究](<docs/research/modern-pack-formats.md#L60-L69>) 的 52/53 ref 与本地 `D:/Code/jsucraft/MC` 对应；该研究原把蛋改动列在 [47–55 窗口](<docs/research/modern-pack-formats.md#L201>)。[Mojang 25w08a 公告](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w08a) 同时声明资源格式 53、重画新蛋和移除旧两图。按官方版本清单校验 profile SHA-1，再按 profile 校验客户端 JAR SHA-1 与大小；实际格式读 JAR 的 `version.json`，与各 ref 的 `client/net/minecraft/DetectedVersion.java:47–48` 相符。

| 客户端 | MC ref / resource / data | Profile SHA-1 | 客户端 JAR SHA-1；字节 |
| --- | --- | --- | --- |
| 25w07a | `02027c155` / **52** / 67 | [b4c167e119fa1410832d0233ab861ca361f9bd9b](https://piston-meta.mojang.com/v1/packages/b4c167e119fa1410832d0233ab861ca361f9bd9b/25w07a.json) | [1077e46be47132c81e235d925c6f90881f319a0f](https://piston-data.mojang.com/v1/objects/1077e46be47132c81e235d925c6f90881f319a0f/client.jar)；28,697,309 |
| 25w08a | `e8d206390` / **53** / 68 | [198f9d0c73b80776acdc595432404514aba1f093](https://piston-meta.mojang.com/v1/packages/198f9d0c73b80776acdc595432404514aba1f093/25w08a.json) | [e8340a01da734194c35fd35fbfa93e5bb96627b1](https://piston-data.mojang.com/v1/objects/e8340a01da734194c35fd35fbfa93e5bb96627b1/client.jar)；28,795,414 |
| 1.21.4 | `178543a33` / 46 / 61 | [c88775961e316f2be09859c54e13017660ee7770](https://piston-meta.mojang.com/v1/packages/c88775961e316f2be09859c54e13017660ee7770/1.21.4.json) | [a7e5a6024bfd3cd614625aa05629adf760020304](https://piston-data.mojang.com/v1/objects/a7e5a6024bfd3cd614625aa05629adf760020304/client.jar)；28,335,587 |
| 1.21.5 | `bbff168ef` / 55 / 71 | [d7eabdfb864d74c6d3942cdb864405778e948f28](https://piston-meta.mojang.com/v1/packages/d7eabdfb864d74c6d3942cdb864405778e948f28/1.21.5.json) | [b88808bbb3da8d9f453694b5d8f74a3396f1a533](https://piston-data.mojang.com/v1/objects/b88808bbb3da8d9f453694b5d8f74a3396f1a533/client.jar)；28,984,409 |

以下资源路径相对 JAR 的 `assets/minecraft/`；源码引用为 MC checkout 的 `ref:path:line`。

| 检查项 | 52（旧默认） | 53（新默认） |
| --- | --- | --- |
| `items/<id>_spawn_egg.json` | 81 个 ID；`minecraft:model` 指向 `minecraft:item/template_spawn_egg`，有序的两项 `minecraft:constant` tint | 同一组 81 个 ID；指向各自 `minecraft:item/<id>_spawn_egg`，无 `tints` |
| `models/item/` | 共享 `template_spawn_egg.json` 继承 `item/generated`；`layer0: item/spawn_egg`，`layer1: item/spawn_egg_overlay` | 共享模板删除；81 个独立模型继承 `minecraft:item/generated`，仅 `layer0` 指向各自 sprite |
| `textures/item/` | 共享 `spawn_egg.png`（索引色 + `tRNS`）和 `spawn_egg_overlay.png`（RGBA），均 16×16，非透明 RGB 都是灰度而不是纯白 | 删除旧两图；81 张 16×16 索引色 `<id>_spawn_egg.png` |
| 透明与图集 | 两图解码 alpha 都只有 0/255，无蛋 PNG 侧车 | 81 张都只有 0/255，也无蛋 PNG 侧车。两端同一 `atlases/blocks.json` 用 `minecraft:directory` 读取 `item`、输出前缀 `item/`；无独立 `atlases/items.json` |

`02027c155:client/net/minecraft/client/data/models/ItemModelGenerators.java:289` 生成共享模板入口、按序写两个 tint（鸡、苦力怕、猪见 :664、:667、:694）。`e8d206390:client/net/minecraft/client/data/models/ItemModelGenerators.java:84–87,659,662,689` 改为 `generateFlatItem(…, ModelTemplates.FLAT_ITEM)`。发行制品补证：46 的两张旧 PNG 与 52 **字节一致**，81 个 ID 和各自两色 tint 均相同；55 的 81 张新图与 53 **解码 RGBA 一致**。这不代表所有中间或后续版本都测过。

## 2. 渲染顺序：先每层出几何，再各自乘色

以下调用链参照 `02027c155`。52/53 的 `models/item/generated.json`、`atlases/blocks.json`、`shaders/core/rendertype_item_entity_translucent_cull.{vsh,fsh}` 和 `shaders/include/{light,fog}.glsl` 字节一致；上述 shader 与两个发行参照的对应源也一致。

1. **模型层。** `client/net/minecraft/client/renderer/block/model/ItemModelGenerator.java:36–42,64–80` 按 `layer0…layer4` 顺序（缺槽即停）。每层先生成覆盖整张纹理的正/背面（Z=7.5/8.5，正反 UV 镜像），再生成该层侧面；quad 的 `tintIndex` 是层号。`client/net/minecraft/client/renderer/block/model/SimpleUnbakedGeometry.java:27–40` 与 `client/net/minecraft/client/resources/model/QuadCollection.java:73–96` 保持这些无 cull-face 槽 quad 的提交顺序。0 = 底图及第一色，1 = overlay 及第二色。
2. **顶点色。** `client/net/minecraft/client/color/item/Constant.java:25–36` 以 `ARGB.opaque` 固定 A=255；`client/net/minecraft/client/renderer/item/BlockModelWrapper.java:69–84,90–107` 计算 tint 数组并提交 quad。`client/net/minecraft/client/renderer/entity/ItemRenderer.java:84–110` 查 `tintIndex`、各通道除以 255；`client/com/mojang/blaze3d/vertex/VertexConsumer.java:70–109` 写入 8-bit 顶点色。未配置该索引时取 `-1`（不透明白）；新默认单层无 tint，所以不是再给新图乘旧 tint。鸡的底/上色是 `#A1A1A1` / `#FF0000`，入口中是有符号 ARGB `-6184543` / `-65536`。
3. **采样、乘色、discard、雾。** 普通非方块蛋走 `Sheets.translucentItemSheet()`（`client/net/minecraft/client/renderer/ItemBlockRenderTypes.java:402–409`；`client/net/minecraft/client/renderer/Sheets.java:53–57,129–131`）。`client/net/minecraft/client/renderer/RenderType.java:74–76` 的纹理状态传 `TriState.FALSE, false`，经 `client/net/minecraft/client/renderer/RenderStateShard.java:159–170`、`client/net/minecraft/client/renderer/texture/AbstractTexture.java:28–36` 与 `client/com/mojang/blaze3d/textures/GpuTexture.java:149–166` 设置 NEAREST 且不采 mipmap；图集本身仍可构建 mip levels，内部格式为 RGBA8（`client/net/minecraft/client/renderer/texture/TextureAtlas.java:66`）。顶点 shader 先以 `minecraft_mix_light(…, Normal, Color)` 乘方向光，再乘 lightmap；片段以 `texture(Sampler0, texCoord0) * vertexColor * ColorModulator` 相乘，乘后 `color.a < 0.1` 则 discard，最后施雾（不改 alpha）。因此是取各层自己的 RGB 乘自己的 tint，而不是先拼灰度再统一着色。不要把光照、雾或背景烘进 PNG。
4. **同面覆盖。** `client/net/minecraft/client/renderer/RenderPipelines.java:64` 选择 `TRANSLUCENT`；`client/com/mojang/blaze3d/pipeline/BlendFunction.java:13` 用 SRC_ALPHA / ONE_MINUS_SRC_ALPHA 作 RGB 因子，ONE / ONE_MINUS_SRC_ALPHA 作 alpha 因子；`client/com/mojang/blaze3d/pipeline/RenderPipeline.java:399` 默认 LEQUAL、写深度、有背面剔除。`client/net/minecraft/client/renderer/MultiBufferSource.java:94–96` 在上传前排序，`client/com/mojang/blaze3d/vertex/MeshData.java:37–56,79,96–99` 求 quad 中心，`client/com/mojang/blaze3d/vertex/VertexSorting.java:27–36` 用稳定的 fastutil `IntArrays.mergeSort`。同方向重合的正/背面中心键相同，保留 0→1；透明 texel 被丢弃，若存活层 alpha 为 1，后面的不透明 overlay 覆盖 base，无需推断半透明混色。**这不是对侧面、不同几何或其他 pass 的排序承诺。**

## 3. 单 sprite 能保到什么程度

**可复验子集：** 静态等尺寸（先限双方 16×16）、已解码 8-bit RGBA、alpha 仅 0/255、明确的原版双层 generated 模型、同一正/背面采样网格、NEAREST level 0、无自定义 shader，且颜色调制不降低不透明 texel 的 alpha。仅比较乘 tint 之后、光照与 framebuffer 之前的 texel：

```text
chosen = overlay if overlay.a == 255 else base if base.a == 255 else none
none → (0,0,0,0)
chosen → per channel round(chosen.rgb * tint[chosen_layer].rgb / 255), A=255
```

逐 RGB 通道计算，用户的有色 PNG 不必先变灰。索引图须应用 `tRNS` 解码，不能用 RGB 值或透明色键猜 alpha。alpha=0 的上层不会带入隐藏 RGB；双透明时只在这个无插值、无 mipmap 的比较范围可以清零 RGB。保留双层时应保留原 PNG 字节。

| 鸡蛋样点（左上为 0,0） | 旧底图 RGBA | 旧 overlay RGBA | 旧式 CPU 单图 | 新默认 RGBA |
| --- | --- | --- | --- | --- |
| (7,2) | (127,127,127,255) | (76,110,105,0) | (80,80,80,255) | (0,0,0,0) |
| (5,3) | (0,0,0,0) | (128,128,128,255) | (128,0,0,255) | (0,0,0,0) |
| (0,0) | (73,161,148,0) | (73,161,148,0) | (0,0,0,0) | (0,0,0,0) |

- **旧与新默认不同。** 旧底图 68 不透明 / 188 透明，旧 overlay 44 / 212；同一格点内两个掩码完全不重叠，合为 112 不透明像素。81 张新默认中，**0 张**与旧式合成具有相同的 alpha 掩码，**0 张**可见 RGBA 相同。鸡新图 90 个不透明像素，苦力怕和猪各 134。新图不能当旧视觉金样，也不能由其反推旧图或旧 tint。
- **量化已有反证。** `rgb * tint / 255` 只有乘积整除 255 才能精确落入 8-bit 通道，否则近似取整误差至多半个通道单位；旧 81 个样本每个至少一处不整除。先量化再打光不等于浮点乘后打光再量化，不能声称 framebuffer 逐像素相等。
- **层的几何不是掩码的并。** `client/net/minecraft/client/renderer/block/model/ItemModelGenerator.java:83–215` 按各层尺寸把像素边映射到 0–16 模型单位，扫所有唯一帧并按方向/锚点归并跨度。`client/net/minecraft/client/renderer/texture/SpriteContents.java:188–195` 判侧面用 `alpha == 0`，不是片段阈值。按原版算法复算，底/上层独立产 37+34 个侧 span，合并掩码只产 21，且跨度集合不同。几何差异不证明每个角度都能看出，但不能保证手持、掉落、展示框或侧视；atlas 依赖的 UV 收缩（`client/net/minecraft/client/renderer/block/model/FaceBakery.java:77–85`，`client/net/minecraft/client/renderer/texture/TextureAtlasSprite.java:118–125`）、foil/其他 pass 也不在 texel 证明内。
- **尺寸和半透明。** 等尺寸更大图可做同样的索引运算，但没有逐例验证；不同尺寸仍各自覆盖完整 UV，有独立采样网格和侧面，不能盲目裁剪、缩放或取较大尺寸。半透明没有通用、与运行条件无关的 RGBA 合图：丢弃阈值、颜色调制、叠加或采样均会改变结果。例如两层 alpha 均为 0.5、统一运行 alpha 缩放为 `m` 且都过阈值，背景覆盖率为 `1-(1-0.5m)^2`，并非一个固定 sprite alpha 乘 `m`。验证范围不含 mipmap、插值、背景混色或自定义 GPU 路径。

## 4. 对用户包的可迁移范围、侧车与冲突

| 输入 / 目标 | 处理边界 |
| --- | --- |
| **46 / 52 → 53 / 55，已定位双输入的简单旧贴图包** | 优先保留源逻辑：原样保留两个 PNG，补足缺失的 generated 共享模板和受影响蛋的 `items/<id>_spawn_egg.json` 路由与原常量 tint（或复用等价已存在定义）。在 53、55 源码里，`client/net/minecraft/client/renderer/block/model/ItemModelGenerator.java:37,64–80`、`client/net/minecraft/client/renderer/item/BlockModelWrapper.java:92`、`client/net/minecraft/client/color/item/Constant.java:25–36` 仍支持多层、tint 和 opaque constant，图集仍接纳旧 `item/` 路径。这是可保留输入几何和颜色的静态依据，不是截图或跨版本绝对保真保证。 |
| **只替换一张旧图** | 另一张须从可证的**源**有效资源堆（含源版本原版 fallback）解析，不能当作空图，也不能用目标的每类新图代替。单包视角拿不到完整输入或优先级时保留文件并诊断；不默认打包 Mojang 资产，也不在运行期拉取研究用 JAR。 |
| **自定义模型或已有目标文件** | 显式目标入口/几何/独立纹理/侧车优先；不按 81 个 ID 对所有包无条件生成或覆盖。自定义 UV、选择树、动态 tint、foil、自定义 atlas、filter/overlays、跨包冲突、模组等需先解析有效的引用链；没有证据就标未知，不以重命名或改格式号当成迁移。 |
| **资源侧车 `.png.mcmeta`** | 原版这条 atlas 路径只选择 `animation`（`02027c155:client/net/minecraft/client/renderer/texture/SpriteLoader.java:44,111–115`，`02027c155:client/net/minecraft/client/resources/model/AtlasSet.java:47`）；`02027c155:client/net/minecraft/client/renderer/texture/atlas/SpriteResourceLoader.java:36–71` 读元数据、验证帧尺寸整除、无该节才以整图为单帧。帧顺序、时间、插值影响结果；边还按所有唯一帧生成。`02027c155:client/net/minecraft/server/packs/resources/FallbackResourceManager.java:218–220,226–238` 表明有效侧车可来自 PNG 所在包或更高优先级，且有 filter；不能只看本 ZIP。不要把一侧侧车直接配给合并结果；本次没有实测用户动画、OptiFine/PBR 或其他扩展，有效侧车、解析异常及未知扩展先不烘焙。若保留层，也仅在源/目标读取语义已确认时原路径保留对应侧车。 |
| **源包 shader 源、include 或其附属配置** | `02027c155:client/net/minecraft/client/renderer/ShaderManager.java:83–105,111–132` 从资源管理器读覆盖后的 shader 源，解析相对与命名空间 include。原版蛋依赖上述 core 程序及 light/fog；任一替换都可能改变 tint、采样、discard 或先后顺序。有效 shader/依赖无法证明原版等价就标未知并跳过烘焙；不删除或自动改写 shader、include 和配置，也不把透传当成兼容证明。 |
| **更早、更晚或未证实的包型** | [既有研究的 44–46 入口迁移](<docs/research/modern-pack-formats.md#L200>) 是独立边界。代表性更旧源 `3015bd18b:client/net/minecraft/client/color/item/ItemColors.java:57` / `3015bd18b:client/net/minecraft/world/item/SpawnEggItem.java:127` 和 `7d9ca125f:client/net/minecraft/client/color/item/ItemColors.java:70` / `7d9ca125f:client/net/minecraft/world/item/SpawnEggItem.java:126` 是运行时颜色表；鸡、苦力怕、猪与 52 样本一致（两个 ref 的 `client/net/minecraft/world/item/Items.java:1094,1097,1124`、`client/net/minecraft/world/item/Items.java:1210,1213,1240`）。不能据此复制 52 的整表给任意早期包。后续 [独立 items atlas](<docs/research/modern-pack-formats.md#L204>) 或渲染改版不由此证明，须按源版/目标版分别核对。 |

**建议先验收** 46/52 已知原版语义且双输入已定位、无有效自定义 shader/atlas/model 冲突的纯静态覆盖包；按原版层结构保留颜色与几何，已有目标文件优先，对输入缺失和冲突诊断。单图只作为明确接受正/背面近似的可选路径；没改旧蛋的包用新原版默认。

## 5. 复算

在仓库根目录，Python 3.13 + Pillow 11.3.0：

```sh
python -B build/viarp-spawn-egg-audit/fetch_official.py
python -B build/viarp-spawn-egg-audit/audit.py
```

[下载校验脚本](<build/viarp-spawn-egg-audit/fetch_official.py>) 仅用 [Mojang 公开版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 与官方 CDN；[静态审计脚本](<build/viarp-spawn-egg-audit/audit.py>) 重验 SHA-1，遍历 81 套布局、侧车缺席、透明掩码、侧 span、示例颜色及 CPU 合成；详见 [本次输出](<build/viarp-spawn-egg-audit/audit.txt>)。审计目录由 Git 忽略；未运行共享 Gradle、未启动客户端、未读取真实凭据、未做 GPU 测试。

## 已实现的保真子集

进入格式 53 时，只有包内两张旧共享纹理都存在、通过 PNG 解码和动画帧预检，且没有影响该模型的同包图集覆盖、过滤器或自定义模板，才为未显式定义的原版蛋补充物品入口。模板继续使用 `item/generated` 的两个图层；81 个原版 ID 对应 162 个旧有常量 ARGB 值。原图与侧车逐字节保留，不将两层合成为新版重绘 PNG。缺一层、路径冲突或旧动画不可核验时记录诊断，不生成部分入口。

独立 Java 8 夹具检查正反 18 种情况；对 Mojang 26.3 原版 codec 的独立检查实际解析全部 81 个入口、染色、几何、两个精灵及动画侧车。这不是客户端画面等价验证。53 的 blocks atlas、97.1 的 items atlas 都能按原 ID 提供这两张同域精灵。本包单独转换，不能证明其他活动包不会覆盖模型、纹理或图集；分散到不同包的两张旧图不会自动合并。
