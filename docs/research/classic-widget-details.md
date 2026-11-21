# 格式 15–18：经典小部件的切图与不可推导项

## 原版样本

[Mojang 23w31a 发布说明](https://www.minecraft.net/en-us/article/minecraft-snapshot-23w31a) 将资源包格式升到 16，并将 GUI 图块改为独立 sprite；[23w32a 发布说明](https://www.minecraft.net/en-us/article/minecraft-snapshot-23w32a) 将格式升到 17。由 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 下载并核对元数据 SHA-1、客户端 JAR 长度和 SHA-1；格式取自各客户端 JAR 的 `version.json` 中的 `pack_version.resource`。源码锚点可用本地 Minecraft 源码镜像的 `git show <rev>:<path>` 复查。

| 原版元数据 | 格式 | 源码镜像提交 | 客户端 JAR SHA-1 |
|---|---:|---|---|
| [1.20.1](https://piston-meta.mojang.com/v1/packages/fb730d2829f057fe26a41e5ecdc9e3307333bef5/1.20.1.json) | 15 | `3015bd18b` | `0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838` |
| [23w31a](https://piston-meta.mojang.com/v1/packages/91264ab59d2c6c94df140fbecef1e90cc4de1f25/23w31a.json) | 16 | `10bf742e1` | `ca51bf36913a7333c055096a52a3a96fbdb11813` |
| [23w32a](https://piston-meta.mojang.com/v1/packages/8459be9e34bedf3f7af1a557e53daf2cb867ba4b/23w32a.json) | 17 | `a03b2cd92` | `08314fae8fbff190e056a8ae4b9fc9cd603436f6` |
| [23w33a](https://piston-meta.mojang.com/v1/packages/f76d8958053b598d766a3b3b99c184cbfa4215be/23w33a.json) | 17 | `8de429700` | `94e539731458bd2e98a204892b0ed55f715a5011` |
| [23w35a](https://piston-meta.mojang.com/v1/packages/85b43401dd378c7eb56bd37cd04c5c287593f5eb/23w35a.json) | 17 | `79b01893a` | `1359e52fb4b254a0e6563fb7a4b3676da5431af2` |
| [1.20.2-pre1](https://piston-meta.mojang.com/v1/packages/2b6dab477e4c6c82211b95739cc6b3578307f9d5/1.20.2-pre1.json) | 17 | `9a2a68164` | `fbbd743e671b0faa872be8ff8caab6189178912c` |
| [1.20.2-pre2](https://piston-meta.mojang.com/v1/packages/a4393db197a9131eb4728459dc2d6a21fb52c3ea/1.20.2-pre2.json) | 18 | `141f21719` | `0159c75351c359c204682b78e5a5b59cad5d5681` |
| [1.20.2](https://piston-meta.mojang.com/v1/packages/2de0c0ee516b632aab03cd5ea4d34e5aafcdf130/1.20.2.json) | 18 | `913dbbb4c` | `82d1974e75fc984c5ed4b038e764e50958ac61a0` |

## 不生成：普通文本框和滚动条

| 目标 ID（默认命名空间为 Minecraft） | 旧端（格式 15 / 16） | 首个独立图块（23w32a，格式 17） |
|---|---|---|
| `widget/text_field`、`widget/text_field_highlighted` | `client/net/minecraft/client/gui/components/EditBox.java:364–365 / :361–362` 以两次 `fill` 绘制焦点颜色边框及黑底；`client/net/minecraft/client/gui/components/AbstractScrollWidget.java:147–148` 同样直绘其背景。没有旧 PNG/UV。 | `client/net/minecraft/client/gui/components/EditBox.java:39,359–360`、`client/net/minecraft/client/gui/components/AbstractScrollWidget.java:19,148–149` 改读对应 sprite；两张 PNG 均 200×20，`nine_slice` 的逻辑尺寸 200×20、border 1。 |
| `widget/scroller` | `client/net/minecraft/client/gui/components/AbstractScrollWidget.java:157–158` 和 `client/net/minecraft/client/gui/components/AbstractSelectionList.java:242–244 / :226–228`，以深浅灰色 `fill` 绘制滚动滑块，选择列表并填充黑色轨道。没有旧 PNG/UV。 | `client/net/minecraft/client/gui/components/AbstractScrollWidget.java:20,152–156`、`client/net/minecraft/client/gui/components/AbstractSelectionList.java:40,228–229` 改读 6×32 sprite，`nine_slice` 的逻辑尺寸 6×32、border 1；选择列表仍直绘黑轨道。 |

三个目标均在格式 15/16 样本缺席，格式 17/18 六个样本的 PNG 和侧车一致。普通滚动条**不是**旧创造模式页签图中的滚动条；后者拥有不同 ID，已由 [ClassicGuiSheets](../../src/main/java/top/syshub/viarp/convert/ClassicGuiSheets.java) 迁移。这里不从 `widgets.png` 猜贴图、不把原版程序绘制固化为包素材，缺失目标留给游戏回退。

## 23 个静态一致的独立图块

下表旧图以 `assets/minecraft/textures/gui/` 为根，除明确列出 Realms 命名空间的两张。全部目标以 `assets/minecraft/textures/gui/sprites/widget/` 为根并追加 `.png`。坐标是 1× 的 `(x,y,w,h)`，裁切半开区间。按旧渲染 UV 与首个新渲染资源 ID 配对，再比较所有 RGBA 分量（含 alpha=0 的 RGB）；这些目标在七个格式 16–18 样本中都与 1.20.1 旧图裁切逐像素一致。七张旧图没有原版侧车。

| 旧图（1× 尺寸） | 新目标名与矩形 | 1.20.1 → 23w31a 渲染锚点 |
|---|---|---|
| `widgets.png`（256×256） | `locked_button` / `unlocked_button`：x=0/20，普通、`_highlighted`、`_disabled` 为 y=146/166/186；各 20×20。 | `client/net/minecraft/client/gui/components/LockIconButton.java:36–45 → :37–46` |
| `checkbox.png`（64×64） | `checkbox`、`checkbox_highlighted`、`checkbox_selected`、`checkbox_selected_highlighted`；x=0/20（普通/焦点），y=0/20（未选/选中），各 20×20。 | `client/net/minecraft/client/gui/components/Checkbox.java:19,62 → :19–22,66` |
| `tab_button.png`（256×256） | `tab_selected`、`tab_selected_highlighted`、`tab`、`tab_highlighted`，依次 (0,0/24/48/72,130,24)；侧车 `nine_slice` 130×24，`border:{left:2,top:2,right:2,bottom:0}`。 | `client/net/minecraft/client/gui/components/TabButton.java:20,41,65–76 → :21,38` |
| `slider.png`（256×256） | `slider`、`slider_highlighted` 为 (0,0/20,200,20)；侧车 `nine_slice` 200×20，border 1。 | `client/net/minecraft/client/gui/components/AbstractSliderButton.java:22,43–51,77 → :22–23,38–42,76`；旧渲染边宽参数不同，不声明任意目标尺寸的运行期画面相同。 |
| `book.png`（256×256） | `page_forward`、`page_backward` 及各自的 `_highlighted`；x=0/23（普通/高亮），y=192/205（向前/后），各 23×13。保留仍使用的书本背景 PNG。 | `client/net/minecraft/client/gui/screens/inventory/PageButton.java:26–35 → :16–19,30–32` |
| `assets/realms/textures/gui/realms/cross_icon.png`（14×28） | `cross_button`、`cross_button_highlighted` 为 (0,0/14,14,14)。 | `client/com/mojang/realmsclient/RealmsMainScreen.java:105,1365–1370 → :942–955` |
| `assets/realms/textures/gui/realms/slot_frame.png`（80×80） | `slot_frame` 为 (0,0,80,80)。 | `client/com/mojang/realmsclient/gui/RealmsWorldSlotButton.java:28,141` 两版同位置；旧键命名空间显式为 Realms，新构造不带命名空间而使用 Minecraft。 |

除表中 4 个 tab、2 个 slider，其他 17 个稳定 sprite 均无侧车。不切 [splitGuiAtlas](../../src/main/java/top/syshub/viarp/convert/IntermediatePackUpgrades.java) 或 [ClassicGuiSheets](../../src/main/java/top/syshub/viarp/convert/ClassicGuiSheets.java) 已覆盖的 sprite，也不寻找任意替代命名空间/模组纹理。

## 同名但尺寸有界变更：滑杆手柄

`client/net/minecraft/client/gui/components/AbstractSliderButton.java:24–25` 在 23w31a 和 23w32a 读相同的两个 ID：`minecraft:widget/slider_handle`、`minecraft:widget/slider_handle_highlighted`。1.20.1 同源码 :78 从 `slider.png` 的 x=0、y=40/60 的 200×20 区域取样，画到 8×20 的目的矩形；`client/net/minecraft/client/gui/GuiGraphics.java:376–389` 将左右边宽分别限制为目的宽的一半，因此各取 4 列。

- **格式 16**：两个文件分别与完整的 (0,40/60,200,20) 逐像素相同，侧车为 `{"gui":{"scaling":{"type":"nine_slice","width":200,"height":20,"border":3}}}`。
- **格式 17–18**：两个文件都是 8×20；在相应行没有任何单一连续 8×20 的旧裁切匹配。在仅拼接两端的 7 种分割中，唯有以 **[0,4) + [196,200)** 顺序拼接零差异。侧车为 `{"gui":{"scaling":{"type":"nine_slice","width":8,"height":20,"border":{"left":2,"top":2,"right":2,"bottom":3}}}}`。只是精灵素材等价，不保证跨版九宫格在任意绘制尺寸上行为相同。
- 已有独立 200×20 PNG 加完全相同的旧侧车，只能识别其**布局**，不能判断创作者意图或证明它由本迁移生成。格式 17 的 `client/net/minecraft/client/resources/metadata/gui/GuiSpriteScaling.java:70–82` 检查边界之和小于逻辑宽高，旧布局仍有效；`client/net/minecraft/client/gui/GuiGraphics.java:421–429` 也没有强制逻辑宽度等于目标宽度。即使 PNG+侧车恰为原版字节，显式资源也保持原样。

## 独立入口与验证

- [ClassicWidgetDetails](../../src/main/java/top/syshub/viarp/convert/ClassicWidgetDetails.java) 的 `apply(UpgradeWorkspace)` 只写上述 23 个格式 16–18 稳定 sprite。可在 16 的 GUI 拆分边界调用，在 18 对名义格式 16/17 但仍使用旧图的输入回补。
- 若调用方知道**最终格式**，可选 `apply(UpgradeWorkspace, boolean compactSliderHandles)`，一次完成稳定图块和两个缺失手柄（目标 16 传 false，目标 ≥17 传 true）。[ResourcePackUpgrader](../../src/main/java/top/syshub/viarp/convert/ResourcePackUpgrader.java) 在 overlay 投影后、跨入 16/17/18 的第一个节点执行一次；不会在同一条通往 17+ 的路径先写宽版再尝试覆盖。同 ID 的任何现有 PNG 或侧车都不重写。
- 只读精确旧路径，拒绝无法安全拆分的旧侧车、大小写歧义输入、阻塞目录的文件、尺寸或结构不合要求的 PNG。已有目标 PNG 或侧车（含大小写别名）优先。通过 [UpgradePng](../../src/main/java/top/syshub/viarp/convert/UpgradePng.java) 限制 PNG 为最大 4096 像素边长、4096² 面积、32 MiB 文件并预检块结构，只接受各张图的等比整数倍，不重采样，新文件使用 `CREATE_NEW`。侧车用逻辑单位，不乘像素比例。原图一律保留；不保证 PNG 压缩字节或内嵌元数据复刻。
- 在 Zulu 8u504 用 `javac -source 8 -target 8 -Xlint:all` 编译类及所需工具，在同版本 Java 上用隔离的 `UpgradeWorkspace` 跑三种调用。临时 Pillow 探针对 23×7（稳定）、25×1（目标 16）、25×6（目标 17/18），共 **336** 次 RGBA 比较，并核对每张 PNG 的侧车存在性与 JSON。另检查原图字节、重复运行、PNG/仅侧车/目录/大小写及旧独立手柄保护、旧侧车、畸形 PNG、错误比例、4097² PNG、占用父路径，以及 2× RGBA 和带 alpha 的索引 PNG（包括隐藏 RGB 和非连续列拼接）。均通过。未运行 Gradle 或游戏客户端，未复制官方素材到仓库。
