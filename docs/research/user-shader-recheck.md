# 26.3 现场 glint 着色器复核

**结论：本次 glint 的旧 GLSL 由驱魔传材质包提供，ViaResourcePack 的 97.1 转换缓存原样保留了两阶段。故障不止是旧导入语法；没有证据支持这两个阶段的保真自动改写。** 若只需让这个 pipeline 回到可编译的原版路径，最小非保真范围是在用户自行制作的 97.1 工作副本中**同时不再覆盖 glint 的 VSH 和 FSH**，让二者一起回落原版；这会放弃该包的自定义 glint 着色器效果。未改原实例、原包或缓存。

## 1. 原日志的时间线与字节证据

证据源：[现场 latest.log](<D:/Softwares/MCJE/.minecraft/versions/26.3-Fabric_0.19.5/logs/latest.log>)。按字节 LF 分行（1 起计），下列 offset 从 0 起计；非 ASCII 与控制字节用 `\xNN`，保留混用 CRLF/LF，不对整份日志套用某个编码。显示的包名来自磁盘文件名。探针只输出白名单中的资源包/GLSL 行，不打开账户、启动器用户配置或选包配置。

| 原行号 | 证据 |
| --- | --- |
| 89–90、96 | 26.3 / protocol 777 / resource pack format 97.1；LWJGL 3.4.3-snapshot；**实际使用 OpenGL**。 |
| 414、419–423 | 格式 42 的驱魔传材质包升到 97.1，93.0 诊断分别指出 glint.fsh:3 和 glint.vsh:3 的旧指令被保留，然后发布到缓存键 0e107ddf3387… 下的 pack.zip。 |
| 467–477、479 | pipeline/glint 编译失败；首诊断第 3 行 invalid directive: moj_import；随后是 5/6/13/14 缺 location、8–11 裸非 opaque uniforms、19 fog_distance 无匹配重载；栈来自 RenderPearl GlslCompiler.compileToSpv。 |
| 7000、7054–7055、10157–10158、10287 | 原版重载失败后清除选中包；同一 glint 诊断在 14:05:38 和 14:07:07 又出现。日志没有为每次着色器读取记录 sourcePackId，不能从它恢复每一次完整覆盖栈。 |
| 362–364 与 7186–7190 | 另一件事：旧 这个resources.zip 的 bow.json 遇到 ZipFile invalid LOC header (bad signature)，转换失败回退原包；随后日志出现 format 22 的 resources 文件夹转换。盘上现只有该文件夹、没有前述 ZIP；不能据名字推定两者字节相同。该文件夹及其缓存无 glint stage，不能把 ZIP 故障等同于 glint 编译失败。 |
| 318、321、325 | 1.zip 分别有格式 88→97.1、另一个 rendertype_entity_solid.fsh:3 的旧指令诊断、成功缓存记录；不是 glint 路径。 |

下列真实行保持字节表示（第一列原行号、第二列字节 offset）：

~~~text
419  44473  [14:04:43] [Render thread/WARN]: Resource pack \xc7\xfd\xc4\xa7\xb4\xab_\xb2\xc4\xd6\xca.zip: 93.0 [assets/minecraft/shaders/core/glint.fsh:3] \xb1\xa3\xc1\xf4 #moj_import\xa3\xbb\xd0\xc2 #include \xb0\xb4\xd7\xca\xd4\xb4 ID \xb4\xd3 shaders/include/ \xb2\xe9\xd5\xd2\xa3\xac\xb2\xbb\xd1\xd8\xd3\xc3\xd2\xfd\xd3\xc3\xce\xc4\xbc\xfe\xc4\xbf\xc2\xbc\xa1\xa3\x0d
420  44674  [14:04:43] [Render thread/WARN]: Resource pack \xc7\xfd\xc4\xa7\xb4\xab_\xb2\xc4\xd6\xca.zip: 93.0 [assets/minecraft/shaders/core/glint.vsh:3] \xb1\xa3\xc1\xf4 #moj_import\xa3\xbb\xd0\xc2 #include \xb0\xb4\xd7\xca\xd4\xb4 ID \xb4\xd3 shaders/include/ \xb2\xe9\xd5\xd2\xa3\xac\xb2\xbb\xd1\xd8\xd3\xc3\xd2\xfd\xd3\xc3\xce\xc4\xbc\xfe\xc4\xbf\xc2\xbc\xa1\xa3\x0d
468  51016  com.mojang.renderpearl.util.ShaderCompileException: Couldn't parse GLSL: minecraft:core/glint:3: error: '#' : invalid directive: moj_import
7055 627631 com.mojang.renderpearl.util.ShaderCompileException: Couldn't parse GLSL: minecraft:core/glint:3: error: '#' : invalid directive: moj_import
10158 1299682 com.mojang.renderpearl.util.ShaderCompileException: Couldn't parse GLSL: minecraft:core/glint:3: error: '#' : invalid directive: moj_import
~~~

