package it.tobaben.dart.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MqttAdapterTopicTest {

    @Test
    void exactMatch() {
        assertTrue(MqttAdapter.topicMatches("lobby/join", "lobby/join"));
        assertFalse(MqttAdapter.topicMatches("lobby/join", "lobby/leave"));
        assertFalse(MqttAdapter.topicMatches("lobby/join", "lobby/join/extra"));
    }

    @Test
    void hashWildcardMatchesRemainder() {
        assertTrue(MqttAdapter.topicMatches("dartboard/#", "dartboard/1"));
        assertTrue(MqttAdapter.topicMatches("dartboard/#", "dartboard/1/status"));
        assertFalse(MqttAdapter.topicMatches("dartboard/#", "status/gameUpdate"));
        assertTrue(MqttAdapter.topicMatches("#", "anything/at/all"));
    }

    @Test
    void plusWildcardMatchesOneLevel() {
        assertTrue(MqttAdapter.topicMatches("lobby/response/+", "lobby/response/abc"));
        assertFalse(MqttAdapter.topicMatches("lobby/response/+", "lobby/response/abc/def"));
        assertFalse(MqttAdapter.topicMatches("lobby/response/+", "lobby/response"));
    }
}
