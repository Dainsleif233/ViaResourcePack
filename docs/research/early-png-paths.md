# 早期资源包格式 4–15：原版 PNG 路径审计

原版制品与映射通过官方摘要校验；本文 `build/` 引用指本地取证中间件，不随仓库分发。

## 格式 9→11 的复选标记

`assets/realms/textures/gui/realms/checkmark.png` → `assets/minecraft/textures/gui/checkmark.png`。1.19.2（格式 9）→ 22w42a（格式 11）是 [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) 的相邻普通发行条目；制品根 `version.json` 确认资源包格式。没有资源包格式 10，不能把正式版 1.19.2→1.19.3 的表观跳号判成 9→12。22w44a（11）、1.19.3（12）、1.20.1（15）继续使用新路径。参见 [22w42a 公告](https://www.minecraft.net/en-us/article/minecraft-snapshot-22w42a)、[格式表](classic-pack-formats.md#L19-L26)和[路径／版本清单核验](../../build/viarp-early-png-audit/checkmark-scope.txt#L1-L16)。

| 版本 | 官方制品（URL 内是完整 SHA-1） | 实测客户端 JAR SHA-256 |
| --- | --- | --- |
| 1.19.2 | [版本元数据](https://piston-meta.mojang.com/v1/packages/9ffd2bd611f007a11d9f1429f74b3d01bb8e84eb/1.19.2.json) · [客户端 JAR](https://piston-data.mojang.com/v1/objects/055b30d860ead928cba3849ba920c88b6950b654/client.jar)（21,644,740 B） · [客户端映射](https://piston-data.mojang.com/v1/objects/8e8c9be5dc27802caba47053d4fdea328f7f89bd/client.txt) | `e1ac65de9b471b6916cc457fdcff00c1bafac17027aa79100c4df893b3d956db` |
| 22w42a | [版本元数据](https://piston-meta.mojang.com/v1/packages/a3a34cd77b35b6791ec256902155889919d494d3/22w42a.json) · [客户端 JAR](https://piston-data.mojang.com/v1/objects/7294caf715c4994b7b26f349c823391afb25c495/client.jar)（22,481,128 B） · [客户端映射](https://piston-data.mojang.com/v1/objects/10743e772469858f7ac41667e3b581dcfad60b65/client.txt) | `6f2618798c87a77834fb02c41fb3f396a9134f69c3aa7b43e1ab35852401f202` |

**唯一性与内容。** 两张 9×8 PNG 逐字节相同，无 `.png.mcmeta`；PNG SHA-256 `072ab9f2f7909be0dd36487dae89f94084d767f040bc9f6f24261b7a302b4e62`，完整 RGBA SHA-256 `dc8758e4d385007f8d4074d08eeecadcdf6957d829381d4ca1d35bb9193e8369`。各版全 JAR PNG 中只有这一张匹配；两份 Mojang 外置索引各 26 张 PNG（合计 24 个不同对象），验证对象 SHA-1／长度并解码后无其他匹配。旧 ID 在 22w42a 消失，新 ID 在 1.19.2 不存在。

**消费者与语义。** 对成对 JAR 的原始字节码与 Mojang 映射交叉核对，不以相关文件同像素单独判定：

| 活跃消费者 | 1.19.2 行证据 | 22w42a 行证据 |
| --- | --- | --- |
| Realms 已选存档槽位：`RealmsWorldSlotButton.renderCheckMark`；原始行两版均 224–230 | [映射](../../build/viarp-early-png-audit/1.19.2-mappings.txt#L3538-L3559) · [调用](../../build/viarp-early-png-audit/1.19.2-ecx.javap.txt#L561-L571) · [绘制](../../build/viarp-early-png-audit/1.19.2-ecx.javap.txt#L638-L674) · [旧 ID](../../build/viarp-early-png-audit/1.19.2-ecx.javap.txt#L697-L702) | [映射](../../build/viarp-early-png-audit/22w42a-mappings.txt#L3508-L3529) · [调用](../../build/viarp-early-png-audit/22w42a-efk.javap.txt#L561-L571) · [绘制](../../build/viarp-early-png-audit/22w42a-efk.javap.txt#L638-L674) · [新 ID](../../build/viarp-early-png-audit/22w42a-efk.javap.txt#L697-L702) |
| 聊天举报选中消息：`ChatSelectionScreen$ChatSelectionList$MessageEntry.renderSelectedCheckmark`；原始行 353–360 → 351–358 | [映射](../../build/viarp-early-png-audit/1.19.2-mappings.txt#L14265-L14289) · [调用](../../build/viarp-early-png-audit/1.19.2-epd$a$d.javap.txt#L168-L183) · [绘制](../../build/viarp-early-png-audit/1.19.2-epd$a$d.javap.txt#L360-L383) · [旧 ID](../../build/viarp-early-png-audit/1.19.2-epd$a$d.javap.txt#L593-L605) | [映射](../../build/viarp-early-png-audit/22w42a-mappings.txt#L14113-L14137) · [调用](../../build/viarp-early-png-audit/22w42a-erm$a$d.javap.txt#L168-L183) · [绘制](../../build/viarp-early-png-audit/22w42a-erm$a$d.javap.txt#L360-L383) · [新 ID](../../build/viarp-early-png-audit/22w42a-erm$a$d.javap.txt#L593-L605) |

两对调用均绑定对应的完整资源 ID 并从 `(0,0)` 采样整张 9×8 图。原版 `RenderSystem._setShaderTexture` 将该 ID 送入 `TextureManager.getTexture`（[旧](../../build/viarp-early-png-audit/1.19.2-com.mojang.blaze3d.systems.RenderSystem.javap.txt#L2245-L2269)、[新](../../build/viarp-early-png-audit/22w42a-com.mojang.blaze3d.systems.RenderSystem.javap.txt#L2245-L2269)）；`SimpleTexture.TextureImage.load` 用同一 ID 查找、读取并解码（[旧](../../build/viarp-early-png-audit/1.19.2-fka$a.javap.txt#L65-L77)、[新](../../build/viarp-early-png-audit/22w42a-fmp$a.javap.txt#L65-L77)）；`MultiPackResourceManager.getResource` 按命名空间找对应资源栈（[旧](../../build/viarp-early-png-audit/1.19.2-aif.javap.txt#L215-L237)、[新](../../build/viarp-early-png-audit/22w42a-ajm.javap.txt#L215-L237)）。旧覆盖不能仅凭等像素被新 ID 找到。

**规则范围。** [经典规则分派](../../src/main/java/top/syshub/viarp/convert/ClassicPackUpgrades.java) 在格式 11 和 16 分别复制这条路径，不全局改命名空间或重写 filter。两个边都保留源 PNG 和侧车，只有目标 PNG 与侧车均未被包占用才复制；显式目标优先，碰撞记入报告。

**后续目标超出 4–15：已核实首次改为 sprite 的入边是 15→16，不是 17 或 18。** [1.20.1 JAR](https://piston-data.mojang.com/v1/objects/0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838/client.jar)（15）仍是独立 GUI PNG；官方清单的下一条 [23w31a JAR](https://piston-data.mojang.com/v1/objects/ca51bf36913a7333c055096a52a3a96fbdb11813/client.jar)（16）改用 `assets/minecraft/textures/gui/sprites/icon/checkmark.png`，完整 RGBA 相同、PNG 编码不同。Realms `renderCheckMark`（原始 226–229 行）和聊天 `renderSelectedCheckmark`（341–347 行）均将 `minecraft:icon/checkmark` 作为 9×8 GUI sprite 绘制；[Realms](../../build/viarp-early-png-audit/23w31a-enr.javap.txt#L648-L689)、[聊天绘制](../../build/viarp-early-png-audit/23w31a-fbt$a$d.javap.txt#L345-L374)、[聊天 ID](../../build/viarp-early-png-audit/23w31a-fbt.javap.txt#L627-L634)、[图集和 SHA](../../build/viarp-early-png-audit/follow-on.txt#L1-L18)。23w32a／23w33a（17）与 [1.20.2 JAR](https://piston-data.mojang.com/v1/objects/82d1974e75fc984c5ed4b038e764e50958ac61a0/client.jar)（18）保留该路径与 PNG。9→11 单独实施不能覆盖现代目标，所以 15→16 添加独立迁移。

[26.3 JAR](https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar)（97.1）仍有该 sprite 路径；聊天举报 [初始化 ID](../../build/viarp-early-png-audit/26.3-net.minecraft.client.gui.screens.reporting.ChatSelectionScreen.javap.txt#L604-L609)，由 [选中消息的绘制分支](../../build/viarp-early-png-audit/26.3-net.minecraft.client.gui.screens.reporting.ChatSelectionScreen$ChatSelectionList$MessageEntry.javap.txt#L169-L183) 调用 [MessageEntry.extractSelectedCheckmark](../../build/viarp-early-png-audit/26.3-net.minecraft.client.gui.screens.reporting.ChatSelectionScreen$ChatSelectionList$MessageEntry.javap.txt#L349-L375) 实际绘制（原始行 330–333）。对比 23w31a，26.3 的 PNG 不再严格 RGBA 相同：22 个全透明像素的 RGB 归零，非透明像素无差异；这里证明路径和聊天消费者延续，不声称 26.3 仍有相同 Realms 消费者。

## 排除记录与覆盖边界

| 样本 | 排除理由 |
| --- | --- |
| 铁傀儡 `iron_golem.png`→`iron_golem/iron_golem.png` | 表面是正式版 4→5；19w46b（4）已移，且格式 5 [已有规则](../../src/main/java/top/syshub/viarp/convert/ClassicPackUpgrades.java#L498-L499)。 |
| 普通鱿鱼 `squid.png`→`squid/squid.png`、土径顶面／侧面 | 早期格式 7 的鱿鱼仍用旧 ID；格式 7 已 [迁移土径并补鱿鱼新路径](../../src/main/java/top/syshub/viarp/convert/ClassicPackUpgrades.java#L555-L582)。 |
| 附魔光效与旧锻造图 | 附魔光效一拆二，侧车及不同时期的像素对应不能证明一对一；锻造旧 ID 被新 GUI 重新使用。两项 [已有定向规则](../../src/main/java/top/syshub/viarp/convert/ClassicPackUpgrades.java#L655-L673)。 |
| 陶罐的四张 `pottery_pattern_*`→`*_pottery_pattern` | 正式版 13→15 察觉变化，但实物定位到 [1.19.4→23w13a](../../build/viarp-early-png-audit/report.txt#L70-L77)，仍是 13→13；陶片 `*_pottery_shard`→`*_pottery_sherd` 则已在 [14→14](../../build/viarp-early-png-audit/expanded-moves.txt#L21-L40) 发生。 |
| 主菜单 Logo、Realms 邀请图、库存药效板等 | 不是唯一且全像素匹配的移位；此审计不把重新排版、裁剪或改尺寸当成路径规则。 |

覆盖格式入边 4→5、5→6、6→7、7→8、8→9、9→11、11→12、12→13、13→14、14→15。比较 1.14.4–1.20.1 的 21 个正式版及 17 个边界快照／候选版；不纳入愚人节分支 `23w13a_or_b`。先比 Minecraft／Realms 的 entity、GUI、misc，后扩查这两命名空间其余 `textures/`；按尺寸和完整解码 RGBA（含透明像素的 RGB）分组，要求一对一、旧路径消失、新路径此前不存在，再核查侧车和实际消费者。本次并未穷尽所有开发快照、Mod／第三方包、同路径内容变化、或子图像变换；没有合格路径不等于该边无资源变化。

复现：校验每版元数据 SHA-1，再校验客户端 JAR、所用映射的 SHA-1 和长度，记录 JAR SHA-256；未启动游戏。忽略目录保留 [采集脚本](../../build/viarp-early-png-audit/audit.py)、[差异行](../../build/viarp-early-png-audit/report.txt#L1-L79)、[字节码脚本](../../build/viarp-early-png-audit/consumers.py#L7-L32)、[外置 PNG 唯一性脚本](../../build/viarp-early-png-audit/checkmark_scope.py)。
