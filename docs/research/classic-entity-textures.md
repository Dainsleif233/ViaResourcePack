# 格式 4→5：旧箱子、旗盾图案与潮涌核心眼

## 官方依据

- [Mojang 1.15 发布说明](https://www.minecraft.net/en-us/article/buzzy-bees-out-now-in-java) 把资源包格式升到 5，并说明会尽量适配旧包。[1.14.4 客户端](https://piston-data.mojang.com/v1/objects/8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9/client.jar)、[1.15 客户端](https://piston-data.mojang.com/v1/objects/7b07fd09d1e3aae1bc7a1304fedc73bfe5d81800/client.jar)、[1.16 客户端](https://piston-data.mojang.com/v1/objects/228fdf45541c4c2fe8aec4f20e880cb8fcd46621/client.jar) 分别核对完整 SHA-1 为 URL 中的散列；各 `version.json.pack_version` 分别为 4、5、5。下述 94 个格式 5 样本在 1.15 与 1.16 ZIP 中的 PNG 字节逐一相同。
- [1.16 Mojang 客户端映射](https://piston-data.mojang.com/v1/objects/9539840a7dbea682b305858fe138bd03ee7823cf/client.txt)（完整 SHA-1 `9539840a7dbea682b305858fe138bd03ee7823cf`）将 `PackResourcesAdapterV4` 映到 `emj`。使用 JDK `javap -p -c -classpath <1.16-client.jar> emj` 静态读 `fixLeftChest`/`fixRightChest`/`fixSingleChest`/`copyRect`/`fixPattern`/`fixConduitEyeTexture`，不加载/运行 Minecraft 类。PNG 对照是独立校验，不从相似图块倒推 UV。

## 实现与同路径覆盖边界

所有路径下表相对于 `assets/minecraft/textures/entity/`；只处理精确 `minecraft` 路径。原输入包不修改。双箱 PNG 与旧侧车在输出包中也按原路径保留；同路径、已确认必须升级的图片只替换隔离工作树中的活跃纹理，不增加虚构资源路径。

| 资源 | 坐标、选取与限制 |
| --- | --- |
| `chest/{normal,trapped,christmas}_double.png` | 旧 `128s×64s` → `_left.png`、`_right.png` 各 `64s×64s`。各 15 个矩形，保留完整 RGBA，未覆盖的区域清为透明；分别检查两个目标，不覆盖已有目标（含大小写冲突）。旧图不删除。 |
| `chest/{normal,trapped,christmas,ender}.png` | 输入、输出同为 `64s×64s`，18 个坐标变换；新旧的非透明外框相同，不能靠尺寸、文件名或透明边界判别。只迁移像素与官方旧图完全相同的样本或逐像素的严格最近邻整数倍复制；不动已知新版，也不猜自定义单双混合包。没有旧 `ender_double` 规则。 |
| `banner/<图案>.png`、`shield/<图案>.png` | `fixPattern` 需要同包配套的 `banner_base.png` 或 `shield_base.png`，且尺寸相同，均为 `64s×64s`。旗帜处理矩形 `[0,42)×[0,41)`、盾牌 `[2,12)×[2,22)`，以 `s` 缩放；输出 RGB 取同坐标 base 的 RGB，alpha 取**旧图案的红通道**，不是旧图案的 alpha 或 base alpha；矩形外为全透明黑。底图本身不迁移。只匹配 40+40 个原版名称和可确证的旧图像素（或严格整倍复制）；自定义图案的新旧可完全同尺寸，原版兼容器根据整个包的格式直接处理，但离线规则不假设混合包每张都旧。不从低优先级包猜底图。 |
| `conduit/{open_eye,closed_eye}.png` | 旧 `8s×8s` 原像素放到 `16s×16s` 左上 `(0,0)`，余下画布全透明黑；不是裁剪，也**不是**图像放大。旧奇数倍率无法当作新布局的整数倍率，允许；若尺寸能同时解释成旧 HD 或新图，只迁移官方旧样本的严格整倍复制。右侧/底部透明仅用于抑制歧义告警，不用于转换。 |

像素输入经 [UpgradePng](../../src/main/java/top/syshub/viarp/convert/UpgradePng.java) 检查 PNG 签名与块结构、长度 ≤32 MiB、边长 ≤4096、像素数 ≤4096²、严格整倍比例；潮涌眼输出再限制边长 ≤4096。最近邻指纹比较验证每个整倍像素块内所有颜色（包括透明 RGB）一致，不用随机或单点抽样。缩放是全部矩形坐标、宽高乘 `s`，逐实际像素拷贝/镜像，不重采样。

### 侧车与未处理部分

- 1.16 `FallbackResourceManager`（`aaw`）按目标 ID 拼 `.mcmeta`；`PackResourcesAdapterV4` 的双箱映射只包含 PNG ID，并不将旧双箱侧车映到两半。`TextureAtlas`（`elu`）读取的是 `AnimationMetadataSection`（`emv.a`），而 `SimpleTexture.TextureImage`（`elr$a`）才读取 `TextureMetadataSection` 的采样设置。`animation` 默认/显式帧尺寸在拆图、变换 UV、变更画布后不能据旧值推定。官版验证的箱子、旗盾图案、眼睛条目没有侧车；故双箱不新拷贝侧车，同路径迁移发现侧车就保留原图和侧车。独立提供的目标侧车一律不覆盖。
- `conduit/wind{,_vertical}.png` 的旧/新官版图是 64×1024 且 PNG 完全相同，1.15 增加动画侧车（`width=64, height=32, frametime=3, frames=0…21`）；但 `PackResourcesAdapterV4.hasResource` 特意屏蔽旧 wind PNG；任意自定义旧风图的动画语义不能由路径推断，不自动迁移。核心其他 PNG 与末影龙不在本规则；铁傀儡路径交给经典迁移入口。

## 集成验证坐标与散列

坐标原点左上，排他边界；`Y` 表示在矩形内翻 Y，`XY` 表示同时翻 X/Y。完整 15+15+18 表见 [ClassicEntityTextures](../../src/main/java/top/syshub/viarp/convert/ClassicEntityTextures.java)。以下坐标用于放不对称值，不能只验证对称方块：

| 目标 | 旧像素 → 新像素 |
| --- | --- |
| 左箱盖 | `(29,13)→(29,0)` 与 `(43,0)→(43,13)`（Y） |
| 左箱侧 | `(43,18)→(43,14)` 与 `(29,14)→(57,18)`（XY） |
| 右箱侧 | `(28,18)→(43,14)` 与 `(14,14)→(57,18)`（XY） |
| 2× 左/右箱 | 分别 `(87,37)`、`(57,37)` → 各自 `(86,28)`（XY）；原图 `(0,110)` 在这两张输出的同坐标仍为 `(0,0,0,0)`。 |
| 单箱锁扣 | `(1,0)→(3,0)`（Y）；`(2,4)→(4,1)`（XY）。 |
| 2× 潮涌核心 | 原 16×16 的 `(15,15)→(15,15)`；输出 `(16,0)`、`(0,16)` 为 `(0,0,0,0)`。 |
| 原版遮罩 | `banner/stripe_left` 在 `(7,7)`：旧图案 `(191,191,191,255)`，输出 `(241,241,241,191)`；`shield/stripe_left` 对应旧 `(0,0,0,255)` → 新 `(242,242,242,0)`。 |

比较哈希使用从左至右、从上到下、非预乘 `R,G,B,A` 每通道 8 位，不是 ImageIO 重编码的 PNG 文件哈希。所有同路径候选的已知旧、新像素散列在 [rgba-sha256.tsv](../../src/main/resources/viarp/migrations/classic-entity/rgba-sha256.tsv)。常用夹具：

| 样本 | PNG SHA-256 | RGBA SHA-256 |
| --- | --- | --- |
| 旧 `chest/normal_double` | `6da0f6af16da2b92fa4bc30b4c9ba3206a5819e6b82ea38790ac89ee3cee2d17` | `b90be838c3270797fd813f70f3567f55c47c58de07d5043615af42e61a771ad5` |
| 新 `chest/normal_left` | `a8ebd8071ceba51a94f9ef910b34e384460f0ebd63ae8029e6252f419496d6e5` | `d44dec3d47f588226f2bd57c7e16355ed5efa03cebb4fa4b5a473e757f758b43` |
| 新 `chest/normal_right` | `37721f67df6e7bebf2a6a6a75c45686f39cc5c7737fc5dcd6e5aa0d31ebdb33a` | `fb4901500ccca8b7675a5fa684f3ce72b4bbb585f984e723988988be06bc0277` |
| 新 `chest/normal` | `8e74d2298fe59992fff90591626585381efb24ff2255413be3364677b01111fc` | `1c33a2978219043d4b5b4aa31e53671991aec8aff106bee80f0840f0eace3b84` |
| 新 `conduit/open_eye` | `ac7202ae34958f1e2d6e22ff91705e49d85f2adbbbd9f93028abd818aaf75842` | `183bec353fe715b7eb59cd584ab3e56f4e166a7666d6d39a445ed507bec1f18d` |
| 新 `conduit/closed_eye` | `02d164276308001c85fb137a29b5d1d7e74f848bc2ac03a6b803f0597dabb25c` | `a49d7fc82427147204c85c60313870eed0519f47a4548ba8361d76e67ce6ff8d` |
| 新 `banner/stripe_left` | `158a50a946e6e9d054c854b2c58b4be713ae2c74ab5fa8cd5518ff1747da58e9` | `9711492116c6cf434e59ec52be5321f979d84db0b11a6decc2118b0dd269ed9b` |
| 新 `shield/stripe_left` | `ab13e004b679322b93a3d6e19ce880eb93b7c48332b4e6bc8d58e0c3bf23f535` | `8307792c9fccc4896099d18e77600240fd9f58f45ed019eecf25675285d34237` |

以临时 Java 验收程序直接调用 `apply(UpgradeWorkspace)`；`javac -source 8 -target 8` 编译后用 Java 8 运行。旧 PNG 解包到临时工作树后，92 张迁移产物与 2 张未变化底图共 94 张逐 RGBA 字节等于官版格式 5；第二次调用整个样本 PNG 字节不变。另验 2× 非对称左右箱哨兵、混合包现成右箱字节不变、旧双箱和侧车保留、未合成新侧车、坏 PNG 与 16×16 显式新眼按原字节保留，及 2× 旧眼正确放置到 32×32。未运行 Gradle 或游戏，不提交夹具、原版 PNG 或 JAR。
