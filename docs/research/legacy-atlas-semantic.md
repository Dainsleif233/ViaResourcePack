# 旧 terrain atlas：原版重绘格的语义白名单

在[原有严格 RGBA 筛选](legacy-atlas-expansion.md)之外，为 [atlas-shared.tsv](../../src/main/resources/viarp/migrations/legacy/atlas-shared.tsv) 新增 **10 个 terrain 格**，合计 **75 terrain + 70 items**。准入证据是六版的同 ID、同坐标、同用途，以及目标方块实际引用的模型纹理；RGBA 只复核重绘与裁切。表末列仍写区分大小写的 **1.5.2 散图 stem**，不写现代名。

## 原件、方法与注册偏移

从既有的 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)和本地已取证原件取样，复核版本 JSON SHA-1 和 ID，再核对其 `downloads.client` 的 SHA-1 与字节数。只用 JDK `javap -p -c -s` 读 JVM 字节码并解析 JAR 的 PNG、JSON，未运行 Minecraft 客户端，未取用 Unstitcher 映射表。

| 版本 | 版本 JSON SHA-1 | 客户端 SHA-1 | 字节数 |
| --- | --- | --- | ---: |
| a1.2.6 | `1c888e4d8aed380db25aeb3835f5918297bb5e3a` | `a68c817afd6c05c253ba5462287c2c19bbb57935` | 1105909 |
| b1.7.3 | `44f6969326bd45aa00dcd3c4ca3a7c05ebb24c04` | `43db9b498cb67058d2e12d394e6507722e71bb45` | 1465375 |
| b1.8.1 | `440e3b845c3991492a3d0c5f0ccfda78ab90d9b6` | `6b562463ccc2c7ff12ff350a2b04a67b3adcd37b` | 1995166 |
| 1.0 | `75062586b830dd5160f13f1c9130eb365e01f1b9` | `b679fea27f2284836202e9365e13a82552092e5d` | 2362837 |
| 1.2.5 | `5158765caf1ca14958cb6c45d52c8e09ed9b046c` | `4a2fac7504182a97dcbcd7560c6392d7c8139928` | 4032098 |
| 1.4.7 | `7aa8e9aeacf4e1076bfd81c096f78de9b883ebe6` | `53ed4b9d5c358ecfff2d8b846b4427b888287028` | 5005735 |
| 1.5.2 | `924a2dcd8bdc31f8e9d36229811c298b3537bbc7` | `465378c9dc2f779ae1d6e8046ebc46fb53a57968` | 5564661 |
| 13w24a | `74666ab85cc5539f08aec638eabd63a552ed4125` | `440abbedd473bdf38821906db6ea825d042848ae` | 4702776 |
| 1.6.1 | `7fd8e0c76f62813eb0465e31bb74b160c01472d6` | `17e2c28fb54666df5640b2c822ea8042250ef592` | 4726671 |
| 1.14.4 | `be146d5f66a3627ed0a87c234c4d8dde8ab35098` | `8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9` | 25191691 |
| 1.8.9 | `d546f1707a3f2b7d034eece5ea2e311eda875787` | `3870888a6c3d349d3771a3e9d16c9bf5e076b908` | 8461484 |
| 1.12.2 | `832d95b9f40699d4961394dcf6cf549e65f15dc5` | `0f275bc1547d01fa5f56ba34bdc87d981ee12daf` | 10180113 |

六版根 `terrain.png` 均为 256×256；坐标从左上角零基计数，`i = x + 16*y`。a1.2.6 的 `nk.<init>()V @66–133` 直接读 `/terrain.png`，计算格起点 `(16*(i%16), 16*(i/16))`。下表每组偏移是对应类 `<clinit>()V` 的 `new` 至实例的 `putstatic`，为方法 Code 内 JVM 指令偏移，不是文件偏移；13w24a 和 1.6.1 的十组各自对原件检查，偏移恰同。

