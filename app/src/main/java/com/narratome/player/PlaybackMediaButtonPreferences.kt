package com.narratome.player

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import com.narratome.R
import com.google.common.collect.ImmutableList

/** Buttons for system media surfaces (QS, lock screen) and Media3 notification defaults. */
@UnstableApi
object PlaybackMediaButtonPreferences {

    fun buttons(context: Context, showPauseButton: Boolean): ImmutableList<CommandButton> {
        val back =
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_30)
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setDisplayName(context.getString(R.string.notification_seek_back_30s))
                .setSlots(CommandButton.SLOT_BACK)
                .build()
        val center =
            if (showPauseButton) {
                CommandButton.Builder(CommandButton.ICON_PAUSE)
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setDisplayName(context.getString(R.string.notification_pause))
                    .setSlots(CommandButton.SLOT_CENTRAL)
                    .build()
            } else {
                CommandButton.Builder(CommandButton.ICON_PLAY)
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setDisplayName(context.getString(R.string.notification_play))
                    .setSlots(CommandButton.SLOT_CENTRAL)
                    .build()
            }
        val forward =
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setDisplayName(context.getString(R.string.notification_seek_forward_30s))
                .setSlots(CommandButton.SLOT_FORWARD)
                .build()
        return ImmutableList.of(back, center, forward)
    }
}
