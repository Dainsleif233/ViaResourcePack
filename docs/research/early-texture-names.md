# 格式 1 早期方块／物品纹理名到格式 2

**结论：格式 2 只补两条：`items/fish_raw` → `items/fish_cod_raw`，`items/fish_cooked` → `items/fish_cod_cooked`。** 这是格式 1 内 1.6.x→1.7.2 已完成的改名；1.8.9、格式 2 首个快照 15w31a 和 1.9 仍用目标名。只在**升入**格式 2 时消费[别名表](../../src/main/resources/viarp/migrations/classic/format-2-early-textures.tsv)，不重写同格式输入。表中是 `assets/minecraft/textures/` 下不含扩展名的逻辑 ID，由迁移实现按同一表处理原版 PNG、侧车及模型纹理字段；不碰自定义命名空间、注册 ID、语言键，也不因未匹配而删旧文件。两条原版输入、输出均无 `.png.mcmeta`；移动时保留用户像素与用户提供的侧车。

## 失效点及两层分工

以 `359960c` 为初始图集证据基线。本次按[图集规则](../../src/main/resources/viarp/migrations/legacy/atlas-shared.tsv)和[格式 1 路径表](../../src/main/resources/viarp/migrations/legacy/texture-paths.tsv)串出 135 个互异的纹理 ID，并对比 1.8.9、1.9、1.12.2 和 1.14.4 的原版 JAR。对照本轮接入前的[格式 4 TSV](../../src/main/resources/viarp/migrations/classic/format-4-textures.tsv)与 Java `FLAT_TEXTURE_EXCEPTIONS`，可复现 57 个路径在格式 4 查不到映射：

- **2 个** 1.6.1 名不被 1.8.9 使用；本表将其归一为 1.12.2 仍使用的 `items/fish_cod_raw`、`items/fish_cod_cooked`。格式 4 仍须由另一张表分别接至 `item/cod`、`item/cooked_cod`；在 1.14.4 的 `models/item/{cod,cooked_cod}.json` 可直接核对 `textures.layer0`，两目标 PNG 实存。本表不跨过格式 3／4，也不重复收录后续别名。
- **55 个**原 ID 在 1.8.9、1.9、1.12.2 均存在，不是早期改名。下面按 1.12.2 源键记录未覆盖集合，并不宣称每项后期都能无损一对一迁移。

| 前缀 | 本轮接入前格式 4 路由未覆盖、且不需早期改名的源 stem |
|---|---|
| `blocks/`（29） | `door_iron_lower`、`door_iron_upper`、`door_wood_lower`、`door_wood_upper`、`farmland_dry`、`farmland_wet`、`flower_dandelion`、`flower_rose`、`furnace_front_off`、`log_oak`、`mushroom_brown`、`mushroom_red`、`pumpkin_face_off`、`pumpkin_face_on`、`rail_normal`、`rail_normal_turned`、`redstone_torch_on`、`reeds`、`stone_slab_side`、`stone_slab_top`、`torch_on`、`wheat_stage_0`、`wheat_stage_1`、`wheat_stage_2`、`wheat_stage_3`、`wheat_stage_4`、`wheat_stage_5`、`wheat_stage_6`、`wheat_stage_7` |
| `items/`（26） | `book_normal`、`bucket_empty`、`bucket_lava`、`bucket_milk`、`bucket_water`、`door_iron`、`door_wood`、`gold_axe`、`gold_boots`、`gold_chestplate`、`gold_hoe`、`gold_leggings`、`gold_pickaxe`、`gold_shovel`、`gold_sword`、`minecart_furnace`、`minecart_normal`、`redstone_dust`、`reeds`、`sign`、`slimeball`、`wood_axe`、`wood_hoe`、`wood_pickaxe`、`wood_shovel`、`wood_sword` |

因此这一步修正 **2 个早期逻辑 ID、其余 133 个不变**；仅接入本表且不补格式 4 时，最终未落入 1.14.4 原版目标的仍是 57 个，而不是 55 个。

## 官方制品和透明像素

使用 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)定位元数据并核验清单中的元数据 SHA-1，再核验 `downloads.client.size` 和客户端 SHA-1，读 ZIP 中 `assets/minecraft/textures/` 下的直接 PNG（非后人整理的资产镜像）。计数：1.6.1 有 487 张直接复数目录方块／物品 PNG，1.8.9 有 600 张；两端按区分大小写的完整路径求差，老名消失共 11 个。