## 2. 提供者与排除范围

只检查 8 个目前可见的本地原包文件/目录及 8 个 ViaResourcePack 缓存的索引、根元数据和目标条目；读出的是路径、大小、指令类型/ID、行号和 SHA-256，没有导出包或 GLSL。缓存完整键为 v2-0e107ddf338712aa5f48846a7d116a44e5529f2b-zip-97-1-658a957f04a8a191cc2a27109db3af3175e0f86b90ae8d3cde2979b40f12eb18（[现场缓存](<D:/Softwares/MCJE/.minecraft/versions/26.3-Fabric_0.19.5/resourcepacks/.viaresourcepack/cache/v2-0e107ddf338712aa5f48846a7d116a44e5529f2b-zip-97-1-658a957f04a8a191cc2a27109db3af3175e0f86b90ae8d3cde2979b40f12eb18/pack.zip>)；[真实原包](<D:/Softwares/MCJE/.minecraft/versions/26.3-Fabric_0.19.5/resourcepacks/驱魔传_材质.zip>)）。源包 pack_format=42，缓存 min_format=max_format=[97,1]，均无声明 overlay/filter。**新包元数据不等于新 shader ABI。**

| 包内路径 | 长度 | 原包与缓存共同 SHA-256 | 行指纹 |
| --- | ---: | --- | --- |
| assets/minecraft/shaders/core/glint.vsh | 415 B | 3a92d36fa60888cf7df046b8b32dbe6c2fe5ab66b5630a703273cfa01bce31e3 | L1 GLSL 150；L3 旧尖括号 ID minecraft:fog.glsl；L5/6、13/14 是未显式标位置的 I/O；L8–11 为 ModelViewMat、ProjMat、TextureMat、FogShape；L19 调 fog_distance。与现场编译报错逐项吻合。 |
| assets/minecraft/shaders/core/glint.fsh | 587 B | 670ccfc20f31199adc3a11c7d37261b838d7f5d278645d421edaf0a925761efb | L1 GLSL 150；L3 相同旧导入；L7–12 含裸 ColorModulator、FogColor、FogStart、FogEnd、GlintAlpha、GameTime；L14/15/17 无显式位置。 |

- 在其他当前可检索的本地包及其转换缓存中，没有同路径 glint stage 或声明 overlay 中的 glint stage。1.zip 原始中央目录同样不含这两个条目，但独立读取器拒绝其根元数据的中央目录/局部头文件名不符，已停止读取该原包，不假称完成全包验证；对应日志转换缓存可以读，且无 glint stage。resources 的 glint PNG 不是 GLSL 覆盖。
- 26.3 原版 VSH/FSH 均为 GLSL 330，L4–6 为新 include，且具显式 location；离线对照两阶段分别通过 ShaderC。未发现原版同症状。未遍历其他 mod/服务器的资产；结论依赖现场转换日志 + 原包/缓存身份 + VSH 指纹，而不是假称观察到游戏的完整资源栈。
- 目标 ShaderManager 按资源路径取最高优先级文件并以 ID+stage 保存，VSH 和 FSH 因而可以分别由不同包覆盖（634debadb:client/net/minecraft/client/renderer/ShaderManager.java:103–139,321–328；634debadb:client/net/minecraft/server/packs/resources/FallbackResourceManager.java:151–170）。GLINT 注册两者同 ID core/glint；RenderPipeline 保存 EnumMap，ShaderType 顺序 Vertex→Fragment，PipelineBuilder 遇失败即无法完成此 pipeline：634debadb:client/net/minecraft/client/renderer/RenderPipelines.java:132、634debadb:client/com/mojang/renderpearl/api/pipeline/RenderPipeline.java:56、634debadb:client/com/mojang/renderpearl/api/pipeline/ShaderType.java:14–15、634debadb:client/com/mojang/renderpearl/frontend/shaders/PipelineBuilder.java:93–107。故不能把日志的首段诊断当成 FSH 已编译通过。

