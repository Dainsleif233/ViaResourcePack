# 73.0 物品图集拆分：诊断与迁移边界

## 原版行为

下面的提交与行号来自本地反编译源码仓库 `<minecraft-reference>`，可用 `git show <commit>:<path>` 核对。

| 结论 | 源码 |
| --- | --- |
| **72.0 是拆分前的最后一版；73.0 才是首个拆分版本。** 25w44a 把 `item/` 和 `block/` 放在同一 atlas；25w45a 把 `item/` 及装备纹饰调色板放进 `items`，保留 `block/` 等来源在 `blocks`。 | `e7226d2e3:version.json:2–9`、`e7226d2e3:client/net/minecraft/client/data/AtlasProvider.java:84–85`；`a083a66d7:version.json:2–9`、`a083a66d7:client/net/minecraft/client/data/AtlasProvider.java:84–89`、`a083a66d7:client/net/minecraft/client/renderer/Sheets.java:60–61` |
| 原版直接加载固定 ID `minecraft:items`，因此生效文件为 `assets/minecraft/atlases/items.json`。`assets/<ns>/atlases/items.json`（`<ns>` 非 `minecraft`）不会成为原版物品图集；图集内的贴图资源和 sprite 本身可以使用自定义命名空间。 | `02189ae7c:client/net/minecraft/data/AtlasIds.java:12–13`、`02189ae7c:client/net/minecraft/client/resources/model/AtlasManager.java:43–57`、`02189ae7c:client/net/minecraft/client/renderer/texture/atlas/SpriteSourceList.java:40,77–85`、`02189ae7c:client/net/minecraft/client/renderer/texture/atlas/sources/DirectoryLister.java:29–34` |
| 普通模型贴图优先查 `items`，查不到才查 `blocks`。单个 wrapper 的四边形必须都在同一张图集，可以全是 items，也可以全是 blocks；混用会抛异常。 | `02189ae7c:client/net/minecraft/client/resources/model/ModelManager.java:199–212`、`02189ae7c:client/net/minecraft/client/renderer/item/BlockModelWrapper.java:122–140,157–164`；`634debadb:client/net/minecraft/client/resources/model/sprite/MaterialBaker.java:66–79`、`634debadb:client/net/minecraft/client/renderer/item/CuboidItemModelWrapper.java:110–124,141–149` |
| 生成模型依次处理 `layer0`–`layer4`，遇到首个空槽就停止；默认粒子贴图引用 `layer0`。 | `a083a66d7:client/net/minecraft/client/renderer/block/model/ItemModelGenerator.java:36–40,64–79`；`a083a66d7:client/net/minecraft/client/data/models/model/ModelTemplates.java:166–171`（原版扁平生成物品模板） |

## ViaResourcePack 2.0 的诊断

[ItemAtlasSplitDiagnostics.java](../../src/main/java/top/syshub/viarp/convert/ItemAtlasSplitDiagnostics.java) 在进入 **73.0** 时检查已投影源 overlay 的工作副本，只报告以下情况：

1. 本包的 `assets/<ns>/items/<id>.json` 直接使用 `minecraft:model`，且引用本包的几何模型。
2. 只沿本包的父模型查找常规生成器：到达 `minecraft:builtin/generated`，或没有被本包覆盖的常规原版 `minecraft:item/generated`；有 `elements`、自定义 `loader` 或无法确定父模型时跳过。
3. 由子及父得到的 `layer0` 开始的槽连续、至少两层，且均是字面 sprite ID；每个 ID 有同名、同大小写的包内 PNG 路径，并且 `item/` 和 `block/` 各有至少一层。这里检查路径存在，不解码 PNG 或执行模型烘焙。

告警只说按**原版目录来源**推断可能混图集。不模拟实际资源包栈、资源过滤器、作者定义的 sprite 别名、模组加载器或烘焙；原版命名空间内存在 `blocks` 或 `items` 定义时会在对应告警中特别注明。没有混层线索时，不会因为这个诊断而把非原版命名空间图集、全 item 或全 block 的模型视作错误。

资源清单沿用工作树的排序遍历；最多检查 2,048 个物品定义、每条链读取 8 个本包父模型（含被引用的起始模型），单个 JSON 最多解析 16 KiB、嵌套 24 层，累计读取预算 8 MiB，最多给出 32 条逐模型诊断。复杂或超限的资源不作推断。诊断不写入模型、图集或贴图。

## 代表性样例

在 `assets/minecraft/items/stick.json` 设置 `{"model":{"type":"minecraft:model","model":"demo:item/stick"}}`；在 `assets/demo/models/item/stick.json` 设置：

```json
{
  "parent": "minecraft:builtin/generated",
  "textures": { "layer0": "demo:item/base", "layer1": "demo:block/edge" }
}
```

| 同一测试输入的变化 | 预期 |
| --- | --- |
| 两张 PNG 都存在；没有自定义图集 | 告警：72.0 两层同在 blocks；按 73.0 原版目录拆分后可能混用。 |
| 两层都用 `item/` 或都用 `block/`，PNG 都存在 | 不告警：单图集是正常情况。 |
| 修改为非直接模型、缺少 PNG、`#` 引用、层序号有间隙或父链成环 | 不猜测实际贴图来源。 |
| 只有 `assets/demo/atlases/items.json`，无混层证据 | 不告警；不推广成 `minecraft:items`。 |
| 混层模型另带 `assets/minecraft/atlases/items.json` | 仍是线索告警，要求核对作者定义，不改写 JSON。 |

## 为什么不自动改资源

不能把整个 `blocks` 定义复制到 `items`，甚至不能把某个同名 sprite 全局复制过去：`items` 优先会改变别的消费者，原版还会记录跨图集重复的 sprite（`02189ae7c:client/net/minecraft/client/resources/model/AtlasManager.java:129–136`）。即使仅有低优先级的第三方包，也可能在原版目录来源之后追加 `single` 或过滤器，改变这个 ID 的实际指向；来源按栈顺序执行，后写覆盖前写（`02189ae7c:client/net/minecraft/client/renderer/texture/atlas/SpriteSourceList.java:47–85`）。当前[转换输入和缓存](../../src/main/java/top/syshub/viarp/convert/ConversionService.java#L287-L316)只有单包，不包含完整资源栈。

手工可修一个窄场景：确知完整有效栈，已有 `assets/minecraft/atlases/items.json` 把 `resource: "demo:block/edge"` 用 `minecraft:single` 声明成一个不同的、只在 items 的 `sprite: "demo:item_alias/edge"`。核对别名与原来解析的 block sprite 使用同一个有效 PNG 和侧车，再创建一个私有子模型，只把 `layer1` 指向该别名，最后让目标物品的 `minecraft:model` 指向子模型。不要改其他消费者使用的原模型或任何图集文件。`SingleFile` 查找精确资源并以别名提交，加载器读取同一 `Resource` 的元数据（`02189ae7c:client/net/minecraft/client/renderer/texture/atlas/sources/SingleFile.java:29–43`、`02189ae7c:client/net/minecraft/client/renderer/texture/atlas/SpriteResourceLoader.java:33–53`）。这保留图像来源和各层输入，不保证拆分前后的 mipmap 与采样像素完全一致。
