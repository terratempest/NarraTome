package com.narratome.player

import androidx.media3.common.Player
import androidx.media3.common.FlagSet
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Proxy
import org.junit.Test

@OptIn(markerClass = [UnstableApi::class])
class ForwardingPlayerListenerTest {

    @Test
    fun forwardingPlayerPreservesCallbacksAndIdentity() {
        var registeredListener: Player.Listener? = null
        val delegatePlayer = playerProxy { methodName, args ->
            if (methodName == "addListener") {
                registeredListener = args?.firstOrNull() as Player.Listener
            }
            null
        }
        val forwardingPlayer = androidx.media3.common.ForwardingPlayer(delegatePlayer)
        val events = Player.Events(FlagSet.Builder().build())
        var receivedPlayer: Player? = null
        var receivedEvents: Player.Events? = null
        var receivedIsPlaying: Boolean? = null
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                receivedPlayer = player
                receivedEvents = events
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                receivedIsPlaying = isPlaying
            }
        }

        forwardingPlayer.addListener(listener)
        val forwardingListener = requireNotNull(registeredListener)
        forwardingListener.onIsPlayingChanged(true)
        forwardingListener.onEvents(delegatePlayer, events)

        assertThat(receivedIsPlaying).isTrue()
        assertThat(receivedPlayer).isSameInstanceAs(forwardingPlayer)
        assertThat(receivedEvents).isSameInstanceAs(events)
    }

    private fun playerProxy(onInvocation: (String, Array<out Any?>?) -> Any? = { _, _ -> null }): Player =
        Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PlayerProxy"
                else -> onInvocation(method.name, args)
            }
        } as Player
}
