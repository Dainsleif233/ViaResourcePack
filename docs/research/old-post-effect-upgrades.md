# 资源包格式 35→36：旧后处理链的可保真子集

结论：只迁移引用原版程序的双 pass **反色**链；**实体轮廓**即使外形与原版完全一致，也不能只靠改路径和字段保留渲染语义。规则不复制程序、GLSL 或原版 JSON，不处理任意后处理效果。

## 证据与边界

| 客户端 | Mojang 元数据、客户端 SHA-1 | MC Git 提交 |
| --- | --- | --- |
| 24w33a，资源 35 | [版本 JSON](https://piston-meta.mojang.com/v1/packages/f8c6aff027e4aee548e04893f97ef3f040c83cbc/24w33a.json) · [客户端 JAR](https://piston-data.mojang.com/v1/objects/376d3cee0b5043b11163dfac67f0a6197c3e97bc/client.jar) · `376d3cee0b5043b11163dfac67f0a6197c3e97bc` | `03d2adcb88eb48f98080c301f3a77e7e993dec91` |
| 24w34a，资源 36 | [版本 JSON](https://piston-meta.mojang.com/v1/packages/592285c7bc4954b46aef5e3956f562c66180c818/24w34a.json) · [客户端 JAR](https://piston-data.mojang.com/v1/objects/f252db3368f588fa8c946dcbeed712bfe109e816/client.jar) · `f252db3368f588fa8c946dcbeed712bfe109e816` | `03f39551638e20d6e93211a6902d9f6fc686e645` |

SHA-1 按 Mojang 版本 JSON 的 `downloads.client.sha1` 核对，资源格式按 JAR 内 `version.json` 核对；末列是本地 `D:/Code/jsucraft/MC` 中对应的客户端源码提交。以下文中的 35／36 源码行号均针对这些快照，而非提交后的新版。

- 两个原版链的内容都从 `assets/minecraft/shaders/post/<id>.json` 移到 `assets/minecraft/post_effect/<id>.json`；逐 pass 对比，仅将裸 `name` 换成 `program: minecraft:post/<name>`，目标、采样及 uniform 数组不变。35 的 `GameRenderer.java:337`、`LevelRenderer.java:235` 使用旧路径；36 的 `GameRenderer.java:176–187,462–468`、`LevelRenderer.java:146,494–496` 使用效果 ID，`ShaderManager.java:68–71,85–111,164–182` 映射到新资源目录。链原路径在 36 是同名**程序** JSON 的路径，不能仅复制、保留旧链。
- 35 的 `EffectInstance.java:51,72–81,151–162` 在 `shaders/program/` 查找裸程序名和阶段。36 的 `PostChainConfig.java:64–68` 解码 `program` 和同形 uniform 数组，`PostChain.java:74–87` 用 `POSITION` 顶点格式／空 defines 链接程序；程序与阶段改查 `shaders/post/` 下的资源。两个快照中的 `blit.vsh`、`blit.fsh`、`invert.fsh`、`sobel.vsh`、`entity_sobel.fsh`、`blur.vsh`、`entity_outline_box_blur.fsh`，按上述旧、新目录逐文件比较均逐字节相等。

## 为什么反色可以迁移，而实体轮廓不能

35 的 `EffectInstance.java:130,169–209,235` 读取并应用程序 JSON 的 `blend`；`BlendMode.java:34–62` 对非分离模式用同一对因子混合 RGBA。35 的 `PostPass.java:63–89` 和 36 的 `PostPass.java:57–87` 都先清到透明黑后画全屏。36 的 `ShaderProgramConfig.java:23` 不解码 `blend`，`PostPass` 也不按程序设置它。36 轮廓模糊程序 JSON 残留的 `blend` 不是运行时支持。

- **反色：**35 `shaders/program/invert.json` 的源／目标因子是 ONE／ZERO；`invert.fsh` 将输出 alpha 固定为 1。第二个 pass 的 `blit.fsh` 用中性 `ColorModulate` 时仍输出 alpha=1，因此 35 的 SRC_ALPHA／ONE_MINUS_SRC_ALPHA 等价于直接写入；36 的 `GameRenderer.java:462–468` 执行屏幕后处理前显式禁用混合。原版反色链常量是 0.8，**程序默认值**才是 0；省略 `InverseAmount` 必须补 0，不能补 0.8。
- **共享 uniform：**36 `ShaderManager.java:248–257` 缓存编译程序，`PostPass.java:63–68` 只写声明的 uniform。原版 `post_effect/spider.json:258–269` 给同一个 `minecraft:post/blit` 写入非中性调色。迁移结果比原版 36 反色链多一个显式中性 `ColorModulate`，避免继承旧链不会接受的共享状态。
- **实体轮廓：**35 `shaders/program/entity_outline.json` 使用 SRC_ALPHA／ONE_MINUS_SRC_ALPHA，阶段是 `sobel.vsh` 与 `entity_sobel.fsh`。Sobel RGB 来自五个邻域采样的颜色和 alpha，输出 alpha 则来自四个差值，二者不等价。例如五个不透明同色采样输出非零 RGB、零 alpha；35 该 pass 混入透明黑后得到全零，36 单纯直写是非零 RGB、零 alpha。一般情况下 35 得到 `(rgb * alpha, alpha²)`，而直写得到 `(rgb, alpha)`；后两个带线性采样的 blur pass 会读取这个中间结果。末尾 `blit` 也不能保证只接收 alpha=1。改字段或照搬 36 链均无法指定旧混合；不新写 shader，不迁移此链。保留旧字节不等于兼容：旧链若在 36 被选为 `minecraft:post/entity_outline` 的程序资源，会遮蔽原版程序，并因缺少 `vertex`／`fragment` 而无法解码（36 `ShaderManager.java:85–101,166–175,265–269`）。规则会报告这个风险，不擅自覆盖或隔离文件。

## 放行条件和验证

[独立迁移规则](../../src/main/java/top/syshub/viarp/convert/OldPostEffectUpgrades.java#L53-L131)只接受原版 `minecraft` 路径、`main → swap → main`、一个默认全屏 `swap`、各 pass 只有一个最近邻颜色采样、裸 `invert`／`blit` 程序引用、有限且位于 [0,1] 的反色比例与中性调色。未识别形状、轮廓、资源过滤器、源和目标的程序/阶段覆盖或相关 `.mcmeta`、目标占用及大小写冲突均不改原文件。检查范围是传入的已投影工作副本；不能证明用户另外启用的其他包没有覆盖同名原版程序。此研究不改调度或缓存版本。

用临时 Java 8 黑盒与 Mojang 两个快照的实际资源验证（186 项断言，产物为 class major 52，不运行共享 Gradle）：35→36 的反色结果与原版 36 链结构一致，只有显式中性调色这一项差异；随后 [52/57 迁移](../../src/main/java/top/syshub/viarp/convert/PostEffectUpgrades.java)产出直接顶点／片元阶段和带类型 uniform 数组，再产出 `InvertConfig`／`BlitConfig` 块，保持原 0.8 与中性调色。另核对省略参数、用户参数、幂等、官方旧轮廓逐字节保留、自定义程序、有效性与冲突拒绝。未做 GPU 截图回归；语义结论来自两端资产和调用链。
