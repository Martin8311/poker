package martin.game.service;

import martin.game.model.Room;
import martin.game.model.SeatType;
import martin.game.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GameServiceTest {

    private final GameService gameService = new GameService();

    @Nested
    @DisplayName("calculateResult")
    class CalculateResult {

        @Test
        @DisplayName("好人前三名出完时，好人赢 2 分，小鬼 -2，大鬼 -4")
        void peopleTopThreeWinBig() {
            Room room = roomWithTeams(
                    Map.of("p1", 0, "p2", 0, "p3", 0, "small", 1, "big", 2),
                    Map.of(1, "p1", 2, "p2", 3, "p3"));

            Map<String, Integer> result = gameService.calculateResult(room);

            assertThat(result).containsEntry("p1", 2)
                    .containsEntry("p2", 2)
                    .containsEntry("p3", 2)
                    .containsEntry("small", -2)
                    .containsEntry("big", -4);
        }

        @Test
        @DisplayName("大小鬼前两名出完时，鬼方大胜")
        void ghostsTopTwoWinBig() {
            Room room = roomWithTeams(
                    Map.of("p1", 0, "p2", 0, "p3", 0, "small", 1, "big", 2),
                    Map.of(1, "big", 2, "small"));

            Map<String, Integer> result = gameService.calculateResult(room);

            assertThat(result).containsEntry("big", 4)
                    .containsEntry("small", 2)
                    .containsEntry("p1", -2)
                    .containsEntry("p2", -2)
                    .containsEntry("p3", -2);
        }

        @Test
        @DisplayName("好人、鬼、好人、鬼出完时，本局打平")
        void personGhostPersonGhostDraws() {
            Room room = roomWithTeams(
                    Map.of("p1", 0, "p2", 0, "p3", 0, "small", 1, "big", 2),
                    Map.of(1, "p1", 2, "small", 3, "p2", 4, "big"));

            Map<String, Integer> result = gameService.calculateResult(room);

            assertThat(result.values()).containsOnly(0);
        }
    }

    @Nested
    @DisplayName("checkGameIsOver")
    class CheckGameIsOver {

        @Test
        @DisplayName("只有第一名出空时不结束")
        void onePlayerEmptyDoesNotEnd() {
            Room room = roomWithTeams(
                    Map.of("p1", 0, "p2", 0, "p3", 0, "small", 1, "big", 2),
                    Map.of(1, "p1"));

            assertThat(gameService.checkGameIsOver(room)).isFalse();
        }

        @Test
        @DisplayName("两名鬼前两名出空时结束")
        void twoGhostsEmptyEnds() {
            Room room = roomWithTeams(
                    Map.of("p1", 0, "p2", 0, "p3", 0, "small", 1, "big", 2),
                    Map.of(1, "big", 2, "small"));

            assertThat(gameService.checkGameIsOver(room)).isTrue();
        }

        @Test
        @DisplayName("鬼、好人、鬼出空时结束")
        void ghostPersonGhostEnds() {
            Room room = roomWithTeams(
                    Map.of("p1", 0, "p2", 0, "p3", 0, "small", 1, "big", 2),
                    Map.of(1, "big", 2, "p1", 3, "small"));

            assertThat(gameService.checkGameIsOver(room)).isTrue();
        }
    }

    @Test
    @DisplayName("nextActor 跳过已经出空的座位")
    void nextActorSkipsEmptyHands() {
        Room room = new Room();
        room.setSeatTypePlayerMap(Map.of(
                "p1", SeatType.SEAT_1,
                "p2", SeatType.SEAT_2,
                "p3", SeatType.SEAT_3,
                "p4", SeatType.SEAT_4,
                "p5", SeatType.SEAT_5));
        room.setHandEmptyMap(Map.of(
                "p1", false,
                "p2", true,
                "p3", true,
                "p4", false,
                "p5", false));

        assertThat(gameService.nextActor(room, "p1")).isEqualTo("p4");
    }

    private static Room roomWithTeams(Map<String, Integer> teams, Map<Integer, String> order) {
        Room room = new Room();
        room.setPlayers(players(teams.keySet()));
        room.setPlayersTeamMap(teams);
        room.setPlayersSettlementSequencesMap(order);
        return room;
    }

    private static Set<User> players(Set<String> usernames) {
        Set<User> users = new HashSet<>();
        for (String username : usernames) {
            User user = new User();
            user.setUsername(username);
            users.add(user);
        }
        return users;
    }
}
