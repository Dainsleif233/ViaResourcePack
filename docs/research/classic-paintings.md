<!-- SPDX-License-Identifier: GPL-3.0-or-later; Copyright (c) 2026 Dainsleif -->

# 经典绘画：旧图集到 1.14.4 独立纹理

**结论：** 1.12.2 和 1.13 使用字节相同的 256 × 256 绘画图集。1.14.4 的全部 26 张正面和 1 张背面，均可按旧渲染器实际使用的矩形直接裁出：**27 个尺寸完全一致，32,256 个 RGBA 像素无差异**。磁盘路径是单数的 `assets/minecraft/textures/painting/<id>.png`；复数 `textures/atlas/paintings.png` 是游戏拼装后的图集 ID，不是资源包输入路径。

[Mojang 19w07a 公告](https://www.minecraft.net/en-us/article/minecraft-snapshot-19w07a)明确记载 “Paintings are now broken up into individual textures”。这发生在格式 4 **内部**，不是 1.13 首次进入格式 4 时；1.13 仍消费旧图。以下具体路径、尺寸和裁切关系以 1.14.4 正式版与两个旧端 JAR 为证，不把公告当作快照字节码证明。

## 制品与校验

复用[语言迁移已取证的制品](<flatten-language.md#L10-L26>)，从 [Mojang Launcher 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)核对正式版 JSON 的 SHA-1，再用 JSON 的 `downloads.client` 校验客户端 JAR 的字节数与 SHA-1；另计算 JAR 的 SHA-256。检查 ZIP 无重名条目，直接读取条目和 `javap -p -s -c`，无需启动游戏。

| 版本 | JAR 字节数 | JAR SHA-1 | JAR SHA-256 |
| --- | ---: | --- | --- |
| 1.12.2 | 10,180,113 | `0f275bc1547d01fa5f56ba34bdc87d981ee12daf` | `8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f` |
| 1.13 | 16,033,625 | `c0b970952cdd279912da384cdbfc0c26e6c6090b` | `d807a5ed9428b6c16b64888fd2b7fa76dcf5c55d06f96f7c0c47762c3fc3e26e` |
| 1.14.4 | 25,191,691 | `8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9` | `b3b2a798e2d67b566008fe4a03767ae2c7ff3f8c7ba6751e7b71fc7299672d0a` |

1.14.4 的 [Mojang 客户端映射](https://piston-data.mojang.com/v1/objects/6073e4ba6949217eb708c4512be2ccc1850a603f/client.txt)亦按 `downloads.client_mappings` 校验：4,709,268 字节，SHA-1 `6073e4ba6949217eb708c4512be2ccc1850a603f`，SHA-256 `2dd53a5e70ba493cf6e33c0fc52bbdf4c57f9429c7a13842565aa825fd44d910`。

1.12.2 和 1.13 在 `assets/minecraft/textures/painting/` 下各只有 `paintings_kristoffer_zetterstrand.png`；两份 **编码后的 PNG** 都是 77,687 字节，SHA-256 `97dd3c0be8aea81534b7bc04a223af05e9035a1ee6b688708c1ba031980f4960`，且直接逐字节比较相等。`art/kz.png` 是仓库已有[旧路径映射](<../../src/main/resources/viarp/migrations/legacy/texture-paths.tsv#L21>)的输入名，不是这两个 JAR 的条目。1.14.4 同一目录恰有下表 27 个文件，无旧图集、无侧车。

## 消费路径与尺寸

- **1.12.2：** `cai` 的静态初始化绑定 `textures/painting/paintings_kristoffer_zetterstrand.png`；绘制调用把 `acd$a.C/D/E/F`（旧绘画枚举注册的宽、高、X、Y）传入 `cai.a(Lacd;IIII)V`。**1.13：** `czi` 绑定相同路径，`alz.b/c/d/e()I` 的四项传入 `czi.a(Lama;IIII)V`。两端按每 16 像素分片绘制，正面 UV 以 256 归一化（各方法偏移 155–221）。1.14.4 的 `EntityPaintingMotiveFix`（`acb`）对历史 Motive 小写化，并将 `donkeykong`、`burningskull`、`skullandroses` 转为下表的下划线写法。按此对齐后，两份旧注册表的 26 个正面矩形完全一致。
- **背面不是靠寻图猜的。** 两端绘制方法偏移 377–499 的四个背面顶点（法线 +Z）送入 UV 端点 `u=0.75..0.8125, v=0..0.0625`，即 X=192、Y=0、宽高=16；边缘采样中 `0.001953125`、`0.7519531` 是半 texel 内移，不是另一个矩形。旧原版 PNG 中相同像素在 X=192/208/224/240、Y=0/16/32/48 的 16 个网格位置重复；**只能用实际 UV 选定 (192, 0)**，否则旧自定义图包可能裁错。
- **1.14.4：** `PaintingRenderer`（`dtg`）在渲染调用偏移 68–100 获取 `PaintingTextureManager`（`dxs`），把当前 `Motive`（`atp`）的宽高与 `dxs.a(Latp;)Ldxb;`（正面）、`dxs.b()Ldxb;`（背面）一起传给 `dtg.a(Latq;IILdxb;Ldxb;)V`。26 个 Motive 注册的宽高与旧端一致，注册不再携带 X/Y。正面按纹理坐标插值；背面使用独立精灵的 UV 边界，侧边另取精灵跨度 1/16 处的样本（`dxb.a(D)F` / `dxb.b(D)F` 参数为 `1.0`）。像素相同不代表新旧顶点 UV 完全相同。渲染器绑定的是 `TextureAtlas`（`dxa`）的 `h`（`textures/atlas/paintings.png`）。
- **1.14.4 的实际输入前缀：** 客户端 `cyc` 将 `dxs` 注册为资源重载监听器。`dxs` 传入 `textures/painting` 给 `TextureAtlasHolder`（`dxv`），枚举 `fn.y` 注册表键和 `new qv("back")`；`qv` 的默认命名空间是 `minecraft`。`dxa.b(Lqv;)Lqv;` 保留命名空间，将路径拼成 `prefix + "/" + spriteId + ".png"`，由纹理装载代码以 `xi.a(Lqv;)Lxh;` 向资源管理器取实际 PNG。旧图集名不在这些输入中。

## 逐像素对照

对每一行，先按上面的字节码得到旧矩形 `(x, y, width, height)`，再比对两端 PNG 解码成 8 位 RGBA 的尺寸与完整像素序列。裁切左上角为原点，不翻转、不插值、不跳过 alpha 为 0 的像素。SHA-256 的输入是按行从左到右的 RGBA 字节，不是 PNG 编码；每行的哈希在旧裁片与对应的 1.14.4 PNG 上都相同。本批原版裁片本身全部不透明，透明通道保留另用自定义输入验证。

| 1.14.4 文件 | 旧左上角 X, Y | 宽 × 高 | 两端解码 RGBA SHA-256 |
| --- | --- | --- | --- |
| `kebab.png` | 0, 0 | 16 × 16 | `816131bfc979b3ef4132e968e92da3dd3cc971780728fddec79c40b7704ddd0d` |
| `aztec.png` | 16, 0 | 16 × 16 | `e2247eed79c1a32316b8c7762c864ae3e7d37e8e603c2f349c0c58b217a01d73` |
| `alban.png` | 32, 0 | 16 × 16 | `63a3c466f3722c6a74296796ec1839406e701b1d8a02d5aac23f4203342c3fa0` |
| `aztec2.png` | 48, 0 | 16 × 16 | `ee288ee88b3d8c45b8a78b3b4516482e91a4d986f6e897dee113927648bbec7b` |
| `bomb.png` | 64, 0 | 16 × 16 | `1d2068231a2c505f3d893512e071d4eefe871e10171c32eba023b2a32e50ce56` |
| `plant.png` | 80, 0 | 16 × 16 | `e20b8c1b7bf9a64d93edaab01a2bd7580571f1cf25837682776f2bee1f516b16` |
| `wasteland.png` | 96, 0 | 16 × 16 | `5af68a826fbd5d22dc66b7eff598bf7336dab3bb1cc2be5cad3e6bda9760ddb4` |
| `pool.png` | 0, 32 | 32 × 16 | `b16b3d98da2593b5113f5a5c11125b321e3584f8c365ebc686f432ff56ccbeac` |
| `courbet.png` | 32, 32 | 32 × 16 | `7e179fe31118f4c6f8ca898fbad522f30d05a038d939e05ef03c373e9c91d0bb` |
| `sea.png` | 64, 32 | 32 × 16 | `836b13f8f3b1705c69a681a26f655535c7c1599b26f280159f3e18f4cffed719` |
| `sunset.png` | 96, 32 | 32 × 16 | `3816f2c878ddd274d7f2be15950f23091660a15409e2fcc1aa87ecb7971d5bd8` |
| `creebet.png` | 128, 32 | 32 × 16 | `2761fc641b09650f49fa5665516455dcc31606982fc581e1704724da8cfbda60` |
| `wanderer.png` | 0, 64 | 16 × 32 | `1b28118b9056e58a9271c25fa006a7ac83ebb614c59c211278c862eb2eac6fca` |
| `graham.png` | 16, 64 | 16 × 32 | `2fbf3db5321e2d351dafa6b0a031f96e41374bbbcefd67c832ebcb5a65e6e12c` |
| `match.png` | 0, 128 | 32 × 32 | `eea48630966ad744755f0a11ce821d8176b2f7872517a6c9cb370eefb570aad0` |
| `bust.png` | 32, 128 | 32 × 32 | `650d28f897cb6edb9e15fd4c1fcf3b5735147d3ae59e477e91eea8fa130c69bf` |
| `stage.png` | 64, 128 | 32 × 32 | `eba24603d69c097c9b5754c81bd8361a0417f3bc523833befbd6316e9f495e45` |
| `void.png` | 96, 128 | 32 × 32 | `efd0b605ad856d34505061da89470360bea1f0ce4da7b7c4c6bef85c8a2726d4` |
| `skull_and_roses.png` | 128, 128 | 32 × 32 | `998ab74c834e2945df6a745c093854e2941360f950a644766938fd539c3e176d` |
| `wither.png` | 160, 128 | 32 × 32 | `652a8b95e06257f6632a19cecd711ab938d2a1535784adf572159b7dd02adb6e` |
| `fighters.png` | 0, 96 | 64 × 32 | `6073eab973a92967107f09c82be3741405115ed0d7f5c09d87f1460a279faaf0` |
| `pointer.png` | 0, 192 | 64 × 64 | `1a7509ccf9bc748d61d3b372e7e5507160e8f1143f0dafaf655069174895794b` |
| `pigscene.png` | 64, 192 | 64 × 64 | `8d7e82641df48248834251b957e84b5c323c3365e28074f0636a3fe9532ae539` |
| `burning_skull.png` | 128, 192 | 64 × 64 | `7ecd7526aa7a1e685321bde931087839eac45affbde76e36d42a1e6bbba28059` |
| `skeleton.png` | 192, 64 | 64 × 48 | `9f5d48b9d174d5b43c6137123aefae088e2b3a409ec762a8f1a16e90474eb75b` |
| `donkey_kong.png` | 192, 112 | 64 × 48 | `2e3a02582aab25aeee8c40a34df51929006b3c9e38b8dde6ed63138d9425554e` |
| `back.png` | 192, 0 | 16 × 16 | `be86861ecd46157949f8dd3a615bff76504a2495c669e1d1c0c264ecae07880a` |

## 规则边界和验证

[绘画子规则](<../../src/main/java/top/syshub/viarp/convert/ClassicPaintingUpgrades.java>)只接收工作副本中的确切原版图集路径；更早的 `art/kz.png` 仍由已有命名迁移负责。使用 `UpgradePng.scaledSheet` 验证图集尺寸为 256 × 256 的等比整数倍并应用其读图上限，按同倍率直接裁切。只补缺失目标：显式 PNG、目标侧车及其大小写别名优先，写入使用 `CREATE_NEW`。源图集从不删除；遇到旧图集侧车（无法安全推断如何拆分）、无效 PNG 或不合布局时，保留输入并告警。无确切来源则不猜测其他路径或项目自定变体。[调度](../../src/main/java/top/syshub/viarp/convert/ResourcePackUpgrader.java)在穿过格式 4 时调用；如果格式 4 为起点，则在下一个升级节点 5 补做。不会为 4→4 启动转换，所以 1.13 图集包在同为格式 4 的 1.14.4 上仍需手工拆分；这是严格按格式单向升版的边界。

独立 Java 探针用 JDK 25 以 `-source 8 -target 8` 编译，不使用 Gradle。对两份旧原版输入分别生成 27 个 PNG，并用 ImageIO 与 1.14.4 逐像素复核 32,256 个位置；源 PNG 字节不变，重跑生成字节不变。512 × 512 自定义图集的 RGB 和 alpha 随坐标变化；保留 5 处显式目标后，其余 22 张、123,904 个像素按 2× 坐标核对相等（包括全透明像素的 RGB）。另检查缺来源、来源大小写不符、源侧车、矩形布局和无效 PNG。