## 3. 92→93 边界与修法

本地 Mojang 对照根为 [MC](<D:/Code/jsucraft/MC>)；提交 e79868855（Snapshot 4，92.0）、3a325c395（Snapshot 5，93.0）、634debadb（26.3，97.1）的 version.json:7–9 已复核。见 [现代着色器边界](<./modern-shader-boundaries.md#L23-L37>)。

- 92.0 把 #moj_import 交 Mojang 预处理器；角括号绝对 ID 走 shaders/include/，引号按旧规则从引用者目录起算；还有 stage 内去重和 #version 合并（e79868855:client/net/minecraft/client/renderer/ShaderManager.java:105–140、e79868855:client/com/mojang/blaze3d/preprocessor/GlslPreprocessor.java:23–94）。93.0 的 ShaderManager 不再展开旧指令，共用 ShaderC：OpenGL 也先生成 SPIR-V、再转回 GLSL。这就是现场虽为 OpenGL，仍出现 Vulkan GLSL 限制文字的原因，不是选错显卡后端（3a325c395:client/net/minecraft/client/renderer/ShaderManager.java:86–107,121–123、3a325c395:client/com/mojang/renderpearl/frontend/shaders/PipelineBuilder.java:76,102、3a325c395:client/com/mojang/renderpearl/backend/opengl/GlPipelineRecompiler.java:198–221）。
- 93.0 和 97.1 的回调按请求字符串解析 ID；不是按引入者目录解析，也不据 quote/angle 类型切换路径（3a325c395:client/com/mojang/renderpearl/frontend/shaders/GlslCompiler.java:44–60,96–114；634debadb:client/com/mojang/renderpearl/frontend/shaders/GlslCompiler.java:47–71、634debadb:client/net/minecraft/client/renderer/ShaderManager.java:71–74,172–173）。**本包这个特例**的旧尖括号 ID 正好可映射到同名原版文件，但 26.3 发行客户端的 include/fog.glsl:4–11,24–40 定义的是 Fog block、total_fog_value、fog_spherical_distance、fog_cylindrical_distance，并不定义旧 fog_distance/FogShape 契约。
- **不要做全局字符串替换、任意插位置编号、自动改 uniform 或删除所有 shader。** 本例即使 import 成功，仍需目标 GLSL 版本/扩展、两阶段显式且链接一致的 ABI，以及目标 pipeline 认识的 UBO/绑定，后者还检查跨阶段/绑定类型（634debadb:client/com/mojang/renderpearl/frontend/shaders/PipelineBuilder.java:110–179,192–236,305–334）。不能从旧源码机械推出旧雾表现和私有效果在新运行时的等价实现。
- **保真需要作者人工移植**：以 [26.3 官方客户端](<D:/Softwares/MCJE/.minecraft/versions/26.3-Fabric_0.19.5/26.3-Fabric_0.19.5.jar>) 的同名入口为 ABI 基线。VSH Position/UV0 的位置为 0/1，传递 sphericalVertexDistance/cylindricalVertexDistance/texCoord0 到 FSH 的位置为 0/1/2；FSH fragColor 输出位置 0，采样器绑定 Sampler0。使用官方 Projection（ProjMat）、DynamicTransforms（ModelViewMat/TextureMat/ColorModulator）、Fog（两套距离范围）、Globals（GlintAlpha/GameTime）块，转换自定义计算而不是把每个旧 uniform 独立塞进任意块；保留 GLSL 330 + GL_ARB_separate_shader_objects 或经过目标验证的等价头。以上只确定移植起点，不声称视觉效果等价；需要另用隔离的 26.3 实例验证编译、链接、绑定及实际 OpenGL/Vulkan 绘制。
- **已支持的非保真处理**：在授权的隔离副本里同时移除这两个精确路径的覆盖（包括若存在的生效 overlay），不移除整个包、不改变打包校验、不改原缓存，也不需要更动其他着色器或纹理。以当前已检查的包栈为前提，两个资源重新命中 26.3 原版；探针确认原版两阶段各自能编译，未重载游戏验证全部包或画面。仅去掉 VSH 会暴露旧 FSH 的同类错误；仅去掉 FSH 仍保留现场失败的 VSH。立即止血可在游戏中禁用提供包，代价是它的其他资源一并禁用。自动转换器宜维持**保留 + 精确诊断**，不把这种降级冒充保真升级。

## 4. 可复现探针

调试程序保存在专用构建目录（Git 忽略，清理构建产物前请保留探针源码）：[日志字节探针](<../../build/viarp-latest-shader/inspect_log.mjs>)、[资源包只读探针](<../../build/viarp-latest-shader/inspect_packs.py>)、[ShaderC 对照](<../../build/viarp-latest-shader/GlintShadercProbe.java>)。从 ViaResourcePack 根用 Git Bash、Node 26.12.1、Python 3.13.12、JDK 25.0.4.1 运行：

~~~bash
B='D:/Softwares/MCJE/.minecraft/versions/26.3-Fabric_0.19.5'
L='D:/Softwares/MCJE/.minecraft/libraries/org/lwjgl'
P='D:/Code/jsucraft/Mods/ViaResourcePack/build/viarp-latest-shader'
CP="$L/lwjgl/3.4.3/lwjgl-3.4.3.jar;$L/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar;$L/lwjgl-shaderc/3.4.3/lwjgl-shaderc-3.4.3.jar;$L/lwjgl-shaderc/3.4.3/lwjgl-shaderc-3.4.3-natives-windows.jar"
node "$P/inspect_log.mjs" "$B/logs/latest.log"
python "$P/inspect_packs.py" "$B"
java --enable-native-access=ALL-UNNAMED -Dorg.lwjgl.system.SharedLibraryExtractPath="$P/native" --class-path "$CP" "$P/GlintShadercProbe.java" "$B/26.3-Fabric_0.19.5.jar"
~~~

- 包探针对 ZIP 先限制数量、布局与声明大小、不写出条目，并把精选读取额外限制到 256 KiB；读取到 EOF 校验大小/CRC，发生坏头错误即停止该包。不运行生产解包，不绕开坏包或放宽转换限制。
- Java 探针用实际 LWJGL 3.4.3 ShaderC（Vulkan 1.2 SPIR-V、auto-bind、无 auto-location，按 97.1 前端选项），只编译手写合成 GLSL 及原版 jar 的选定条目。只在构建目录创建 native 库缓存；没有启动客户端或读取真实包 GLSL 到编译器。它验证 *stage 编译* 的错误，不是对完整 RenderPearl pipeline/binding 或像素等价性的验证。
- 实测：minimal-directive **2** 且精确命中 glint:3 invalid directive，去掉该指令 **0**；legacy **2**；rename-only 命中 demo:compat.glsl 但 **2**，仍报位置和裸 uniform；具位置与 block 的合成移植 **0**；显式 ID 的引号也 **0**，省掉命名空间则按 minecraft 查找而 **2 / not found**；两个原版 stage 分别 **0**。三项探针收尾合跑退出 **0**，并断言原包/缓存两个 stage 的投影与摘要全等、其外仅原版可观察到两 stage。
