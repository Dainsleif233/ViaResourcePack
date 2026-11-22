# TrueType 字体：资源格式 22 → 24 的度量边界

**建议：只诊断，不自动改写 `ttf`。** 第一次改变是 **23w51b（22）→ 24w03a（24）**，不是到 1.20.5 正式版（32）才发生。两端 JSON 和文件路径兼容，但同一份字体会由不同的原生后端加载与度量。即使手头有完整字体，也存在不能用目标 `size`、`shift`、`oversample` 精确表示的旧字形。

## 首见及资源证据

本地 vanilla 证据取自 `D:/Code/jsucraft/MC` 的 Git 历史。资源格式看 `client/net/minecraft/DetectedVersion.java:43–48`；不要与数据包格式混用。以 `client/com/mojang/blaze3d/font/TrueTypeGlyphProvider.java` 的 Git blob 校验实现是否相同：旧 `bc30553555766bb133eccf26c054b700715c159f`，新 `c765e099b96a896b57a3d6e5f80663957093816e`。

| 版本 | Git 提交 | 资源格式 | TTF 观察 |
| --- | --- | ---: | --- |
| 1.20.3-pre1 / 1.20.4 | `683e0e30f` / `9e2d2eebd` | 22 / 22 | 格式 22 首见；两者均为旧 STB blob。 |
| 23w51a / 23w51b | `e811dffbc` / `b0080f518` | 22 / 22 | 均为旧 blob；`b0080f518` 是 24w03a 的直接父提交。 |
| **24w03a** | **`07a9fef3a`** | **24** | **首见新 FreeType blob；shift、EM 缩放、基线与 advance 在此切换。** |
| 24w03b / 24w04a | `7f1c765e5` / `361215e45` | 24 / 25 | 同一 FreeType blob。 |
| 1.20.5 | `ac39e1ebb` | 32 | 沿用以下度量，后续代码另有错误处理、同步和上传改动。 |

