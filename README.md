# ViaResourcePack

[English](README.en.md) | [中文](README.md)

一个让 Minecraft 加载更早版本资源包的 Fabric 客户端模组。支持 **1.14.4–26.3**。

## 兼容性

- **加速资源包加载的模组**（如 PackForge）：尚未验证与各模组组合使用的兼容性。
- **数据包**：不处理。
- **内置包**（vanilla、Programmer Art、高对比度）：不处理。

## 已知限制

只能自动迁移有明确对应关系的内容。旧纹理图集中未映射的格子、被移除的资源，以及部分自定义模型、着色器和其他模组格式，可能需要手动调整。日志会报告已发现但未处理的情况，并给出缓存条目内 `warnings.txt` 的位置；复用缓存时仍可查看此报告。

### 怎么确认转换结果？

查看游戏的 `logs/latest.log`：

- `Upgraded … -> …`：升级产物已写入缓存。如果只有部分素材异常，先查看同一条目中的 `warnings.txt`；没有报告不代表所有素材都能正常使用。
- `Not upgrading`：没有适用的向上路线。比如在 26.3 打开格式 104 的包，请改用适配该包的客户端或旧版包。
- `Failed to convert …; loading it unchanged`：整包没有转换，Minecraft 会尝试按原样加载。日志会记录失败原因。

## 许可

GPL-3.0-or-later，见 [LICENSE](LICENSE)。
