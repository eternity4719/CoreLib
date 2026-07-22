# 代码规范性清理记录（2026-07-16）

本次清理原则：**不改动任何 public API 的签名、可见性、返回类型**，只调整函数内部实现、注释与 import，保证依赖 CoreLib
的下游插件完全二进制兼容。

全部改动已通过 `gradlew build` 编译验证。

---

## 一、内部实现简化（签名、行为不变）

| 文件                   | 改动                                                        | 说明                                                                                         |
|----------------------|-----------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `utils/Exts.kt`      | `asPlayer` 函数体重写                                          | 原来用 `as? Player ?: run { … isTrue { } … }` 绕行，改为直白的 `if (this is Player) return this` 三行写法 |
| `utils/Exts.kt`      | `ItemStack?.isNull` getter 单表达式化                          | 去掉多余的 `return` 与括号                                                                         |
| `utils/Exts.kt`      | `checkName` 委托给 `checkNames`                              | 消除 `checkName` / `checkNames` / `checkItemDisplayName` 三处重复的显示名比较逻辑，三个函数全部保留               |
| `utils/Exts.kt`      | 新增私有 `parseLorePattern` 辅助函数                              | `getLoreAttributeValue` 与 `setLoreAttribute` 原本各自重复「转换颜色 → 校验 `%s%` → split」前置逻辑，现共用一份     |
| `utils/Exts.kt`      | `Location.isSafe()` 复用 `willSuffocate()`                  | 脚部/头部窒息判断原本写了两遍                                                                            |
| `utils/CDUtil.kt`    | `isInCd` 合并重复分支                                           | 「首次触发」与「冷却已过」两个分支都是 `playerCd[key] = current; return 0L`，合并为一处                             |
| `utils/Inventory.kt` | `addItems` 按 `maxStackSize` 整堆放入                          | 原来 `repeat(amount) { addItem(单个) }`，数量大时要调用几千次 `addItem`；结果不变，性能更好                         |
| `utils/Chat.kt`      | `onQuit` 一行化                                              | 去掉两个多余的中间变量                                                                                |
| `utils/Color.kt`     | 删除 `colorCodes` set，改为 `Char.isColorCode` 私有扩展            | 原本 `legacy` map 与 `colorCodes` set 双份维护颜色码知识，新增码要改两处                                       |
| `utils/Price.kt`     | `Double.unit` 改为数值阈值判断                                    | 原实现靠 `DecimalFormat("0.00")` 格式化后的字符串长度判断数量级，负数会因 `-` 号偏移一档出错；现按绝对值阈值判断，顺带修复负数 bug         |
| `CoreLib.kt`         | `onCommand` 展平双层 `when`                                   | `when(args.size) → when(subCmd)` 各只有一个分支，改为 `if`；nocd 开/关两条几乎相同的消息合并为一条模板                  |
| `CoreLib.kt`         | `onTabComplete` 用 `map` 收集玩家名                             | 替换 `mutableListOf` + `forEach { add() }`                                                   |
| `CoreLib.kt`         | `contentEquals(…, true)` → `equals(…, ignoreCase = true)` | 与项目其他位置的写法统一                                                                               |
| `CoreLib.kt`         | `.append { msg }` → `.append(msg)`                        | 原写法靠 lambda SAM 转换成 `ComponentLike`，改为直接传 Component                                        |

## 二、注释 / import 清理

| 文件                        | 改动                                                                         |
|---------------------------|----------------------------------------------------------------------------|
| `utils/Exts.kt`           | 删除重复出现两遍的「5. 逻辑流与数学扩展」分节注释                                                 |
| `utils/Economy.kt`        | 删除悬空注释 `/** 获取玩家点券余额 */`（下面没有代码）                                           |
| `inventory/GuiManager.kt` | 修正 `fillGlass` 注释：写的「灰色玻璃板」实际是蓝色（`BLUE_STAINED_GLASS_PANE`）                |
| `inventory/GuiManager.kt` | 删除过时的编号注释（1、2、4 缺 3）                                                       |
| `utils/IpUtil.kt`         | 删除两行导入自身 object 成员的无用 import（`IpUtil.load` / `IpUtil.reload`）              |
| `objects/CustomConfig.kt` | Guava 的 `Charsets.UTF_8` 换为 JDK 标准 `StandardCharsets.UTF_8`，减少一处 Guava 依赖点 |

## 三、只补文档、不改行为

| 文件                   | 改动                                                                      |
|----------------------|-------------------------------------------------------------------------|
| `utils/Exts.kt`      | `air` / `stone` 全局共享 `ItemStack` 加 KDoc 警告：只读，修改前必须 `clone()`，否则污染所有引用处 |
| `utils/Inventory.kt` | `takeRandomStack()` 补 KDoc 前置条件：存储区为空时抛 `NoSuchElementException`        |
| `utils/Inventory.kt` | `hasSpace` 补 KDoc 说明：只按空格子保守估计，不计入现有同类堆的剩余容量                            |
| `utils/Price.kt`     | `priceFormat` 补注释说明为何每次访问新建实例（`DecimalFormat` 非线程安全）                    |

## 四、玩家可见文案微调（非 API）

| 文件                | 改动                                                                    |
|-------------------|-----------------------------------------------------------------------|
| `utils/CDUtil.kt` | 免 CD 提示原文「已为您跳过剩余冷却: $time」中 `$time` 实为总冷却时长而非剩余时间，改为「已为您跳过本次冷却(…ms)」 |
| `CoreLib.kt`      | nocd 开/关提示合并为统一模板（内容不变，仅标点统一）                                         |

## 附：验证记录

本次改动曾附带 `SelfTest.kt` 自检类（`/corelib test`，覆盖颜色转换、价格单位、数学扩展、
冷却、物品名/Lore 属性、背包操作、PDC、玩家名解析、asPlayer 共 9 组断言），
已于 2026-07-16 在服务器上实测**全部通过**，随后移除。
如需复跑，可从 git 历史找回（提交 7a23ca7）。

## 五、备案：发现但按兼容性要求不处理的问题

以下问题涉及 public API 的删除、签名或可见性变更，为保证下游插件兼容**全部保留原样**：

- `ItemUtil.kt` 存在两套平行 API（`ItemUtil.make/addLore` 与顶层 `itemOf/withName/withLore/appendLore`、`itemToString` 与
  `toBase64` 等）。注意 `ItemUtil.addLore` 会把已有 lore 重新做一次颜色转换，而 `appendLore` 只转换新增行，两者行为已有差异。
- `Exts.kt` 的 `isTrue/isFalse/isNotTrue/isNotFalse/isTrueAnd/isFalseAnd/isNullAnd/isNullOr/isEqual` 布尔扩展家族与
  Kotlin 惯用法（`== true`、`?.let`、`takeIf`）大量重叠。
- `getLoreAttributeValue` 用 `-1.0`（未找到）/ `0.0`（解析失败）双哨兵返回值，语义易混淆。
- `CDUtil.cds`、`CDUtil.noCdPlayers`、`Chat.chatHandlers` 为 public 可变集合，下游可能直接访问。
- `GuiHolder` 的 `var onClick` 属性与 `fun onClick()` 函数成对冗余（共 4 组）。
- `IpUtil.getPlayerIp(player)` 与 `Player.getIP()` 功能重复。
- `Chat.kt` 监听的 `AsyncPlayerChatEvent` 在 Paper 已弃用（建议未来迁移 `AsyncChatEvent`，属行为变更，暂不动）。
