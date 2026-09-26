package sealmc.swe3tie.sealcore

import org.bukkit.entity.Player
import sealmc.swe3tie.sealcore.gui.GuiContext
import sealmc.swe3tie.sealcore.gui.GuiSession
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * Bukkit's [Player] is an interface with a few hundred methods, so the tests
 * hand out a no-op proxy rather than a stub implementation. The code under
 * test only ever passes the instance around or reads a couple of getters.
 */
fun fakePlayer(name: String = "Tester", id: UUID = UUID.randomUUID()): Player =
    Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
        when (method.name) {
            "getName" -> name
            "getUniqueId" -> id
            "isOnline" -> true
            "toString" -> "FakePlayer($name)"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            else -> null
        }
    } as Player

fun fakeContext(player: Player = fakePlayer()): GuiContext =
    GuiContext(
        player = player,
        playerId = player.uniqueId,
        session = GuiSession(player, player.uniqueId, windowId = 1),
        placeholders = null,
        scheduler = { it.run() },
    )
