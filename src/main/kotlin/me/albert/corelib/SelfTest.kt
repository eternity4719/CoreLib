package me.albert.corelib

import me.albert.corelib.utils.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * 服务器上运行的自检类,覆盖本次代码清理涉及的全部核心逻辑。
 * 用法:`/corelib test`,只打印失败项的详情,每组末尾输出统计。
 *
 * 验证通过后可保留(不影响运行时性能,仅命令触发)或直接删除本文件。
 */
class SelfTest(private val sender: CommandSender) {

    private var passed = 0
    private var failed = 0

    fun runAll() {
        sender.sendMessage("§8========= §bCoreLib 自检开始 §8=========")
        group("颜色/字符 (Color.kt / Exts.kt)") { colorTests() }
        group("价格/数量级 (Price.kt)") { priceTests() }
        group("逻辑/数学扩展 (Exts.kt)") { mathTests() }
        group("冷却 (CDUtil.kt)") { cdTests() }
        group("物品名与 Lore 属性 (Exts.kt / ItemUtil.kt)") { itemTests() }
        group("背包操作 (Inventory.kt)") { inventoryTests() }
        group("PDC 存取 (PersistData.kt)") { pdcTests() }
        group("玩家名解析 (PlayerName.kt)") { playerNameTests() }
        group("asPlayer (Exts.kt)") { asPlayerTests() }
        val summary =
            if (failed == 0) "§a全部通过: $passed 项"
            else "§c存在失败! 通过 $passed 项, 失败 $failed 项(详情见上方红字)"
        sender.sendMessage("§8========= $summary §8=========")
    }

    // ==================== 断言工具 ====================

    private fun group(name: String, block: () -> Unit) {
        val p0 = passed
        val f0 = failed
        runCatching(block).onFailure {
            failed++
            sender.sendMessage("§c  ✗ [$name] 测试组抛出异常: $it")
        }
        val p = passed - p0
        val f = failed - f0
        sender.sendMessage(if (f == 0) "§a[$name] $p 项通过" else "§c[$name] $f 项失败, $p 项通过")
    }

    private fun eq(name: String, actual: Any?, expected: Any?) {
        if (actual == expected) {
            passed++
        } else {
            failed++
            sender.sendMessage("§c  ✗ $name: 期望 [$expected] 实际 [$actual]")
        }
    }

    private fun ok(name: String, condition: Boolean) = eq(name, condition, true)

    // ==================== 测试组 ====================

    private fun colorTests() {
        eq("removeColors", "§a你§l好".removeColors(), "你好")
        eq("rBukkit § 转 &", "§a你好".rBukkit, "&a你好")
        eq("bukkit & 转 §", "&a你好".bukkit, "§a你好")
        // isColorCode 重构:颜色码重置装饰、装饰码正常打开
        eq("ampToMini 基础颜色", "&a你好".ampToMini(), "<green>你好")
        eq("ampToMini 颜色重置装饰", "&a&lhh&b00".ampToMini(), "<green><bold>hh</bold><aqua>00")
        eq("ampToMini 大写码", "&A&L哈".ampToMini(), "<green><bold>哈")
        eq("ampToMini RGB", "&x&F&F&0&0&0&0红".ampToMini(), "<color:#FF0000>红")
        eq("ampToMini 装饰不跨标签", "&lA<red>B".ampToMini(), "<bold>A</bold><red><bold>B")
        ok(
            "ampToMini 结果可被 MiniMessage 解析",
            runCatching { mm.deserialize("&a&l测试<click:run_command:'/say hi'>点我</click>&r完".ampToMini()) }.isSuccess
        )
    }

    private fun priceTests() {
        eq("unit 百", 150.0.unit, "百")
        eq("unit 千", 1500.0.unit, "千")
        eq("unit 万", 15000.0.unit, "万")
        eq("unit 十万", 150000.0.unit, "十万")
        eq("unit 百万", 1500000.0.unit, "百万")
        eq("unit 千万", 15000000.0.unit, "千万")
        eq("unit 亿", 150000000.0.unit, "亿")
        eq("unit 十亿", 1500000000.0.unit, "十亿")
        eq("unit 超出十亿返回空", 1.0e10.unit, "")
        eq("unit 不足百返回空", 99.0.unit, "")
        eq("unit 零", 0.0.unit, "")
        eq("unit 负数按绝对值(本次修复)", (-1500.0).unit, "千")
        eq("format 向下截断", 3.999.format(), "3.99")
        eq("format 整数不带小数点", 3.0.format(), "3")
    }

    private fun mathTests() {
        eq("Int.pow", 2.pow(10), 1024)
        eq("Int.pow 零次幂", 3.pow(0), 1)
        eq("Long.pow", 2L.pow(32), 4294967296L)
        eq("negativeIf true", 5.negativeIf(true), -5)
        eq("negativeIf false", 5.negativeIf(false), 5)
        eq("Boolean?.toInt true", (true as Boolean?).toInt(), 1)
        eq("Boolean?.toInt null", (null as Boolean?).toInt(), 0)
        ok("isNotNull", ("x" as String?).isNotNull())
        ok("isNull", (null as String?).isNull())
    }

