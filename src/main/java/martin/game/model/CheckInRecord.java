package martin.game.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "check_in_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_date", columnNames = {"username", "check_in_date"}),
        indexes = @Index(name = "idx_user_date", columnList = "username, check_in_date")
)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckInRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 45)
    private String username;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "consecutive_days", nullable = false)
    private Integer consecutiveDays;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
    }
}
