# 47 → 48：从实体合图提取鞍具纹理

结论：在原版 UV 上，**骆驼、驴、骡、骷髅马、僵尸马**各有一个确定的合图来源，能原坐标提取；普通马的七种毛色共用一个新版鞍具槽，只有本包的七张旧图都存在、尺寸相同、掩膜内可见 RGBA 相同时才可合并。任何未被验证的外观（例如自定义实体模型）不视为等价。

## 确认相邻边界

用 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 定位各版本 JSON，以 JSON 的 `downloads.client.sha1` 校验实际下载的官方客户端 JAR；格式来自 JAR 内 `version.json` 的 `pack_version.resource`。源码历史是相邻提交 `9d861cd572808a3beb011170312aa4914f22c114` → `e13daa1e0b595ff7d25410ee282287941b7df440` → `dcce7af21d9d350169c61a10370c1b2e8574f01d`；各提交的 `client/net/minecraft/SharedConstants.java:38` 也可核对。

| 官方版本 JSON | 资源格式 | 客户端 JAR SHA-1（实测与 JSON 一致） |
| --- | ---: | --- |
| [25w02a](https://piston-meta.mojang.com/v1/packages/a8fb8f69c2524308898d89c4947d1c831883a681/25w02a.json) | 47 | `5dc770c10d3769f9f3437a2bd7029b10b9c0d780` |
| [25w03a](https://piston-meta.mojang.com/v1/packages/87194cf5b5960d184805a73e5aa4a054679654a8/25w03a.json) | 48 | `9acca901e3564a91250b941cd2c55a55d0b71bca` |
| [25w04a](https://piston-meta.mojang.com/v1/packages/fec0fd5e96dd43dcad46c2b09d66b4260b96b6c1/25w04a.json) | 49（对照） | `6102118fc64fba5ac798225e1c3618c005e01a51` |

## 路径、共享槽与原版对照

以下路径相对于 `assets/minecraft/textures/entity/`。25w02a 的这 12 张旧图包含鞍具，25w03a 仍用同名路径加载体表，另加 6 张鞍具纹理。25w04a 对照中，这 12 张体表和 6 张鞍具均与 25w03a 字节相同。

| 旧合图 | 新鞍具纹理 | 原版尺寸 | 新鞍具非零 alpha 像素 |
| --- | --- | ---: | ---: |
| `camel/camel.png` | `equipment/camel_saddle/saddle.png` | 128×128 | 948 |
| `horse/donkey.png` | `equipment/donkey_saddle/saddle.png` | 64×64 | 244 |
| `horse/mule.png` | `equipment/mule_saddle/saddle.png` | 64×64 | 244 |
| `horse/horse_skeleton.png` | `equipment/skeleton_horse_saddle/saddle.png` | 64×64 | 244 |
| `horse/horse_zombie.png` | `equipment/zombie_horse_saddle/saddle.png` | 64×64 | 221 |
| `horse/horse_{white,creamy,chestnut,brown,black,gray,darkbrown}.png` | `equipment/horse_saddle/saddle.png`（7→1） | 64×64 | 244 |

原版**目标 PNG 文件** SHA-1：骆驼 `4da48001c572d784c13cbccb08b2dbd29a7a52e8`；普通马、驴、骡和骷髅马恰好字节相同 `0ba762ac7609eca1f1cb9a090184acdbcf4aceb5`（不是共享路径，不能用一种实体的自定义图补另一种）；僵尸马 `a0d70e08e29cb634bbde9b6203ebeaa2364cbc6d`。原版僵尸马的独立层相对普通马有 44 个仅普通马非透明、21 个仅僵尸马非透明、10 个两者都非透明但颜色不同的像素。

猪、炽足兽已经各有旧的独立鞍图，不适用合图掩膜：炽足兽 `strider/strider_saddle.png` 是字节相同的路径迁移；猪 `pig/pig_saddle.png` 从 64×32 扩到 64×64，130 个非透明像素原坐标、原 RGBA，没有缩放或位移。这两种不由本规则处理。

## 为什么掩膜而不是裁边、减色或整图复制

- 47 以同一 PNG 绘制体表及鞍具。鞍和笼头按 saddled 显示，缰绳同时要求 ridden；见 `9d861cd57:client/net/minecraft/client/model/CamelModel.java:53–55,83–91` 和 `9d861cd57:client/net/minecraft/client/model/AbstractEquineModel.java:78–84,108–113`。
- 48 的 `CamelSaddleModel` 和 `EquineSaddleModel` 基于体表网格加回同 UV 的鞍具；体表的几何也在这个专用装备模型里，故不能直接复用整张合图。缰绳在装备已进入渲染时仍按 ridden 显示。见 `e13daa1e0:client/net/minecraft/client/model/CamelSaddleModel.java:27–43`、`e13daa1e0:client/net/minecraft/client/model/EquineSaddleModel.java:39–59`、`e13daa1e0:client/net/minecraft/client/model/DonkeyModel.java:34–55` 及 `e13daa1e0:client/net/minecraft/client/model/geom/LayerDefinitions.java:228,255,299,322,380,442`。幼体缩放变换不是另一张 UV 图。
- 48 的普通鞍是 `EquipmentSlot.SADDLE` 上带 `Equippable` 组件的物品（`e13daa1e0:client/net/minecraft/world/item/Items.java:938`、`e13daa1e0:client/net/minecraft/world/item/equipment/Equippable.java:54–55`）。同 JAR 的 `data/minecraft/tags/entity_type/can_equip_saddle.json` 包括全部八类；`assets/minecraft/equipment/saddle.json` 分别声明八种 `*_saddle` 层，每种的 texture 都为 `minecraft:saddle`。`e13daa1e0:client/net/minecraft/client/renderer/entity/layers/SimpleEquipmentLayer.java:42–50` 从该物品组件选装备 asset；`e13daa1e0:client/net/minecraft/client/resources/model/EquipmentClientInfo.java:120–121` 以层名派生目录。普通马的毛色选择仍只作用于体表，装备层类型固定，见 `e13daa1e0:client/net/minecraft/client/renderer/entity/HorseRenderer.java:28–39`。所以有一张自定义马毛色图并不代表其它六种（例如来自底层包）的鞍图也可被同一图替换。

坐标从 PNG 左上角起，区间均左闭右开。根据 `e13daa1e0:client/net/minecraft/client/model/geom/ModelPart.java:252–281` 的盒子六面，每个 `(u,v,w,h,d)` 产生这两个矩形的并集；对每种网格取所有盒子的并集，不应用旋转、镜像、缩放插值或位置偏移：

	saddleMask(cube) = [u+d, u+d+2w) × [v, v+d)
	                 ∪ [u, u+2(w+d)) × [v+d, v+d+h)

| 网格 | `(u,v,w,h,d)`（重复镜像的 UV 只列一次） | 并集面积 |
| --- | --- | ---: |
| 骆驼，128² | `(74,64,9,5,11) (92,114,7,3,11) (0,89,15,12,27) (98,42,0,7,15) (84,57,7,7,0) (60,87,7,8,19) (21,64,7,14,7) (50,64,5,5,6) (74,70,1,2,2)` | 4,144 |
| 马科，64² | `(26,0,10,9,9) (29,5,1,2,2) (32,2,0,3,16) (1,1,6,5,6) (19,0,4,5,2)` | 902 |

缰绳是退化盒面，`w=0` 或 `d=0` 只使部分矩形为空，不能丢掉整个盒子。例如骆驼左/右缰绳覆盖 `[98,128)×[57,64)`，连接片覆盖 `[84,98)×[57,64)`，马科左右缰绳覆盖 `[32,64)×[18,21)`。

将鞍具掩膜与各实体的体表盒面 UV 求交，**交集均为 0**（含驴、骡的加大耳朵和箱子）：体表 UV 并集面积是骆驼 5,566、驴与骡各 2,578、其余马科各 2,330。体表数据见 `e13daa1e0:client/net/minecraft/client/model/CamelModel.java:35–47`、`e13daa1e0:client/net/minecraft/client/model/AbstractEquineModel.java:47–61,70–76`、`e13daa1e0:client/net/minecraft/client/model/DonkeyModel.java:34–55`。所有 12 个原版体表在其**被采样的 UV** 上，新旧可见 RGBA 相同。原版发布图还删掉部分未采样的旧填充色；迁移不必照搬这种编辑，也不必从旧体表抠去旧鞍具：原样保留体表文件，未使用区域不会成为新体表采样。鞍图之外必须留全透明，而不是填充旧打底色。

## 迁移契约和未覆盖的效果

- 只读已知原版命名、正方形且为基准尺寸整数倍的 PNG。将每个原版 texel 对应的 `scale×scale` 像素块原坐标复制；原版使用无模糊、无 mipmap 的 cutout 采样，故把 alpha=0 的 RGB 归零，不改变 alpha>0 的 RGBA。用独立的原版目标 PNG 检验，抽取的六张图含透明像素在内逐位解码 RGBA 相等；不要求 PNG 压缩编码相同，也不分发原版字节。滤镜和深度状态的依据为 `e13daa1e0:client/net/minecraft/client/renderer/RenderType.java:58–63,202` 以及 JAR 内 `assets/minecraft/shaders/core/entity.fsh` 的 alpha cutout。
- 来源损坏、非常规尺寸、>8 位通道、源侧车、目的 PNG 或目的侧车已占用、目标路径冲突、自定义 `equipment/saddle.json` 时保留既有资源，不猜。七种马缺项、鞍具平面不同或不同分辨率时也如此；即使已提供的几张相同，也不借用底层或打包原版素材作为未知来源。
- 不能模拟多资源包各自独立的优先级、自定义装备 JSON/物品 asset、实体模型/CEM、shader 采样、旧盔甲或花纹覆盖图上另绘的鞍具，也不声明全场景逐像素渲染一致：旧鞍属于实体 base cutout，新鞍走独立的 armor cutout 层，有不同的深度偏移、顺序、轮廓或附魔表现。原版相邻版本验证是纹理和 UV 的证据，不是客户端截图回归，也不证明任意更早或经 mod 修改的 UV。

验证方法：对官方 JAR 路径及 SHA-1 进行复核；由公开盒面公式以 Pillow 解码对照；用真实 `UpgradeWorkspace`、`UpgradePng` 和 Java 8 在仓库外独立执行，逐像素比较六张输出并检查来源 PNG 未改。另测 2× 比例与两种退化缰绳、不合法 PNG/尺寸/位深、源侧车、目的 PNG/侧车、显式装备 JSON、路径冲突及马毛色子集/不同像素/不同分辨率/相同掩膜四种情况。
