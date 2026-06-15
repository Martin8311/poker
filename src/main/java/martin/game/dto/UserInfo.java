package martin.game.dto;

import jakarta.persistence.criteria.CriteriaBuilder;
import lombok.Data;
import martin.game.model.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

@Data
public class UserInfo {
    private String nickName;
    private Integer total_game;
    private Integer score;
    private Integer win_game;
    private String win_rate;
    private String iconUrl;

    public UserInfo(String nickName, Integer score, Integer total_game, Integer win_game, String iconUrl){
        this.nickName = nickName;
        this.total_game = total_game;
        this.score = score;
        this.win_game = win_game;
        this.iconUrl = iconUrl;
        win_rate = calculateWinRate();
    }

    /**
     * 计算胜率并格式化为百分比形式（保留两位小数）
     * 逻辑：总场数为0时显示"0.00%"，否则按（胜场数/总场数*100）计算并格式化
     */
    private String calculateWinRate() {
        // 处理总场数为null或0的情况（避免除以0）
        if (total_game == null || total_game == 0) {
            return "0.00%";
        }

        // 处理胜场数为null的情况（默认0胜）
        int wins = (win_game == null) ? 0 : win_game;

        // 防止胜场数超过总场数（业务容错）
        if (wins > total_game) {
            wins = total_game;
        }

        // 计算胜率（胜场数/总场数*100），保留两位小数
        BigDecimal winCount = new BigDecimal(wins);
        BigDecimal totalCount = new BigDecimal(total_game);
        BigDecimal rate = winCount.divide(totalCount, 4, RoundingMode.HALF_UP) // 先保留4位小数用于计算
                .multiply(new BigDecimal(100)) // 乘以100转为百分比
                .setScale(2, RoundingMode.HALF_UP); // 最终保留两位小数

        // 格式化为百分比字符串（如"62.50%"）
        DecimalFormat df = new DecimalFormat("0.00%");
        return df.format(rate.doubleValue() / 100); // 注意：DecimalFormat会自动乘以100，这里先除以100抵消
    }

}
