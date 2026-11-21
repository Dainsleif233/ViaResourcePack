# 格式 3 → 4：独立方块模型覆盖

## 原版依据

对 [1.12.2](https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar)、[1.13](https://piston-data.mojang.com/v1/objects/c0b970952cdd279912da384cdbfc0c26e6c6090b/client.jar) 和 [1.14.4](https://piston-data.mojang.com/v1/objects/8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9/client.jar) 正式客户端 JAR 核对 SHA-1 和字节数；元数据及核验值见[方块状态证据](classic-blockstates.md#制品)。读取实际 ZIP 成员，用 `javap -p -c` 检查资源查找和模型属性，不启动客户端。

- 1.12.2 的 `cgb.c(nf)` 经 `cgb.d(nf)`，1.13 的 `des.c(pc)` 与 1.14.4 的 `dys.c(qv)` 均把普通模型 ID 变为同命名空间下的 `models/<ID 路径>.json`，再经资源管理器查找。`dys.c(qv)` 对应 [1.14.4 官方映射](https://piston-data.mojang.com/v1/objects/6073e4ba6949217eb708c4512be2ccc1850a603f/client.txt)的 `ModelBakery.loadBlockModel(ResourceLocation)`。即使包未提供 `blockstates`，原版状态依然会按其模型 ID 读取包内覆盖。
- 1.12.2 的状态模型字段会自动加 `block/`；三个版本的模型 JSON 反序列化器直接解析 `parent` 的资源 ID，不加此目录。1.13 的 `cur.e()` 与 1.14.4 的 `doe.e()` 直接返回当前模型自己的 `overrides` 字段。因此只写一个 `parent` 转发并不等价，尤其在其他模型把它作为物品模型使用时。
- 下表来源为两端原版状态实际指向的 `assets/minecraft/models/block/*.json` 成员。四个旧模型均以 `block/cube_all` 为父级。新旧解析后的模型 JSON 除纹理资源 ID 外相等；该原版父级也相等。

| 1.12.2 状态 | 1.12.2 模型文件名 | 1.13 / 1.14.4 状态及模型文件名 |
| --- | --- | --- |
| `silver_wool` | `silver_wool` | `light_gray_wool` |
| `smooth_granite` | `granite_smooth` | `polished_granite` |
| `smooth_diorite` | `diorite_smooth` | `polished_diorite` |
| `smooth_andesite` | `andesite_smooth` | `polished_andesite` |

三种抛光石材的纹理是 `blocks/stone_{granite,diorite,andesite}_smooth` → `block/polished_{granite,diorite,andesite}`；浅灰羊毛是 `blocks/wool_colored_silver` → `block/light_gray_wool`。旧端和 1.13 的三个抛光石材 PNG 各对字节相同；1.14.4 的对应 PNG 名称不变，内容更新。另经实际模型纹理槽与 PNG 核实、但不增加本模块复制目标的改名：`blocks/stone_{granite,diorite,andesite}` → `block/{granite,diorite,andesite}`、`blocks/tallgrass` → `block/grass`、`blocks/deadbush` → `block/dead_bush`。这五对 PNG 在 1.12.2 与 1.13 之间也逐对字节相同。

## 实现范围与边界

[独立模型规则](../../src/main/java/top/syshub/viarp/convert/ClassicModelOverrides.java)在格式 4 的状态及模型纹理引用改写**之后**运行。仅复制四个精确旧路径到未占用的新路径；保留旧 ID 和原有引用，不要求包提供状态文件。复制的是已完成纹理引用改写的**整份文件**，保留作者的父级、几何、显示、覆盖谓词与顺序，不生成空转发模型。自定义命名空间不改名。

目标已有文件、同一文件路径仅大小写不同或目标为目录时不覆盖；同折叠的源路径不明确时不复制。旧文件须能通过有大小上限的严格 UTF-8 JSON 对象读取（拒绝重复键）；沿 `parent` 与 `overrides[*].model` 检查至多 32 个包内模型；遇到本地依赖不明确或将形成自引用时不生成目标。外部包和扩展不在检查范围内；不会把未知引用解释为某个重命名。

- 复制保留经过核实的模型身份，不保证消费该模型的原版资源集合不变：1.14.4 的三个 `polished_*_slab` 状态另以同一模型提供 `type=double` 外观（1.13 尚无这三个状态路径）。这是覆盖对应现代模型 ID 的自然结果，不是新增 slab 迁移规则。物品模型可继承现代方块模型，本规则不移动 `models/item` 文件。
- 不复制 `tall_grass` 或 `grass_normal`：旧 `block/grass` 是两层草方块模板、由 `grass_normal` 继承；新 `block/grass` 是单格交叉面草，同址不同身份。单纯复制会覆盖显式现代草或使旧父模板链指向短草。旧 `grass_snowed` 对应 `grass_block_snow`，两端均由草方块、灰化土和菌丝体共享；也留给完整草方块模型依赖的后续方案。双格高草、任意自定义模型链和外部资源包间的覆盖关系不在此阶段处理。

## 最小验证

Java 8 目标临时探针直接调用复制模块，无 Gradle、无游戏运行。四个模型覆盖均不提供状态文件，验证原路径保留、目标文件逐字节相同、继承自定义父级且保留元素、显示字段、未知扩展和两个有序覆盖；重复调用不改变文件集合或字节。另验显式现代目标、大小写冲突、目标目录不会覆盖，并且不阻塞独立规则；大小写不明确的源、重复 JSON 键、直接父级或覆盖自引用均保留而不生成目标；草和自定义命名空间、物品模型不参与复制。