| 版本 | Mojang 元数据 SHA-1 | Mojang 客户端 SHA-1 |
|---|---|---|
| 1.6.1 | [7fd8e0c76f62813eb0465e31bb74b160c01472d6](https://piston-meta.mojang.com/v1/packages/7fd8e0c76f62813eb0465e31bb74b160c01472d6/1.6.1.json) | [17e2c28fb54666df5640b2c822ea8042250ef592](https://launcher.mojang.com/v1/objects/17e2c28fb54666df5640b2c822ea8042250ef592/client.jar) |
| 1.6.4 | [b71bae449192fbbe1582ff32fb3765edf0b9b0a8](https://piston-meta.mojang.com/v1/packages/b71bae449192fbbe1582ff32fb3765edf0b9b0a8/1.6.4.json) | [1703704407101cf72bd88e68579e3696ce733ecd](https://launcher.mojang.com/v1/objects/1703704407101cf72bd88e68579e3696ce733ecd/client.jar) |
| 1.7.2 | [c2e8ecbf355760a74c93d7210767fa043d53f27c](https://piston-meta.mojang.com/v1/packages/c2e8ecbf355760a74c93d7210767fa043d53f27c/1.7.2.json) | [0c8689f904922af71c7144dcfb81bce976cadd49](https://launcher.mojang.com/v1/objects/0c8689f904922af71c7144dcfb81bce976cadd49/client.jar) |
| 1.7.10 | [ed5d8789ed29872ea2ef1c348302b0c55e3f3468](https://piston-meta.mojang.com/v1/packages/ed5d8789ed29872ea2ef1c348302b0c55e3f3468/1.7.10.json) | [e80d9b3bf5085002218d4be59e668bac718abbc6](https://launcher.mojang.com/v1/objects/e80d9b3bf5085002218d4be59e668bac718abbc6/client.jar) |
| 1.8 | [9eb165eef46294062d8698c8a78e8ac914949e7a](https://piston-meta.mojang.com/v1/packages/9eb165eef46294062d8698c8a78e8ac914949e7a/1.8.json) | [d722504db9de2b47f46cc592b8528446272ae648](https://launcher.mojang.com/v1/objects/d722504db9de2b47f46cc592b8528446272ae648/client.jar) |
| 1.8.9 | [d546f1707a3f2b7d034eece5ea2e311eda875787](https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json) | [3870888a6c3d349d3771a3e9d16c9bf5e076b908](https://launcher.mojang.com/v1/objects/3870888a6c3d349d3771a3e9d16c9bf5e076b908/client.jar) |
| 15w31a | [4c9ad2064a55148bef3da7d98dde8d9ff50596ea](https://piston-meta.mojang.com/v1/packages/4c9ad2064a55148bef3da7d98dde8d9ff50596ea/15w31a.json) | [d702effa2e2ff9f79ffc74dc6becc3eeca376fee](https://launcher.mojang.com/v1/objects/d702effa2e2ff9f79ffc74dc6becc3eeca376fee/client.jar) |
| 1.9 | [9178828493e41a24452841039710f05728c466d6](https://piston-meta.mojang.com/v1/packages/9178828493e41a24452841039710f05728c466d6/1.9.json) | [2f67dfe8953299440d1902f9124f0f2c3a2c940f](https://piston-data.mojang.com/v1/objects/2f67dfe8953299440d1902f9124f0f2c3a2c940f/client.jar) |
| 1.12.2 | [832d95b9f40699d4961394dcf6cf549e65f15dc5](https://piston-meta.mojang.com/v1/packages/832d95b9f40699d4961394dcf6cf549e65f15dc5/1.12.2.json) | [0f275bc1547d01fa5f56ba34bdc87d981ee12daf](https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar) |
| 1.14.4 | [be146d5f66a3627ed0a87c234c4d8dde8ab35098](https://piston-meta.mojang.com/v1/packages/be146d5f66a3627ed0a87c234c4d8dde8ab35098/1.14.4.json) | [8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9](https://piston-data.mojang.com/v1/objects/8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9/client.jar) |

两源名字只在所测的 1.6.1、1.6.4 含 PNG；新名从 1.7.2 起出现，且 1.7.10、1.8、1.8.9、15w31a、1.9、1.12.2 的两枚 PNG 的 RGBA 分别完全一致。除 JAR 路径缺失外，还按元数据的 SHA-1 和大小验证 [1.8 资产索引](https://launchermeta.mojang.com/v1/packages/f6ad102bcaa53b1a58358f16e376d548d44933ec/1.8.json)（1.8.9、15w31a，734 条）、[1.9 资产索引](https://piston-meta.mojang.com/v1/packages/d7aae43ea69d80cc3441bee4179abd791f6534cd/1.9.json)（1,049 条）与 [1.12 资产索引](https://piston-meta.mojang.com/v1/packages/a21e1ded1a24ea1548dd8db0cf30b6acb02655a9/1.12.json)（1,305 条）：均无两旧 PNG；这四版 JAR 的原版模型 JSON（分别 1,595、1,485、1,476、1,595 份）亦无两个旧 `items/` ID。

| 旧图集及格式 1 | 1.8.9 纹理 | 1.6.1 原始 RGBA SHA-256 | 1.8.9 原始 RGBA SHA-256 | 两端 16×16 像素差 |
|---|---|---|---|---|
| `items (9,5)`／`fish_raw` | `fish_cod_raw` | `74d1c89f0ffcdf660a0658021ba8140ddce4672b961e956a12ddd167bf6a4bcc` | `cbad743f1176973a11d86e4d0cf7ba7ed374ae1118d39925e1c16e1620c16fb2` | 74/256 都只变全透明点的隐藏 RGB；非零 alpha 的 RGBA 与全图 alpha 没有差。|
| `items (10,5)`／`fish_cooked` | `fish_cod_cooked` | `46c5aec721537b309dac37212d2374b2eb27cccd2889b4a28c70754a1998f511` | `46c5aec721537b309dac37212d2374b2eb27cccd2889b4a28c70754a1998f511` | 0/256；严格相等。|

[旧图集证据](legacy-atlas-expansion.md#L183-L184)已核对 1.5.2 `fishRaw`、`fishCooked` 与 1.6.1 对应纹理。此处仅为识别候选，将 **alpha=0 像素的 RGB** 归零后，两枚旧 PNG 在 1.8.9 的物品 PNG 中都恰好各自唯一命中。没有改变运行期 PNG 内容的操作，不假定隐藏 RGB 可在任意渲染器上忽略。

## 对象与渲染绑定，而非名字猜测

1. **1.6.4**：`javap -c -p -classpath 1.6.4.jar yc`，`yc(int)` 偏移 11–16 加 256，59–65 按同下标注册。类初始化偏移 2601–2624、2627–2650 用构造值 93/94（实际 ID **349/350**），依次将 `fishRaw`／`fishCooked` 和 `fish_raw`／`fish_cooked` 写入对应物品。`lang/en_US.lang` 为 `item.fishRaw.name=Raw Fish`／`item.fishCooked.name=Cooked Fish`。
2. **1.7.2**：`abn` 注册偏移 3105–3132、3138–3165 仍分别使用 **349/350**；`abf(false)`／`abf(true)` 代表生／熟。原 `fish_raw`、`fish_cooked` 字符串仍存在该注册路径里，不能据字符串存在断言 PNG 仍被用。`abg` 静态初始化偏移 0–19 注册 `COD`，变种号 0，名字 `cod`；`abg.a(pt)` 偏移 9–34 与 53–78 实际注册 `fish_` + 变种名 + `_raw`／`_cooked`。语言键分别变为 `item.fish.cod.raw.name`／`item.fish.cod.cooked.name`，英文显示仍为 `Raw Fish`／`Cooked Fish`，另有鲑鱼等独立变种，因此不做一对多。
3. **1.8.9、15w31a、1.9、1.12.2**：`assets/minecraft/models/item/cod.json` 的 `textures.layer0` 仍是 `items/fish_cod_raw`，`cooked_cod.json` 的是 `items/fish_cod_cooked`；同名模型在 1.14.4 分别引用 `item/cod`、`item/cooked_cod`。编码层和模型层的证据一致。

## 不可迁移的其余 9 个同域候选

- `blocks/leaves_{oak,spruce,birch,jungle}_opaque`：1.6.1 至 1.7.10 仍有此组（1.7.x 另添两材质），自 1.8 起组整体不见。在 1.7.2 `akx` 可见 `leaves_oak` 和 `leaves_oak_opaque` 被分别登记；1.8.9 `models/block/oak_leaves.json` 的 `textures.all` 指向原本就同存的 `blocks/leaves_oak`。不能把 fast 纹理覆盖作者已有的常规纹理。
- `items/skull_{creeper,skeleton,steve,wither,zombie}`：1.7.10 还有旧 PNG，1.8／1.8.9 已无这组。1.8.9 相应的物品模型 `skull_{creeper,skeleton,char,wither,zombie}.json` 为 `parent: "builtin/entity"`，不带 `textures`；二维图标没有可直接重命名的实体 UV 贴图目标。`skull_steve`→`skull_char` 是相关显示对象线索，不是 PNG 别名。
- 两个旧鱼 ID 加上述 9 个消失 ID，等于 1.6.1 与 1.8.9 的直接 PNG 路径差集。其它旧名在两端均实存；本次不推断同路径的内容或语义永远不变。

## 验证与限制

- 隔离脚本 `python build/early-texture-evidence-359960c/regression.py`：未加表时 `AssertionError: items/fish_raw still emitted as items/fish_raw; 1.9 cod.json requires items/fish_cod_raw`；加表后通过，检查两端与中间制品、唯一像素匹配、两条表键目标无碰撞，以及迁后 135 个旧图集 ID 都在 1.8.9 存在。`audit.py red` 则为未集成的旧全链路径复现；`assetcheck.py` 独立校验资产索引和全模型旧名缺失。
- 脚本和官方下载均留在忽略的独立 `build/early-texture-evidence-359960c/`（同项目的已校验 JAR 只读复用），不提交图像或字节码，也不运行共享 Gradle。验证是制品与规则数据的静态回归，不是 Java 集成或游戏实时测试；未断言后续进入 26.4-s10 的渲染结果。
