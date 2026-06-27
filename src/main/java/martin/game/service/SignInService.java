package martin.game.service;

import lombok.RequiredArgsConstructor;
import martin.game.dto.SignInResultDto;
import martin.game.dto.SignInStatusDto;
import martin.game.model.CheckInRecord;
import martin.game.repository.CheckInRecordRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日签到服务。
 *
 * <h3>设计原则</h3>
 * <ol>
 *     <li><b>MySQL 为主存（权威）</b>，{@code check_in_record} 表的 {@code uk_user_date} 唯一键兜底防重复；</li>
 *     <li><b>Redis Bitmap 作读加速</b>，位偏移 = 自 {@link #EPOCH} 起的天数；</li>
 *     <li><b>SETNX 锁</b>在 30s 内防止同一用户同一天重复提交；</li>
 *     <li><b>连续天数</b>采用 GETBIT 反向扫描（从今天起遇 0 停止，O(n) n≤365），最长连续由 MySQL 记录一次扫描得到；</li>
 *     <li><b>Redis 失败降级</b>：bitmap 写失败仅 warn log，不阻断主流程；读失败时回退 MySQL。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SignInService {

    private static final Logger logger = LogManager.getLogger(SignInService.class);

    /** 签到位图：每用户一个 key，bit 偏移 = 自 EPOCH 起的天数 */
    private static final String BITMAP_KEY_PREFIX = "signin:bitmap:";
    /** 防重提交锁：30s 过期 */
    private static final String LOCK_KEY_PREFIX = "signin:lock:";
    private static final long LOCK_TTL_SECONDS = 30L;

    /** 位偏移零点（2024-01-01），向前最多支持 100 年，向后无限 */
    public static final LocalDate EPOCH = LocalDate.of(2024, 1, 1);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CheckInRecordRepository checkInRecordRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private final ZoneId zoneId = ZoneId.of("Asia/Shanghai");

    // ============================================================
    //  对外接口
    // ============================================================

    /**
     * 今日是否已签（轻量检查，给登录后自动弹窗用）。
     */
    public boolean isSignedToday(String username) {
        long offset = toBitOffset(LocalDate.now(zoneId));
        Boolean bit = safeGetBit(bitmapKey(username), offset);
        if (bit != null) {
            return bit;
        }
        // Redis 不可用时回退 MySQL
        return checkInRecordRepository
                .findByUsernameAndCheckInDate(username, LocalDate.now(zoneId))
                .isPresent();
    }

    /**
     * 执行签到。幂等：同一天重复调用返回 alreadySigned=true。
     */
    @Transactional
    public SignInResultDto doSignIn(String username) {
        LocalDate today = LocalDate.now(zoneId);
        String lockKey = LOCK_KEY_PREFIX + username + ":" + today.format(ISO);

        // 1. SETNX 抢锁；抢到才继续，否则直接返回「处理中/已签到」
        Boolean acquired = tryAcquireLock(lockKey);
        if (Boolean.FALSE.equals(acquired)) {
            logger.info("签到锁未抢到（并发提交）: user={}, date={}", username, today);
            return buildResult(username, true, true, "今天已经签过到了", today, 0);
        }

        try {
            // 2. 优先查 Redis Bitmap（O(1)）
            long offset = toBitOffset(today);
            Boolean bit = safeGetBit(bitmapKey(username), offset);
            if (Boolean.TRUE.equals(bit)) {
                return buildResult(username, true, true, "今天已经签过到了", today, countConsecutiveFromBitmap(username, today));
            }

            // 3. 兜底查 MySQL（Redis 不可用 / 数据漂移）
            if (checkInRecordRepository.findByUsernameAndCheckInDate(username, today).isPresent()) {
                // 修复 bitmap 镜像
                safeSetBit(bitmapKey(username), offset);
                return buildResult(username, true, true, "今天已经签过到了", today, countConsecutiveFromBitmap(username, today));
            }

            // 4. 计算本次连续天数（GETBIT 反向扫）
            int consecutive = countConsecutiveFromBitmap(username, today);

            // 5. 写 MySQL（唯一键兜底）
            CheckInRecord record = new CheckInRecord();
            record.setUsername(username);
            record.setCheckInDate(today);
            record.setConsecutiveDays(consecutive);
            try {
                checkInRecordRepository.save(record);
            } catch (DataIntegrityViolationException dup) {
                // 并发同天双写：另一线程已写入，按已签到返回
                logger.warn("签到唯一键冲突（并发同天双写）: user={}, date={}", username, today);
                safeSetBit(bitmapKey(username), offset);
                return buildResult(username, true, true, "今天已经签过到了", today, countConsecutiveFromBitmap(username, today));
            }

            // 6. 写 Redis Bitmap 镜像（失败降级）
            safeSetBit(bitmapKey(username), offset);

            logger.info("签到成功: user={}, date={}, consecutive={}", username, today, consecutive);
            return buildResult(username, true, false, "签到成功！连续 " + consecutive + " 天", today, consecutive);
        } finally {
            // 释放锁（best-effort）
            try {
                stringRedisTemplate.delete(lockKey);
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * 月度视图（含统计信息）。
     */
    public SignInStatusDto getMonthStatus(String username, int year, int month) {
        LocalDate today = LocalDate.now(zoneId);
        YearMonth ym = YearMonth.of(year, month);
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();

        // 1. 当月日历位图（Redis GETBIT 逐日）
        List<SignInStatusDto.DayStatus> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            long offset = toBitOffset(d);
            Boolean bit = safeGetBit(bitmapKey(username), offset);
            boolean signed;
            if (bit != null) {
                signed = bit;
            } else {
                // Redis 不可用：仅在当月范围内用 MySQL 单点兜底（1 次查）
                signed = checkInRecordRepository
                        .findByUsernameAndCheckInDate(username, d)
                        .isPresent();
            }
            days.add(new SignInStatusDto.DayStatus(
                    d.getDayOfMonth(),
                    d.format(ISO),
                    signed,
                    d.equals(today),
                    d.isAfter(today),
                    d
            ));
        }

        // 2. 统计
        long monthDays = days.stream().filter(SignInStatusDto.DayStatus::isSigned).count();
        long totalDays = countTotalDays(username);
        int consecutive = countConsecutiveFromBitmap(username, today);
        int longest = computeLongestStreak(username);
        boolean signedToday = days.stream()
                .filter(d -> d.getRawDate().equals(today))
                .findFirst()
                .map(SignInStatusDto.DayStatus::isSigned)
                .orElse(false);

        return new SignInStatusDto(
                year, month, days, signedToday, today.format(ISO),
                totalDays, consecutive, longest, monthDays
        );
    }

    /**
     * 顶部信息条简版：今日是否签 + 连续天数。
     */
    public SignInStatusDto getSummary(String username) {
        LocalDate today = LocalDate.now(zoneId);
        boolean signedToday = isSignedToday(username);
        int consecutive = countConsecutiveFromBitmap(username, today);
        long total = countTotalDays(username);
        return new SignInStatusDto(
                today.getYear(), today.getMonthValue(),
                new ArrayList<>(),
                signedToday, today.format(ISO),
                total, consecutive, computeLongestStreak(username), 0
        );
    }

    // ============================================================
    //  内部：Redis 封装（带降级）
    // ============================================================

    private String bitmapKey(String username) {
        return BITMAP_KEY_PREFIX + username;
    }

    private Boolean tryAcquireLock(String lockKey) {
        try {
            return stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(LOCK_TTL_SECONDS));
        } catch (Exception e) {
            logger.warn("签到锁获取失败（已降级放行）: {}, err={}", lockKey, e.getMessage());
            return true; // Redis 挂了直接放行，靠 MySQL 唯一键兜底
        }
    }

    private Boolean safeGetBit(String key, long offset) {
        try {
            return stringRedisTemplate.opsForValue().getBit(key, offset);
        } catch (Exception e) {
            logger.warn("GETBIT 失败（已降级）: key={}, offset={}, err={}", key, offset, e.getMessage());
            return null;
        }
    }

    private void safeSetBit(String key, long offset) {
        try {
            stringRedisTemplate.opsForValue().setBit(key, offset, true);
        } catch (Exception e) {
            logger.warn("SETBIT 失败（已降级）: key={}, offset={}, err={}", key, offset, e.getMessage());
        }
    }

    private long countTotalDays(String username) {
        try {
            Long cnt = stringRedisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<Long>) connection ->
                            connection.stringCommands().bitCount(bitmapKey(username).getBytes())
            );
            return cnt == null ? 0L : cnt;
        } catch (Exception e) {
            logger.warn("BITCOUNT 失败（回退 MySQL）: user={}, err={}", username, e.getMessage());
            return checkInRecordRepository.findByUsernameOrderByCheckInDateAsc(username).size();
        }
    }

    /**
     * 从 today 起反向 GETBIT 扫描，遇 0 停止。
     * 上限 366 次以避免无限循环。
     */
    private int countConsecutiveFromBitmap(String username, LocalDate today) {
        // 1) Bitmap 可用
        try {
            int count = 0;
            LocalDate cursor = today;
            for (int i = 0; i < 366; i++) {
                long offset = toBitOffset(cursor);
                Boolean bit = stringRedisTemplate.opsForValue().getBit(bitmapKey(username), offset);
                if (Boolean.TRUE.equals(bit)) {
                    count++;
                    cursor = cursor.minusDays(1);
                } else {
                    break;
                }
            }
            return count;
        } catch (Exception e) {
            logger.warn("GETBIT 连续扫描失败（回退 MySQL）: user={}, err={}", username, e.getMessage());
        }

        // 2) 降级：MySQL 记录（按时间倒序扫）
        List<CheckInRecord> recent = checkInRecordRepository
                .findTop366ByUsernameOrderByCheckInDateDesc(username);
        int count = 0;
        LocalDate expected = today;
        for (CheckInRecord r : recent) {
            if (r.getCheckInDate().equals(expected)) {
                count++;
                expected = expected.minusDays(1);
            } else if (r.getCheckInDate().isBefore(expected)) {
                break;
            }
        }
        return count;
    }

    /**
     * 最长连续天数：扫描 MySQL 升序记录，单次 O(n)。
     */
    private int computeLongestStreak(String username) {
        List<CheckInRecord> records = checkInRecordRepository
                .findByUsernameOrderByCheckInDateAsc(username);
        if (records.isEmpty()) {
            return 0;
        }
        int longest = 1, current = 1;
        for (int i = 1; i < records.size(); i++) {
            LocalDate prev = records.get(i - 1).getCheckInDate();
            LocalDate cur = records.get(i).getCheckInDate();
            if (cur.minusDays(1).equals(prev)) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }

    // ============================================================
    //  工具
    // ============================================================

    private static long toBitOffset(LocalDate date) {
        return date.toEpochDay() - EPOCH.toEpochDay();
    }

    private SignInResultDto buildResult(String username, boolean success, boolean already, String msg,
                                       LocalDate today, int consecutive) {
        long total = countTotalDays(username);
        return new SignInResultDto(
                success, already, msg,
                consecutive,
                total,
                today.format(ISO)
        );
    }
}