    private fun cdTests() {
        val u = UUID.randomUUID()
        try {
            eq("首次触发无冷却", u.checkCD("selftest", 5000), 0L)
            ok("立即再触发处于冷却", u.checkCD("selftest", 5000) > 0)
            eq("inCD", u.inCD("selftest", 5000), true)
            CDUtil.addNoCdUUID(u)
            eq("免CD特权生效", u.checkCD("selftest", 5000), 0L)
            CDUtil.removeNoCdUUID(u)
            eq("移除后 isNoCd", CDUtil.isNoCd(u), false)
            ok("移除特权后恢复冷却", u.checkCD("selftest", 5000) > 0)
        } finally {
            CDUtil.cds.remove(u)
        }
    }

    private fun itemTests() {
        val item = itemOf(Material.DIAMOND_SWORD, "&a测试之剑", "&7攻击力: &c10.00", "&7一行别的")
        ok("checkName 命中", item.checkName("&a测试之剑"))
        ok("checkName 未命中", !item.checkName("&a别的剑"))
        ok("checkNames 多名单命中", item.checkNames("&b别的", "&a测试之剑"))
        ok("checkNames 全不命中", !item.checkNames("&b别的"))
        ok("null 物品 checkName", !(null as ItemStack?).checkName("&a测试之剑"))

        eq("getLoreAttributeValue 提取", item.getLoreAttributeValue("&7攻击力: &c%s%"), 10.0)
        item.setLoreAttribute("&7攻击力: &c%s%", 25.5)
        eq("setLoreAttribute 后再读", item.getLoreAttributeValue("&7攻击力: &c%s%"), 25.5)
        eq("模板无 %s% 返回 -1", item.getLoreAttributeValue("&7攻击力"), -1.0)
        eq("lore 无匹配行返回 -1", item.getLoreAttributeValue("&7防御力: &c%s%"), -1.0)

        ok("air.isNull", air.isNull)
        ok("null.isNull", (null as ItemStack?).isNull)
        ok("stone 非空", !stone.isNull)

        ok("isSame 相同", ItemStack(Material.STONE, 5).isSame(ItemStack(Material.STONE, 5)))
        ok("isSame 数量不同", !ItemStack(Material.STONE, 5).isSame(ItemStack(Material.STONE, 6)))

        ok("Base64 序列化往返", item.toBase64().toItemStack().isSame(item))
        ok("ItemUtil 序列化往返", ItemUtil.stringToItem(ItemUtil.itemToString(item)).isSame(item))

        val paper = ItemStack(Material.PAPER).withName("&e纸").appendLore("&7第一行")
        ok("withName", paper.checkName("&e纸"))
        eq("appendLore 行数", paper.itemMeta?.lore?.size, 1)
    }

    private fun inventoryTests() {
        val inv = Bukkit.createInventory(null, 27)
        val tpl = ItemStack(Material.COBBLESTONE)

        inv.addItems(tpl, 70) // 本次改为按 maxStackSize 整堆放入
        eq("addItems 总数", inv.amountOf(tpl), 70)
        eq("addItems 占用格数(64+6)", inv.storageContents.count { it != null && !it.isEmpty }, 2)

        inv.removeItems(tpl, 30)
        eq("removeItems 后数量", inv.amountOf(tpl), 40)
        eq("totalAmount", inv.totalAmount, 40)
        eq("emptySlots", inv.emptySlots, 25)

        ok("hasSpace 足够", inv.hasSpace(tpl, 64 * 25))
        ok("hasSpace 不足", !inv.hasSpace(tpl, 64 * 25 + 1))

        val taken = inv.takeRandomStack()
        eq("takeRandomStack 取 1 个", taken.amount, 1)
        eq("takeRandomStack 后总数", inv.totalAmount, 39)
    }

    private fun pdcTests() {
        val meta = ItemStack(Material.PAPER).itemMeta!!
        meta["selftest_int"] = 42
        meta["selftest_str"] = "hello"
        eq("PDC Int 读写", meta.get<Int>("selftest_int"), 42)
        eq("PDC String 读写", meta.get<String>("selftest_str"), "hello")
        ok("hasPD", meta.hasPD("selftest_int"))
        meta.removePD("selftest_int")
        ok("removePD 后不存在", !meta.hasPD("selftest_int"))
        eq("不存在的键返回 null", meta.get<Int>("selftest_missing"), null)
    }

    private fun playerNameTests() {
        val ghost = "__corelib_selftest__"
        eq(
            "uuidOf 离线回退",
            uuidOf(ghost),
            UUID.nameUUIDFromBytes("OfflinePlayer:$ghost".toByteArray())
        )
        eq("nameOf 未知名原样返回", nameOf(ghost), ghost)
        eq("uuidOrNullOf 未知名", uuidOrNullOf(ghost), null)
    }

    private fun asPlayerTests() {
        if (sender is Player) {
            eq("玩家 asPlayer 返回自身", sender.asPlayer(), sender)
        } else {
            eq("控制台 asPlayer 返回 null", sender.asPlayer(""), null)
        }
    }
}
