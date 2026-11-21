# Classic 声音迁移：format 1 → 2 → 3 → 4

**事件 ID、文件 ID、字幕键是三套名字。** 比如唱片 13 的事件为 `records.13 → record.13 → music_disc.13`，JSON 时代的文件始终是 `sounds/records/13.ogg`。烟花事件改名两次，文件仅在 format 3 改大小写。

| 边 | 证据支持的首批规则 |
| --- | --- |
| 1 → 2 | [10 个逻辑事件](../../src/main/resources/viarp/migrations/classic/format-2-sound-events.tsv)；[506 条早期物理别名](../../src/main/resources/viarp/migrations/classic/format-2-sound-files.tsv)。不合成 sounds.json。 |
| 2 → 3 | 原有小写适配覆盖两条确定物理变化；无事件更名。 |
| 3 → 4 | [17 个逻辑事件](../../src/main/resources/viarp/migrations/classic/format-4-sound-events.tsv)；1.13 和 1.14.4 目标一致。不移 OGG。 |

### 证据来源

从 [Mojang launcher manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 按版本取元数据，核验 manifest 项 SHA-1、`downloads.client` 与 `assetIndex` 的 SHA-1/长度，以及索引中声音 JSON 的 SHA-1/长度。OGG 比对使用**已核验索引声明**的哈希，未逐个下载。检查 JAR 条目及 `javap -p -c -classpath <jar> <class>`，未运行游戏。采样 JAR 不内置声音 JSON 或 OGG。1.6.1/1.6.4/1.7.2 共用虚拟 legacy index（定义路径 `sounds.json`），其余索引为 `minecraft/sounds.json`。用 [Mojang 1.14.4 client mappings](https://piston-data.mojang.com/v1/objects/6073e4ba6949217eb708c4512be2ccc1850a603f/client.txt) 核对 1.14.4 类名，校验 SHA-1 即链接对象哈希。

| MC | JAR SHA-1 | Asset index SHA-1 | sounds.json SHA-1 |
| --- | --- | --- | --- |
| 1.6.1 | `17e2c28fb54666df5640b2c822ea8042250ef592` | `770572e819335b6c0a053f8378ad88eda189fc14` | [对象](https://resources.download.minecraft.net/fc/fce0bb3e14ade7b292a7c1d5fa785533d8106d82) `fce0bb3e14ade7b292a7c1d5fa785533d8106d82` |
| 1.6.4 | `1703704407101cf72bd88e68579e3696ce733ecd` | `770572e819335b6c0a053f8378ad88eda189fc14` | [对象](https://resources.download.minecraft.net/fc/fce0bb3e14ade7b292a7c1d5fa785533d8106d82) `fce0bb3e14ade7b292a7c1d5fa785533d8106d82` |
| 1.7.2 | `0c8689f904922af71c7144dcfb81bce976cadd49` | `770572e819335b6c0a053f8378ad88eda189fc14` | [对象](https://resources.download.minecraft.net/fc/fce0bb3e14ade7b292a7c1d5fa785533d8106d82) `fce0bb3e14ade7b292a7c1d5fa785533d8106d82` |
| 1.8.9 | `3870888a6c3d349d3771a3e9d16c9bf5e076b908` | `f6ad102bcaa53b1a58358f16e376d548d44933ec` | [对象](https://resources.download.minecraft.net/5e/5e06ca070067486427a3167ade2ffe01623e5591) `5e06ca070067486427a3167ade2ffe01623e5591` |
| 1.9 | `2f67dfe8953299440d1902f9124f0f2c3a2c940f` | `d7aae43ea69d80cc3441bee4179abd791f6534cd` | [对象](https://resources.download.minecraft.net/97/97accfa8d1505c3c2de436883c58efff7266fdc5) `97accfa8d1505c3c2de436883c58efff7266fdc5` |
| 1.10.2 | `dc8e75ac7274ff6af462b0dcec43c307de668e40` | `7c2800b458376b8fc0b738382fb7784328fddda9` | [对象](https://resources.download.minecraft.net/49/49d7e221b4c0a63f2294e956c7e8fd55362ae02a) `49d7e221b4c0a63f2294e956c7e8fd55362ae02a` |
| 1.11 | `780e46b3a96091a7f42c028c615af45974629072` | `c64959fe73672e9b053b157f57b6aba318d0b3b5` | [对象](https://resources.download.minecraft.net/a0/a09de8942defe1fb7326af410208a50a8b5e90ed) `a09de8942defe1fb7326af410208a50a8b5e90ed` |
| 1.12.2 | `0f275bc1547d01fa5f56ba34bdc87d981ee12daf` | `a21e1ded1a24ea1548dd8db0cf30b6acb02655a9` | [对象](https://resources.download.minecraft.net/97/975a99ed9870f51bbae348533d775d730e3fee18) `975a99ed9870f51bbae348533d775d730e3fee18` |
| 1.13 | `c0b970952cdd279912da384cdbfc0c26e6c6090b` | `2175b85e150c64f7ed285e7624b87c18cd992497` | [对象](https://resources.download.minecraft.net/e6/e6fbfd02f0c7b3cf745cd623e2a9e218ff41a32f) `e6fbfd02f0c7b3cf745cd623e2a9e218ff41a32f` |
| 1.14.4 | `8c325a0c5bd674dd747d6ebaa4c791fd363ad8a9` | `43b2f3021fe9f7d768378de95538e22da3ee8301` | [对象](https://resources.download.minecraft.net/b9/b9be2246b416bbff0749415d4482a06d82fee78a) `b9be2246b416bbff0749415d4482a06d82fee78a` |

### 比对口径和覆盖量

标准化字符串 clip 为 `{"name":"…","type":"file"}`。非空直连事件的指纹是 `(OGG SHA-1, weight, volume, pitch, stream, preload, attenuation_distance)` 多重集合，默认分别 1/1/1/false/false/16；旧解析器无后两项，直连样本也未显式设非默认值。保留重复项，不把引用递归摊成文件，不把 category、字幕或 replace 当成音色。候选需旧键独有、新键独有，且两侧**全量**事件中各唯一；另比对实际文件 ID。候选不等于行为身份，例如 `random.chestopen` 的唯一同音色目标是末影箱，不能据此搬普通箱子覆盖。反之同音色 UI 或火焰弹可用调用消歧。注册锚点为 1.9 `nf/ng`、1.12.2 `qe/qf`、1.13 `wh/wi`、1.14.4 `yp`。用 `ldc <键>` → `putstatic` 锁定字段，再看消费者 `getstatic`；1.8.9 多用直接字符串。

| 版本对 | 事件旧独有 / 共有 / 新独有 | 唯一直连候选（多 clip） | OGG 旧独有 / 共有 / 新独有；共有改字节 |
| --- | --- | --- | --- |
| 1.7.2 → 1.9 | 227 / 2 / 442 | 146（63） | 0 / 532 / 331；4 |
| 1.8.9 → 1.9 | 244 / 2 / 442 | 157（70） | 4 / 574 / 289；5 |
| 1.9 → 1.11 | 1 / 443 / 50 | 0 | 2 / 861 / 139；0 |
| 1.11 → 1.12.2 | 0 / 493 / 56 | 0 | 0 / 1000 / 85；0 |
| 1.12.2 → 1.13 | 135 / 414 / 248 | 88（43） | 0 / 1085 / 321；0 |
| 1.12.2 → 1.14.4 | 135 / 414 / 381 | 同 88 条 | 0 / 1085 / 694；26 |
| 1.13 → 1.14.4 | 0 / 662 / 133 | 0 | 0 / 1406 / 373；26 |

OGG 此处只数 JSON 加载器使用的 `sounds/…`。146 条早期候选全部也在 157 条中，两次 3→4 得同 88 条；各候选实际 clip ID 未改。1.8.9 的 4 个移除路径 `mob/guardian/guardian_idle{1..4}.ogg` 在 1.9 无同字节后继。

## 1 → 2：同属 format 1，加载路径也已先变过

- 1.6.1 `blc.h()` 递归列客户端资源根目录，`blc.a(File)` 只分派 `sound/`、`records/`、`music/`。`blf.a(String)` 去扩展名，sound/music 池再去尾数字、用点替换斜杠；`blf.b(String)` 组内 `nextInt(size)` 随机选。`blf.c(String)` 造 `minecraft:<pool>/<file>` URL；`blg.openConnection → blh.getInputStream → bje.a(bjd)` 通过资源管理器按**旧路径**取音频。包可以覆盖已登记的 clip，非 sounds.json 机制，不能从包里任意文件推导新事件。1.6.4 `bln` 同样用旧池；两个 JAR 都无加载 sounds.json 的类常量。
- 1.7.2 `btc` 读取各 namespace 的 sounds.json，文件分支只查 `sounds/<id>.ogg`，**不回退**单数或独立池；1.8.9 `bpz`、1.9 `byx/byf` 同理。共享 legacy 索引含两套文件并自带 1.7.2 复数路径的 vanilla fallback，并不证明 1.6.x 会读共享的 JSON。迁移器不需写入原版资源。
- 物理表生成：把 legacy 的 `sound/<suffix>.ogg`、`records/<disc>.ogg`、`music/<basename>.ogg` 分别候选到 `sounds/<suffix>.ogg`、`sounds/records/<disc>.ogg`、`sounds/music/game/<basename>.ogg`。只收新路径在 1.7.2 和 1.9 都有 type:file 消费者、两端新文件和旧池文件同 SHA-1 的项。结果 482/491、12/12、12/12；排除 4 条换字节的 `ambient/weather/rain1..4` 和至少一端无人用的 `note/bass`、`random/{breath,classic_hurt,door_close,door_open}`。例：
  - `sound/ambient/cave/cave1.ogg → sounds/ambient/cave/cave1.ogg`（`29d4dccf3353334c7aa2a49cb6fed3780a51a1ba`）。
  - `records/13.ogg → sounds/records/13.ogg`（`9cbed0e40296f68c61090c9b81816061e068a0ec`）。
  - `music/calm1.ogg → sounds/music/game/calm1.ogg`（`50a59a4f56e4046701b758ddbb1c1587efa4cadf`），1.9 `music.game` 保持流加载。
- 共享 legacy index 不是 1.6.1 首发历史快照。format 1 声明不能区分包为 1.6.x 还是 1.7+ 创作；1.7+ 包如果留了当时被忽略的旧位置，新表可能重新启用它。只处理确实存在的精确旧路径；有人提供目的文件则目的优先。证明使用 vanilla SHA-1，不用该 SHA 过滤自制音频，不创建原版 OGG 或 JSON。

| 1.8.9 → 1.9 事件 | 相同 clip 数 | 旧 → 新调用锚点 |
| --- | ---: | --- |
| `gui.button.press → ui.button.click` | 1 | GUI widget，`avs → bcz: ng.go` |
| `random.levelup → entity.player.levelup` | 1 | 玩家升级，`wn → zj: ng.eh` |
| `random.drink/eat → entity.generic.drink/eat` | 1 / 3 | 消耗动作，`wn → sa: ng.bB/bC` |
| `ambient.weather.thunder → entity.lightning.thunder` | 3 | 闪电初始 tick，`uv → ya: ng.dh` |
| `tile.piston.in/out → block.piston.contract/extend` | 各 1 | 活塞收缩/伸出，`als → aqu: ng.dU/dV` |
| `mob.cow.say → entity.cow.ambient` | 4 | 牛 ambient accessor，`to → vy: ng.am` |
| `mob.bat.idle → entity.bat.ambient` | 4 | 蝙蝠 ambient accessor，`tk → vu: ng.w` |
| `item.fireCharge.use → item.firecharge.use` | 1 | 火焰弹交互，`zm → ade: ng.bl`，与其它事件共用 clip，调用消歧 |

暂缓：12 首原版 `records.* → record.*` 都保持 `stream:true`，只比对了声音图的这一批不自动推广。`fireworks.largeBlast[_far] → entity.firework.large_blast[_far]` 的 OGG 在此边仍是 camel-case。`music.game.creative → music.creative` 保持六个流式 creative clip 和一个 `type:event` 引用 `music.game`，后者 12 个流式 clip 在 1.7.2/1.8.9/1.9 参数与哈希相同。`mob.cow.hurt` 可对应 hurt/death，两者不可凭哈希择一；`step.stone` 可对应 12 个材质/子类，不能扇出。

## 2 → 3：两条物理更名，无逻辑更名

1.9→1.11 与 1.10.2→1.11 均为 `fireworks/largeBlast1 → fireworks/largeblast1`（`ef59205829f09ac4855c614a0e7046db820df18b`）以及 `fireworks/largeBlast_far1 → fireworks/largeblast_far1`（`bfb0e14bf130de4ecda75d8e793260e8bbaca35b`）；事件仍是 `entity.firework.large_blast[_far]`。唯一消失的 `entity.experience_orb.touch` 与既有 `entity.experience_orb.pickup`、`entity.arrow.hit_player` 共用 `random/orb`，不是新增替代键。同名事件也可能改随机组，如 `ambient.cave` 增 clip。

## 3 → 4：同一 clip 不改文件名

以下每行验证了相同 clip ID、哈希、播放参数，目标注册可在 1.13 `wi` 重查。表内斜杠表示成对缩写，不是通配符。

| 1.12.2 → 1.13/1.14.4 事件 | clip 数 | 1.12.2 → 1.14.4 消费者 |
| --- | ---: | --- |
| `block.enderchest.open/close → block.ender_chest.open/close` | 各 1 | `avs: qf.aT/aS → bui: yp.cv/cu`（EnderChestBlockEntity） |
| `block.note.harp/basedrum → block.note_block.harp/basedrum` | 各 1 | `ask: qf.eA/eu → bwu: yp.gU/gO`（NoteBlockInstrument） |
| `entity.enderdragon.flap → entity.ender_dragon.flap` | 6 | `abd: qf.aX → asp: yp.cz`（EnderDragon） |
| `entity.endermen.ambient/hurt → entity.enderman.ambient/hurt` | 5 / 4 | `acu: qf.bd/bf → aui: yp.cF/cH`（EnderMan） |
| `entity.firework.large_blast/large_blast_far → entity.firework_rocket.large_blast/large_blast_far` | 各 1 | `bsu$c: qf.bG/bH → dlc$e: yp.dj/dk`（FireworkParticles$Starter） |
| `entity.firework.launch → entity.firework_rocket.launch` | 1 | `aem: qf.bI → awr: yp.dl`（FireworkRocketEntity） |
| `entity.lightning.thunder → entity.lightning_bolt.thunder` | 3 | `aci: qf.dK → atu: yp.fX`（LightningBolt） |
| `entity.irongolem.attack → entity.iron_golem.attack` | 1 | `aak: qf.dj → ari: yp.ft`（IronGolem） |
| `entity.zombie.attack_door_wood → entity.zombie.attack_wooden_door` | 4 | `buy: qf.jj → dng: yp.nT`（LevelRenderer 方块事件） |
| `entity.bobber.splash → entity.fishing_bobber.splash` | 1 | `acf: qf.K → ats: yp.ak`（FishingHook） |
| `entity.snowman.hurt → entity.snow_golem.hurt` | 3 | `aai: qf.hs → aru: yp.kX`（SnowGolem） |
| `entity.magmacube.squish → entity.magma_cube.squish` | 4 | `add: qf.dY → aur: yp.gl`（MagmaCube） |
| `record.13 → music_disc.13` | 1 | `ain: qf.gb → bck: yp.jp`（Items 唱片注册） |

## 引用、命名空间与实现边界

| 版本 | `assets/custom/sounds.json` 内无前缀名 `foo` | 字节码锚点与例外 |
| --- | --- | --- |
| 1.7.2 / 1.8.9 | file 和 event 都使用 owner namespace：`custom:foo` | `btc/bte/bqo`、`bpz/bpz$2/jy`。显式 file 按 namespace/path 拆分；显式 event delegate 则把原始 `name` 直接放进两参数构造器的 path，字面冒号保留。不要把这种旧引用当现代显式引用来猜。 |
| 1.9 / 1.12.2 | 都解析成 `minecraft:foo`；事件**键**仍属于 `custom` | `byh/byf/kk/byx$2`、`cho/cho$2/cgq/nf`。单参数 ID 构造；采样版本显式单字符 namespace 回落至 minecraft。 |
| 1.13 / 1.14.4 | 同上 | `dgj/dfj/pc`、`eap$a$1/dzm/qv`。显式单字符 namespace 不再回落，无效 namespace/path 字符会被拒。 |

- 跨 1→2，不把早期自定义包无前缀名当 vanilla；需要保留旧 owner 绑定时，可写合法显式 `custom:foo`。事件引用不做 OGG fallback，文件 clip 不走事件表。有效绑定 vanilla 的引用可以映射至原版目标，无需本包额外定义。若目的键/路径已由作者提供，则保留它，不自动覆盖或合并。旧定义留作兼容；已证且无冲突的 vanilla 事件引用仍改为目标名，以便绑定新原版事件。保留原有 JSON 条目顺序、数组中的重复 clip、`replace` 和各 flags；遇到重复对象键则保留原文。
- 1.8.9 `bpz$2` / 1.9 `byx$2` 使用目标权重和所选声音，不应用 edge volume/pitch/stream。1.12.2 `cho$2` 乘 volume/pitch、OR stream，复制 edge weight 至返回文件，选择仍按目标总权重。1.14.4 `eap$a$1` 又带回所选 file 的 preload/attenuation_distance。随机分布等价需要保留图结构而非平铺。
- `entity.parrot.imitate.magmacube → entity.parrot.imitate.magma_cube` 的唯一 edge `entity.magmacube.squish → entity.magma_cube.squish` 指到相同 4 个文件，edge volume 0.6、pitch 1.8 不变。首批只收已证的 squish 目标。反例：`entity.parrot.imitate.evocation_illager → entity.parrot.imitate.evoker` 改从 ambient 引到 cast_spell，不能因 edge flags 一样就判为更名。另 `enderdragon.ambient` / `growl` 同音色不能消歧。
- 字幕和事件迁移分离。1.13 某些原版定义误写复数 `subtitles`，不能推广成通用字段。未知类型、重复键和循环留存，不推断。以上映射依据是发行物静态证据，不声称每个中间快照一致。

实现另在隔离 Minecraft 1.14.4 + Fabric Loader 0.19.5（Java 8）中实载格式 1 的按钮声效包：升级到格式 4，原版声音引擎成功启动；缓存中的 `ui.button.click` 与旧定义一致，`sounds/random/click.ogg` 与源 OGG 逐字节相同。未试听或测量播放分布。
