# 旧 atlas 输出到格式 4：已核对的纹理路径

[格式 4 纹理表](../../src/main/resources/viarp/migrations/classic/format-4-textures.tsv)新增 **56 条**：54 条直接承接 [共享 atlas 裁切表](../../src/main/resources/viarp/migrations/legacy/atlas-shared.tsv)经 [旧纹理路径表](../../src/main/resources/viarp/migrations/legacy/texture-paths.tsv)发出的 ID；另两条承接格式 2 归一化后的生／熟鳕鱼 ID。升入格式 4 以最低受支持客户端 **1.14.4** 的原版路径为终点；1.13 只作扁平化证据，不增加已经声明格式 4 的包的同格式迁移。只改路径，不换入原版图、不重绘、不复制旧图到新的变种。

## 原版制品与验证方法

从 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)核对版本 JSON 的 SHA-1 和版本 ID，按 JSON 的 `downloads.client` 核对客户端 JAR 的 SHA-1 与文件大小。只读取归档中的 PNG／JSON 并静态反汇编，不运行游戏。下表的哈希均已对本地原件计算。

| 版本 | 版本 JSON SHA-1 | 客户端 SHA-1 | 字节数 |
| --- | --- | --- | ---: |
| 1.6.1 | [`7fd8e0c76f62813eb0465e31bb74b160c01472d6`](https://piston-meta.mojang.com/v1/packages/7fd8e0c76f62813eb0465e31bb74b160c01472d6/1.6.1.json) | [`17e2c28fb54666df5640b2c822ea8042250ef592`](https://launcher.mojang.com/v1/objects/17e2c28fb54666df5640b2c822ea8042250ef592/client.jar) | 4726671 |
| 1.6.4 | [`b71bae449192fbbe1582ff32fb3765edf0b9b0a8`](https://piston-meta.mojang.com/v1/packages/b71bae449192fbbe1582ff32fb3765edf0b9b0a8/1.6.4.json) | [`1703704407101cf72bd88e68579e3696ce733ecd`](https://launcher.mojang.com/v1/objects/1703704407101cf72bd88e68579e3696ce733ecd/client.jar) | 4745096 |
| 1.7.2 | [`c2e8ecbf355760a74c93d7210767fa043d53f27c`](https://piston-meta.mojang.com/v1/packages/c2e8ecbf355760a74c93d7210767fa043d53f27c/1.7.2.json) | [`0c8689f904922af71c7144dcfb81bce976cadd49`](https://launcher.mojang.com/v1/objects/0c8689f904922af71c7144dcfb81bce976cadd49/client.jar) | 5117607 |
| 1.12.2 | [`832d95b9f40699d4961394dcf6cf549e65f15dc5`](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json) | [`0f275bc1547d01fa5f56ba34bdc87d981ee12daf`](https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar) | 10180113 |
| 1.13 | [`c24c2fd37c8ca2e1c18721e2c77caf4d24c87f92`](https://piston-meta.mojang.com/v1/packages/c24c2fd37c8ca2e1c18721e2c77caf4d24c87f92/1.13.json) | [`c0b970952cdd279912da384cdbfc0c26e6c6090b`](https://piston-data.mojang.com/v1/objects/c0b970952cdd279912da384cdbfc0c26e6c6090b/client.jar) | 16033625 |
| 1.14.4 | [`be146d5f66a3627ed0a87c234c4d8dde8ab35098`](https://piston-meta.mojang.com/v1/packages/be146d5f66a3627ed0a87c234c4d8dde8ab35098/1.14.4.json) | [`8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9`](https://piston-data.mojang.com/v1/objects/8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9/client.jar) | 25191691 |

- 对 54 条直接路径，1.12.2 与 1.13 的 PNG SHA-1 逐条相同；以图像尺寸和完整 RGBA 字节（保留全透明像素的 RGB）检索 1.13 两个单数纹理目录的全部直接子级 PNG，每条只有一个命中。1.14.4 中仅 `block/sugar_cane`、`item/iron_door`、`item/oak_door` 仍与对应 1.12.2 文件 RGBA 完全相同；其余按原版两端消费槽确认，不把图像差异误判为没有后继。
- 1.13 到 1.14.4 路径又变的三条：`item/sign` → `item/oak_sign`；`block/stone_slab_side` → `block/smooth_stone_slab_side`；`block/stone_slab_top` → `block/smooth_stone`。1.14.4 JAR 的 `aak.b(Ljava/lang/String;)Ljava/lang/String;` 与 `aak.c(Ljava/lang/String;)Ljava/lang/String;` 在 0–11 的常量操作数给出原版注册 ID 对 `minecraft:stone_slab` → `minecraft:smooth_stone_slab`、`minecraft:sign` → `minecraft:oak_sign`，随后传入 `ImmutableMap.of`。经 SHA-1 核验的 [Mojang 1.14.4 客户端映射](https://piston-data.mojang.com/v1/objects/6073e4ba6949217eb708c4512be2ccc1850a603f/client.txt)将 `aak` 标为 `DataFixers`。旧、新两端的实际纹理值取自下表模型槽，不把注册 ID 当纹理地址；新版完整平滑石块也消费同一张 `smooth_stone`，但没有另一个旧源文件抢占该目标。
- 早期整图格来的 `items/fish_raw`、`items/fish_cooked` 是格式 2 的历史别名；本表不直接新增这两个旧键。1.12.2 原版 `models/item/cod.json` 与 `models/item/cooked_cod.json` 的 `textures.layer0` 分别读取 `items/fish_cod_raw`、`items/fish_cod_cooked`；1.13 与 1.14.4 同模型的 `layer0` 已是 `item/cod`、`item/cooked_cod`。1.12.2 → 1.14.4 的 RGBA 差异为生鱼 208 像素（95 个至少一端 alpha 非零）、熟鱼 235（84 个至少一端 alpha 非零）；1.13 与 1.14.4 各自完全相同。这两条靠模型身份，不靠把透明 RGB 清零后的图像碰巧相同。

## 保留的歧义与别名边界

- **不加 `blocks/flower_rose` → `block/poppy`。** 1.6.1 的 `aqs.<clinit>()V` 1469–1501 将方块 ID 38 的名称设为 `rose`、纹理设为 `flower_rose`；1.6.1 和 1.6.4 的 `lang/en_US.lang` 都是 `tile.rose.name=Rose`，源 PNG 的 RGBA SHA-256 同为 `3f70350459b3ebd54b8fc0f76e5e79aa84dbed2350b8a72b5ef9c4585796871f`（也是 atlas 的 rose 格）。1.7.2 的 `ajo.<clinit>()V` 在 25 与 82 处给出同一变体索引 0 的纹理名 `flower_rose` 和名称 `poppy`，语言值为 `tile.flower2.poppy.name=Poppy`；该图的 RGBA SHA-256 已变为 `bf99d38326d37022cd4cb1d8c5881f53b7bf291e9af7ed18e46ab3cd669faacf`。1.12.2 `models/block/poppy.json` 仍以 `textures.cross` 读取这个已复用的旧名，1.13 则改读同像素的 `block/poppy`。1.13／1.14.4 的直接 block/item PNG 均没有早期玫瑰的完整 RGBA 对端；也不猜为双格玫瑰丛。路径本身不能区分自定义包原来画的是哪一物，故不给它现代目标。
- 原共享 atlas 筛选已排除 `tree_top`、`leaves`／`leaves_opaque` 和 `musicBlock` 的多目标同像素格；原因见 [共享裁切证据](legacy-atlas-expansion.md#容易误认的面状态与废弃候选)。早期差分 B 组的四张不透明树叶及五个物品栏头颅图标也都不是这 135 个共享输出；不把移除的图标擅自指向其他现代资源。红石火把亮／灭、方块甘蔗／物品甘蔗、门的上下半及物品图标均保有不同目标。有效映射原有的两条染料别名合用 `item/red_dye`，本次未改变，也没有新增任何目标冲突。

## 机械覆盖

以共享表每行的旧 PNG 路径连接旧纹理路径表（`=` 为原路径）得到 **135** 个不同 ID，再与格式 4 表和运行时已有例外求交。新增前 **78/135**；直接连接本次结果 **132/135**（未连接的是原玫瑰和两个早期鱼名）；先应用独立的格式 2 鱼名归一化后 **134/135**，仅原玫瑰没有目标。134 个已连接输出均在 1.14.4 原版 JAR 中存在。机械检查验证原有 429 行值不变、新表 485 行排序且符合两列 TSV schema、表内无重复源或目标、新行与已有运行时例外无目标冲突、56 个源／目标都存在且都有模型引用；没有运行 Gradle 或游戏。只计算共享纹理路径覆盖，不宣称完成整包模型或运行时渲染验证。

## 逐条账本

纹理 ID 相对 JAR 内 `assets/minecraft/textures/`，省略 `.png`；模型文件相对 `assets/minecraft/models/`，冒号后是该文件 `textures` 对象的键。每行给出一组代表性直接消费槽；不是所有消费者的清单。

| 1.12.2 源 | 1.13 对照 | 1.14.4 目标 | 原版模型槽：1.12.2 → 1.14.4 |
| --- | --- | --- | --- |
| `blocks/door_iron_lower` | `block/iron_door_bottom` | `block/iron_door_bottom` | `block/iron_door_bottom.json:bottom` → `block/iron_door_bottom.json:bottom` |
| `blocks/door_iron_upper` | `block/iron_door_top` | `block/iron_door_top` | `block/iron_door_top.json:top` → `block/iron_door_top.json:top` |
| `blocks/door_wood_lower` | `block/oak_door_bottom` | `block/oak_door_bottom` | `block/wooden_door_bottom.json:bottom` → `block/oak_door_bottom.json:bottom` |
| `blocks/door_wood_upper` | `block/oak_door_top` | `block/oak_door_top` | `block/wooden_door_top.json:top` → `block/oak_door_top.json:top` |
| `blocks/farmland_dry` | `block/farmland` | `block/farmland` | `block/farmland_dry.json:top` → `block/farmland.json:top` |
| `blocks/farmland_wet` | `block/farmland_moist` | `block/farmland_moist` | `block/farmland_moist.json:top` → `block/farmland_moist.json:top` |
| `blocks/flower_dandelion` | `block/dandelion` | `block/dandelion` | `block/dandelion.json:cross` → `block/dandelion.json:cross` |
| `blocks/furnace_front_off` | `block/furnace_front` | `block/furnace_front` | `block/furnace.json:front` → `block/furnace.json:front` |
| `blocks/log_oak` | `block/oak_log` | `block/oak_log` | `block/oak_log.json:side` → `block/oak_log.json:side` |
| `blocks/mushroom_brown` | `block/brown_mushroom` | `block/brown_mushroom` | `block/brown_mushroom.json:cross` → `block/brown_mushroom.json:cross` |
| `blocks/mushroom_red` | `block/red_mushroom` | `block/red_mushroom` | `block/red_mushroom.json:cross` → `block/red_mushroom.json:cross` |
| `blocks/pumpkin_face_off` | `block/carved_pumpkin` | `block/carved_pumpkin` | `block/pumpkin.json:front` → `block/carved_pumpkin.json:front` |
| `blocks/pumpkin_face_on` | `block/jack_o_lantern` | `block/jack_o_lantern` | `block/lit_pumpkin.json:front` → `block/jack_o_lantern.json:front` |
| `blocks/rail_normal` | `block/rail` | `block/rail` | `block/normal_rail_flat.json:rail` → `block/rail.json:rail` |
| `blocks/rail_normal_turned` | `block/rail_corner` | `block/rail_corner` | `block/normal_rail_curved.json:rail` → `block/rail_corner.json:rail` |
| `blocks/redstone_torch_on` | `block/redstone_torch` | `block/redstone_torch` | `block/lit_redstone_torch.json:torch` → `block/redstone_torch.json:torch` |
| `blocks/reeds` | `block/sugar_cane` | `block/sugar_cane` | `block/reeds.json:cross` → `block/sugar_cane.json:cross` |
| `blocks/stone_slab_side` | `block/stone_slab_side` | `block/smooth_stone_slab_side` | `block/half_slab_stone.json:side` → `block/smooth_stone_slab.json:side` |
| `blocks/stone_slab_top` | `block/stone_slab_top` | `block/smooth_stone` | `block/half_slab_stone.json:top` → `block/smooth_stone_slab.json:top` |
| `blocks/torch_on` | `block/torch` | `block/torch` | `block/normal_torch.json:torch` → `block/torch.json:torch` |
| `blocks/wheat_stage_0` | `block/wheat_stage0` | `block/wheat_stage0` | `block/wheat_stage0.json:crop` → `block/wheat_stage0.json:crop` |
| `blocks/wheat_stage_1` | `block/wheat_stage1` | `block/wheat_stage1` | `block/wheat_stage1.json:crop` → `block/wheat_stage1.json:crop` |
| `blocks/wheat_stage_2` | `block/wheat_stage2` | `block/wheat_stage2` | `block/wheat_stage2.json:crop` → `block/wheat_stage2.json:crop` |
| `blocks/wheat_stage_3` | `block/wheat_stage3` | `block/wheat_stage3` | `block/wheat_stage3.json:crop` → `block/wheat_stage3.json:crop` |
| `blocks/wheat_stage_4` | `block/wheat_stage4` | `block/wheat_stage4` | `block/wheat_stage4.json:crop` → `block/wheat_stage4.json:crop` |
| `blocks/wheat_stage_5` | `block/wheat_stage5` | `block/wheat_stage5` | `block/wheat_stage5.json:crop` → `block/wheat_stage5.json:crop` |
| `blocks/wheat_stage_6` | `block/wheat_stage6` | `block/wheat_stage6` | `block/wheat_stage6.json:crop` → `block/wheat_stage6.json:crop` |
| `blocks/wheat_stage_7` | `block/wheat_stage7` | `block/wheat_stage7` | `block/wheat_stage7.json:crop` → `block/wheat_stage7.json:crop` |
| `items/book_normal` | `item/book` | `item/book` | `item/book.json:layer0` → `item/book.json:layer0` |
| `items/bucket_empty` | `item/bucket` | `item/bucket` | `item/bucket.json:layer0` → `item/bucket.json:layer0` |
| `items/bucket_lava` | `item/lava_bucket` | `item/lava_bucket` | `item/lava_bucket.json:layer0` → `item/lava_bucket.json:layer0` |
| `items/bucket_milk` | `item/milk_bucket` | `item/milk_bucket` | `item/milk_bucket.json:layer0` → `item/milk_bucket.json:layer0` |
| `items/bucket_water` | `item/water_bucket` | `item/water_bucket` | `item/water_bucket.json:layer0` → `item/water_bucket.json:layer0` |
| `items/door_iron` | `item/iron_door` | `item/iron_door` | `item/iron_door.json:layer0` → `item/iron_door.json:layer0` |
| `items/door_wood` | `item/oak_door` | `item/oak_door` | `item/oak_door.json:layer0` → `item/oak_door.json:layer0` |
| `items/fish_cod_cooked` | `item/cooked_cod` | `item/cooked_cod` | `item/cooked_cod.json:layer0` → `item/cooked_cod.json:layer0` |
| `items/fish_cod_raw` | `item/cod` | `item/cod` | `item/cod.json:layer0` → `item/cod.json:layer0` |
| `items/gold_axe` | `item/golden_axe` | `item/golden_axe` | `item/golden_axe.json:layer0` → `item/golden_axe.json:layer0` |
| `items/gold_boots` | `item/golden_boots` | `item/golden_boots` | `item/golden_boots.json:layer0` → `item/golden_boots.json:layer0` |
| `items/gold_chestplate` | `item/golden_chestplate` | `item/golden_chestplate` | `item/golden_chestplate.json:layer0` → `item/golden_chestplate.json:layer0` |
| `items/gold_hoe` | `item/golden_hoe` | `item/golden_hoe` | `item/golden_hoe.json:layer0` → `item/golden_hoe.json:layer0` |
| `items/gold_leggings` | `item/golden_leggings` | `item/golden_leggings` | `item/golden_leggings.json:layer0` → `item/golden_leggings.json:layer0` |
| `items/gold_pickaxe` | `item/golden_pickaxe` | `item/golden_pickaxe` | `item/golden_pickaxe.json:layer0` → `item/golden_pickaxe.json:layer0` |
| `items/gold_shovel` | `item/golden_shovel` | `item/golden_shovel` | `item/golden_shovel.json:layer0` → `item/golden_shovel.json:layer0` |
| `items/gold_sword` | `item/golden_sword` | `item/golden_sword` | `item/golden_sword.json:layer0` → `item/golden_sword.json:layer0` |
| `items/minecart_furnace` | `item/furnace_minecart` | `item/furnace_minecart` | `item/furnace_minecart.json:layer0` → `item/furnace_minecart.json:layer0` |
| `items/minecart_normal` | `item/minecart` | `item/minecart` | `item/minecart.json:layer0` → `item/minecart.json:layer0` |
| `items/redstone_dust` | `item/redstone` | `item/redstone` | `item/redstone.json:layer0` → `item/redstone.json:layer0` |
| `items/reeds` | `item/sugar_cane` | `item/sugar_cane` | `item/reeds.json:layer0` → `item/sugar_cane.json:layer0` |
| `items/sign` | `item/sign` | `item/oak_sign` | `item/sign.json:layer0` → `item/oak_sign.json:layer0` |
| `items/slimeball` | `item/slime_ball` | `item/slime_ball` | `item/slime_ball.json:layer0` → `item/slime_ball.json:layer0` |
| `items/wood_axe` | `item/wooden_axe` | `item/wooden_axe` | `item/wooden_axe.json:layer0` → `item/wooden_axe.json:layer0` |
| `items/wood_hoe` | `item/wooden_hoe` | `item/wooden_hoe` | `item/wooden_hoe.json:layer0` → `item/wooden_hoe.json:layer0` |
| `items/wood_pickaxe` | `item/wooden_pickaxe` | `item/wooden_pickaxe` | `item/wooden_pickaxe.json:layer0` → `item/wooden_pickaxe.json:layer0` |
| `items/wood_shovel` | `item/wooden_shovel` | `item/wooden_shovel` | `item/wooden_shovel.json:layer0` → `item/wooden_shovel.json:layer0` |
| `items/wood_sword` | `item/wooden_sword` | `item/wooden_sword` | `item/wooden_sword.json:layer0` → `item/wooden_sword.json:layer0` |
