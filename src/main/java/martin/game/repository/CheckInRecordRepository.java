package martin.game.repository;

import martin.game.model.CheckInRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, Long> {

    Optional<CheckInRecord> findByUsernameAndCheckInDate(String username, LocalDate checkInDate);

    /**
     * 取某用户签到日期升序的全部记录（用于月度 / 总览渲染、计算最长连续）。
     * 数据量很小（全用户历史累计），单查即可。
     */
    List<CheckInRecord> findByUsernameOrderByCheckInDateAsc(String username);

    /**
     * 取最近 N 条（连续天数计算用，避免全表扫描）。
     * 由于 checkInDate 有索引，走 idx_user_date 倒序。
     */
    List<CheckInRecord> findTop366ByUsernameOrderByCheckInDateDesc(String username);

    /**
     * 区间内统计：用于本月已签天数。
     */
    @Query("SELECT COUNT(c) FROM CheckInRecord c WHERE c.username = :username AND c.checkInDate BETWEEN :start AND :end")
    long countByUsernameAndDateRange(@Param("username") String username,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end);
}
