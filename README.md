# 🍳 OrderToCook（下单了）

> 在 Minecraft 里开一家属于自己的餐厅！接单、烹饪、装盘、配送——体验从后厨到餐桌的完整经营流程。

![Minecraft](https://img.shields.io/badge/Minecraft-Java%20Edition-brightgreen)
![Platform](https://img.shields.io/badge/Fabric%20|%20NeoForge-1.20.1%20|%201.21.1%20|%201.21.4-orange)
![License](https://img.shields.io/badge/License-GPL%20v3-blue)
[![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?style=flat&logo=modrinth)](https://modrinth.com/mod/order-to-cook)
[![CurseForge](https://img.shields.io/badge/CurseForge-F16436?style=flat&logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/order-to-cook)

## 概述

OrderToCook 是一个 Minecraft 餐厅经营模组。放置打单机开始营业，接收各种订单，在操作台上烹饪食物，用打包袋完成外卖配送——经营你的餐厅，赚取 oTc 币，升级设备，登上全服排行榜。

### 核心玩法

- **打单机** — 放置后接取订单，随等级解锁更多槽位、更高收益和特殊订单类型
- **操作台** — 根据订单内容装盘或打包，完成后交给顾客
- **顾客 NPC** — 到店堂食或等待配送，订单超时会流失顾客
- **小电驴** — 骑上它完成远距离配送，可自定义车身颜色
- **oTc 币** — 营收货币，用于升级餐厅和重命名

### 订单类型

| 类型 | 说明 |
|------|------|
| 拼好饭 | 基础订单 |
| 普通套餐 | 标准订单 |
| 奢华套餐 | 高级订单 |
| 多人团聚 | 大额订单 |
| 至尊土豪 | 顶级订单 |

订单附带 **配送**、**远距离配送**、**加急** 等额外属性，对应不同的收益倍率。

## 弹幕/聊天点单（可选）

开启后，直播间观众可以通过弹幕选择套餐和外卖方式，游戏会自动以观众昵称创建订单。默认支持 **DyDanmaku** 和 **BakaDanmaku**，其他弹幕模组也可以在配置文件中添加匹配规则。

### 开启方法

1. 启动一次游戏，让模组生成配置文件。
2. 打开 `config/ordertocook/ordertocook.json5`。
3. 将下面的选项改为 `true`，然后重启游戏：

```json5
"chatOrderEnabled": true
```

开始营业前，请确认已经放置并激活打单机，并通过菜单板设置好餐厅菜单。

### 观众如何点单

弹幕中必须包含完整触发词 `我来下单了`。例如：

```text
我来下单了 套餐A
我来下单了 套餐2 外卖
我来下单了 随机订单
```

支持的点单内容如下：

| 内容 | 效果 |
|------|------|
| `套餐A`、`套餐B`…… | 按字母顺序选择菜单项，A 表示第 1 项 |
| `套餐1`、`套餐2`…… | 按数字选择菜单项，1 表示第 1 项 |
| `随机订单` | 从完整菜单中随机生成订单 |
| `外带`、`外卖`、`带走`、`打包` | 将订单设为外卖 |

游戏聊天栏中会显示为类似下面的格式，顾客名会自动使用观众昵称：

```text
[消息] 测试观众：我来下单了 套餐A 外卖
[舰] <测试观众> 我来下单了 套餐2 打包
```

### 手动测试

可以使用下面的客户端指令模拟一条弹幕：

```text
/ordertocook danmuku [消息] 测试观众：我来下单了 套餐A 外卖
```

也可以测试通用格式：

```text
/ordertocook danmuku 我来下单了-测试观众-套餐A
/ordertocook danmuku 我来下单了-随机订单
```

### 自定义其他弹幕模组

如果使用的弹幕模组不是 DyDanmaku 或 BakaDanmaku，可以修改配置文件中的 `chatOrderRegexPatterns`。规则会由上到下依次尝试，建议将最具体的弹幕格式放在前面，并将通用格式留在最后。

每条规则可以提取以下内容：

- `(?<name>...)`：观众昵称，可省略；未提取到昵称时会随机生成顾客名
- `(?<content>...)`：观众发言，必须存在；套餐、外卖等关键词会从这里读取

例如，某个弹幕模组显示的消息格式为 `[弹幕] 用户名：发言内容`，可以这样配置：

```json5
"chatOrderRegexPatterns": [
    "^\\[弹幕\\]\\s*(?<name>[^：:]+?)\\s*[：:]\\s*(?<content>.+)$",
    "^我来下单了-(?:(?<name>[^-\\r\\n]+?)-)?(?<content>.+)$"
]
```

配置文件中的反斜杠需要写成双反斜杠，例如正则中的 `\s` 应写为 `\\s`。如果弹幕字符串带有 Minecraft `§` 颜色代码，可以参考配置文件内置的 DyDanmaku 和 BakaDanmaku 规则。无论使用哪条规则，提取出的发言内容仍然必须包含 `我来下单了` 才会触发订单。

在多人游戏中，弹幕订单会加入收到该弹幕玩家所拥有的最近一台已激活打单机。若订单没有生成，请检查触发词、菜单编号、打单机状态和功能开关；需要进一步排查时，可以开启 `devMode` 后查看游戏日志。

## 项目结构

```
├── common/                  # 跨版本通用代码
├── fabric-1.20.1/           # Fabric 1.20.1 平台
├── fabric-1.21.1/           # Fabric 1.21.1 平台
├── gradle/                  # Gradle wrapper
└── build.gradle             # 根项目构建脚本
```

## 开发

本项目使用 Gradle 多模块结构，`common` 模块存放共享逻辑，各平台子模块包含平台特定实现。使用 GeckoLib 进行实体动画渲染。


## 许可

本项目代码基于 GNU General Public License v3.0 开源。详见 [LICENSE.txt](LICENSE.txt)。

### 1.3.2更新（主要为bug修复）：
- forge1.20.1、neoforge1.21.1发布！
- 修复目前已知的所有bug（不具体赘述）
- 优化刷碗体验操作等细节
- 现在你不仅可以通过指令将无饥饿值物品加入菜单外，还能魔改已有饥饿值物品的菜单饥饿值
- 现在你可以下载官方材质包，为你的顾客增加多种多样的皮肤！（详见mcmod教程）
  
如果你调整了config配置文件，更新前建议提前备份
