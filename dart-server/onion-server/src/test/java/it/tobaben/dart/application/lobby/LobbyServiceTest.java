package it.tobaben.dart.application.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LobbyServiceTest {

    LobbyService lobby = new LobbyService();

    @Test
    void joinShouldAddPlayer() {
        assertTrue(lobby.join("Alice", 1, "client-a").ok());
        assertEquals(1, lobby.getPlayers().size());
        assertEquals(new LobbyPlayer("Alice", 1, "client-a"), lobby.getPlayers().get(0));
    }

    @Test
    void joinShouldRejectDuplicateNameFromOtherClient() {
        lobby.join("Alice", 1, "client-a");
        LobbyResult result = lobby.join("Alice", 2, "client-b");
        assertFalse(result.ok());
        assertTrue(result.error().contains("bereits vergeben"));
    }

    @Test
    void rejoinWithSameClientShouldUpdateDartboardId() {
        lobby.join("Alice", 1, "client-a");
        assertTrue(lobby.join("Alice", 5, "client-a").ok());
        assertEquals(1, lobby.getPlayers().size());
        assertEquals(5, lobby.getPlayers().get(0).dartboardId());
    }

    @Test
    void sameClientMaySharesOneDartboard() {
        assertTrue(lobby.join("Alice", 1, "client-a").ok());
        assertTrue(lobby.join("Bob", 1, "client-a").ok());
    }

    @Test
    void otherClientMayNotUseSameDartboard() {
        lobby.join("Alice", 1, "client-a");
        LobbyResult result = lobby.join("Bob", 1, "client-b");
        assertFalse(result.ok());
        assertTrue(result.error().contains("anderen Client"));
    }

    @Test
    void joinShouldRejectBlankName() {
        assertFalse(lobby.join("  ", 1, "client-a").ok());
        assertFalse(lobby.join(null, 1, "client-a").ok());
    }

    @Test
    void joinShouldRejectNegativeDartboardId() {
        assertFalse(lobby.join("Alice", -1, "client-a").ok());
    }

    @Test
    void leaveShouldOnlyRemoveOwnPlayer() {
        lobby.join("Alice", 1, "client-a");
        assertFalse(lobby.leave("Alice", "client-b").ok());
        assertTrue(lobby.leave("Alice", "client-a").ok());
        assertTrue(lobby.isEmpty());
    }

    @Test
    void leaveAllShouldRemoveEveryPlayerOfClient() {
        lobby.join("Alice", 1, "client-a");
        lobby.join("Bob", 1, "client-a");
        lobby.join("Carol", 2, "client-b");
        assertTrue(lobby.leaveAll("client-a"));
        assertEquals(1, lobby.getPlayers().size());
        assertEquals("Carol", lobby.getPlayers().get(0).name());
    }

    @Test
    void removeShouldDropPlayerRegardlessOfClient() {
        lobby.join("Alice", 1, "client-a");
        assertTrue(lobby.remove("Alice"));
        assertFalse(lobby.remove("Alice"));
    }

    @Test
    void moveShouldReorderPlayers() {
        lobby.join("Alice", 1, "client-a");
        lobby.join("Bob", 1, "client-a");
        lobby.join("Carol", 2, "client-b");
        assertTrue(lobby.move("Carol", -1).ok());
        assertEquals("Carol", lobby.getPlayers().get(1).name());
        assertTrue(lobby.move("Alice", 1).ok());
        assertEquals("Carol", lobby.getPlayers().get(0).name());
    }

    @Test
    void moveShouldClampAtListEnds() {
        lobby.join("Alice", 1, "client-a");
        lobby.join("Bob", 1, "client-a");
        assertTrue(lobby.move("Alice", -5).ok());
        assertEquals("Alice", lobby.getPlayers().get(0).name());
        assertTrue(lobby.move("Alice", 99).ok());
        assertEquals("Alice", lobby.getPlayers().get(1).name());
    }

    @Test
    void moveShouldFailForUnknownPlayer() {
        assertFalse(lobby.move("Nobody", 1).ok());
    }
}
