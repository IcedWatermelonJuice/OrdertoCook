package cn.breezeth.ordertocook.api.client;

import cn.breezeth.ordertocook.OrderToCookMod;
import cn.breezeth.ordertocook.config.ConfigManager;
import cn.breezeth.ordertocook.network.ModClientNetworking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 供原生事件、聊天界面捕获、指令和第三方模组共同调用的客户端公共入口。 */
public final class ChatOrderClientApi {
    private static final int MAX_CUSTOMER_NAME_LENGTH = 64;
    private static final long DEDUPLICATION_WINDOW_NANOS = 1_500_000_000L;
    /** 所有捕获来源都必须包含此触发词，才会开始正则解析。 */
    private static final String ORDER_TRIGGER_KEYWORD = "我来下单了";
    private static final String RANDOM_ORDER_KEYWORD = "随机订单";
    private static final Pattern DELIVERY_KEYWORDS = Pattern.compile("外带|外卖|带走|打包");
    /** 弹幕信息中出现“堂食”时，强制按到店订单处理，不生成外卖单。 */
    private static final String DINE_IN_KEYWORD = "堂食";
    private static final Pattern MENU_KEYWORD = Pattern.compile("套餐\\s*(\\d+|[A-Za-z])", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Long> RECENT_FINGERPRINTS = new LinkedHashMap<>();
    private static volatile List<Pattern> messagePatterns = List.of();
    private static volatile boolean initialized;

    private ChatOrderClientApi() {}

    public static synchronized void initialize() {
        messagePatterns = compilePatterns(ConfigManager.get().chatOrderRegexPatterns);
        RECENT_FINGERPRINTS.clear();
        initialized = true;
    }

    /** @return 正则匹配成功时返回 true；被指纹去重拦截的重复请求也返回 true */
    public static boolean submitMessage(String sourceId, String rawMessage) {
        if (!ConfigManager.get().chatOrderEnabled || rawMessage == null) return false;
        // 普通聊天不可能形成订单，不为其输出开发模式日志，避免刷屏。
        if (!rawMessage.contains(ORDER_TRIGGER_KEYWORD)) return false;
        boolean devMode = ConfigManager.isDevModeEnabled();
        String normalizedSource = sourceId == null || sourceId.isBlank() ? "external_api" : sourceId;
        if (devMode) {
            OrderToCookMod.LOGGER.info(
                    "[ChatOrder/Dev] 客户端收到候选消息：source={}, rawMessage=\"{}\"",
                    normalizedSource, rawMessage);
            logMessageDiagnostics(normalizedSource, rawMessage);
        }
        ensureInitialized();
        for (int ruleIndex = 0; ruleIndex < messagePatterns.size(); ruleIndex++) {
            Pattern pattern = messagePatterns.get(ruleIndex);
            Matcher matcher = pattern.matcher(rawMessage);
            boolean matched = matcher.matches();
            if (devMode) {
                OrderToCookMod.LOGGER.info(
                        "[ChatOrder/Dev] 正则检测：source={}, ruleIndex={}, matched={}, pattern=\"{}\"",
                        normalizedSource, ruleIndex, matched, escapeForLog(pattern.pattern()));
            }
            if (!matched) continue;
            if (devMode) {
                OrderToCookMod.LOGGER.info(
                        "[ChatOrder/Dev] 正则匹配成功：ruleIndex={}, pattern=\"{}\"",
                        ruleIndex, pattern.pattern());
            }
            String content = namedGroup(matcher, "content");
            if (content == null || content.isBlank()) {
                if (devMode) OrderToCookMod.LOGGER.info(
                        "[ChatOrder/Dev] 放弃候选消息：ruleIndex={} 未提取到非空 content", ruleIndex);
                continue;
            }
            String customerName = namedGroup(matcher, "name");
            customerName = customerName == null || customerName.isBlank()
                    ? ""
                    : truncate(customerName.trim(), MAX_CUSTOMER_NAME_LENGTH);
            int menuIndex = parseMenuIndex(content);
            if (menuIndex < -1 || (menuIndex == -1 && !content.contains(RANDOM_ORDER_KEYWORD))) {
                if (devMode) OrderToCookMod.LOGGER.info(
                        "[ChatOrder/Dev] 放弃候选消息：ruleIndex={}, content=\"{}\"，未解析出有效套餐或“随机订单”",
                        ruleIndex, content);
                continue;
            }
            if (devMode) {
                OrderToCookMod.LOGGER.info(
                        "[ChatOrder/Dev] 解析完成：ruleIndex={}, customerName=\"{}\", orderSelection={}",
                        ruleIndex, customerName.isEmpty() ? "<random>" : customerName,
                        menuIndex >= 0 ? "menuIndex=" + menuIndex : "random");
            }
            if (isRecentDuplicate(customerName + '\u0000' + (content == null ? "" : content))) {
                if (devMode) {
                    OrderToCookMod.LOGGER.info(
                            "[ChatOrder/Dev] 指纹去重命中：source={}, customerName=\"{}\", windowMs={}",
                            normalizedSource, customerName.isEmpty() ? "<random>" : customerName,
                            DEDUPLICATION_WINDOW_NANOS / 1_000_000L);
                } else {
                    OrderToCookMod.LOGGER.debug(
                            "[ChatOrder] 忽略重复消息：source={}, customerName=\"{}\"",
                            normalizedSource, customerName.isEmpty() ? "<random>" : customerName);
                }
                return true;
            }
            OrderToCookMod.LOGGER.info(
                    "[ChatOrder] 捕获到关键信息：source={}, 内容=\"{}\"，顾客名=\"{}\"，用户发言=\"{}\"",
                    normalizedSource, rawMessage, customerName.isEmpty() ? "<random>" : customerName, content);
            Boolean deliveryRequested;
            if (content.contains(DINE_IN_KEYWORD)) {
                deliveryRequested = Boolean.FALSE;
            } else {
                deliveryRequested = DELIVERY_KEYWORDS.matcher(content).find() ? Boolean.TRUE : null;
            }
            OrderToCookMod.LOGGER.info(
                    "[ChatOrder] 发送结构化请求：customerName=\"{}\", deliveryRequested={}, menuIndex={}",
                    customerName.isEmpty() ? "<random>" : customerName, deliveryRequested, menuIndex);
            ModClientNetworking.sendChatOrder(customerName, deliveryRequested, menuIndex);
            return true;
        }
        if (devMode) {
            OrderToCookMod.LOGGER.info(
                    "[ChatOrder/Dev] 放弃候选消息：{} 条已配置正则均未产生有效订单", messagePatterns.size());
        }
        return false;
    }

    private static synchronized void ensureInitialized() {
        if (!initialized) initialize();
    }

    private static synchronized boolean isRecentDuplicate(String fingerprint) {
        long now = System.nanoTime();
        RECENT_FINGERPRINTS.entrySet().removeIf(entry -> now - entry.getValue() > DEDUPLICATION_WINDOW_NANOS);
        if (RECENT_FINGERPRINTS.containsKey(fingerprint)) return true;
        RECENT_FINGERPRINTS.put(fingerprint, now);
        return false;
    }

    private static int parseMenuIndex(String content) {
        if (content == null) return -1;
        Matcher matcher = MENU_KEYWORD.matcher(content);
        if (!matcher.find()) return -1;
        String code = matcher.group(1);
        if (code.length() == 1 && Character.isLetter(code.charAt(0))) {
            return Character.toUpperCase(code.charAt(0)) - 'A';
        }
        try {
            int number = Integer.parseInt(code);
            return number > 0 ? number - 1 : -2;
        } catch (NumberFormatException ignored) {
            return -2;
        }
    }

    private static List<Pattern> compilePatterns(List<String> configuredPatterns) {
        List<Pattern> compiled = new ArrayList<>();
        if (configuredPatterns == null) return compiled;
        for (String expression : configuredPatterns) {
            if (expression == null || expression.isBlank()) continue;
            try {
                compiled.add(Pattern.compile(expression));
            } catch (PatternSyntaxException exception) {
                OrderToCookMod.LOGGER.warn("Ignoring invalid chat-order regex: {}", expression, exception);
            }
        }
        return List.copyOf(compiled);
    }

    private static String namedGroup(Matcher matcher, String groupName) {
        try {
            return matcher.group(groupName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 输出不会受日志编码影响的字符串诊断信息，用于定位不可见字符。 */
    private static void logMessageDiagnostics(String sourceId, String value) {
        OrderToCookMod.LOGGER.info(
                "[ChatOrder/Dev] 字符串诊断：source={}, utf16Length={}, codePointCount={}, escaped=\"{}\", codePoints={}",
                sourceId, value.length(), value.codePointCount(0, value.length()),
                escapeForLog(value), describeCodePoints(value));
    }

    /** 将控制字符、格式字符和特殊空格转成 Unicode 转义，防止日志显示时被吞掉。 */
    private static String escapeForLog(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.FORMAT
                    || codePoint == 0x00A0 || codePoint == 0x3000) {
                escaped.append(String.format(codePoint <= 0xFFFF ? "\\u%04X" : "\\U%08X", codePoint));
            } else {
                escaped.appendCodePoint(codePoint);
            }
        });
        return escaped.toString();
    }

    /** 按 Unicode 码点输出完整序列，复制日志后仍可无歧义地比较两条消息。 */
    private static String describeCodePoints(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (!result.isEmpty()) result.append(' ');
            result.append(String.format(codePoint <= 0xFFFF ? "U+%04X" : "U+%06X", codePoint));
        });
        return result.toString();
    }
}