| 方块 ID | i (x,y) | a1.2.6 `nq` | b1.7.3 `uu` | b1.8.1 `lr` | 1.0 `yy` | 1.2.5 `pb` | 1.4.7 `amq` | 1.5.2 `apa` | 13w24a `aqq` / 1.6.1 `aqs` |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 5 | 4 (4,0) | 291–318 | 319–354 | 319–354 | 327–362 | 311–342 | 360–391 | 348–376 | 369–403 |
| 45 | 7 (7,0) | 1048–1077 | 1603–1637 | 1600–1634 | 1608–1642 | 1591–1625 | 1658–1698 | 1538–1576 | 1727–1770 |
| 4 | 16 (0,1) | 260–288 | 283–316 | 283–316 | 291–324 | 275–308 | 318–357 | 308–345 | 324–366 |
| 12 | 18 (2,1) | 485–507 | 579–606 | 579–606 | 587–614 | 567–594 | 622–649 | 582–607 | 639–669 |
| 13 | 19 (3,1) | 510–532 | 609–636 | 609–636 | 617–644 | 597–624 | 652–679 | 610–635 | 672–702 |
| 48 | 36 (4,2) | 1129–1158 | 1699–1733 | 1696–1730 | 1704–1738 | 1687–1721 | 1760–1800 | 1634–1672 | 1838–1881 |
| 19 | 48 (0,3) | 679–699 | 817–842 | 814–839 | 822–847 | 802–827 | 857–882 | 799–824 | 891–921 |
| 52 | 65 (1,4) | 1254–1276 | 1850–1880 | 1844–1874 | 1852–1882 | 1835–1865 | 1914–1944 | 1777–1805 | 2001–2034 |
| 87 | 103 (7,6) | 2167–2189 | 3042–3069 | 3036–3063 | 3048–3075 | 3031–3058 | 3120–3147 | 2864–2889 | 3209–3239 |
| 89 | 105 (9,6) | 2217–2246 | 3102–3136 | 3096–3130 | 3108–3142 | 3091–3125 | 3180–3214 | 2920–2952 | 3275–3312 |

- **基础纹理选择**：六版基类 `<init>(IIL…;)V @8` 分别写 `bg/bm/bz/bL/bN/cl`；`a(II)I @2` 调单参选择器，其 `@1` 读字段（1.0 是 `b(I)I`、1.2.5 是 `a_(I)I`、其余 `a(I)I`）。砂、砾石、地狱岩、荧石、刷怪笼的子类构造器将索引传给父类；海绵要看 `pd/xf/nk/wh/rf/amf.<init>(I)V @9–11` 直接写 48。Alpha 无后续的注册名，另有独立语义锚点：`cn.a(ILjava/util/Random;)I @0–6` 让石头掉落 ID 4；`en.<init>()V @425–464` 四砖配方产出 ID 45、`@874–908` 将 ID 17 原木变为四份 ID 5；`lt.d(I)I @52–67` 将 ID 12 熔炼为 ID 20 玻璃。
- **橡木木板仅对应 metadata 0**：前四版 ID 5 直接传 4；1.2.5 的 `nz.a(II)I` 和 1.4.7 的 `ani.a(II)I`，metadata 1/2/3 分别选 198/214/199，含 0 的默认分支 `@28` 才选 4。`ani.<clinit>()V @5–8` 的树种索引 0 为 `oak`。1.5.2 的 `apu.<clinit>()V @5–8/@32–35` 建立索引 0 的 `oak` / `wood`，`a(Lly;)V @27–38` 注册、`a(II)Llx; @0–21` 按下标选图标。13w24a 的 `ark.a(Lmp;)V`、1.6.1 的 `arm.a(Lmq;)V @35–63` 拼接 `planks`、`_` 与树种 `oak`。
- **刷怪笼仅对应笼体**：六版 ID 52 的 `br/dd/bp/kk/cm/ald.<init>(II)V @6` 经 `lh/rw/zt/ba/agy/akb.<init>(IIL…;)V @4` 传递 65。方块工厂依次为 `a_()Lji; / a_()Low; / m_()Lij; / j_()Lbq; / u_()Lkw; / a(Lyc;)Lany;`，各在 `@0–7` 创建 `bl/cy/bn/ze/cj/ans`，相应实体注册 `ji/ow/ij/bq/kw/any.<clinit>()V @41–45/55–59/55–59/55–59/55–59/62–66` 精确命名为 `MobSpawner`。1.5.2、13w24a、1.6.1 的 ID 52 工厂类 `ank/aoz/apb` 在 `@0–7` 创建 `aqj/asa/asc`，后者仍由 `aqp/asg/asi.<clinit>()V @69–73` 注册为 `MobSpawner`。
- **散图资源名不同于注册名**：1.5.2 基类 `apa.c(Ljava/lang/String;)Lapa; @2` 写入名，`apa.a(Lly;)V @3–11` 从字段 `b` 注册 sprite；13w24a/1.6.1 用 `d(Ljava/lang/String;)Laqq; / d(Ljava/lang/String;)Laqs; @2` 写专用纹理名字段 `f`，`E()Ljava/lang/String;` 返回非空的 `f`，`a(Lmp;)V / a(Lmq;)V @3–11` 使用返回值。`bge.<init>(Lbju;Lavy;)V @192–200`、`atl.O()V @762–772`、`atn.O()V @846–856` 均为方块图集设置 `textures/blocks/`。例如 ID 4 的注册名 `stonebrick` 在后两版仍在，纹理名却已是 `cobblestone`，不指后来出现的石砖。

