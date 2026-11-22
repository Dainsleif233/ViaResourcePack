# 现代 core 着色器：85 的文本合并、93 的编译契约

ViaResourcePack 2.0 只向上迁移，本文目标为 26.3 正式版（97.1）。**保留自定义 GLSL，不按旧名搬到新入口，也不自动改写 import 或分配 location**：这两处都不只是语法或文件名变化。本文仅讨论 shader 源与 pipeline；旧后处理 JSON 见 [后处理资源的有限迁移](<../../docs/research/post-effect-upgrades.md>)。

证据基线：本地 Minecraft Git 根为 `D:/Code/jsucraft/MC`，以下各提交的 `version.json` 核实了 26.1（84.0，`097c9cc6f`）、26.2 Snapshot 1（85.0，`1d3d3caa1`）、26.3 Snapshot 4（92.0，`e79868855`）、Snapshot 5（93.0，`3a325c395`）和 26.3（97.1，`634debadb`）。Git 引用以 `提交:路径:行号` 标记；可用 `git show <提交>:<路径>` 复核。主要证据为 Mojang 发布说明和这些提交的加载器、编译器与 pipeline；未启动客户端或编译 GLSL，不把 pipeline 注册当作用户包实际渲染成功的证据。

## 84.0 → 85.0：六条旧程序收敛到两个入口

[Mojang：26.2 Snapshot 1](https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-1) 宣布六个 `core/rendertype_text*` 程序归并；Git 对照显示旧、新 pipeline 的两阶段都绑定下表的程序 ID（`097c9cc6f:client/net/minecraft/client/renderer/RenderPipelines.java:83–91`、`1d3d3caa1:client/net/minecraft/client/renderer/RenderPipelines.java:37–38,47,83–91`）。表中 ID 省略 `minecraft:core/`；对应 `assets/minecraft/shaders/core/<名称>.vsh` 与 `.fsh`（`1d3d3caa1:client/com/mojang/blaze3d/shaders/ShaderType.java:14–15,38–39`）。

| 84.0 程序 | 85.0 程序 | 85.0 文本变体宏（仅列该组） |
| --- | --- | --- |
| `rendertype_text` | `text` | 世界普通／偏移均无；GUI 为 `IS_GUI` |
| `rendertype_text_intensity` | `text` | 世界为 `IS_GRAYSCALE`；GUI 再加 `IS_GUI` |
| `rendertype_text_see_through` | `text` | `IS_SEE_THROUGH` |
| `rendertype_text_intensity_see_through` | `text` | `IS_GRAYSCALE` + `IS_SEE_THROUGH` |
| `rendertype_text_background` | `text_background` | 无 |
| `rendertype_text_background_see_through` | `text_background` | `IS_SEE_THROUGH` |

- **不是同接口改名。** 85.0 的普通世界字使用 `POSITION_TEX_LIGHTMAP_COLOR`、`Sampler2` 和雾绑定；GUI／透视字使用 `POSITION_TEX_COLOR`，背景正常／透视分别使用 `POSITION_COLOR_LIGHTMAP`／`POSITION_COLOR`。裸复制单个旧 stage 会把一个分支的输入假设施加到其他消费者；仅有一份旧覆盖或目标缺失也不能证明安全。固定写入宏不能代替 pipeline 按分支传入宏。
- **85.0 的第二个入口不是 97.1 的目标。** `c581feaf7`（26.3 Snapshot 8；其 `version.json` 为 96.0）的 `RenderPipelines` 删除四个背景 pipeline（含 OIT）；97.1 的 `634debadb:client/net/minecraft/client/renderer/RenderPipelines.java:53–54,69,135–146` 保留 `core/text`、三种宏及若干 OIT 变体，没有 `core/text_background` 原版引用。独立背景由 `TextFeatureRenderer` 转给 `Font.prepareBackground` 创建字形效果，再由字形按显示模式选文本 render type（`634debadb:client/net/minecraft/client/renderer/feature/TextFeatureRenderer.java:39–51,85–88`；`634debadb:client/net/minecraft/client/gui/Font.java:91–93`；`634debadb:client/net/minecraft/client/gui/font/glyphs/BakedSheetGlyph.java:236–237`）。这不是旧背景 stage 到新 stage 的一对一映射。

## 92.0 → 93.0：include 改了解析和地址规则，location 改了链接规则

[Mojang：26.3 Snapshot 5](https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-5) 宣布 OpenGL 和 Vulkan 都由 ShaderC 编译，改用 `#include`，shader 输入／输出要显式指定 `location`。92.0 的 OpenGL 先注入宏、再把 GLSL 交给驱动，并按顶点属性名绑定位置（`e79868855:client/com/mojang/renderpearl/backend/opengl/GlDevice.java:318–328`、`e79868855:client/com/mojang/renderpearl/backend/opengl/GlProgram.java:48–68`）；Vulkan 已有 ShaderC。93.0 开始共用前端 `PipelineBuilder`（`3a325c395:client/com/mojang/renderpearl/frontend/shaders/PipelineBuilder.java:76,102`），OpenGL 再将 SPIR-V 转回 GLSL，不是继续把原始资源文字送给驱动（`3a325c395:client/com/mojang/renderpearl/backend/opengl/GlPipelineRecompiler.java:198–221`）。

