# 格式 43 → 44：钓竿与弓的旧物品模型谓词

**结论：只迁移原版钓竿、弓默认入口的受限谓词表。** 钓竿用 `minecraft:fishing_rod/cast`；弓用 `minecraft:using_item` 和不缩放的 `minecraft:use_duration`，从旧归一化阈值算出首个命中的整数 tick。保真对象是有效几何资源 ID 的选择，而不是截图、像素、烘焙结果或修改物品组件后的行为。

## 证据范围

- 相邻历史快照：24w44a（43）`7d9ca125f4d1ebdbdc412ed6fc43c5fc5b0672bd` 和 24w45a（44）`cb3c8c664c147eada1465398f7170c09e7f1150b`；另抽查 26.3（97.1）`634debadbbb7d440370f6ad29a86d1587058ce76`。下文 `ref:path:行号` 可在本地只读原版 Git 用 `git show <ref>:<path>` 复核。
- 43 和 44 的资源来自[已按官方 manifest 验证的 JAR](clock-compass-items.md#L5-L12)，本次复算 SHA-1 分别为 `aee14fb6019ac3460574eaecdad3b66a96dfd6e6`、`4db71417912fdd7be8aed0792bc09dc70e505ff5`。两端均有 `assets/minecraft/models/item/` 下的 `bow`、`bow_pulling_0…2`、`fishing_rod`、`fishing_rod_cast` 六份 JSON，只有 44 提供这两种物品的 `assets/minecraft/items/` 入口。几何路径相同不等于字节相同：44 原版 cast 几何的父模型由 `item/fishing_rod` 换成 `minecraft:item/handheld_rod`；弓拉弦叶子仍引用同 ID 的基础弓模型。迁移不改写父模型或贴图。

| 物品 | 43 原版覆盖（原始顺序） | 44 原版入口 |
| --- | --- | --- |
| 钓竿 | `cast:1` → `item/fishing_rod_cast` | `minecraft:condition`，属性 `minecraft:fishing_rod/cast`；false 为普通 `minecraft:item/fishing_rod`，true 为 `minecraft:item/fishing_rod_cast`；入口 SHA-1 `5028fd593033af0c59ed24e2030d7723c5b70a5e` |
| 弓 | `pulling:1`，`pulling:1,pull:0.65`，`pulling:1,pull:0.9` → `item/bow_pulling_0…2` | `minecraft:using_item` 条件的 false 为普通 `minecraft:item/bow`；true 为 `minecraft:use_duration` range，`scale:0.05`、fallback `minecraft:item/bow_pulling_0`、阈值 `0.65` / `0.9`；入口 SHA-1 `39897bae407d8faac332d87c5fb6f9e3b8e7edf7` |

## 状态与阈值证明

### 钓竿

43 的 `cast` 只给原版钓竿注册。对它的堆栈 `stack`，只有玩家有非空鱼钩且满足下式才返回 `1`，否则 `0`：

```text
main == stack || (!mainIsFishingRod && offhand == stack)
```

同一堆栈引用位于主手就意味着主手持钓竿；因此该式恰等于 `(mainIsFishingRod ? main : offhand) == stack`。44 和 97.1 用玩家主臂/对侧臂做等价选择：主手有钓竿时检查主手，否则检查副手；仍按引用比较，且要求玩家持有鱼钩。缺 owner、非 Player、无鱼钩、两手都抓钓竿却渲染与主手不同的副手堆栈，都不是 cast。有限阈值 `<=0` 在两种状态都命中，`(0,1]` 仅 true 命中，`>1` 不可达；不是只认作者写的整数 1。

### 弓

- 43 的 `pulling` 为 `owner != null && owner.isUsingItem() && owner.getUseItem() == stack`，44/97.1 的 `minecraft:using_item` 是该布尔值。43 `pull` 与新 `minecraft:use_duration` 的 elapsed 模式在缺 owner/不同堆栈引用时都取 0；其余用同一个形状的 int 差 `stack.getUseDuration(owner) - owner.getUseItemRemainingTicks()`。数值读取本身**没有** `isUsingItem()` 守卫；不能用原版入口的 false 静态分支代替任意自定义表的 false 分支。
- 旧数值是 `clamp((float) elapsed / 20.0f, 0.0f, 1.0f)`，新原版树用 `(float) elapsed * 0.05f`，不截断。新值对负 tick 小于 0、对大于 20 的 tick 大于 1；例如自定义阈值 `1.05` 可在新比例树命中，却永远不满足旧 `pull`。即使只看 `0…20`，也有三个不同的点：

| elapsed | 旧除法 float（位） | 新乘法 float（位） | 在该 tick 提前命中的阈值 |
| ---: | --- | --- | --- |
| 9 | `0.45`（`3ee66666`） | `0.45000002`（`3ee66667`） | `0.45000002f` |
| 13 | `0.65`（`3f266666`） | `0.65000004`（`3f266667`） | `0.65000004f` |
| 18 | `0.9`（`3f666666`） | `0.90000004`（`3f666667`） | `0.90000004f` |

三个乘法值都是旧值的下一可表示 float。原版实写的 `0.65` 和 `0.9` 向 float 舍入后等于旧除法值，因此两端默认表仍在 13 / 18 tick 切换；但不能把缩放式直接迁给自定义阈值。

对任意有限阈值 `p`：`p<=0` 恒真、`p>1` 恒假，其他阈值用旧 float 除法枚举求首个命中的 `t∈[1,20]`，即最小满足 `(float)t / 20.0f >= p` 的整数。所有 elapsed<=0 落在同一个回退区间，elapsed>=20 不能再改变结果；大整数转 float 虽可能舍入，但不会越回 1…20 内的边界。对 pulling 的两个状态分别编译阈值序列，写成正整数 `range_dispatch` 阈值，默认 `scale:1.0`、`remaining:false`；不发明额外的时间属性。

## 输出与保守拒绝

- 仅在迁移链跨过 44，且当前迁移视图有 `assets/minecraft/models/item/bow.json` 或 `assets/minecraft/models/item/fishing_rod.json` 时，用[已登记的原版入口](../../src/main/resources/viarp/migrations/intermediate/item-entrypoints-44.txt)写 `assets/minecraft/items/<物品>.json`。缺旧根文件（例如只改贴图）不生成；非 minecraft 命名空间、拉弦/cast 后缀几何不作入口。接入层先检查目标项是否已存在（含大小写折叠冲突），不会解析或覆盖作者的新入口。迁移仅改工作副本，不回写旧几何文件。
- 行严格为 `model` + `predicate`。只认裸键及其 `minecraft:` 拼法：弓 `pull/pulling`，钓竿 `cast`；不认双拼同 ID、其他命名空间的属性或混入 `custom_model_data` 等未知属性。数值必须是严格 JSON 数值并可直接舍入为有限 float；不经 double（例如 `0.05000000260770320892333984374999` 经 double 会把第一 tick 的边界向上舍入）。任何不支持的行整表拒绝，由接入层诊断并保留旧文件；不输出残缺规则。
- 叶子是 `{"type":"minecraft:model","model":"<资源 ID>"}`，几何路径不加/删 `item/`、后缀或斜线。合法裸 ID 补 `minecraft:`；显式有效命名空间（可以是作者命名空间）不动。不承诺宽松解析：空命名空间、额外冒号、非法字符、空路径段或点路径段拒绝。
- 旧覆盖表按末行优先，各行多个谓词为 AND。没有任何属性时旧引擎完全不遍历覆盖（缺字段、空数组或全是空谓词行都回基础模型）；有其他已知属性时，空谓词行无条件匹配并参与同样的末行优先。布尔分支逐状态求解；弓按各 tick 先解决行优先再仅写模型变化点，保证唯一阈值，不靠新引擎的排序解决重复/塌缩阈值。没有命中的旧模型 ID 是基础 ID；新的 range fallback 则是该状态下 elapsed<=0 应选的 ID，未必是基础模型。相同分支合并为一支，完全不变的序列输出普通模型叶子。
- 保证限于原版默认物品/入口及两代得到等价的 owner/堆栈状态、几何可正常解析的选择。旧属性按实际物品注册，新从堆栈 `ITEM_MODEL` 取树；把这两个入口给其他物品复用，旧属性可能未注册（取 `-Infinity`），新条件/计时属性仍可求值，不在等价域内。也不预测跨包叠加、缺失组件或引用、错误烘焙、特殊渲染、自定义 owner、mod 扩展或视觉结果。旧渲染器遇到无匹配或选中的烘焙模型为 null 会用基础几何；普通 `minecraft:model` 的坏引用不应视作该旧 null 回退的等价替代。

## 源码锚点与复核

- 43：`7d9ca125f:client/net/minecraft/client/renderer/item/ItemProperties.java:58–60,70–86,102–109,117,185–196`；`7d9ca125f:client/net/minecraft/client/renderer/item/ClampedItemPropertyFunction.java:16–24`；`7d9ca125f:client/net/minecraft/client/renderer/block/model/ItemOverride.java:39–51`；`7d9ca125f:client/net/minecraft/client/renderer/block/model/BakedOverrides.java:42–74,92–99`；`7d9ca125f:client/net/minecraft/client/renderer/entity/ItemRenderer.java:221–225`；`7d9ca125f:client/net/minecraft/client/renderer/ItemModelShaper.java:17–34`。
- 44：`cb3c8c664:client/net/minecraft/client/renderer/item/properties/conditional/FishingRodCast.java:20–34`、`cb3c8c664:client/net/minecraft/client/renderer/entity/FishingHookRenderer.java:67–69`；`cb3c8c664:client/net/minecraft/client/renderer/item/properties/conditional/IsUsingItem.java:21–27`、`cb3c8c664:client/net/minecraft/client/renderer/item/properties/conditional/ConditionalItemModelProperties.java:31–34`、`cb3c8c664:client/net/minecraft/client/renderer/item/properties/numeric/UseDuration.java:25–43`；`cb3c8c664:client/net/minecraft/client/renderer/item/RangeSelectItemModel.java:56–78,116–143`、`cb3c8c664:client/net/minecraft/client/renderer/item/ConditionalItemModel.java:43–57`、`cb3c8c664:client/net/minecraft/client/renderer/item/BlockModelWrapper.java:65–77`；`cb3c8c664:client/net/minecraft/client/resources/model/ItemStackModelLoader.java:36–49`、`cb3c8c664:client/net/minecraft/client/renderer/item/ItemModelResolver.java:60–68`。
- 97.1 静态抽检：`634debadb:client/net/minecraft/client/renderer/item/properties/conditional/FishingRodCast.java:21–35`、`634debadb:client/net/minecraft/client/renderer/entity/FishingHookRenderer.java:75–77`、`634debadb:client/net/minecraft/client/renderer/item/properties/conditional/IsUsingItem.java:22–28`、`634debadb:client/net/minecraft/client/renderer/item/properties/conditional/ConditionalItemModelProperties.java:33–36`、`634debadb:client/net/minecraft/client/renderer/item/properties/numeric/UseDuration.java:26–46`；`634debadb:client/net/minecraft/client/renderer/item/ConditionalItemModel.java:50–57`、`634debadb:client/net/minecraft/client/renderer/item/RangeSelectItemModel.java:57–89,95–109`、`634debadb:client/net/minecraft/client/renderer/item/ItemModels.java:24–36`、`634debadb:client/net/minecraft/client/renderer/item/ItemModelResolver.java:55–63`。条件和数值属性都取 `ItemOwner.asLivingEntity()`；不是对不同渲染上下文 owner 生成策略相同的断言。
- [转换器](../../src/main/java/top/syshub/viarp/convert/ClassicItemPredicates.java)用独立 Java 8 夹具对照 32,500 个模型选择状态点：真实 JAR 数组、负/零/过饱和 elapsed、边界 float、int 端点、优先顺序、空/畸形输入、跨命名空间 ID、20 阈值二分分支及固定种子随机表；另对照两份 44 原版入口的 484 个选择点，并穷举钓竿的 36 个 owner、鱼钩及手部组合。交付验证仅为静态原版读取与合成 Java 计算，未启动游戏、未作视觉断言。