[24w03a 官方公告](https://www.minecraft.net/en-us/article/minecraft-snapshot-24w03a)确认 shift 原来应用两次、size 改按 EM、基线固定为 7。公告建议试 `size: 9`，**不是默认值改成 9**。两端 `ttf` 均要求 `file`（读作 `assets/<namespace>/font/<path>`），都可省略 `size: 11.0`、`oversample: 1.0`、`shift: [0,0]`、`skip: ""`；`skip` 仍可用字符串或字符串列表。见 `client/net/minecraft/client/gui/font/providers/TrueTypeGlyphProviderDefinition.java`，旧 `b0080f518:42–70,98–107`、新 `07a9fef3a:47–81,109–119`。格式 24 仍是两个 `Codec.FLOAT`，没有独立 shift 数值范围；[1.20.5 正式公告](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-5)里的 `[-512,512]` 是后来变化：同文件 `13379a666:105–108`（1.20.5-pre2）先加 `[-100,100]`，`456a7ba27:105–108`（1.20.5-pre4）再放宽，不能回填到 24。

官方交叉核验：按版本清单、版本 JSON、asset index 的 SHA-1 与可用声明长度校验下级制品，再实算 SHA-256。JAR 根 `version.json` 分别报 `{resource:22,data:27}` / `{resource:24,data:28}`。版本清单的 LWJGL 依赖中，前者有 `lwjgl-stb:3.3.2`；后者新增 `lwjgl-freetype:3.3.2` 且保留 STB（仍有其他用途）。这确认 Java binding 版本，不宣称已经识别每个平台的 native 构建。两端各有 7 个 JAR 内 `assets/*/font/**` 文件，逐项摘要相同且无 `ttf` provider；共用同一 asset index（ID 12）与两个 indexed 字体条目（`include/unifont.json` 和其 `unifont.zip`），也相同。这说明没有可直接照抄移植法的 vanilla 自带 TTF 素材；**不能**由字体资源相同推出自定义 TTF 语义相同。细目见[静态审计结果](<../../build/viarp-truetype-audit/mojang-assets.json>)。

| 客户端 | 官方 SHA-1 | 实算 SHA-256 |
| --- | --- | --- |
| [23w51b](https://piston-data.mojang.com/v1/objects/de83457c22691e61cd800161182eda8395049bf2/client.jar) | `de83457c22691e61cd800161182eda8395049bf2` | `c9081ec9757c3493319bba46aacd70f64f2e0a5742b0b5c0b4434d2fa1dab057` |
| [24w03a](https://piston-data.mojang.com/v1/objects/723cca5ce4684d04a259a7860a41e75856a5c08a/client.jar) | `723cca5ce4684d04a259a7860a41e75856a5c08a` | `4d920aa59f72d3a1195845456ec86f0ad81e6d6723c46d68e591829348137670` |

## 度量公式：以同一个文本渲染原点为准

记 `S=size`、`O=oversample`（讨论正常可加载的有限正值）、`sx,sy=shift`、`U=head.unitsPerEm`、`A,D=hhea.ascent/descent`、`aw,lsb=STB 返回的 hmtx advance/bearing`。字库边界 `xmin,ymin,xmax,ymax` 为字体单位、Y 向上。下文 `left,top,W,H` 是正常不倾斜的渲染四边形相对文本原点的值（Y 向下），不是纹理 UV；不含 shadow、bold、字距修饰。式中保留取整位置；源码用 Java/native float 或定点，有理数化简不代表全域位级等价。

### 22：STB

```text
k = (S*O)/(A-D); dx = sx*O; dy = sy*O
x0 = floor(xmin*k + dx); x1 = ceil(xmax*k + dx)
y0 = floor(-ymax*k + dy); y1 = ceil(-ymin*k + dy)
advance = (aw*k)/O
left = (lsb*k + x0 + dx)/O
sheetBearingY = (A*k + y0 + dy)/O
renderTop = sheetBearingY - 3
W = (x1-x0)/O; H = (y1-y0)/O
```

从 `stbtt_ScaleForPixelHeight`、`stbtt_GetFontVMetrics`、`stbtt_GetGlyphHMetrics`、`stbtt_GetGlyphBitmapBoxSubpixel` 可定位到 [STB 上游](https://github.com/nothings/stb/blob/5c205738c191bcb0abc65c4febfa9bd25ff35234/stb_truetype.h) `2305–2314,2634–2639,2660–2669,2721–2736`：用 `hhea` ascender−descender，不计 line gap；边界盒已加 shift；无轮廓时盒全 0。`b0080f518` 的 `client/com/mojang/blaze3d/font/TrueTypeGlyphProvider.java:50–64,69–96,131–136,174–179` 显式再加一次 shift，另加 LSB；空白只返回同公式 advance。结合 `client/com/mojang/blaze3d/font/SheetGlyphInfo.java:17–39`、`client/net/minecraft/client/gui/font/FontTexture.java:46–59` 和 `client/net/minecraft/client/gui/font/glyphs/BakedGlyph.java:38–51` 才得到最终 `-3`，不能把旧 sheet bearing 直接与新 render top 比较。

### 24：FreeType

```text
p = Math.round(S*O)                 // 请求 FT_Set_Pixel_Sizes(p,p)，名义 EM
vx = Math.round((sx*O)*64)
vy = Math.round((-sy*O)*64)          // FT Y 向上；JSON 正 Y 仍向下
FT_Set_Transform(face, null, (vx,vy)) // 单次，26.6 坐标
L,T,Wb,Hb = slot.bitmap_left, bitmap_top, bitmap.width, bitmap.rows
m = slot.advance.x                  // 26.6 水平 advance
advance = (m/64)/O
left = L/O; renderTop = 7 - T/O; W = Wb/O; H = Hb/O
```

`07a9fef3a` 的 `client/com/mojang/blaze3d/font/TrueTypeGlyphProvider.java:53–90,135–141,168–183`、`client/net/minecraft/client/gui/font/providers/FreeTypeUtil.java:45–52` 给出调用与格式；`client/com/mojang/blaze3d/font/SheetGlyphInfo.java:17–39` 在这里用 `7 - getBearingTop()`，旧渲染器的 `-3` 已从 `client/net/minecraft/client/gui/font/glyphs/BakedGlyph.java:38–48` 去掉，**不能再减 3**。无像素 glyph 仍返回 advance。

[FreeType 上游](https://github.com/freetype/freetype/blob/VER-2-13-0/src/base/ftobjs.c) `481–525,1088–1171,3251–3259,3561–3592`：像素请求选 `NOMINAL` 的 `units_per_EM`，普通 outline 在变换后按像素边界外扩，水平布局取 `slot.metrics.horiAdvance`，不是 `linearHoriAdvance`。默认水平 advance 可受 hinting、phantom points 或字库度量表影响（[TT driver](https://github.com/freetype/freetype/blob/VER-2-13-0/src/truetype/ttgload.c) `827–879,1534–1536,2151–2155`），而该次只有平移，advance 向量只乘线性矩阵，不加 delta。`FT_Load_Glyph(..., 0x400008)` 为 `FT_LOAD_NO_BITMAP | FT_LOAD_BITMAP_METRICS_ONLY`；没有 `FT_LOAD_NO_HINTING`（[FreeType 标志定义](https://github.com/freetype/freetype/blob/VER-2-13-0/include/freetype/freetype.h)）。实际烘焙另以 `FT_LOAD_RENDER` 取位图并检查像素模式、尺寸（`07a9fef3a:client/com/mojang/blaze3d/platform/NativeImage.java:464–479`），不要把度量一致当成栅格 alpha 一致。`p/U` 是标称缩放，实际是 FreeType 的定点缩放、hinting 和位图度量，不是把未 hint 的 `glyf` 盒直接乘 `p/U`。

### 分开验证四项

- **Shift**：旧 `dx/dy` 先进入取整，再以 float 加到 bearing。当位移增量对应整数栅格步长（`Δsx*O,Δsy*O` 是整数），旧位置在正常数值下各移动两倍 UI 增量，盒宽高不变；小数位移的旧结果包含 floor/ceil 的相位，绝非恒等的 `2×`。新只动 outline，先转 26.6 再取位图边界。
- **Advance**：旧概念上约为 `aw*S/(A-D)`，跟 shift、oversample 无关（忽略 float 末位）；新取 FreeType 同一槽位的 `m/(64*O)`，纯 shift 同样不改变 advance，但换缩放、取整或 hinting 会变。空白同样受差异影响，不能拿位图宽度替代 advance。
- **基线**：旧无 shift 的文本基线锚是 `A*S/(A-D)-3`；新固定 `7`。这是文本渲染锚，实际顶端还取决于各 glyph 的 Y 上界；不是「把所有 glyph 往下移 7」。
- **Oversampling**：旧先以 `O` 扩大轮廓和位移再量化，最后除回 `O`；这仍改变原始尺寸与 bearing 的取整。新还在前端把 `S*O` 舍入到整数 ppem，且 shift 用 26.6；`O` 不能简单约掉，也不是纯抗锯齿开关。普通正值下 FreeType 尺寸接口还会把 0 修正到 1、把极大请求限制至 65535（上游所见，不是 JSON codec 的取值规则）。

## 可复算对照

以下是**算术与受控槽位对照，未运行原版，也未把假定的控制盒当成真实字库的 hinting 结果**。默认 `U=1024,A=640,D=-384`，矩形字体单位盒 `(0,0,512,512)`，`lsb=0,aw=768,S=16,O=1,shift=[0,0]`。新端对这些样例明确给定已经过缩放/可能 hinting 的控制盒与 `m`，用其计算单次变换及向外取整；脚本输出完整 `(L,T,Wb,Hb,m)`。表中向量依次是 `(left,renderTop,W,H,advance)`。

| 控制变量 | 22：STB | 24：FreeType | 判读 |
| --- | --- | --- | --- |
| 默认矩形，新控制盒 8×8、`m=768` | `(0,-1,8,8,12)` | `(0,-1,8,8,12)` | 度量可相同：旧锚也是 7。 |
| 两端 `shift=[1,1]`，其他同上 | `(2,1,8,8,12)` | `(1,0,8,8,12)` | 旧双加；只改位置不改 advance。 |
| 上一行仅新改成 `[2,2]` | `(2,1,8,8,12)` | `(2,1,8,8,12)` | 整数对齐特例可补。 |
| 两端 `[1/4,1/4]`，或仅新改成 `[1/2,1/2]` | `(1/4,-3/4,9,9,12)` | 两种均 `(0,-1,9,9,12)` | 小数不是简单翻倍；光栅相同盒尺寸仍不等位。 |
| 仅将 `A,D` 换为 `768,-256`，仍 `A-D=U` | `(0,1,8,8,12)` | `(0,-1,8,8,12)` | 只改变旧锚（9 vs 7），无尺寸/advance 变化。 |
| 改为 `D=-640,S=20`；新控制盒 10×10、`m=960` | `(0,-1,8,8,12)` | `(0,-3,10,10,15)` | 两端文本锚均 7，但 EM 尺寸不同；仅新改 `S=16`、恢复 8×8/`m=768` 即同度量。 |
| 空白 `aw=769,S=8,O=1`；示例新 `m=384` | `769/128` | `6` | 强于该示例：任意整数 `m` 都无法令 `m/64=769/128`。 |
| `S=10.5,aw=U,O=1`；新 `p=11`、控制盒 5.5×5.5、`m=704` | `(0,-39/16,6,6,10.5)` | `(0,1,6,6,11)` | 同尺寸、advance 不同；`S*O` 在新端被舍入。 |
| 上一行仅改两端 `O=2`；新 `p=21`、控制盒 10.5×10.5、`m=1344` | `(0,-31/16,5.5,5.5,10.5)` | `(0,1.5,5.5,5.5,10.5)` | 两端 advance 现在相同，仍不可推出位置相同。 |

## 单包迁移决定

在**忽略 hinting、定点和边界取整，并假设不需补偿旧 glyph-specific LSB** 的连续模型中，`'` 表示新值，取 `O'=O`，可写出只供作者试调的近似：

```text
S'  ≈ S*U/(A-D)
sx' ≈ 2*sx
sy' ≈ 2*sy + A*S/(A-D) - 10
```

第一式补标称缩放，第二式只补重复位移，第三式还补旧基线锚与新 7 的差。它不处理旧 `lsb*k/O` 的每字形残差；即便 `U=A-D`、位移为 0，也不能预设两端兼容。不能批量设 9、翻倍 shift、或将新 codec 范围倒用于旧包。

**不可表示反例：** 旧 `S=8,O=1,U=A-D=1024`，无 shift，矩形 `xmin=lsb=1,xmax=1024,ymin=0,ymax=512`，其 `x0=0,x1=8,k=1/128`，所以 `left=1/128,W=8`，光栅 8×4 可烘焙。任意目标 TTF glyph 的左边是整数 `L/O'`，宽是整数 `Wb/O'`；每页图集为 256×256，无法容纳更宽的位图，新建空页仍失败就回退 missing glyph（`07a9fef3a:client/net/minecraft/client/gui/font/FontTexture.java:24–34,98–101`；`client/net/minecraft/client/gui/font/FontSet.java:147–161`）。保留 `W=8` 要求 `O'≤32`，此时最小正左边至少 `1/32`，不可能是 `1/128`。不依赖新 hinting、shift、size 或原生位图具体值；连把这个字形拆到单独 TTF provider 也无解。这里的量是足以避开 float 末位误差的二进制分数，已用脚本断言相邻 binary32 oversample 的边界。它反驳的是用原 TTF 类型与这组 JSON 参数无损补偿，不声称所有人工字体重制都无解。

还有非度量的限制：旧 STB 不先要求 FreeType 报告字体类型；新端必须通过面索引 0、`TrueType` 格式和 Unicode charmap 检查。旧 `getSupportedGlyphs` 报 `[0,65535)` 扣除 skip，新遍历实际 Unicode charmap 再扣 skip；两端实际单字形查询都排除 index 0（`b0080f518:client/com/mojang/blaze3d/font/TrueTypeGlyphProvider.java:69–96,116–119`；`07a9fef3a` 同路径 `69–90,111–126`）。引用、包叠加以及 overlay 可能让实际使用的字体不在这一个包内；即使内部存在同名文件也不等于它最终生效。

**项目决策：** 只有向上路径经过格式 24 才需要考虑诊断（[PackFormatHistory.java:34–41](<../../src/main/java/top/syshub/viarp/convert/PackFormatHistory.java#L34-L41>) 含相邻 `22 24`）。对存在的 `ttf` provider 报明字体 JSON、provider 序号、原 `file/size/oversample/shift` 与风险；引用资源是否可离线解析另列未知，不把 warning 描述成证实显示变化。保留 JSON、二进制、skip 与 provider 顺序，不触碰其他 provider，不引入 native 或 Minecraft 依赖。非 TTF 或未跨边界不做此项诊断。

## 复验

```bash
git -C D:/Code/jsucraft/MC log --format='%h %p %s' -12 07a9fef3a
git -C D:/Code/jsucraft/MC diff b0080f518 07a9fef3a -- client/net/minecraft/DetectedVersion.java client/com/mojang/blaze3d/font client/net/minecraft/client/gui/font
git -C D:/Code/jsucraft/MC log --format='%h %s' b0080f518..ac39e1ebb -- client/com/mojang/blaze3d/font/TrueTypeGlyphProvider.java client/net/minecraft/client/gui/font/providers/TrueTypeGlyphProviderDefinition.java
python -I -B build/viarp-truetype-audit/metrics.py
python -I -B build/viarp-truetype-audit/mojang-assets.py
```

[样例脚本](<../../build/viarp-truetype-audit/metrics.py>)用分数自检并记录槽位，[样例输出](<../../build/viarp-truetype-audit/metrics.txt>)可直接对照。[静态审计脚本](<../../build/viarp-truetype-audit/mojang-assets.py>)只访问 Mojang 公开静态分发端点，校验制品后在内存读 ZIP 目录和所需资源，不执行制品，也不接触账号或游戏服务。未跑 Gradle；未改 Java。