| 证据 | 导入契约 |
| --- | --- |
| **92.0**：`e79868855:client/net/minecraft/client/renderer/ShaderManager.java:105–140`；`e79868855:client/com/mojang/blaze3d/preprocessor/GlslPreprocessor.java:23–94` | 预载阶段由 Mojang 预处理器展开：stage 直接写 `#moj_import "foo.glsl"` 时按该 stage 目录和命名空间找；`#moj_import <ns:foo.glsl>` 从 `ns:shaders/include/` 找。嵌套时预处理器另行传递相对前缀。按 stage 去重已导入的资源 ID，并折叠导入文件的 `#version`、插入 `#line`。 |
| **93.0**：`3a325c395:client/net/minecraft/client/renderer/ShaderManager.java:86–107,121–123`；`3a325c395:client/com/mojang/renderpearl/frontend/shaders/GlslCompiler.java:44–60,96–114` | Loader 保留原文；ShaderC 的回调只使用请求字符串，不用 quote/angle 类型或引用者目录。请求字符串解析为资源 ID，并加 `shaders/include/` 前缀。 |
| **97.1**：`634debadb:client/net/minecraft/client/renderer/ShaderManager.java:71–74,103–139,172–173,321–328`；`634debadb:client/com/mojang/renderpearl/frontend/shaders/GlslCompiler.java:47–71,119–146`；`634debadb:client/net/minecraft/resources/Identifier.java:40,69–106` | 同样只看请求字符串；改为直接查独立 include 表，键保留 `.glsl` 后缀。例：`#include "demo:lib/fog.glsl"` 查 `assets/demo/shaders/include/lib/fog.glsl`，无 namespace 则默认 `minecraft`，不继承引用者命名空间；非法 ID 与缺资源分别返回 `malformed id` 与 `not found`。 |

例：`assets/demo/shaders/core/custom.vsh` 的旧 `#moj_import "foo.glsl"` 查 `assets/demo/shaders/core/foo.glsl`；只改指令名后则查 `assets/minecraft/shaders/include/foo.glsl`。旧尖括号即使指到同一资源，也不能假定旧预展开、去重和 `#version` 处理在 ShaderC 中等效。还要考虑嵌套 include、条件宏、资源覆盖和过滤。不要移动或展开被引用的文件来“修好”单个副本。

**location 是接口，不是顺序编号。** 97.1 的 `634debadb:client/com/mojang/renderpearl/frontend/shaders/PipelineBuilder.java:110–179,305–334` 先按名称把顶点输入匹配到顶点格式元素，再取反射的 location 并校验输入类型及分量；顶点输出与片元输入按 location 匹配而非按名字，仍要对齐基础类型、向量长度和 `flat` 插值。不能按发现顺序插入 `layout(location=…)`，也不能把两个变量同名当成通过链接。Mojang 的兼容注释是为旧 OpenGL 输入不支持显式位置、旧跨阶段按名匹配而写；不是 97.1 可以省略位置的例外，也不是 ViaResourcePack 自动插入 `VULKAN` 条件的依据。

**宏也要核对实际拼写。** 93.0 说明将深度宏写成 `B3D_IS_ZERO_TO_ONE` → `RENDERPEARL_IS_ZERO_TO_ONE`；检查的编译路径实际注入的是 92.0 的 `B3D_DEPTH_IS_ZERO_TO_ONE` 与 93.0／97.1 的 `RENDERPEARL_DEPTH_IS_ZERO_TO_ONE`，后者按设备特性注入；`RENDERPEARL_INSTANCE_INDEX_INCLUDES_BASE_INSTANCE` 也按设备能力注入（`e79868855:client/com/mojang/renderpearl/backend/opengl/GlDevice.java:150`；`e79868855:client/com/mojang/renderpearl/backend/vulkan/glsl/GlslCompiler.java:43`；`3a325c395:client/com/mojang/renderpearl/frontend/shaders/GlslCompiler.java:77–89`；`634debadb:client/com/mojang/renderpearl/frontend/shaders/GlslCompiler.java:97–113`）。只作人工核对提示，不据此改宏名或实例索引。

## 迁移诊断（不改变资源）

- **越过 85.0**（源声明上界 < 85.0 ≤ 目标）：按表中六个精确旧原版 stage 路径匹配，不用宽泛前缀，也不把别的 namespace 同名文件判为已失效。报实际路径、共享入口的变体冲突；97.1 的背景报独立入口已移除。保护显式新文件；不新增、改名或删除旧文件。若源声明已跨过 85、但仍有废旧路径，不把它误报为此次跨界。
- **越过 93.0**（同样按源声明上界与目标判定）：仅把非注释的 `#moj_import` 指令（包括 stage 与可能被引入的 `.glsl`）报为可定位的迁移线索，列文件与行号、必要时带引用路径。单文件里“没看见 `location`”不是编译失败证据：宏、include、条件分支、内建接口，以及用户只覆写一阶段都影响判断。通用提示仅说核对目标 pipeline 的顶点输入名、双阶段 locations／类型／插值和绑定；也不要把盘上文件存在当成已被原版或 mod 引用。
- **到 97.1 的额外背景线索**：若单独增加 96.0 边界诊断，源声明上界 < 96.0 且出现 `assets/minecraft/shaders/core/text_background.{vsh,fsh}` 时，提示原版背景消费者已更换；不能因为源已经 ≥ 85.0 就推断该覆盖会继续生效，也不能删除仍可能被 mod 引用的资源。
- **验证边界**：若另行设计静态测试，至少覆盖仅一份旧变体、显式新文件、其他命名空间、旧相对/根导入、注释、宏声明 location 和只覆盖一阶段，并断言文件字节保持。没有实际目标客户端的变体编译与绘制验证前，不承诺能编译或等效。

实现只定位单行字面旧导入，忽略行、块注释；不评估条件宏或引用图。每包最多读 512 个候选、总计 32 MiB，每文件最多报告 4 条具体行号，超出仍保留文件并提示未全面扫描。