## 目标路径和实际消费者

1.5.2 散图为 `textures/blocks/<stem>.png`；13w24a、1.6.1、1.8.9、1.12.2 共用 `assets/minecraft/textures/blocks/<stem>.png`。1.12.2 方块状态及 1.14.4 同 stem 的空键状态在 `assets/minecraft/blockstates/`；它们各自引用 `assets/minecraft/models/block/` 下的模型，模型以 `textures.all` 指向对应纹理（缺省命名空间为 `minecraft`）。各散图均实测解码为 16×16，无同名动画 `.txt` 或 `.png.mcmeta`。末列为原版重绘的 RGBA 像素数（满格 256），不参与准入。

| ID | 1.5.2 stem | 13w24a / 1.6.1 / 1.8.9 / 1.12.2 stem | 1.12.2 状态 → 模型 | 1.14.4 纹理 PNG | 1.12.2→1.14.4 像素变化 |
| ---: | --- | --- | --- | --- | ---: |
| 5 | `wood` | `planks_oak` | `oak_planks:normal → oak_planks` | `assets/minecraft/textures/block/oak_planks.png` | 241 |
| 45 | `brick` | `brick` | `brick_block:normal → brick` | `assets/minecraft/textures/block/bricks.png` | 232 |
| 4 | `stonebrick` | `cobblestone` | `cobblestone:normal → cobblestone` | `assets/minecraft/textures/block/cobblestone.png` | 254 |
| 12 | `sand` | `sand` | `sand:normal → sand` | `assets/minecraft/textures/block/sand.png` | 256 |
| 13 | `gravel` | `gravel` | `gravel:normal → gravel` | `assets/minecraft/textures/block/gravel.png` | 230 |
| 48 | `stoneMoss` | `cobblestone_mossy` | `mossy_cobblestone:normal → mossy_cobblestone` | `assets/minecraft/textures/block/mossy_cobblestone.png` | 256 |
| 19 | `sponge` | `sponge` | `sponge:wet=false → sponge` | `assets/minecraft/textures/block/sponge.png` | 256 |
| 52 | `mobSpawner` | `mob_spawner` | `mob_spawner:normal → mob_spawner_cage` | `assets/minecraft/textures/block/spawner.png` | 212 |
| 87 | `hellrock` | `netherrack` | `netherrack:normal → netherrack` | `assets/minecraft/textures/block/netherrack.png` | 256 |
| 89 | `lightgem` | `glowstone` | `glowstone:normal → glowstone` | `assets/minecraft/textures/block/glowstone.png` | 255 |

十项在 1.5.2→13w24a→1.6.1 散图间 RGBA 均相同。1.6.1→1.8.9 只有干海绵变化（256/256），但 1.12.2 干态模型和 1.14.4 干海绵模型均选择 `sponge`，没有借湿态补证。图集时代 Alpha 圆石、砖块相较 b1.7.3 分别变化 250、256 像素；砾石在 b1.8.1→1.0 和 1.2.5→1.4.7 各变化 256 像素。这些是原版重绘，不是换格。

## 排除与验收

- **换坐标**：金、铁、钻石块 ID 41/42/57，a1.2.6 的注册是 i39/38/40，后五版是 i23/22/24。a1.2.6 的 `nq.<clinit>()V` 偏移依次为 932–959、962–989、1371–1398；1.4.7 的 `amq.<clinit>()V` 为 1516–1548、1551–1583、2073–2105。
- **旧样本缺少消费者**：a1.2.6 不注册青金石矿/块、发射器、砂岩、蜘蛛网（ID 21/22/23/24/30）。不凭像素相似填未使用格；不把橡木、干海绵推及其他木种、湿态。既有 `tree_top` / 叶子通用格、音符盒/唱片机共图及未消费箭袋仍不放行；本批不声称穷尽全部旧图集布局。
- **迁移实测**：六份原图集各取到独立临时目录，用 JDK 直接编译和调用现有 `LegacyTextureUpgrade.unstitch`、`toResourcePack`、`ClassicPackUpgrades.apply`（格式 2→3→4）。每份 10 格共 **60 格** 均通过三阶段的路径和全 256 像素 ARGB 验证；原图集保留，无额外动画元数据，格式 4 不残留旧复数目录源文件。未运行共享 Gradle。[格式 1 路径表](../../src/main/resources/viarp/migrations/legacy/texture-paths.tsv)已有十项；[格式 4 路径表](../../src/main/resources/viarp/migrations/classic/format-4-textures.tsv)同批接通 `blocks/brick → block/bricks`、`blocks/cobblestone_mossy → block/mossy_cobblestone`、`blocks/mob_spawner → block/spawner`。
