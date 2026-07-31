package cn.breezeth.ordertocook.api.client;

import cn.breezeth.ordertocook.client.OrderToCookModClient;
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
        if (devMode) OrderToCookModClient.LOGGER.info(
                "[ChatOrder/Dev] 客户端收到候选消息：source={}, rawMessage=\"{}\"", normalizedSource, rawMessage);
        ensureInitialized();
        for (int ruleIndex = 0; ruleIndex < messagePatterns.size(); ruleIndex++) {
            Pattern pattern = messagePatterns.get(ruleIndex);
            Matcher matcher = pattern.matcher(rawMessage);
            if (!matcher.matches()) continue;
            if (devMode) OrderToCookModClient.LOGGER.info(
                    "[ChatOrder/Dev] 正则匹配成功：ruleIndex={}, pattern=\"{}\"", ruleIndex, pattern.pattern());
            String content = namedGroup(matcher, "content");
            if (content == null || content.isBlank()) {
                if (devMode) OrderToCookModClient.LOGGER.info(
                        "[ChatOrder/Dev] 放弃候选消息：ruleIndex={} 未提取到非空 content", ruleIndex);
                continue;
            }
            String customerName = namedGroup(matcher, "name");
            customerName = customerName == null || customerName.isBlank()
                    ? ""
                    : truncate(customerName.trim(), MAX_CUSTOMER_NAME_LENGTH);
            int menuIndex = parseMenuIndex(content);
            if (menuIndex < -1 || (menuIndex == -1 && !content.contains(RANDOM_ORDER_KEYWORD))) {
                if (devMode) OrderToCookModClient.LOGGER.info(
                        "[ChatOrder/Dev] 放弃候选消息：ruleIndex={}, content=\"{}\"，未解析出有效套餐或“随机订单”",
                        ruleIndex, content);
                continue;
            }
            if (devMode) OrderToCookModClient.LOGGER.info(
                    "[ChatOrder/Dev] 解析完成：ruleIndex={}, customerName=\"{}\", orderSelection={}",
                    ruleIndex, customerName.isEmpty() ? "<random>" : customerName,
                    menuIndex >= 0 ? "menuIndex=" + menuIndex : "random");
            if (isRecentDuplicate(customerName + '\u0000' + (content == null ? "" : content))) {
                if (devMode) OrderToCookModClient.LOGGER.info(
                        "[ChatOrder/Dev] 指纹去重命中：source={}, customerName=\"{}\", windowMs={}",
                        normalizedSource, customerName.isEmpty() ? "<random>" : customerName,
                        DEDUPLICATION_WINDOW_NANOS / 1_000_000L);
                else OrderToCookModClient.LOGGER.debug(
                        "[ChatOrder] 忽略重复消息：source={}, customerName=\"{}\"",
                        normalizedSource, customerName.isEmpty() ? "<random>" : customerName);
                return true;
            }
            OrderToCookModClient.LOGGER.info(
                    "[ChatOrder] 捕获到关键信息：source={}, 内容=\"{}\"，顾客名=\"{}\"，用户发言=\"{}\"",
                    normalizedSource, rawMessage, customerName.isEmpty() ? "<random>" : customerName, content);
            boolean deliveryRequested = DELIVERY_KEYWORDS.matcher(content).find();
            OrderToCookModClient.LOGGER.info(
                    "[ChatOrder] 发送结构化请求：customerName=\"{}\", deliveryRequested={}, menuIndex={}",
                    customerName.isEmpty() ? "<random>" : customerName, deliveryRequested, menuIndex);
            ModClientNetworking.sendChatOrder(customerName, deliveryRequested, menuIndex);
            return true;
        }
        if (devMode) OrderToCookModClient.LOGGER.info(
                "[ChatOrder/Dev] 放弃候选消息：{} 条已配置正则均未产生有效订单", messagePatterns.size());
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
                OrderToCookModClient.LOGGER.warn("Ignoring invalid chat-order regex: {}", expression, exception);
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
}
