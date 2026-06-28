package martin.game.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import martin.game.dto.MutedUserDto;
import martin.game.model.ModerationAction;
import martin.game.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private static final Logger logger = LogManager.getLogger(ContentModerationService.class);
    private static final String RATE_KEY_PREFIX = "chat:rate:";
    private static final String DUP_KEY_PREFIX = "chat:dup:";
    private static final String MUTE_KEY_PREFIX = "chat:mute:";
    private static final String VIOLATION_KEY_PREFIX = "chat:violation:";
    private static final String MUTE_USERS_KEY = "chat:mute-users";
    private static final String MASK_WORDS_FILE = "mask-words.txt";
    private static final String REJECT_WORDS_FILE = "reject-words.txt";
    private static final Pattern CONTACT_PATTERN = Pattern.compile(
            "(1[3-9]\\d{9})|(qq\\d{5,})|(vx[a-z0-9_]{4,})|(wx[a-z0-9_]{4,})|(wechat[a-z0-9_]{4,})");

    private final StringRedisTemplate redisTemplate;
    private final TrieNode maskRoot = new TrieNode();
    private final TrieNode rejectRoot = new TrieNode();
    private final Set<String> maskWords = new LinkedHashSet<>();
    private final Set<String> rejectWords = new LinkedHashSet<>();
    private final UserService userService;

    @Value("${app.chat-moderation.words-dir:data/moderation}")
    private String wordsDir;

    @Value("${app.chat-moderation.max-length:120}")
    private int maxLength;

    @Value("${app.chat-moderation.rate-window-seconds:3}")
    private long rateWindowSeconds;

    @Value("${app.chat-moderation.rate-max-messages:3}")
    private long rateMaxMessages;

    @Value("${app.chat-moderation.duplicate-window-seconds:10}")
    private long duplicateWindowSeconds;

    @Value("${app.chat-moderation.violation-window-seconds:60}")
    private long violationWindowSeconds;

    @Value("${app.chat-moderation.violation-mute-threshold:3}")
    private long violationMuteThreshold;

    @Value("${app.chat-moderation.mute-seconds:300}")
    private long muteSeconds;

    @PostConstruct
    public void loadSensitiveWords() {
        reloadWords();
    }

    public synchronized void reloadWords() {
        ensureWritableWordFile(MASK_WORDS_FILE);
        ensureWritableWordFile(REJECT_WORDS_FILE);
        maskRoot.clear();
        rejectRoot.clear();
        maskWords.clear();
        rejectWords.clear();
        loadWords(wordPath(MASK_WORDS_FILE), maskRoot, maskWords);
        loadWords(wordPath(REJECT_WORDS_FILE), rejectRoot, rejectWords);
    }

    public synchronized List<String> listWords(WordType type) {
        Set<String> words = type == WordType.REJECT ? rejectWords : maskWords;
        List<String> result = new ArrayList<>(words);
        Collections.sort(result);
        return result;
    }

    public synchronized void addWord(WordType type, String rawWord) {
        String word = normalizeWordForStorage(rawWord);
        if (word.isBlank()) {
            throw new IllegalArgumentException("敏感词不能为空");
        }
        Set<String> words = type == WordType.REJECT ? rejectWords : maskWords;
        if (words.add(word)) {
            persistWords(type, words);
            reloadWords();
        }
    }

    public synchronized void deleteWord(WordType type, String rawWord) {
        String word = normalizeWordForStorage(rawWord);
        Set<String> words = type == WordType.REJECT ? rejectWords : maskWords;
        if (words.remove(word)) {
            persistWords(type, words);
            reloadWords();
        }
    }

    public ModerationResult moderateRoomChat(String roomId, String username, String content) {
        String text = Optional.ofNullable(content).orElse("").trim();
        if (text.isEmpty()) {
            return ModerationResult.reject("消息不能为空", ModerationAction.REJECT);
        }
        if (text.length() > maxLength) {
            return ModerationResult.reject("消息过长，最多 " + maxLength + " 个字符", ModerationAction.REJECT);
        }

        String muteKey = MUTE_KEY_PREFIX + username;
        String mute = redisTemplate.opsForValue().get(muteKey);
        if (mute != null) {
            return ModerationResult.reject("你已被临时禁言，请稍后再试", ModerationAction.MUTE);
        }

        ModerationResult rateResult = checkRateLimit(roomId, username, text);
        if (!rateResult.isPassed()) {
            return rateResult;
        }

        NormalizedText normalized = normalize(text);
        if (normalized.value.isEmpty()) {
            return ModerationResult.reject("消息不能为空", ModerationAction.REJECT);
        }
        if (CONTACT_PATTERN.matcher(normalized.value).find()) {
            recordViolation(username);
            return ModerationResult.reject("消息包含联系方式或广告信息，请修改后发送", ModerationAction.REJECT);
        }

        List<MatchRange> rejectMatches = findMatches(normalized, rejectRoot);
        if (!rejectMatches.isEmpty()) {
            recordViolation(username);
            return ModerationResult.reject("消息包含违规内容，请修改后发送", ModerationAction.REJECT);
        }

        List<MatchRange> maskMatches = findMatches(normalized, maskRoot);
        if (!maskMatches.isEmpty()) {
            String masked = mask(text, maskMatches);
            return ModerationResult.mask(masked);
        }

        return ModerationResult.pass(text);
    }

    public ModerationResult moderatePrivateMessage(String conversationId, String username, String content) {
        String text = Optional.ofNullable(content).orElse("").trim();
        if (text.isEmpty()) {
            return ModerationResult.reject("消息不能为空", ModerationAction.REJECT);
        }
        if (text.length() > 1000) {
            return ModerationResult.reject("消息过长，最多 1000 个字符", ModerationAction.REJECT);
        }

        String mute = redisTemplate.opsForValue().get(MUTE_KEY_PREFIX + username);
        if (mute != null) {
            return ModerationResult.reject("你已被临时禁言，请稍后再试", ModerationAction.MUTE);
        }

        ModerationResult rateResult = checkRateLimit("dm:" + conversationId, username, text);
        if (!rateResult.isPassed()) {
            return rateResult;
        }

        return moderateTextSafety(text, username, "消息", true);
    }

    public ModerationResult moderateNickname(String username, String nickname) {
        String text = Optional.ofNullable(nickname).orElse("").trim();
        if (text.isEmpty()) {
            return ModerationResult.reject("昵称不能为空", ModerationAction.REJECT);
        }
        if (text.length() > 20) {
            return ModerationResult.reject("昵称过长，最多 20 个字符", ModerationAction.REJECT);
        }

        ModerationResult result = moderateTextSafety(text, username, "昵称", false);
        if (result.isMasked()) {
            return ModerationResult.reject("昵称包含敏感内容，请修改后提交", ModerationAction.REJECT);
        }
        return result;
    }

    public ModerationResult moderateRoomDescription(String username, String roomDescription) {
        String text = Optional.ofNullable(roomDescription).orElse("").trim();
        if (text.isEmpty()) {
            return ModerationResult.reject("房间简介不能为空", ModerationAction.REJECT);
        }
        if (text.length() > 200) {
            return ModerationResult.reject("房间简介过长，最多 200 个字符", ModerationAction.REJECT);
        }
        return moderateTextSafety(text, username, "房间简介", true);
    }

    private ModerationResult moderateTextSafety(String text, String username, String fieldName, boolean allowMask) {
        NormalizedText normalized = normalize(text);
        if (normalized.value.isEmpty()) {
            return ModerationResult.reject(fieldName + "不能为空", ModerationAction.REJECT);
        }
        if (CONTACT_PATTERN.matcher(normalized.value).find()) {
            recordViolation(username);
            return ModerationResult.reject(fieldName + "包含联系方式或广告信息，请修改后提交", ModerationAction.REJECT);
        }

        List<MatchRange> rejectMatches = findMatches(normalized, rejectRoot);
        if (!rejectMatches.isEmpty()) {
            recordViolation(username);
            return ModerationResult.reject(fieldName + "包含违规内容，请修改后提交", ModerationAction.REJECT);
        }

        List<MatchRange> maskMatches = findMatches(normalized, maskRoot);
        if (!maskMatches.isEmpty()) {
            if (!allowMask) {
                return ModerationResult.reject(fieldName + "包含敏感内容，请修改后提交", ModerationAction.REJECT);
            }
            return ModerationResult.mask(mask(text, maskMatches));
        }

        return ModerationResult.pass(text);
    }

    private ModerationResult checkRateLimit(String roomId, String username, String text) {
        String normalized = normalize(text).value;
        String duplicateKey = DUP_KEY_PREFIX + roomId + ":" + username + ":" + Integer.toHexString(normalized.hashCode());
        Boolean firstSeen = redisTemplate.opsForValue()
                .setIfAbsent(duplicateKey, "1", Duration.ofSeconds(duplicateWindowSeconds));
        if (Boolean.FALSE.equals(firstSeen)) {
            return ModerationResult.reject("请不要重复发送相同内容", ModerationAction.DUPLICATE);
        }

        String rateKey = RATE_KEY_PREFIX + roomId + ":" + username + ":" + windowBucket(rateWindowSeconds);
        Long count = incrementWindowCounter(rateKey, rateWindowSeconds);
        if (count != null && count > rateMaxMessages) {
            return ModerationResult.reject("消息发送过于频繁，请稍后再试", ModerationAction.RATE_LIMIT);
        }
        return ModerationResult.pass(text);
    }

    private void recordViolation(String username) {
        String key = VIOLATION_KEY_PREFIX + username + ":" + windowBucket(violationWindowSeconds);
        Long count = incrementWindowCounter(key, violationWindowSeconds);
        if (count != null && count >= violationMuteThreshold) {
            redisTemplate.opsForValue().set(MUTE_KEY_PREFIX + username, "1", Duration.ofSeconds(muteSeconds));
            redisTemplate.opsForSet().add(MUTE_USERS_KEY, username);
        }
    }

    public List<MutedUserDto> listMutedUsers() {
        Set<String> usernames = redisTemplate.opsForSet().members(MUTE_USERS_KEY);
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }

        List<MutedUserDto> result = new ArrayList<>();
        for (String username : usernames) {
            String key = MUTE_KEY_PREFIX + username;
            Long ttl = redisTemplate.getExpire(key);
            if (ttl == null || ttl <= 0) {
                redisTemplate.opsForSet().remove(MUTE_USERS_KEY, username);
                continue;
            }

            String nickname = username;
            try {
                User user = userService.findByUsername(username);
                nickname = user.getNickname();
            } catch (Exception ignore) {
                // 用户被删除等异常不影响禁言列表展示
            }
            result.add(new MutedUserDto(username, nickname, ttl));
        }
        result.sort(Comparator.comparing(MutedUserDto::getUsername));
        return result;
    }

    public void unmute(String username) {
        String normalized = Optional.ofNullable(username).orElse("").trim();
        if (normalized.isEmpty()) {
            return;
        }
        redisTemplate.delete(MUTE_KEY_PREFIX + normalized);
        redisTemplate.opsForSet().remove(MUTE_USERS_KEY, normalized);
    }

    private Long incrementWindowCounter(String key, long ttlSeconds) {
        Boolean initialized = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds));
        if (Boolean.TRUE.equals(initialized)) {
            return 1L;
        }
        return redisTemplate.opsForValue().increment(key);
    }

    private long windowBucket(long windowSeconds) {
        long seconds = Math.max(1L, windowSeconds);
        return System.currentTimeMillis() / 1000 / seconds;
    }

    private void loadWords(Path path, TrieNode root, Set<String> storage) {
        int loaded = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String raw = line.trim();
                if (raw.isBlank() || raw.startsWith("#")) {
                    continue;
                }
                String word = normalize(raw).value;
                if (word.isBlank()) {
                    continue;
                }
                storage.add(raw);
                addWord(root, word);
                loaded++;
            }
        } catch (IOException e) {
            logger.warn("内容安全词库加载失败 path={}, err={}", path, e.getMessage());
        }
        logger.info("内容安全词库加载完成: {}, count={}", path, loaded);
    }

    private void ensureWritableWordFile(String filename) {
        try {
            Path dir = Paths.get(wordsDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(filename);
            if (Files.exists(file)) {
                return;
            }
            ClassPathResource defaults = new ClassPathResource("moderation/" + filename);
            if (defaults.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        defaults.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } else {
                Files.createFile(file);
            }
        } catch (IOException e) {
            throw new IllegalStateException("初始化敏感词文件失败: " + filename, e);
        }
    }

    private void persistWords(WordType type, Set<String> words) {
        Path path = wordPath(type == WordType.REJECT ? REJECT_WORDS_FILE : MASK_WORDS_FILE);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String word : words) {
                writer.write(word);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("保存敏感词文件失败", e);
        }
    }

    private Path wordPath(String filename) {
        return Paths.get(wordsDir).resolve(filename);
    }

    private String normalizeWordForStorage(String rawWord) {
        return Optional.ofNullable(rawWord).orElse("").trim();
    }

    private void addWord(TrieNode root, String word) {
        TrieNode node = root;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            node = node.children.computeIfAbsent(ch, ignored -> new TrieNode());
        }
        node.end = true;
    }

    private List<MatchRange> findMatches(NormalizedText text, TrieNode root) {
        List<MatchRange> ranges = new ArrayList<>();
        String value = text.value;
        for (int i = 0; i < value.length(); i++) {
            TrieNode node = root;
            for (int j = i; j < value.length(); j++) {
                node = node.children.get(value.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.end) {
                    ranges.add(new MatchRange(text.originalIndexes.get(i), text.originalIndexes.get(j)));
                }
            }
        }
        return ranges;
    }

    private NormalizedText normalize(String raw) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> indexes = new ArrayList<>();
        String text = Optional.ofNullable(raw).orElse("").toLowerCase();
        for (int i = 0; i < text.length(); i++) {
            char ch = toHalfWidth(text.charAt(i));
            if (Character.isWhitespace(ch) || isIgnoredSymbol(ch)) {
                continue;
            }
            normalized.append(ch);
            indexes.add(i);
        }
        return new NormalizedText(normalized.toString(), indexes);
    }

    private char toHalfWidth(char ch) {
        if (ch == 12288) {
            return ' ';
        }
        if (ch >= 65281 && ch <= 65374) {
            return (char) (ch - 65248);
        }
        return ch;
    }

    private boolean isIgnoredSymbol(char ch) {
        return ".,，。!！?？;；:：、/\\|_-—~`·'\"“”‘’()（）[]【】{}<>《》@#$%^&*+= ".indexOf(ch) >= 0;
    }

    private String mask(String text, List<MatchRange> ranges) {
        if (ranges.isEmpty()) {
            return text;
        }
        ranges.sort(Comparator.comparingInt(range -> range.start));
        StringBuilder masked = new StringBuilder(text);
        for (MatchRange range : ranges) {
            for (int i = range.start; i <= range.end && i < masked.length(); i++) {
                if (!Character.isWhitespace(masked.charAt(i))) {
                    masked.setCharAt(i, '*');
                }
            }
        }
        return masked.toString();
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean end;

        private void clear() {
            children.clear();
            end = false;
        }
    }

    private static class NormalizedText {
        private final String value;
        private final List<Integer> originalIndexes;

        private NormalizedText(String value, List<Integer> originalIndexes) {
            this.value = value;
            this.originalIndexes = originalIndexes;
        }
    }

    private static class MatchRange {
        private final int start;
        private final int end;

        private MatchRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public static class ModerationResult {
        private final boolean passed;
        private final String sanitizedText;
        private final String reason;
        private final boolean masked;
        private final ModerationAction action;

        private ModerationResult(boolean passed, String sanitizedText, String reason,
                                 boolean masked, ModerationAction action) {
            this.passed = passed;
            this.sanitizedText = sanitizedText;
            this.reason = reason;
            this.masked = masked;
            this.action = action;
        }

        public static ModerationResult pass(String text) {
            return new ModerationResult(true, text, null, false, ModerationAction.PASS);
        }

        public static ModerationResult mask(String text) {
            return new ModerationResult(true, text, null, true, ModerationAction.MASK);
        }

        public static ModerationResult reject(String reason, ModerationAction action) {
            return new ModerationResult(false, null, reason, false, action);
        }

        public boolean isPassed() {
            return passed;
        }

        public String getSanitizedText() {
            return sanitizedText;
        }

        public String getReason() {
            return reason;
        }

        public boolean isMasked() {
            return masked;
        }

        public ModerationAction getAction() {
            return action;
        }
    }

    public enum WordType {
        MASK,
        REJECT;

        public static WordType from(String value) {
            if ("REJECT".equalsIgnoreCase(value)) {
                return REJECT;
            }
            return MASK;
        }
    }
}
