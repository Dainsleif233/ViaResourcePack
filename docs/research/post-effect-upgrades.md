# 后处理资源的有限迁移：51→52 与 56→57

只迁移在相邻原版格式上能核对语义的后处理 JSON，不生成、不替换、不删除 GLSL 或旧 program JSON。未识别的旧声明整文件保留并诊断，原有着色器保守诊断不变。此规则不承诺自定义 shader 或多资源包叠加后的等价。

## 证据来源

本机 Minecraft 源码历史以 `D:/Code/jsucraft/MC` 为 Git 根，历史目录为 `client/`。按 [官方版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 定位各客户端，用元数据中的 SHA-1 核对下载，再从 jar 提取 `assets/minecraft/{post_effect,shaders/post,shaders/include}`。下文用 Git 历史复核相同资源如何被解析和装载。

| 格式 | 快照与源码提交 | 官方客户端 SHA-1 |
|---|---|---|
| 51.0 | 25w06a · `e7c83fcbc` | [fa74b7a5065b02c94955fbd2690a3a6000c9a3dc](https://piston-data.mojang.com/v1/objects/fa74b7a5065b02c94955fbd2690a3a6000c9a3dc/client.jar) |
| 52.0 | 25w07a · `02027c155` | [1077e46be47132c81e235d925c6f90881f319a0f](https://piston-data.mojang.com/v1/objects/1077e46be47132c81e235d925c6f90881f319a0f/client.jar) |
| 56.0 | 25w15a · `034755801` | [a12b375db4d067af9653557e7e3c8a61fa1016c5](https://piston-data.mojang.com/v1/objects/a12b375db4d067af9653557e7e3c8a61fa1016c5/client.jar) |
| 57.0 | 25w16a · `b781aae7d` | [46b06a1758054ab1a50f7f3047772db7c2e5a9d7](https://piston-data.mojang.com/v1/objects/46b06a1758054ab1a50f7f3047772db7c2e5a9d7/client.jar) |

## 51→52：从原版 program 到两条 stage 引用

- 51.0：`e7c83fcbc:client/net/minecraft/client/renderer/PostChainConfig.java:68,81` 读取 `program` 和以名字索引的 `values`；`e7c83fcbc:client/net/minecraft/client/renderer/ShaderManager.java:72–73,167–180` 将 program 指向 `shaders/<path>.json`；`e7c83fcbc:client/net/minecraft/client/renderer/ShaderProgramConfig.java:23–30` 解析该文件的 stage、sampler 和 uniform。旧轮廓模糊 program 中虽残留 `blend` 字段，但 51.0 的该 codec 不读取它；不需把它迁入 pass。
- 52.0：`02027c155:client/net/minecraft/client/renderer/PostChainConfig.java:66,75` 改读 `vertex_shader` / `fragment_shader`；`02027c155:client/net/minecraft/client/renderer/PostChain.java:77–83` 按 pass 声明的类型和值提供 uniform。[25w07a 公告](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w07a) 同样说明此结构变化。
- 下表涉及的 7 条 stage 路径在两个 jar 中逐字节相等，无额外 include。只在原版反色 `assets/minecraft/post_effect/invert.json` 和实体轮廓 `assets/minecraft/post_effect/entity_outline.json` 中启用对应规则。旧 pass 不覆盖某 uniform 时使用 51.0 原版 program 的默认值，不使用目标效果文件中的值覆盖输入。

| 原版 program（`minecraft:post/`） | 目标 vertex / fragment（`minecraft:post/`） | 补齐类型的 uniform（51.0 原版默认值） |
|---|---|---|
| `blit` | `blit` / `blit` | `ColorModulate: vec4`，`[1, 1, 1, 1]` |
| `invert` | `blit` / `invert` | `InverseAmount: float`，`[0]` |
| `entity_outline` | `sobel` / `entity_sobel` | 无自定义 uniform |
| `entity_outline_box_blur` | `blur` / `entity_outline_box_blur` | `BlurDir: vec2`，`[1, 1]` |

不能推广：52.0 原版 `bits` 将 `Saturation` 固定为常量；`color_convolve` 删除旧 uniform 并改变计算；菜单 `box_blur` 删除 `RadiusMultiplier` 并改变计算。即使文件名一致，也不升级这些旧 pass，不部分重写包含它们的效果文件。

## 56→57：从平面 uniform 列表到按声明顺序写入的 UBO

- 56.0：`034755801:client/net/minecraft/client/renderer/PostChainConfig.java:37–78` 使用平面 `[{name,type,values}]`，并定义目标与采样输入。
- 57.0：`b781aae7d:client/net/minecraft/client/renderer/PostChainConfig.java:35–51` 将块名映射到 uniform 列表；`b781aae7d:client/net/minecraft/client/renderer/PostChain.java:80–86,145` 绑定块；`b781aae7d:client/net/minecraft/client/renderer/PostPass.java:57–68,96–109` 依列表顺序写入 UBO；`b781aae7d:client/net/minecraft/client/renderer/UniformValue.java:36–66` 区分标量和向量的 `value`。块内的 JSON 名字是描述，不能代替声明顺序。[25w16a 公告](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w16a) 说明新的块结构。
- 在原版反色、实体轮廓、苦力怕三种效果中，两端相关 stage 的运算主体不变，变化是 uniform 的声明与来源。目标 vertex 引用原版 `shaders/include/projection.glsl`，由它提供 `ProjMat`，无嵌套 include。已核对的 `SamplerInfo` 从 `OutSize, InSize` 取值，因此只接受单个 `In` 颜色目标采样器，不自行生成该块。57.0 的目标宽、高可各自缺省；本规则仅接受双方共有的空全屏定义。

| vertex / fragment（`minecraft:post/`） | 目标 UBO 与声明顺序 |
|---|---|
| `blit` / `blit` | `BlitConfig(ColorModulate: vec4)` |
| `blit` / `invert` | `InvertConfig(InverseAmount: float)` |
| `sobel` / `entity_sobel` | 无自定义 UBO |
| `blur` / `entity_outline_box_blur` | `BlurConfig(BlurDir: vec2, Radius: float)` |
| `sobel` / `color_convolve` | `ColorConfig(RedMatrix: vec3, GreenMatrix: vec3, BlueMatrix: vec3)` |
| `sobel` / `bits` | `BitsConfig(Resolution: float, MosaicSize: float)` |

轮廓 `Radius` 是唯一追加的目标块槽位：57.0 原版轮廓 JSON 写入 `2.0`；两个端点的 `entity_outline_box_blur.fsh` 都内置 `radius = 2`、字节相同（SHA-256 `203035c25665d24ae318f0849dfec295f8359c3d5efc2d54fcff90f91da0739b`），对应目标 vertex 不读取该槽位。这不适用于菜单 `box_blur`：56.0 由运行时设置 `Radius` 并使用 `round(Radius)`；57.0 小值可退回 `MenuBlurRadius`，所以保留菜单效果与诊断。

## 闭包边界和验证

- 仅进入 52.0 或 57.0 时检查。每个 pass 的唯一颜色输入必须已经可用、不能读写同一目标，输出必须是已声明的内部目标或对应原版外部目标（`minecraft:main` 或 `minecraft:entity_outline`），末 pass 必须写回外部目标。保留输入字段（包括 `bilinear`）、目标定义及 pass 顺序。
- 生效的源 overlay 由调度器先投影到工作区根。工作区若提供旧 program、所用 stage、目标 include 或它们的伴随元数据，或者包声明 `filter`，就不能确定原版依赖，整文件保留。未知字段、未知/重复 uniform、非有限浮点值、尺寸变体、深度/纹理/多输入、缺失依赖或混合格式也不改写。检查只覆盖转换中的包与原版端点，不验证多个包在运行时的覆盖次序。
- 使用 Zulu OpenJDK `1.8.0_504` 独立编译后，在临时目录运行 5 组原版 JSON fixture（51→52 两组，56→57 三组），输出与目标 jar 按 JSON 结构匹配，数字 `1` 和 `1.0` 视为数值相同；另验证重复应用字节不变、两步串行、块顺序、旧 program 默认值，以及非等价效果、包内 program/stage/include、filter、未知 stage、错误 uniform/target 与重复 JSON 键的保留。共 458 项断言通过；未运行游戏或共享 Gradle。
