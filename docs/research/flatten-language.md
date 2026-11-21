<!-- SPDX-License-Identifier: GPL-3.0-or-later; Copyright (c) 2026 Dainsleif -->

# 格式 3 → 4：原版语言键的证据与覆盖范围

[映射表](<../../src/main/resources/viarp/migrations/classic/format-4-language.tsv>)用于把原版
`assets/minecraft/lang/*.lang` 转成 JSON 时改写**键**，不提供任何原版翻译值。旧端是 1.12.2，
目标端是项目支持的最早版本 1.14.4；另用 1.13 首个正式格式 4 版本定位变更。下文的「匹配」指同一对象、
变体或使用位置，不是相同的译文。未知键不应猜测，资源包作者提供的值也不应被改写成原版值。

## 原始工件

从 [Mojang Launcher 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
查找每个正式版本的元数据，先校验元数据 SHA-1，再按其 `downloads.client` 校验客户端 JAR 的字节数和 SHA-1。
直接从已校验的 JAR 读取 UTF-8 条目，不运行游戏。下列语言条目路径都相对于 `assets/minecraft/lang/`。

| 版本与 Mojang 元数据 | 客户端 JAR、字节数、SHA-1 | JAR 内语言条目 | 条目 SHA-256 | 不同键数 |
| --- | --- | --- | --- | ---: |
| [1.12.2](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json) | [client.jar](https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar) · 10,180,113 · `0f275bc1547d01fa5f56ba34bdc87d981ee12daf` | `en_us.lang` | `c205015ae3d70caa079733545f7ecc48cdefffc866d872852411b39c656bdf6b` | 3,303 |
| [1.13](https://piston-meta.mojang.com/v1/packages/c24c2fd37c8ca2e1c18721e2c77caf4d24c87f92/1.13.json) | [client.jar](https://piston-data.mojang.com/v1/objects/c0b970952cdd279912da384cdbfc0c26e6c6090b/client.jar) · 16,033,625 · `c0b970952cdd279912da384cdbfc0c26e6c6090b` | `en_us.json` | `b60f3771c2c68f408b737f4d591b22b121f38d1c181a97b26b4175474af06c2f` | 3,907 |
| [1.14.4](https://piston-meta.mojang.com/v1/packages/be146d5f66a3627ed0a87c234c4d8dde8ab35098/1.14.4.json) | [client.jar](https://piston-data.mojang.com/v1/objects/8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9/client.jar) · 25,191,691 · `8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9` | `en_us.json` | `a74f6405f9bbe1d57c5b7cf5bd8522416e8f577bbf1d1704e49cf8c926646f5e` | 4,326 |

- **注册表和数据修复表：** 使用 [1.14.4 Mojang 客户端映射](https://piston-data.mojang.com/v1/objects/6073e4ba6949217eb708c4512be2ccc1850a603f/client.txt)
  （4,709,268 字节，按元数据校验 SHA-1 `6073e4ba6949217eb708c4512be2ccc1850a603f`）定位
  `Blocks`（`bmw`）、`Items`（`bck`）、`EntityType`（`ais`）、`MobEffects`（`aik`）、`Enchantments`（`bfw`）、
  `Stats`（`yy`）、`BannerPattern`（`btp`）、`DyeColor`（`bbg`）。用 `javap -p -c` 读取常量和注册调用；对有歧义的旧名，
  还查看 1.12.2 的方块和物品注册（`aow`、`ain`）、模型/方块状态以及目标客户端的
  `BlockStateData`（`abc`）、`ItemStackTheFlatteningFix`（`add`）、`EntityTheRenameningFix`（`ack`）、`DyeItemRenameFix`（`abm`）。
  数据修复表里的中间 ID 不一定是最终语言键；要再次核对 1.14.4 的实际 ID 和语言字典。
- **字幕：** 从各版本元数据校验 [旧端资产索引](https://piston-meta.mojang.com/v1/packages/a21e1ded1a24ea1548dd8db0cf30b6acb02655a9/1.12.json) 和
  [目标端资产索引](https://piston-meta.mojang.com/v1/packages/43b2f3021fe9f7d768378de95538e22da3ee8301/1.14.json)，再校验其中
  `minecraft/sounds.json` 的 SHA-1 与字节数：[1.12.2 声音定义](https://resources.download.minecraft.net/97/975a99ed9870f51bbae348533d775d730e3fee18)
  （105,658 字节）和 [1.14.4 声音定义](https://resources.download.minecraft.net/b9/b9be2246b416bbff0749415d4482a06d82fee78a)
  （195,388 字节）。比较实际 `subtitle` 引用、声音事件和每个事件的样本名；音频文件本身未下载。

## 取舍与反例

1. 旧 `.lang` 跳过注释和空行，只拆第一个 `=`；目标 JSON 检查重复键后再建表。旧端共有 3,304 条非注释记录，
   仅 `tipped_arrow.effect.water` 重复且两值不同。比较时取最后一条；该映射另按箭的形态及 `water` 后缀确认，
   不是把旧的通用条目映射成水箭。原版翻译只用于工作区内比对，不写入交付文件。
2. 仅考虑 1.12.2 有而 1.14.4 无的旧键，以及 1.14.4 有而 1.12.2 无的新键。两端完整英文词典中各只出现一次、
   内容逐字相同的旧→新组合有 1,498 个；这只是候选。额外核对对象类别、目标注册名和变体，或可确认的使用位置。
   例如同样的英文标题不会把物品投掷形态的翻译迁到实体上，也不会把容器界面标题当作方块名。
3. 结构化键按语义字段对应：旗帜是 38 种旧图案 × 16 色，药水/药箭按 4 种形态 × 20 个后缀分别匹配；
   盾牌、烟火星颜色按各自的对象类别及颜色匹配，`lightBlue` → `light_blue`，`silver` → `light_gray`。
   唱片只配对具体编号/名称的 `.desc` 键。字幕要求两端实际引用的事件语义一致；普通项的事件样本名集合在两端相符，
   音符盒按事件逐一核对旧乐器仍指向相同样本，目标新增乐器允许共用同一音符盒字幕。无法用两端定义确认的字幕不迁移。

| 容易误判的旧键 | 结果与证据 |
| --- | --- |
| `tile.endPortalFrame.name` | 旧 `aow` 把 `endPortalFrame` 绑定到 `end_portal_frame`；目标 `BlockStateData` 保留框架和传送门两种 ID。选 `block.minecraft.end_portal_frame`，不选与旧英文相同的 `block.minecraft.end_portal`。 |
| `tile.redstoneDust.name`、`item.redstone.name` | 旧 `aow` 把 `redstoneDust` 绑定到方块 `redstone_wire`，而 `ain` 另注册物品 `redstone`。分别选 `block.minecraft.redstone_wire`、`item.minecraft.redstone`，不因译文变化把方块键改到物品上。 |
| `item.fish.clownfish.raw.name` | 旧鱼物品的第 2 号元数据经 `ItemStackTheFlatteningFix` 指向 `clownfish`，再经重命名表变为 `tropical_fish`。选 `item.minecraft.tropical_fish`，不选同文的热带鱼实体变种键。 |
| `tile.sandStone.smooth.name`、`tile.redSandStone.smooth.name` | `BlockStateData` 将这两个旧砂岩变体指到 `cut_sandstone`、`cut_red_sandstone`；不能选英文更相似的 `smooth_*`。 |
| `item.clay.name` / `tile.clay.name`；`effect.wither` / `entity.WitherBoss.name` | 两端都可能有同文，但目标注册对象分别是黏土球/黏土方块、效果/实体；只在对应类别内配对。 |
| `item.dyePowder.{red,green,yellow}.name` | 1.13 中间键基于 `rose_red`、`cactus_green`、`dandelion_yellow`；1.14.4 的 `DyeItemRenameFix` 确认三个对应的 `*_dye` 终点。旧数据使用终点而不是过渡键。 |
| `tile.log.oak.name` 等旧木材键、`tile.pumpkin.name`、`item.appleGold.name` | 不配对。旧键可能覆盖多个现代物体（如轴向原木与全皮木块、南瓜变体、金苹果与附魔金苹果）。同文不能决定唯一落点。 |
| `item.record.name`、`tile.wood.name`、`item.monsterPlacer.name`、`entity.generic.name`、`entity.Villager.name` | 通用、聚合或者已分化的标签，不迁移。类似地，不为 `entity.Cat.name` 猜测 1.14.4 的猫键，也不把 `item.skull.player.name` 迁到有玩家名参数的头颅标签。命令和其它通用界面文字不在本表内。 |

## 覆盖、校验与数据格式

- 1.12.2 / 1.14.4 有 **1,218** 个共享键，不重写；旧端独有 **2,085** 个，其中收录 **1,571** 个（**75.3%**），
  其余 **514** 个不猜。按旧键首段统计：`item` 900、`tile` 369、`entity` 75、`stat` 47、`subtitles` 43、
  `enchantment` 30、`effect` 27、四种药水/药箭前缀各 20。旗帜图案占 608 条，不能把此数字当作独立对象数。
- 新旧英文恰好相同的已收录行有 **1,517** 条，其中 **1,448** 条来自初始唯一同文候选；**54** 条译文不同，仍有具名对象依据。
  **1,568** 个目标键在正式版 1.13 已存在；只有红、绿、黄染料使用 1.14 的终点键（本表不是 1.13 目标映射）。
- 逐项对照旧版 [ClassicPackUpgrades.java](<../../src/main/java/top/syshub/viarp/convert/ClassicPackUpgrades.java>)
  中原先内联的 `FLAT_LANG`，保留 **23/23** 个键对。验证每个端点的键均在其 Mojang 字典中、目标无重复、
  旧键无重复并已排序；另检查上表列出的错误映射未被采纳。没有用运行游戏或其他语言文件的运行结果替代这些证据。
- **精确格式：** UTF-8（无 BOM），LF，末行有换行；以 `#` 开头的行是注释。数据行仅含大小写敏感的
  `旧键<TAB>新键`（一个实际制表符、无引号、无填充空格），按第一列逐字节升序；无键值、无正则、无运行期模糊匹配。
  本表不补齐所有曾存在的键，不覆盖模组命名空间，也不解决用户资源包中已经同时定义旧键和新键的优先级。
