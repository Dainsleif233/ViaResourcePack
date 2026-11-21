# 格式 3 → 4：已经分拆的方块状态资源

## 制品

比较的是正式客户端 JAR 中 `assets/minecraft/blockstates/` 的实际资源，不是存档中序列化的方块 ID。按 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 核验本地版本元数据 SHA-1，再核验元数据的 `downloads.client` SHA-1 和大小。无需启动游戏。

| 版本与核验后的元数据 | 核验后的官方客户端 JAR | 字节数 |
| --- | --- | ---: |
| [1.12.2 · 832d95b9f40699d4961394dcf6cf549e65f15dc5](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json) | [0f275bc1547d01fa5f56ba34bdc87d981ee12daf](https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar) | 10,180,113 |
| [1.13 · c24c2fd37c8ca2e1c18721e2c77caf4d24c87f92](https://piston-meta.mojang.com/v1/packages/c24c2fd37c8ca2e1c18721e2c77caf4d24c87f92/1.13.json) | [c0b970952cdd279912da384cdbfc0c26e6c6090b](https://piston-data.mojang.com/v1/objects/c0b970952cdd279912da384cdbfc0c26e6c6090b/client.jar) | 16,033,625 |
| [1.14.4 · be146d5f66a3627ed0a87c234c4d8dde8ab35098](https://piston-meta.mojang.com/v1/packages/be146d5f66a3627ed0a87c234c4d8dde8ab35098/1.14.4.json) | [8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9](https://piston-data.mojang.com/v1/objects/8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9/client.jar) | 25,191,691 |

原版 1.12.2 **已在资源层按变种分拆**：三份 JAR 均无 `blockstates/{planks,wool,tallgrass}.json`；旧端确有 `blockstates/stone.json`，但其中唯一键是 `normal`，不是 `variant=…`。旧端 `bvl.d()V` 在字节偏移 248–268、1119–1145、1310–1330、1613–1639 处注册石材、羊毛、草和木板的状态映射；`bwr.a(awt)` 将用于命名路径的属性从剩余条件中取走，再用 `bwo` 为无剩余属性的状态输出 `normal`。草的枚举 `aun$a` 的序列化名为 `dead_bush`、`tall_grass`、`fern`。

[1.14.4 客户端官方映射](https://piston-data.mojang.com/v1/objects/6073e4ba6949217eb708c4512be2ccc1850a603f/client.txt)（4,709,268 字节，按版本元数据核对 SHA-1）定位的 `BlockStateData`（`abc`）含 `stone.variant`、`planks.variant`、`wool.color`、`tallgrass.type` 的**存档**变换。这不能证明有同名资源 JSON，不能据此给未被原版读取的路径生成新覆盖。例：旧存档 `tallgrass[type=dead_bush]` 与另一个旧注册 ID `deadbush` 都迁为 `dead_bush`；这不构成两个可以分别写入的资源目标。

## 可写入的闭合子集

以下是 32 个原版旧状态，每份恰有 `variants.normal`，目标在 1.13 和 1.14.4 都恰有 `variants[""]`。对旧 `normal` 的**整个值**做对应改写，不制造额外变种。两端实证中裸模型 ID 补成 `block/<id>`，只对已核验别名改 ID；数组、顺序、权重、旋转和 `uvlock` 不变。文件名不动不代表内容不动：`oak_planks.json` 仍要更换空选择器和模型引用。

| 资源族（数量） | 旧 → 新状态文件名（无扩展名） | 模型 ID 的额外改名（均补 `block/`） |
| --- | --- | --- |
| 木板（6） | `{oak,spruce,birch,jungle,acacia,dark_oak}_planks` 不改文件名 | 无 |
| 羊毛（16） | `silver_wool` → `light_gray_wool`；`{white,orange,magenta,light_blue,yellow,lime,pink,gray,cyan,purple,blue,brown,green,red,black}_wool` 不改文件名 | `silver_wool` → `light_gray_wool` |
| 石材（7） | `{stone,granite,diorite,andesite}` 不改文件名；`smooth_{granite,diorite,andesite}` → `polished_{granite,diorite,andesite}` | 旧 ID 是 `{granite,diorite,andesite}_smooth`（与状态文件名不同），变为 `polished_{granite,diorite,andesite}` |
| 旧单格草类（3） | `tall_grass` → `grass`；`{fern,dead_bush}` 不改文件名 | `tall_grass` → `grass` |

`stone.json` 的确切输出（两端目标结构一致）：

```json
{"variants":{"": [{"model":"block/stone"}, {"model":"block/stone_mirrored"}, {"model":"block/stone","y":180}, {"model":"block/stone_mirrored","y":180}]}}
```

没有额外的模型属性包装层。旧 `bwe$a`、新 `cvb$a`（1.13）和 `doo$a`（1.14.4）均从同一变体对象读取 `model`、`x`、`y`、`uvlock`、`weight`；原版状态 JSON 的 `variants` 和 `multipart[*].apply` 都可见对象或数组（`chorus_plant` 有内联权重）。1.12.2 的 `bwe$a.a(String)` 为模型路径无条件加 `block/` 并保留命名空间，1.13/1.14.4 不自动加前缀。假设格式 3 的路径一定是旧语法也不安全；选择器或模型写法有冲突时不猜。

## 草的复用路径与保全策略

- 1.12.2 的 `grass.json` 是草方块，不是短草。仅在恰有 `snowy=false` 和 `snowy=true` 两个已知条件时迁到 `grass_block.json`，保留条件及四项旋转数组；`grass_normal` → `block/grass_block`，`grass_snowed` → `block/grass_block_snow`。若同一个 `grass.json` 已用目标的空选择器，不改它，不让旧短草覆盖它。只有整个迁移计划能腾出确证的旧路径时才允许短草接续占位。
- 1.12.2 的 `double_grass.json`、`double_fern.json` 和两新端的 `tall_grass.json`、`large_fern.json` 都是 `half=lower`／`half=upper`；单格旧短草没有这两个选择器，不能留在同名新路径或人为展开两半。这两种双格植物不是本规则的转换对象。
- 模型也有重用：旧 `models/block/grass.json` 是 `grass_normal` 的立方体模板；新同名模型是短草的 `block/tinted_cross`。状态规则只限定其直接模型引用，不重建自定义父模型依赖，也不搬迁独立提供的模型文件。已核验别名若有包内旧模型，则可继续引用该文件；明确的不同名新模型优先。若同名 `block/grass` 令所需映射或继承关系有歧义，则保留并诊断。
- [转换实现](../../src/main/java/top/syshub/viarp/convert/ClassicBlockStates.java) 只看核验清单中的 `assets/minecraft` 路径；不将任意方块的 `normal` 当作现代全状态匹配。未知条件、多键混合、重复 JSON 键、空数组、未知包装结构、不明确模型 ID 和目标冲突都不生成结果。已有目标或大小写折叠碰撞不覆盖；不把相同目标的两个变种合并，也不改未被原版读取的三个聚合文件。

## 复核

从旧 JAR 读出表中 32 份状态与旧雪草方块（共 33 份，不复制任何模型）到临时工作目录；运行实际转换实现，分别把输出解析为 JSON，与两目标 JAR 的对应成员比较。33/33 均完全相等；验证目标路径集合、每个输出模型的官方目标条目，以及重复迁移后字节不变。另以重复键（含转义的同名键）、`normal` 与空键相撞、已有显式新状态／大小写路径、草路径链被阻断、双格草不改、无效变体、聚合旧路径和自定义命名空间为负例。包含权重、转角和 `uvlock` 的合法自定义数组也验证其内容与顺序未变。此验证用直接编译的 Java 8 目标探针和标准 ZIP/JSON 库，未运行 Gradle 或游戏。
