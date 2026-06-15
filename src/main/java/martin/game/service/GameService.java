package martin.game.service;

import martin.game.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    @Autowired
    private RoomService roomService;
    private Map<String, Map<String, List<Card>>> playerCardsMap = new ConcurrentHashMap<>();

    private static final Logger logger = LogManager.getLogger(GameService.class);

    /**
     * 开始游戏并发牌
     */
    public boolean startGame(String roomId) {
        Room room = roomService.getRoomWithLock(roomId);
        try {
            // 检查游戏状态（避免重复开始）
//            if (room.getGameState() != GameState.WAITING_FOR_PLAYERS &&
//                    room.getGameState() != GameState.READY_TO_START) {
//                return false;
//            }

            // 3. 给玩家发牌（按房间内玩家数量平均分配）
            Map<String, List<Card>> playerCards = dealCards(room.getPlayers());
            logger.info(roomId);
            logger.info(playerCards);
            // 4. 存储手牌信息
            room.setPlayersCards(playerCards);
            playerCardsMap.put(roomId, playerCards);

            // 5. 更新房间状态
            room.setGameState(GameState.GAME_IN_PROGRESS);
            return true;
        } finally {
            roomService.releaseRoomLock(roomId);
        }
    }

    /**
     *  生成一副扑克牌
     */
    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        String[] suits = {"Spade", "Heart", "Diamond", "Club"}; // 花色
        String[] name = {"Spade", "Heart", "Diamond", "Club"};
        String[] ranks = {"5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2", "3", "4",}; // 点数
        Integer[] value = {1,   2,   3,   4,   5,   6,    7,   8,   9,   10,  11,  12,  12};

        for (String suit : suits) {
            for (int i = 0; i < ranks.length; i++) {

                // 除了红桃3 以外的3
                if(ranks[i].equals("3") && !suit.equals("Heart"))
                    continue;


                // 红心3 4 只1张
                if(suit.equals("Heart") && ((ranks[i].equals("3") || ranks[i].equals("4")))){
                    deck.add(new Card(suit + ranks[i] + "-1", suit + ranks[i], suit, ranks[i], value[i]));
                    continue;
                }

                deck.add(new Card(suit + ranks[i] + "-1", suit + ranks[i], suit, ranks[i], value[i]));
                deck.add(new Card(suit + ranks[i] + "-2", suit + ranks[i], suit, ranks[i], value[i]));
            }
        }

        // 大小王
        deck.add(new Card("JOKER-A1", "JokerA", "JokerA", "ja", 14));
        deck.add(new Card("JOKER-A2", "JokerA", "JokerA", "ja", 14));
        deck.add(new Card("JOKER-B1", "JokerB", "JokerB", "jb", 13));
        deck.add(new Card("JOKER-B2", "JokerB", "JokerB", "jb", 13));

        logger.info(deck);
        return deck;
    }

    /**
     * 发牌逻辑
     */
    private Map<String, List<Card>> dealCards(Set<User> players) {
        int maxRetries = 10;
        int retryCount = 0;

        while(retryCount < maxRetries){
            List<Card> deck = createDeck();
            Collections.shuffle(deck);

            Map<String, List<Card>> playerCards = new ConcurrentHashMap<>();
            int cardIndex = 0;

            // 初始化每个玩家的手牌列表
            for (User player : players) {
                playerCards.put(player.getUsername(), new ArrayList<>());
            }

            // 轮流发牌，直到牌发完
            while (cardIndex < deck.size()) {
                for (User player : players) {
                    if (cardIndex >= deck.size()) break;
                    playerCards.get(player.getUsername()).add(deck.get(cardIndex++));
                }
            }

            if(!hasHeart3And4(playerCards)){
                return playerCards;
            }

            logger.info(playerCards);
            logger.info("重发");
            retryCount++;
        }

        return null;
    }

    private boolean hasHeart3And4(Map<String, List<Card>> playerCards){
        for (List<Card> cards : playerCards.values()) {
            boolean hasHeart3 = false;
            boolean hasHeart4 = false;

            for (Card card : cards) {
                // 假设Card类有getSuit()获取花色，getValue()获取点数
                // 这里的"红心"、3、4需要根据实际枚举或常量调整
                if ("Heart".equals(card.getSuit())) {
                    if ("3".equals(card.getRank())) {
                        hasHeart3 = true;
                    } else if ("4".equals(card.getRank())) {
                        hasHeart4 = true;
                    }
                }

                // 提前退出内层循环
                if (hasHeart3 && hasHeart4) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取玩家的手牌
     */
    public List<Card> getPlayerCards(String roomId, String username) {
        Map<String, List<Card>> playerCards = playerCardsMap.get(roomId);
        return playerCards != null ? playerCards.get(username) : new ArrayList<>();
    }

    /**
     * 房间发牌情况
     */
    public Map<String, List<Card>> getRoomCards(String roomId){
        return playerCardsMap.get(roomId);
    }

    /**
     *  发牌后初始化回合：找到持有红桃3的玩家作为首个出牌者
     */
    public String initFirstTurn(String roomId){
        // 1 获取所有玩家手牌
        Map<String, List<Card>> playerCards = getRoomCards(roomId);
        Map<String, Integer> teamMap = roomService.getRoom(roomId).getPlayersTeamMap();
        Set<User> set = roomService.getRoom(roomId).getPlayers();

        for(User u : set){
            teamMap.put(u.getUsername(), 0);
        }

        // 2 查找持有红桃3的玩家（红桃3的suit="Heart"，rank="3"）
        String firstPlayer = null;
        int i = 0;

        for (Map.Entry<String, List<Card>> entry : playerCards.entrySet()) {
            String username = entry.getKey();
            List<Card> cards = entry.getValue();
            for (Card card : cards) {
                if ("Heart".equals(card.getSuit()) && "3".equals(card.getRank())) {
                    firstPlayer = username;
                    teamMap.put(username, 2);
                    i++;
                } else if ("Heart".equals(card.getSuit()) && "4".equals(card.getRank())) {
                    teamMap.put(username, 1);
                    i++;
                }
            }
            if (i == 2) break;
        }
        logger.info("tempMap:" + teamMap);
        logger.info("firstPlayer:"+ firstPlayer);
        roomService.getRoom(roomId).setBigGhostPlayerUsername(firstPlayer);
        return firstPlayer;
    }

    public String nextActor(String roomId, String currentPlayer) {
        Room room = roomService.getRoom(roomId);
        Map<String, SeatType> seatMap = room.getSeatTypePlayerMap();
        Map<String, Boolean> handEmptyMap = room.getHandEmptyMap();

        // 获取当前玩家的座位
        SeatType currentSeat = seatMap.get(currentPlayer);
        if (currentSeat == null) {
            return null; // 无效玩家，返回空
        }

        // 定义座位顺序（用于循环查找）
        List<SeatType> seatOrder = Arrays.asList(
                SeatType.SEAT_1,
                SeatType.SEAT_2,
                SeatType.SEAT_3,
                SeatType.SEAT_4,
                SeatType.SEAT_5
        );

        // 找到当前座位在顺序列表中的索引
        int currentIndex = seatOrder.indexOf(currentSeat);
        if (currentIndex == -1) {
            return null; // 座位无效，返回空
        }

        // 从当前座位的下一个开始循环查找（最多循环5次，覆盖所有座位数）
        for (int i = 1; i <= seatOrder.size(); i++) {
            // 计算下一个座位的索引（超出后从0开始，实现循环）
            int nextIndex = (currentIndex + i) % seatOrder.size();
            SeatType nextSeat = seatOrder.get(nextIndex);

            // 找到该座位对应的玩家
            String candidatePlayer = null;
            for (Map.Entry<String, SeatType> entry : seatMap.entrySet()) {
                if (entry.getValue().equals(nextSeat)) {
                    candidatePlayer = entry.getKey();
                    break;
                }
            }

            // 检查该玩家是否存在且未出空
            if (candidatePlayer != null) {
                // 未出空（map中不存在该玩家，或存在但值为false）
                boolean isEmpty = handEmptyMap.getOrDefault(candidatePlayer, false);
                if (!isEmpty) {
                    return candidatePlayer; // 找到第一个未出空的玩家，返回
                }
            }
        }

        // 所有玩家都已出空（理论上不会走到这里，游戏应已结束）
        return null;
    }

    public void restartGame(String roomId){
        Room room = roomService.getRoom(roomId);
        room.restart();
        startGame(roomId);
    }

    /**
     * 根据阵营和 走的顺序计算结果
     * @param roomId
     * @return
     */
    public Map<String, Integer> calculateResult(String roomId){
        Map<String, Integer> scoreMap = new HashMap<>();
        Room room = roomService.getRoom(roomId);
        Map<String, Integer> teamMap = room.getPlayersTeamMap();  // 0 好人 1 small 2 big
        Map<Integer, String> orderMap = room.getPlayersSettlementSequencesMap();
        Set<User> playersSet = room.getPlayers();

        if(teamMap.get(orderMap.get(1)) == 0){//人
            if(teamMap.get(orderMap.get(2)) == 0){ // 人人
                if(teamMap.get(orderMap.get(3)) == 0){ // 人人人
                    for(User u : playersSet){
                        if(teamMap.get(u.getUsername()) == 0){
                            scoreMap.put(u.getUsername(), 2);
                        }else if(teamMap.get(u.getUsername()) == 1){
                            scoreMap.put(u.getUsername(), -2);
                        }else{
                            scoreMap.put(u.getUsername(), -4);
                        }
                    }
                }else{//人人鬼
                    for(User u : playersSet){
                        if(teamMap.get(u.getUsername()) == 0){
                            scoreMap.put(u.getUsername(), 1);
                        }else if(teamMap.get(u.getUsername()) == 1){
                            scoreMap.put(u.getUsername(), -1);
                        }else{
                            scoreMap.put(u.getUsername(), -2);
                        }
                    }
                }
            }else{//人鬼
                if(teamMap.get(orderMap.get(3)) == 0){ // 人鬼人
                    if(teamMap.get(orderMap.get(4)) == 0){ // 人鬼人人
                        for(User u : playersSet){
                            if(teamMap.get(u.getUsername()) == 0){
                                scoreMap.put(u.getUsername(), 1);
                            }else if(teamMap.get(u.getUsername()) == 1){
                                scoreMap.put(u.getUsername(), -1);
                            }else{
                                scoreMap.put(u.getUsername(), -2);
                            }
                        }
                    }else{
                        for(User u : playersSet){
                            if(teamMap.get(u.getUsername()) == 0){
                                scoreMap.put(u.getUsername(), 0);
                            }else if(teamMap.get(u.getUsername()) == 1){
                                scoreMap.put(u.getUsername(), 0);
                            }else{
                                scoreMap.put(u.getUsername(), 0);
                            }
                        }
                    }
                }else{ // 人鬼鬼
                    for(User u : playersSet){
                        if(teamMap.get(u.getUsername()) == 0){
                            scoreMap.put(u.getUsername(), -1);
                        }else if(teamMap.get(u.getUsername()) == 1){
                            scoreMap.put(u.getUsername(), 1);
                        }else{
                            scoreMap.put(u.getUsername(), 2);
                        }
                    }
                }
            }
        }else{//鬼
            if(teamMap.get(orderMap.get(2)) == 0){// 鬼人
                if(teamMap.get(orderMap.get(3)) == 0){// 鬼人人
                    if(teamMap.get(orderMap.get(4)) == 0){// 鬼人人人鬼
                        for(User u : playersSet){
                            if(teamMap.get(u.getUsername()) == 0){
                                scoreMap.put(u.getUsername(), 0);
                            }else if(teamMap.get(u.getUsername()) == 1){
                                scoreMap.put(u.getUsername(), 0);
                            }else{
                                scoreMap.put(u.getUsername(), 0);
                            }
                        }
                    }else{ //鬼人人鬼人
                        for(User u : playersSet){
                            if(teamMap.get(u.getUsername()) == 0){
                                scoreMap.put(u.getUsername(), -1);
                            }else if(teamMap.get(u.getUsername()) == 1){
                                scoreMap.put(u.getUsername(), 1);
                            }else{
                                scoreMap.put(u.getUsername(), 2);
                            }
                        }
                    }
                }else{ //鬼人鬼
                    for(User u : playersSet){
                        if(teamMap.get(u.getUsername()) == 0){
                            scoreMap.put(u.getUsername(), -1);
                        }else if(teamMap.get(u.getUsername()) == 1){
                            scoreMap.put(u.getUsername(), 1);
                        }else{
                            scoreMap.put(u.getUsername(), 2);
                        }
                    }
                }
            }else{ // 鬼鬼
                for(User u : playersSet){
                    if(teamMap.get(u.getUsername()) == 0){
                        scoreMap.put(u.getUsername(), -2);
                    }else if(teamMap.get(u.getUsername()) == 1){
                        scoreMap.put(u.getUsername(), 2);
                    }else{
                        scoreMap.put(u.getUsername(), 4);
                    }
                }
            }
        }

        return scoreMap;
    }

    /**
     * 判断游戏是否结束
     */
    public boolean checkGameIsOver(String roomId){
        Room room = roomService.getRoom(roomId);
        Map<String, Integer> teamMap = room.getPlayersTeamMap();  // 0 好人 1 small 2 big
        logger.info(teamMap);
        Map<Integer, String> orderMap = room.getPlayersSettlementSequencesMap();

        // 不会结束
        if(orderMap.size() == 1){
            logger.info("checkGameIsOver: orderMap.size() == 1 -> false");
            return false;
        }

        // 鬼鬼结束
        if(orderMap.size() == 2){
            if(teamMap.get(orderMap.get(1)) != 0 && teamMap.get(orderMap.get(2)) != 0){
                logger.info("checkGameIsOver: 1st.ghost 2nd.ghost");
                return true;
            }
        }

        // 鬼人鬼 人人人 人人鬼  人鬼鬼结束
        // 人鬼人 鬼人人 不会结束
        if(orderMap.size() == 3){
            if(teamMap.get(orderMap.get(1)) != 0 && teamMap.get(orderMap.get(2)) == 0 && teamMap.get(orderMap.get(3)) != 0){
                logger.info("checkGameIsOver: 1st.ghost 2nd.person 3rd.ghost");
                return true;
            }

            if(teamMap.get(orderMap.get(1)) == 0 && teamMap.get(orderMap.get(2)) == 0 && teamMap.get(orderMap.get(3)) == 0){
                logger.info("checkGameIsOver: 1st.person 2nd.person 3rd.person");
                return true;
            }

            if(teamMap.get(orderMap.get(1)) == 0 && teamMap.get(orderMap.get(2)) == 0 && teamMap.get(orderMap.get(3)) != 0){
                logger.info("checkGameIsOver: 1st.person 2nd.person 3rd.ghost");
                return true;
            }

            if(teamMap.get(orderMap.get(1)) == 0 && teamMap.get(orderMap.get(2)) != 0 && teamMap.get(orderMap.get(3)) != 0){
                logger.info("checkGameIsOver: 1st.person 2nd.ghost 3rd.ghost");
                return true;
            }
        }

        return false;
    }
}