package it.tobaben.dart.infrastructure;

import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MqttVirtualBoardTest {

    @Test
    void forwardsWireCodesToTheThrowPublisher() {
        List<String> published = new ArrayList<>();
        MqttVirtualBoard board = new MqttVirtualBoard((id, code) -> published.add(id + ":" + code));

        board.throwDart(900, Segment.TRIPLE_20);
        board.throwDart(900, Segment.BOARD_HIT);
        board.next(900);

        assertEquals(List.of("900:320", "900:998", "900:999"), published);
    }
}
