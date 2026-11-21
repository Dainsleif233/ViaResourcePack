# 旧 atlas 共享裁切规则：扩展证据

基线 `359960c`。本报告只扩展 ViaResourcePack 2.0 的**输入侧** `LegacyAtlas → LegacyTiles`，不新增旧客户端适配器，也不变更格式 1 后面通往 26.4-s10 的节点。结果在 [atlas-shared.tsv](../../src/main/resources/viarp/migrations/legacy/atlas-shared.tsv)：**135 行（terrain 65、items 70）**，原 19 格不变，新增 terrain 54、items 62。表使用零基左上角坐标，`i = x + 16*y`，末列是 **区分大小写的 1.5.2 stem**，不是格式 1 或现代名。

## 取证与筛选

从 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 独立取样：逐字节验证清单条目所列 JSON SHA-1、JSON 的版本 ID、`downloads.client` 的 SHA-1 和文件大小；仅解析 ZIP、解码 PNG、用 JDK `javap -p -c -s` 读取字节码，未运行客户端或取用 Unstitcher 的映射表。以下是实际校验值及原件：

| 版本 | 版本 JSON SHA-1 | 客户端 SHA-1 |
| --- | --- | --- |
| a1.2.6 | [`1c888e4d8aed380db25aeb3835f5918297bb5e3a`](https://piston-meta.mojang.com/v1/packages/1c888e4d8aed380db25aeb3835f5918297bb5e3a/a1.2.6.json) | [`a68c817afd6c05c253ba5462287c2c19bbb57935`](https://launcher.mojang.com/v1/objects/a68c817afd6c05c253ba5462287c2c19bbb57935/client.jar) |
| b1.7.3 | [`44f6969326bd45aa00dcd3c4ca3a7c05ebb24c04`](https://piston-meta.mojang.com/v1/packages/44f6969326bd45aa00dcd3c4ca3a7c05ebb24c04/b1.7.3.json) | [`43db9b498cb67058d2e12d394e6507722e71bb45`](https://launcher.mojang.com/v1/objects/43db9b498cb67058d2e12d394e6507722e71bb45/client.jar) |
| b1.8.1 | [`440e3b845c3991492a3d0c5f0ccfda78ab90d9b6`](https://piston-meta.mojang.com/v1/packages/440e3b845c3991492a3d0c5f0ccfda78ab90d9b6/b1.8.1.json) | [`6b562463ccc2c7ff12ff350a2b04a67b3adcd37b`](https://launcher.mojang.com/v1/objects/6b562463ccc2c7ff12ff350a2b04a67b3adcd37b/client.jar) |
| 1.0 | [`75062586b830dd5160f13f1c9130eb365e01f1b9`](https://piston-meta.mojang.com/v1/packages/75062586b830dd5160f13f1c9130eb365e01f1b9/1.0.json) | [`b679fea27f2284836202e9365e13a82552092e5d`](https://launcher.mojang.com/v1/objects/b679fea27f2284836202e9365e13a82552092e5d/client.jar) |
| 1.2.5 | [`5158765caf1ca14958cb6c45d52c8e09ed9b046c`](https://piston-meta.mojang.com/v1/packages/5158765caf1ca14958cb6c45d52c8e09ed9b046c/1.2.5.json) | [`4a2fac7504182a97dcbcd7560c6392d7c8139928`](https://launcher.mojang.com/v1/objects/4a2fac7504182a97dcbcd7560c6392d7c8139928/client.jar) |
| 1.4.7 | [`7aa8e9aeacf4e1076bfd81c096f78de9b883ebe6`](https://piston-meta.mojang.com/v1/packages/7aa8e9aeacf4e1076bfd81c096f78de9b883ebe6/1.4.7.json) | [`53ed4b9d5c358ecfff2d8b846b4427b888287028`](https://launcher.mojang.com/v1/objects/53ed4b9d5c358ecfff2d8b846b4427b888287028/client.jar) |
| 1.5.2 | [`924a2dcd8bdc31f8e9d36229811c298b3537bbc7`](https://piston-meta.mojang.com/v1/packages/924a2dcd8bdc31f8e9d36229811c298b3537bbc7/1.5.2.json) | [`465378c9dc2f779ae1d6e8046ebc46fb53a57968`](https://launcher.mojang.com/v1/objects/465378c9dc2f779ae1d6e8046ebc46fb53a57968/client.jar) |
| 13w24a | [`74666ab85cc5539f08aec638eabd63a552ed4125`](https://piston-meta.mojang.com/v1/packages/74666ab85cc5539f08aec638eabd63a552ed4125/13w24a.json) | [`440abbedd473bdf38821906db6ea825d042848ae`](https://launcher.mojang.com/v1/objects/440abbedd473bdf38821906db6ea825d042848ae/client.jar) |
| 1.6.1 | [`7fd8e0c76f62813eb0465e31bb74b160c01472d6`](https://piston-meta.mojang.com/v1/packages/7fd8e0c76f62813eb0465e31bb74b160c01472d6/1.6.1.json) | [`17e2c28fb54666df5640b2c822ea8042250ef592`](https://launcher.mojang.com/v1/objects/17e2c28fb54666df5640b2c822ea8042250ef592/client.jar) |

1. 各源图集（`terrain.png`、`gui/items.png`）都是 256×256；逐版裁成 256 个 16×16 格。用从上到下、从左到右的每像素 RGBA 8 位原始字节计数；透明像素的 RGB **不作归零**。候选必须不全透明，而且六版**同域、同坐标、同字节、各自图集中唯一**。
2. 只在 1.5.2 同域的所有直接子级 16×16 PNG 中找唯一的 RGBA 精确对端（`textures/blocks/` 或 `textures/items/`）。由此复现旧报告的 **65 terrain + 70 items**。这一步不认定运行时使用或名称语义。
3. 用同一 RGBA 在 13w24a 和 1.6.1 的命名空间同域中均找唯一对端，并要求两版名字一致，且由现有 [texture-paths.tsv](../../src/main/resources/viarp/migrations/legacy/texture-paths.tsv) 的原始大小写路径精确映射（或 `=`）；匹配端没有同 stem 的动画 TXT 或 PNG.mcmeta。多目标、禁用与无对应均不添加共享裁切。
4. RGBA 和路径对端独立之外，用原版注册或分面判定易混淆的名字、状态和占位。**69 个入选物品**的六份图集客户端注册均用同一内部物品 ID 选择同一索引；1.4.7 注册名精确等于 1.5.2 散图 stem。解析器按 `<clinit>()V` 的构造和图标 setter 切分；Alpha 用直接索引，后续样本可用坐标 setter。注册类（按版本顺序）为 `dx` / `gm` / `sv` / `acy` / `yr` / `up`。这是对具体注册调用的观察，不是凭纹理名假定物品存在。terrain 对端的变体/分面边界见下节。

| 集合 | terrain | items |
| --- | ---: | ---: |
| 严格 RGBA 候选 | 65 | 70 |
| 排除 | 4 | 1 |
| 通过候选筛选（含基线候选） | 61 | 69 |
| 沿用基线中不属严格候选的格 | 4 | 1 |
| **表中总数** | **65** | **70** |

最后一行与第一行计数相同只是巧合，集合并不相同。原 19 格依 [旧报告 §5](legacy-texture-packs.md#5-首批-atlas-白名单原版客户端独立证据)。其中草方块顶面、石、泥土、草方块侧面及苹果 5 格走原报告的重绘、原版注册或透明 RGB 证据，不把它们冒充六版严格像素候选。其余 14 格也在下方记录。

## 容易误认的面、状态与废弃候选

下列偏移是在对应 JAR 的类方法 `Code` 内的指令偏移，不是文件偏移。a1.2.6 和 1.4.7 的方块注册表分别是 `nq.<clinit>()V` 和 `amq.<clinit>()V`。中间样本用于逐格 RGBA 与注册对照，不宣称对每个样本完整解出了渲染器。

| 已采纳的族 | 原版静态证据和映射边界 |
| --- | --- |
| 石台阶与 TNT | a1.2.6 `qj.a(I)I` 0–9、1.4.7 `amk.a(II)I` 0–18：石变体顶/底用 6、侧用 5，不推台阶其他材质。两个版本的 `v.a(I)I` / `amv.a(I)I` 0–27 从 TNT 基础格 8 分出顶 9、底 10。 |
| 原木侧面、白羊毛 | a1.2.6 `ny.a(I)I` 0–17 给树干侧面 20 和截面 21；只取有唯一终点的侧面 20 → `log_oak`。Alpha ID 35 的纹理参数为 64（`nq.<clinit>()V` 787–812）；1.4.7 `ajm.<init>()V` 0–8 与 `ajm.a(II)I` 0–8 也只把 64 给 metadata 0，不当全部羊毛色。 |
| 工作台与熔炉 | a1.2.6 `dd.<init>(I)V` 8–11 设置 59，`dd.a(I)I` 0–46 与 1.4.7 `anj.a(I)I` 0–46 分出顶 43、正面 60、侧 59；底复用木板，不另发格。a1.2.6 `mj.a(Lpk;IIII)I` 45–70、1.4.7 `akh.d(Lym;IIII)I` 47–72 从基数 45 得侧 45、熄灭正面 44、点燃正面 61；不顺带承诺炉顶。 |
| 雪草侧面、仙人掌、唱片机顶 | 对应版本的 `os.a(Lpk;IIII)I` / `akj.d(Lym;IIII)I` 15–49 单独选择雪侧 68；不同于基线普通草侧。`je.a(I)I` / `ajg.a(I)I` 0–27 从仙人掌基数 70 分出顶 69、底 71。`dg.a(I)I` / `alt.a(I)I` 0–15 从唱片机基数 74 分出顶 75；侧面 74 另列为歧义。 |
| 门、耕地与小麦 | 木门/铁门注册分别为 ID 64/71；a1.2.6 `gv.<init>(ILhb;)V` 与 1.4.7 `ajx.<init>(ILagi;)V` 的 6–26 设木 97、铁 98；分面方法（`gv.a(II)I` / `ajx.d(Lym;IIII)I`）按上下半与朝向选择，不把物品栏图标当门面。`oa.a(II)I` / `akc.a(II)I` 0–27 自耕地基数 87 取湿 86、干 87；`ig.a(II)I` / `ajq.a(II)I` 0–13 以 88 加上受限的生长阶段 0–7，不扩到其他作物。 |
| 轨道、南瓜与红石火把 | 轨道 ID 66 的注册基数是 128，`jn.a(II)I` / `alr.a(II)I` 的普通轨道分支用基数或基数减 16（112 是转弯）；不扩到动力、探测轨道。`cx.a(II)I` / `alq.a(II)I` 0–93 从基数 102 选择上下、侧 118 与两个状态的正面 119/120。红石火把 ID 75/76 在早晚注册均分别取 115/99；与普通火把 80 分离。 |
| 弓与钓竿 | 1.5.2 `wk.<clinit>()V` 112–128/2013–2030 保持内部 ID 5/90；`uw.<clinit>()V` 0–22 和 `wd.a(Lly;)V` 0–17 分别另外登记拉弓帧、钓竿另一图标。这里只有原注册的 `bow` / `fishingRod` 对端，分别进入 `bow_standby` / `fishing_rod_uncast`，不推其他状态，不伪造动画。 |

| 排除格 | 1.5.2 stem | 为什么不发射 |
| --- | --- | --- |
| terrain (5,1) | `tree_top` | 格在 Alpha 渲染，但两期格式 1 同字节命中 `log_birch_top`、`log_jungle_top`、`log_oak_top`、`log_spruce_top`；现有路径表也一对四。共享表不能把较老的树种语义传播到未识别版本的其他树种。 |
| terrain (4,3)、(5,3) | `leaves`、`leaves_opaque` | 两期格式 1 分别命中桦木与橡木（各自透明/不透明版本）；现有路径一对二。不掩盖叶种和图形设置差异。 |
| terrain (10,4) | `musicBlock` | Alpha 的唱片机侧面确用 74，但两期格式 1 同字节命中 `jukebox_side` 与 `noteblock`，现有路径也是一对二；保留到有来源档案的规则，不把单一老唱片机格改成两个现代方块。 |
| items (6,2) | `quiver` | 虽有同像素的三份散图，`up.<clinit>()V` 逐条注册中没有在索引 38 的直接图标，同一抽样范围根部混淆类常量池也未找到精确 `quiver`。这是范围有限的否定观察；不足以确认实际物品，排除。 |

## 可复核账本

以下 130 项为通过严格候选筛选的行。RGBA SHA-256 同时适用于六份原版格子、1.5.2 对端、13w24a 和 1.6.1 对端。账本含 14 项基线，不含按旧证据保留的另外 5 项。物品内部 ID 不含 `+256`，偏移指 1.4.7 `up.<clinit>()V` 的注册起止。文件名后缀均为 `.png`。

<details>
<summary>Terrain：61 个严格候选</summary>

| x,y | 1.5.2 stem | 格式 1 stem | RGBA SHA-256 |
| --- | --- | --- | --- |
| 5,0 | `stoneslab_side` | `stone_slab_side` | `77527cdbf7b39d7f25dafebd2f23f64e812f0668c9b68f985c7b61d88728ba8d` |
| 6,0 | `stoneslab_top` | `stone_slab_top` | `cb65938ab9d511130daf47eef65fcf9fe4d014c75e123a32c5a003338e780ca4` |
| 8,0 | `tnt_side` | `tnt_side` | `d88fae9e6187c0b59bab49515e0d12d32b41e722e90c98cdc8c183cd960d1dd3` |
| 9,0 | `tnt_top` | `tnt_top` | `20cdbbe5c832d82cc9781514c576ea8b976fa6c0c6244c9188db870a9e557c32` |
| 10,0 | `tnt_bottom` | `tnt_bottom` | `75950c01aef280d0e174dbc1762bba9d54d7854226e4c9f9a082dec8ae1c24bc` |
| 12,0 | `rose` | `flower_rose` | `3f70350459b3ebd54b8fc0f76e5e79aa84dbed2350b8a72b5ef9c4585796871f` |
| 13,0 | `flower` | `flower_dandelion` | `6ccfdfd66faa700aac90fe4fca01f76b385116b9558cbab77c2fa3c318fe916c` |
| 1,1 | `bedrock` | `bedrock` | `0fddd326cd10bd8795da2e9206bd13fdc5bb2e814a2ff6b40e9aef90f8477ea3` |
| 4,1 | `tree_side` | `log_oak` | `ed03fa8db6398fb69ce7f49b035a26ed15a77097fd948952ce6bcbc254ac7995` |
| 12,1 | `mushroom_red` | `mushroom_red` | `e192badc86e601b24f61d98bbff9fb289b4cdf25a6a273cc8b3024c2eec3f63b` |
| 13,1 | `mushroom_brown` | `mushroom_brown` | `4f6263aee8215786a1cc6a124731807b1b0de4533c23191df49e3c0e3689fd6a` |
| 0,2 | `oreGold` | `gold_ore` | `772f949014a04a5e20313748138449c40fc578619bf685c1e454cf53d9dce796` |
| 1,2 | `oreIron` | `iron_ore` | `f8b6e228ba9ee3e33dcf58d7b15e00ec7a4f5a1736605bb85dccd69aa18779b1` |
| 2,2 | `oreCoal` | `coal_ore` | `90f2a1dcef6bb9cdc6c2b4933fa187d629cf626f22b2de3c05139babba37bbb8` |
| 3,2 | `bookshelf` | `bookshelf` | `93b23817734665b954d6beaafd57e5e440f161f9e0ffcfdeee69672e4322f40a` |
| 5,2 | `obsidian` | `obsidian` | `e20270bfdf7f1230211d04fe5b0ca744917c57c3e62b6c501e6cdfca6a53e7ca` |
| 11,2 | `workbench_top` | `crafting_table_top` | `e6e7a92118f6721e1de840eca4c6acb4d4c6e8c10561e336e06395c63383abf3` |
| 12,2 | `furnace_front` | `furnace_front_off` | `18c6a3e36652cc56ed802a4535dc93742779a0a34a1fa772fd5839a817e2d375` |
| 13,2 | `furnace_side` | `furnace_side` | `22f3743e35d6fbda88880981f629b7d3afb545c4ecdabef9dfea60e404ac578f` |
| 1,3 | `glass` | `glass` | `cc2b59ea27804a28c5f1f9f5f24c811b0d886a7e7735c576bec93d11af7593d6` |
| 2,3 | `oreDiamond` | `diamond_ore` | `6c92df1cbdca8e471fb4f399e5ba8c26d51ecc4660777aa4cb4e5a29c4757c93` |
| 3,3 | `oreRedstone` | `redstone_ore` | `28b4dc70ed7ba0f3389bb83b8cb090bf295a127874ac1f179bf53b1368356b18` |
| 11,3 | `workbench_side` | `crafting_table_side` | `7d2614c5cb7781005abb2be7e0b4da8d95628ab673ebd8fa0b181e9d6b6c602a` |
| 12,3 | `workbench_front` | `crafting_table_front` | `8f5dddccc6876bd943283d0286b339bf0d24c68032931a04470b5640ae5e9df8` |
| 13,3 | `furnace_front_lit` | `furnace_front_on` | `63ba697a4240a288c23f47634472f8c50a799ee0a9a604c3fbdae29c228a9dfa` |
| 0,4 | `cloth_0` | `wool_colored_white` | `097f4bff764b9850a0ba9699903243672130972e4b54e96bfdcb5fe7e2ebc135` |
| 2,4 | `snow` | `snow` | `ba669c8503fc695716b21ad291aef104e20bc1dc222b73183ff612197c89930f` |
| 3,4 | `ice` | `ice` | `db4a1cf0d62f889ceec6d742ffe498b74d7dec6790247a934fd651bde23694f7` |
| 4,4 | `snow_side` | `grass_side_snowed` | `255ccbf77fa99fd858b6c6d9d6b59c2e7439ab38c96819d4d29c498e4f7a96ae` |
| 5,4 | `cactus_top` | `cactus_top` | `c4dafb46792d70de1a33c61ba87338ab3205bb15878e2b2bbec577c9c5c28ee3` |
| 6,4 | `cactus_side` | `cactus_side` | `d413fe31a2667e7b177a444771ae276131a22940e90a6edee7760aee669b82f4` |
| 7,4 | `cactus_bottom` | `cactus_bottom` | `c300f43e7b87a44256235d00d01f97a0c59281ca5d4571cd842d7a50ed945f64` |
| 8,4 | `clay` | `clay` | `cef2fbd8c19cac1fa12a156e9616b9fe3c357697e79c24521ebae316c87dffd7` |
| 9,4 | `reeds` | `reeds` | `374358f39979030718655b221e13c50169667c8e1d53b87d4cd7a165be693e76` |
| 11,4 | `jukebox_top` | `jukebox_top` | `5b799d3d2bcb6bc4b12d77fd7fd07513b86383b397e492cb9e94a297e24b4f65` |
| 0,5 | `torch` | `torch_on` | `e04f9760a4efba7379cf03609d569a5e157f434ae20ab02346913c47878c94d0` |
| 1,5 | `doorWood_upper` | `door_wood_upper` | `d72e72914a84f757dc4d9c04af405b96ea70b992ae2398af8b5746e70fd966d6` |
| 2,5 | `doorIron_upper` | `door_iron_upper` | `693bb2b76c3177ab435abf5433f750fdb56474a5af01f546ea533da94d485348` |
| 3,5 | `ladder` | `ladder` | `e04585d632a06c046de3d2f36eeb020f976e14545f5bcd3cdf47d6866fd6b7ef` |
| 6,5 | `farmland_wet` | `farmland_wet` | `46633a33ce57247b8671bf696771d1448a909a64d98d23d31a66564c27e5649f` |
| 7,5 | `farmland_dry` | `farmland_dry` | `1d02ba3eb6c54a8735f17e6105b549430944b7925e018810427cdc02bd11ff13` |
| 8,5 | `crops_0` | `wheat_stage_0` | `d3f7497f67dd78f53430adb36a8b992a47c0218f8dbbbf3e00e489482b2b5ba1` |
| 9,5 | `crops_1` | `wheat_stage_1` | `a3597d868e716a2b681afe12b688170447d0e02c06969701e2ff69e5aaa54601` |
| 10,5 | `crops_2` | `wheat_stage_2` | `cacd6a7a1e2faa3990756b282be757ef61899e3a81c4321a4936ca5a0be4f44b` |
| 11,5 | `crops_3` | `wheat_stage_3` | `a23ba9d5c0c65ffea309c5050875ae66606383b281810540dd09ff8c601954b7` |
| 12,5 | `crops_4` | `wheat_stage_4` | `39b73f8e61733411e214ba916f80055a35e3a4648b202886b949e2dbe560254d` |
| 13,5 | `crops_5` | `wheat_stage_5` | `b9958f4bca504991b3e46fd4b7c3a01f6471dd716a5685465e57e8506d379055` |
| 14,5 | `crops_6` | `wheat_stage_6` | `7424d4e77bf7ceddb234e8822689126a758a1e66fd7b942962684d62f4ba967b` |
| 15,5 | `crops_7` | `wheat_stage_7` | `35abc7741432b8e99265aca398da114c5e8d7e3d99f9123d0eaa3daf48815e42` |
| 0,6 | `lever` | `lever` | `a96c0061dcacc90b199a2ba3d228851693ef6adbfd6f8bb93567fe3852bb31d4` |
| 1,6 | `doorWood_lower` | `door_wood_lower` | `63fd43046c864d52f728b597b9d334972b8f1d50cea85a3943ce9b41d1aed6a3` |
| 2,6 | `doorIron_lower` | `door_iron_lower` | `60260c463cff37c916708e337a48a301e35564d0fd1c8edf46c8593ca7a4dc48` |
| 3,6 | `redtorch_lit` | `redstone_torch_on` | `a8e9af316addd991cade020e30652431352e6a97ae35ed42855ee0769d2095c6` |
| 6,6 | `pumpkin_top` | `pumpkin_top` | `17e961eac5438d9c02b0d2399f869ef814f68e0d035d0acf5cd9ed2f95d47ff5` |
| 8,6 | `hellsand` | `soul_sand` | `9caffa3475454e925c1a4b9ea43fff1cc4a6dde29b7ea2318789a760350393cd` |
| 0,7 | `rail_turn` | `rail_normal_turned` | `7b4db746944c73eb6f910a14df20031ffa89d0e690a58b0dbd9c28e25057c491` |
| 3,7 | `redtorch` | `redstone_torch_off` | `94882e9156494e54b344104adc3e7ee00d6c81d69a5b56e2c1c67a5fa1f6116c` |
| 6,7 | `pumpkin_side` | `pumpkin_side` | `00633579b9bb3af718fccdff2674d404e12fcb5d65805d23abf42c9dd9c381ce` |
| 7,7 | `pumpkin_face` | `pumpkin_face_off` | `e4d31ed098780e9deb6cc2c915b3a327445a68662e4abb5729ba56c44d31f716` |
| 8,7 | `pumpkin_jack` | `pumpkin_face_on` | `ae5ca48b036abcd88439919220366e741563867ab947979a182a18025170d796` |
| 0,8 | `rail` | `rail_normal` | `67ebb726c2b88a2da9df0ea5ca1dd6db350807df040ea97f1b1c5a47feedcd80` |

</details>

<details>
<summary>Items：69 个严格候选</summary>

| x,y | 1.5.2 stem | 格式 1 stem | 注册 ID / 偏移 | RGBA SHA-256 |
| --- | --- | --- | --- | --- |
| 1,1 | `chestplateChain` | `chainmail_chestplate` | 47 / 1269–1293 | `7051835ecaec00b52acb4fc1793b081c0f4f7669a238c18bee8094af67fd6855` |
| 2,1 | `chestplateIron` | `iron_chestplate` | 51 / 1377–1401 | `ed0cbddd15a4ade4483e3b55eff8d7993d1d83e9b86ed5e313eb1435f65d331e` |
| 3,1 | `chestplateDiamond` | `diamond_chestplate` | 55 / 1485–1509 | `d3b4a5788c30953044451801e6dcff649587e91a3651c3700a420f4d6206211d` |
| 4,1 | `chestplateGold` | `gold_chestplate` | 59 / 1593–1617 | `f19826d4e0753a813692c3335eb3f70af085884089b351f95882a2799984ca46` |
| 5,1 | `bow` | `bow_standby` | 5 / 140–158 | `c126f643aa088076b67dcc0e11405ebfde21b0a7aabc3440d2a2c0dceab255ec` |
| 6,1 | `brick` | `brick` | 80 / 2183–2209 | `bdcd48a1e17b6cc1e3effb5abc86e7c2fdafd9988c7f9271fe6f8f1380acad29` |
| 7,1 | `ingotIron` | `iron_ingot` | 9 / 241–267 | `e3eefaf73595ec23ebce498c33b971f3783251a28637c3abe1ebc5f2f32f4993` |
| 8,1 | `feather` | `feather` | 32 / 849–875 | `cd65e0d4fd6cecb7d6e08872725aba693e9a1635b3c79f5ec61096075689bd02` |
| 9,1 | `wheat` | `wheat` | 40 / 1078–1104 | `3456a2c44da86164c9ac47bf219fff6895abc44111f8a3c579dd5123b0aaaba5` |
| 10,1 | `painting` | `painting` | 65 / 1758–1780 | `1a14a400b87ca2ae89324b74120e2a6630d2ad41c21a2e85dfd17d00bc864a81` |
| 11,1 | `reeds` | `reeds` | 82 / 2241–2270 | `70629d7eb8d4248fb762e6b227b02b36420deedc1d65fa515acc6828170be761` |
| 14,1 | `slimeball` | `slimeball` | 85 / 2331–2357 | `8cc7f2088fa39ad97cded41638947a967ae2cd5c115012584fb6c5ceb59f2a15` |
| 1,2 | `leggingsChain` | `chainmail_leggings` | 48 / 1296–1320 | `0a1d62baa7c4d43689d93ab4859c025f5a93a11d52cf499144e86ef8fac08d90` |
| 2,2 | `leggingsIron` | `iron_leggings` | 52 / 1404–1428 | `140054d73a5c262e90bf51becc662334fc6822032ead04050854b094d9de7362` |
| 3,2 | `leggingsDiamond` | `diamond_leggings` | 56 / 1512–1536 | `4d1c4e8b33620fe3c9e86f6575af1bf6d1a2af19bd7f6ecb592afc9720a12b6f` |
| 4,2 | `leggingsGold` | `gold_leggings` | 60 / 1620–1644 | `a4a4afa577fe3ccf8af66a224c29abe5aeaa56fd1c8e948429085af9d6ebcb66` |
| 5,2 | `arrow` | `arrow` | 6 / 161–186 | `ff0a24e1fdb5a9e391d27dcdd866bf4289dff9fbc96488a09af3683927158da0` |
| 7,2 | `ingotGold` | `gold_ingot` | 10 / 270–296 | `88067a3aa67491ada7fa42096f6d364dd4f114a9d0b70df47984c551ae38d2ed` |
| 8,2 | `sulphur` | `gunpowder` | 33 / 878–910 | `f4ad8e0654b4fdd5216fe791471c60d0942db4dcaa24e00e1c05316f3d05b94a` |
| 10,2 | `sign` | `sign` | 67 / 1825–1845 | `470a71fc23107ff9e5167bf4e6180ed643cd1f757f567d2883b5f018e91c4120` |
| 11,2 | `doorWood` | `door_wood` | 68 / 1848–1871 | `9a2ce5cc5968285308fae7a60d9d345ce7a9ee2660cde67aadf99baf9336ac4a` |
| 12,2 | `doorIron` | `door_iron` | 74 / 2022–2045 | `fb385c07ae3d1b94f0754bc827b2886b26dd8290594908a4ce52e018a2261bd4` |
| 1,3 | `bootsChain` | `chainmail_boots` | 49 / 1323–1347 | `ad99634f7b469ec14609a07cc593be441a6f4dcfcebb3d79998d51902e654fec` |
| 2,3 | `bootsIron` | `iron_boots` | 53 / 1431–1455 | `0bb9da84565cfc9036d3ea62d99807c263593bb0385e2a73dbff461760da285d` |
| 3,3 | `bootsDiamond` | `diamond_boots` | 57 / 1539–1563 | `892f406d6f6d90ae693f409708a514c0f695aa24bdef3a30cff1f972eae0b192` |
| 4,3 | `bootsGold` | `gold_boots` | 61 / 1647–1671 | `0da51e7236b9a7bf63dd02a4a39fcf2e2636091453b233ca0dccf2c2cee2f529` |
| 8,3 | `redstone` | `redstone_dust` | 75 / 2048–2074 | `30176d8a938e193e549f8d053ce616132f003ef2e0a756f64b1e104e030f7542` |
| 9,3 | `clay` | `clay_ball` | 81 / 2212–2238 | `6be219b29fe3f6dd685ceebbeca9fb32a34453b95f2554a34aaaa420ff7e1121` |
| 10,3 | `paper` | `paper` | 83 / 2273–2299 | `d4e52e90f096767dc7ca6a7eb24fca89c569112d2385afb8a45a5de2f2c0c50e` |
| 11,3 | `book` | `book_normal` | 84 / 2302–2328 | `f06e6a3714ab9a202a124583b8775d4dd112c199cbd020c6ead807ad4bca26aa` |
| 0,4 | `swordWood` | `wood_sword` | 12 / 324–346 | `37df1e78cf88457502810fa450d945f9b30a32bdebf9d6617701a28021c258c2` |
| 1,4 | `swordStone` | `stone_sword` | 16 / 426–448 | `df485c385c833b5d954d51f81ca964d612d5e82d6b0b6f031f51c0b60cd7cfe0` |
| 2,4 | `swordIron` | `iron_sword` | 11 / 299–321 | `473975a4da342d27566c668cc45d1ab61bbb2c6956e7a0645fe1503aa7f655b6` |
| 3,4 | `swordDiamond` | `diamond_sword` | 20 / 528–550 | `0bf5f387d76933787506fd7f33f018498be7637a224c4b838275b16438975baa` |
| 4,4 | `swordGold` | `gold_sword` | 27 / 715–737 | `54a0ebede4b7fa357f5978a94915d85bb0d3790cf0aa0f245da33f01623965ae` |
| 5,4 | `fishingRod` | `fishing_rod_uncast` | 90 / 2462–2481 | `7d7f0ede8f9f34148aea26b98b20c9633a062b3526d963c4bcc0a4fadcd91d3f` |
| 7,4 | `bowl` | `bowl` | 25 / 661–687 | `a7b7a074cae6a1c0086f8440f71de0541f045258699f7bd7e43a25a48d96a83c` |
| 8,4 | `mushroomStew` | `mushroom_stew` | 26 / 690–712 | `d508838e5980c9d76f8e4c2a96e03f3a04dde6b0818d4379c8112eef8b859a32` |
| 9,4 | `yellowDust` | `glowstone_dust` | 92 / 2513–2545 | `ff97bda92cdca039439a25635c3cd30c5354b8586b4b083dbc6be82adf408c27` |
| 10,4 | `bucket` | `bucket_empty` | 69 / 1874–1900 | `aad4d09ffe162934adc4ba0c7e2217ec0c32a95ab1c9de991ea14af58bb4c8f6` |
| 11,4 | `bucketWater` | `bucket_water` | 70 / 1903–1935 | `73c0f682fb04df81a48788f24cb58a6bdfe767718b30c3cd1de8d7ec155c639b` |
| 12,4 | `bucketLava` | `bucket_lava` | 71 / 1938–1970 | `ff4d4e6b61109e884e6435151768c2a426b6aa8fb1d03573d873dd65e7d75059` |
| 13,4 | `milk` | `bucket_milk` | 79 / 2154–2180 | `7f66083ed27a2970c7516b585601a500ede941eeab6beb5f275173d44ab5b8c6` |
| 0,5 | `shovelWood` | `wood_shovel` | 13 / 349–371 | `4f571694f918064d71c311f72c52dc269a6172edd050b919da1ba7056a127fd6` |
| 1,5 | `shovelStone` | `stone_shovel` | 17 / 451–473 | `cc8f899c5f74ec69e03b9d193ab0f6c975de519111aeff1eb5b6af3c1fa7c66c` |
| 2,5 | `shovelIron` | `iron_shovel` | 0 / 19–40 | `ef57576ca8d582ef5414809adeff0c4e9cf2a3c9719e3dfca85cc0bcafad3fea` |
| 3,5 | `shovelDiamond` | `diamond_shovel` | 21 / 553–575 | `0a5e5e0e98774b7a3b4baf3ac8d2bb625db777eadf6aca3684cbfce2e56fffa0` |
| 4,5 | `shovelGold` | `gold_shovel` | 28 / 740–762 | `f8ea5943d77f7fb10d6b0fd54592f2893244253a0fccd7b3526aba66464055c2` |
| 9,5 | `fishRaw` | `fish_raw` | 93 / 2548–2572 | `74d1c89f0ffcdf660a0658021ba8140ddce4672b961e956a12ddd167bf6a4bcc` |
| 10,5 | `fishCooked` | `fish_cooked` | 94 / 2575–2599 | `46c5aec721537b309dac37212d2374b2eb27cccd2889b4a28c70754a1998f511` |
| 0,6 | `pickaxeWood` | `wood_pickaxe` | 14 / 374–397 | `32aca3ed0cf42cdbe8b0c4df7f4f64afc5f80c4e6a8111372fc61576e05051b8` |
| 1,6 | `pickaxeStone` | `stone_pickaxe` | 18 / 476–499 | `760b95eea693a13f6137a3e5b16ebe2923ac5db7338ffeecd17f8b56e7c54c8b` |
| 2,6 | `pickaxeIron` | `iron_pickaxe` | 1 / 43–65 | `fe50aceb54711e73a7bb5b090429296569812b5a2dc6b54015bfe61196cf08a8` |
| 3,6 | `pickaxeDiamond` | `diamond_pickaxe` | 22 / 578–601 | `76698058f18fd34118a748db0757c74b9aa69d3f6e92d892baeac2e3bf21e8a4` |
| 4,6 | `pickaxeGold` | `gold_pickaxe` | 29 / 765–788 | `8e8dacd4d8a7ffa3c97a45401d5571654dcd5217f98ffe8e83138a2c3a47af50` |
| 7,6 | `leather` | `leather` | 78 / 2124–2151 | `76381afceb409c63c905ec87294c8863ab55823a69e4a85e0ee639926f0bedee` |
| 8,6 | `saddle` | `saddle` | 73 / 1998–2019 | `a77aa2c768b785a14e7f7c796f6ffe2dc7d9ade9549e93cf94c5a9c23cf56a01` |
| 0,7 | `hatchetWood` | `wood_axe` | 15 / 400–423 | `2c2c93ed1a729c9918bd9d159a05d128d609775ca4fd2c8dae80825f1f3aa5db` |
| 1,7 | `hatchetStone` | `stone_axe` | 19 / 502–525 | `ae17209e2eaf0d698954e67f70d7e72217691c02832cfcb86635a56b0396444e` |
| 2,7 | `hatchetIron` | `iron_axe` | 2 / 68–90 | `2ecc3ae9b8c095b4337e9a17e00ba9f285599e248f5dbdbfdd0666dfd326100c` |
| 3,7 | `hatchetDiamond` | `diamond_axe` | 23 / 604–627 | `7d9f9e2e3b35e06cbcc515e68cf671907197d6425a8ba8a4aa04ee51fe0546f2` |
| 4,7 | `hatchetGold` | `gold_axe` | 30 / 791–814 | `8d0f6d574fd387389ce484dfaaab8b6cb1be9ce052458d993e1b188f6c671a2d` |
| 0,8 | `hoeWood` | `wood_hoe` | 34 / 913–936 | `9122f02fe1568048146157c2482cc95900201dd15d58afe373e3aa1bbf9471ea` |
| 1,8 | `hoeStone` | `stone_hoe` | 35 / 939–962 | `b56e2e686e98655f2255f2cc10ac7dfaa6c182a0cea43ed4b41fdc7ad967d17b` |
| 2,8 | `hoeIron` | `iron_hoe` | 36 / 965–988 | `234c4b2a9ababc561ea93b07e7556902ee0ef0e7d6332ee85aca75e936be0b87` |
| 3,8 | `hoeDiamond` | `diamond_hoe` | 37 / 991–1014 | `2a03adc8103d43e35d461d22bdceef205ca9d8e54650957a2679dc9ca78b0263` |
| 4,8 | `hoeGold` | `gold_hoe` | 38 / 1017–1040 | `3a50fddc6e4240fd196489e065dd24e230d29dfc9e5761fcfaa7a791e1435365` |
| 7,8 | `minecart` | `minecart_normal` | 72 / 1973–1995 | `04abedae61a581f92c9a417760bb645d37925f9a6c5973ace6ceb87a916f87df` |
| 7,10 | `minecartFurnace` | `minecart_furnace` | 87 / 2385–2407 | `fb48e395cc2fc9c77c1c63620d5a3966e0bae3f645b66e1562c22a6a37e7eb93` |

</details>

## 验证与边界

- 在独立的 `build/atlas-evidence-expansion-359960c/` 复算；重新枚举不限制 stem 字符的全部同域直接 PNG，候选清单与第一遍逐字节相同。独立断言现有路径目标与两个版本的真实条目一致、没有动画侧车、69 个物品在六版注册索引一致；核对规则表 UTF-8/LF、四字段、坐标 0–15、域内 stem（不区分大小写）和坐标不重复、行优先顺序，19 个基线格逐项相同。校验输出：`verified rows: 135 {'terrain': 65, 'items': 70} baseline: 19 new: 116`。另用 JDK ImageIO 的 ARGB 像素重排为 RGBA，独立核对全部 135 个原候选在六份图集的坐标、摘要和唯一性，以及三份散图归档的完整命中集合（含歧义），均一致。未运行共享 Gradle；原版归档、转储和本地脚本不进版本控制。
- 这是**已抽样的来源共同语义白名单**，不证明所有版本、未抽样快照、Mod/补丁图集或短命占位在这些坐标相同。程序化水/岩浆/火、钟/指南针、丢失的状态资产、重复图案、不满足严格比较的格均不从本批推断。RGBA 只用于取证；不要把此规则变成按用户提供图集的颜色、透明度或是否匹配原版来决定是否裁切。表未声明剩余格无用。
- 格式 1 之后继续走既有迁移链；本次未验证所有素材在 26.4-s10 的最终路径、模型外观或集成行为。活跃的 1.5 式显式散图优先于 atlas 裁片、未确认格不顶替原版、规则指纹随表更新，均需由集成侧测试确认。
