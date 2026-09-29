package com.narratome.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.narratome.data.repository.MediaNetworkPolicy
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.narratome.BuildConfig
import com.narratome.MainActivity
import com.narratome.data.local.db.CatalogDao
import com.narratome.data.local.preferences.AppPreferencesRepository
import com.narratome.data.local.preferences.DEFAULT_PLAYBACK_NOTIFICATION_RETENTION_MINUTES
import com.narratome.data.local.preferences.playbackNotificationRetentionTimeoutMs
import com.narratome.data.repository.ItemRepository
import com.narratome.data.repository.LibraryRepository
import com.narratome.data.repository.ProgressRepository
import com.narratome.data.repository.ServerReachabilityRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.narratome.data.remote.StrictHttpsPolicy
import com.narratome.data.remote.HttpsRequiredException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import com.narratome.data.repository.CoverCacheRepository

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudiobookPlaybackService : MediaLibraryService() {

    @Inject lateinit var catalogDao: CatalogDao
    @Inject lateinit var itemRepository: ItemRepository
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var progressRepository: ProgressRepository
    @Inject lateinit var serverReachabilityRepository: ServerReachabilityRepository
    @Inject lateinit var coverCacheRepository: CoverCacheRepository
    @Inject lateinit var preferences: AppPreferencesRepository
    @Inject lateinit var httpClient: okhttp3.OkHttpClient
    @Inject lateinit var networkPolicy: MediaNetworkPolicy
    @Inject lateinit var httpsPolicy: StrictHttpsPolicy

    private val browseExecutor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))
    private val playbackExecutor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))
    private val progressExecutor = Executors.newSingleThreadExecutor()

    private var player: ExoPlayer? = null
    @Volatile private var playbackAction = ""
    @Volatile private var insecureSourceAction: String? = null
    private val bookPlaybackRecipes = ConcurrentHashMap<String, BookPlaybackRecipe>()
    private val searchResults = ConcurrentHashMap<MediaSession.ControllerInfo, Pair<String, List<MediaItem>>>()
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var retainPausedSessionAfterNetworkStop = false
    private var stoppingRemotePlayback = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val timelineWindow = androidx.media3.common.Timeline.Window()
    private val mediaButtonPreferenceControllers = mutableMapOf<MediaSession.ControllerInfo, Boolean>()
    private val previousChapterCommand = SessionCommand(ACTION_PREVIOUS_CHAPTER, Bundle.EMPTY)
    private val nextChapterCommand = SessionCommand(ACTION_NEXT_CHAPTER, Bundle.EMPTY)
    @Volatile private var seekBackMs: Long = DEFAULT_SEEK_SKIP_MS
    @Volatile private var seekForwardMs: Long = DEFAULT_SEEK_SKIP_MS

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            saveLastPlayedPosition()
            mainHandler.postDelayed(this, 10_000L) // Update every 10s
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    PLAYBACK_CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val httpFactory = OkHttpDataSource.Factory(httpClient)
        fun guardedHttp(action: String) = DataSource.Factory {
            val upstream = httpFactory.createDataSource()
            object : DataSource by upstream {
                override fun open(dataSpec: DataSpec): Long {
                    checkMediaNetwork()
                    dataSpec.uri.toString().toHttpUrlOrNull()?.let(httpsPolicy::check)
                    return upstream.open(dataSpec).also {
                        if (upstream.uri?.scheme == "http") insecureSourceAction = action
                    }
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    checkMediaNetwork()
                    return upstream.read(buffer, offset, length)
                }
                private fun checkMediaNetwork() {
                    upstream.uri?.toString()?.toHttpUrlOrNull()?.let(httpsPolicy::check)
                    if (!networkPolicy.allowed(action)) {
                        throw java.io.IOException("Media paused: metered connection acknowledgement required")
                    }
                }
            }
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, guardedHttp(""))

        val defaultMediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSourceFactory = object : MediaSource.Factory {
            private var drmProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider? = null
            private var errorPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy? = null
            override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider,
            ): MediaSource.Factory = apply {
                drmProvider = drmSessionManagerProvider
            }

            override fun setLoadErrorHandlingPolicy(
                loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy,
            ): MediaSource.Factory = apply {
                errorPolicy = loadErrorHandlingPolicy
            }

            override fun getSupportedTypes(): IntArray = defaultMediaSourceFactory.supportedTypes

            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val sourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(this@AudiobookPlaybackService,
                        guardedHttp("play:${mediaItem.mediaId.substringBefore('#')}")),
                ).apply {
                    drmProvider?.let { setDrmSessionManagerProvider(it) }
                    errorPolicy?.let { setLoadErrorHandlingPolicy(it) }
                }
                val recipe = mediaItem.localConfiguration?.uri
                    ?.let { null }
                    ?: bookPlaybackRecipes[mediaItem.mediaId]
                if (recipe == null) return sourceFactory.createMediaSource(mediaItem)

                return ConcatenatingMediaSource2.Builder()
                    .setMediaItem(recipe.mediaItem)
                    .apply {
                        recipe.parts.forEach { part ->
                            add(
                                sourceFactory.createMediaSource(part.mediaItem),
                                part.durationMs,
                            )
                        }
                    }
                    .build()
            }
        }

        player =
            ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setSeekBackIncrementMs(DEFAULT_SEEK_SKIP_MS)
                .setSeekForwardIncrementMs(DEFAULT_SEEK_SKIP_MS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true
                )
                .build()

        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player!!) {
            override fun getPlaybackState(): Int =
                playbackStateForRetainedPause(
                    playbackState = super.getPlaybackState(),
                    hasCurrentMediaItem = currentMediaItem != null,
                    playWhenReady = playWhenReady,
                    retainedPausedSession = retainPausedSessionAfterNetworkStop,
                )

            override fun prepare() {
                retainPausedSessionAfterNetworkStop = false
                updatePlaybackAction()
                if (!httpsBlocked() && (!remotePlayback() || networkPolicy.allowed(playbackAction))) super.prepare()
            }

            override fun stop() {
                retainPausedSessionAfterNetworkStop = false
                super.stop()
            }

            override fun play() = requestPlay()

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (playWhenReady) requestPlay() else pause()
            }

            override fun pause() {
                networkPolicy.revoke(playbackAction)
                super.pause()
            }

            override fun seekBack() {
                seekTo((currentPosition - seekBackMs).coerceAtLeast(0L))
            }

            override fun seekForward() {
                val targetPosition = currentPosition + seekForwardMs
                val durationMs = duration
                seekTo(
                    if (durationMs == C.TIME_UNSET) {
                        targetPosition
                    } else {
                        targetPosition.coerceAtMost(durationMs)
                    }
                )
            }

        }

        player?.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                logPlaybackDiagnostic("playWhenReady=$playWhenReady reason=$reason")
                if (!playWhenReady) {
                    networkPolicy.revoke(playbackAction)
                    if (!stoppingRemotePlayback && remotePlayback() && !networkPolicy.state.value.unrestricted) {
                        stopRemoteLoading()
                    }
                } else if (remotePlayback() && !networkPolicy.allowed(playbackAction)) {
                    stopRemoteLoading()
                    requestPlay()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                logPlaybackDiagnostic(
                    "playerState=$playbackState exposedState=${mediaLibrarySession?.player?.playbackState} " +
                        "playWhenReady=${player?.playWhenReady} item=${player?.currentMediaItem != null}",
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    mainHandler.post(updateProgressRunnable)
                } else {
                    mainHandler.removeCallbacks(updateProgressRunnable)
                    saveLastPlayedPosition()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retainPausedSessionAfterNetworkStop = false
                updatePlaybackAction()
                if (remotePlayback() && !networkPolicy.allowed(playbackAction)) {
                    val wantedPlay = player?.playWhenReady == true
                    stopRemoteLoading()
                    if (wantedPlay) requestPlay()
                }
                mediaItem?.let {
                    saveLastPlayedPosition()
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (timeline.isEmpty) retainPausedSessionAfterNetworkStop = false
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                saveLastPlayedPosition()
            }
        })

        val sessionIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        mediaLibrarySession =
            MediaLibrarySession.Builder(
                this,
                forwardingPlayer,
                libraryCallback
            )
                .setSessionActivity(sessionIntent)
                .build()

        setMediaNotificationProvider(
            object : DefaultMediaNotificationProvider(
                this,
                { PLAYBACK_NOTIFICATION_ID },
                PLAYBACK_CHANNEL_ID,
                DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID,
            ) {
                override fun getMediaButtons(
                    session: MediaSession,
                    playerCommands: Player.Commands,
                    mediaButtonPreferences: ImmutableList<CommandButton>,
                    showPauseButton: Boolean,
                ): ImmutableList<CommandButton> =
                    super.getMediaButtons(
                        session,
                        playerCommands,
                        mediaButtonPreferences.filterNot { button ->
                            val action = button.sessionCommand?.customAction
                            action == ACTION_PREVIOUS_CHAPTER || action == ACTION_NEXT_CHAPTER
                        }.let { ImmutableList.copyOf(it) },
                        showPauseButton,
                    )
            }
        )
        setForegroundServiceTimeoutMs(
            playbackNotificationRetentionTimeoutMs(DEFAULT_PLAYBACK_NOTIFICATION_RETENTION_MINUTES),
        )

        serviceScope.launch {
            networkPolicy.state.collect {
                if (remotePlayback() && !networkPolicy.allowed(playbackAction)) stopRemoteLoading()
            }
        }

        serviceScope.launch {
            preferences.requireHttps.distinctUntilChanged().collect { required ->
                if (required && insecurePlayback()) {
                    saveLastPlayedPosition()
                    stopRemoteLoading()
                    showMeteredNotice(HttpsRequiredException().message.orEmpty())
                }
            }
        }

        serviceScope.launch {
            coverCacheRepository.coverRevision.collect {
                refreshPlayerArtworkMetadata()
            }
        }

        serviceScope.launch {
            preferences.seekBackSeconds
                .combine(preferences.seekForwardSeconds) { backSeconds, forwardSeconds ->
                    backSeconds to forwardSeconds
                }
                .distinctUntilChanged()
                .collect { (backSeconds, forwardSeconds) ->
                    seekBackMs = backSeconds * 1000L
                    seekForwardMs = forwardSeconds * 1000L
                    val session = mediaLibrarySession ?: return@collect
                    val controllers = synchronized(mediaButtonPreferenceControllers) {
                        mediaButtonPreferenceControllers.toMap()
                    }
                    controllers.forEach { (controller, includeChapterControls) ->
                        session.setMediaButtonPreferences(
                            controller,
                            playbackButtonPreferences(backSeconds, forwardSeconds, includeChapterControls),
                        )
                    }
                }
        }

        serviceScope.launch {
            preferences.playbackNotificationRetentionMinutes
                .distinctUntilChanged()
                .collect { minutes ->
                    val timeoutMs = playbackNotificationRetentionTimeoutMs(minutes)
                    setForegroundServiceTimeoutMs(timeoutMs)
                    logPlaybackDiagnostic("notificationRetentionMs=$timeoutMs")
                }
        }
    }

    private fun updatePlaybackAction() {
        val key = "play:${player?.currentMediaItem?.mediaId.orEmpty().substringBefore('#')}"
        if (key != playbackAction) {
            networkPolicy.revoke(playbackAction)
            playbackAction = key
        }
    }

    private fun isAndroidAutoController(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): Boolean = session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)

    private fun seekToNextChapterOrMediaItem() {
        val current = player ?: return
        val item = current.currentMediaItem ?: return
        val chapters = bookPlaybackRecipes[item.mediaId]?.chapters.orEmpty()
        val targetPositionMs = nextChapterSeekPositionMs(chapters, current.currentPosition.coerceAtLeast(0L))
        if (targetPositionMs == null) {
            current.seekToNextMediaItem()
        } else {
            current.seekTo(targetPositionMs)
        }
    }

    private fun seekToPreviousChapterOrMediaItem() {
        val current = player ?: return
        val item = current.currentMediaItem ?: return
        val chapters = bookPlaybackRecipes[item.mediaId]?.chapters.orEmpty()
        val targetPositionMs = previousChapterSeekPositionMs(chapters, current.currentPosition.coerceAtLeast(0L))
        if (targetPositionMs == null) {
            current.seekToPreviousMediaItem()
        } else {
            current.seekTo(targetPositionMs)
        }
    }

    private fun remotePlayback(): Boolean {
        val item = player?.currentMediaItem ?: return false
        val parts = bookPlaybackRecipes[item.mediaId]?.parts?.map { it.mediaItem } ?: listOf(item)
        return parts.any { it.localConfiguration?.uri?.scheme !in setOf("file", "content", "android.resource") }
    }

    private fun stopRemoteLoading() {
        val current = player ?: return
        retainPausedSessionAfterNetworkStop = current.currentMediaItem != null
        if (current.playbackState == Player.STATE_IDLE) return
        val index = current.currentMediaItemIndex
        val position = current.currentPosition
        stoppingRemotePlayback = true
        try {
            current.pause()
            if (current.playbackState != Player.STATE_IDLE) {
                current.stop()
                if (index >= 0) current.seekTo(index, position)
            }
        } finally {
            stoppingRemotePlayback = false
        }
    }

    private fun insecurePlayback(): Boolean {
        val item = player?.currentMediaItem ?: return false
        val parts = bookPlaybackRecipes[item.mediaId]?.parts?.map { it.mediaItem } ?: listOf(item)
        return parts.any { it.localConfiguration?.uri?.scheme == "http" } ||
            (remotePlayback() && insecureSourceAction == playbackAction)
    }

    private fun httpsBlocked() = httpsPolicy.required && insecurePlayback()

    private fun requestPlay() {
        val current = player ?: return
        updatePlaybackAction()
        if (current.currentMediaItem == null) return
        if (httpsBlocked()) {
            stopRemoteLoading()
            showMeteredNotice(HttpsRequiredException().message.orEmpty())
            return
        }
        val key = playbackAction
        val play = {
            if (playbackAction == key && player === current) {
                if (current.playbackState == Player.STATE_IDLE || current.playerError != null) current.prepare()
                retainPausedSessionAfterNetworkStop = false
                current.play()
                getSystemService(NotificationManager::class.java).cancel(METERED_NOTICE_NOTIFICATION_ID)
            }
        }
        if (!remotePlayback() || networkPolicy.allowed(key)) {
            play()
        } else {
            stopRemoteLoading()
            networkPolicy.request(key, current.mediaMetadata.title?.toString() ?: "Playback", play)
            showMeteredNotice()
        }
    }

    private fun showMeteredNotice(message: String = getString(com.narratome.R.string.metered_open_app)) {
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = androidx.core.app.NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle("Playback paused")
            .setContentText(message)
            .setContentIntent(open).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(METERED_NOTICE_NOTIFICATION_ID, notification)
    }

    private val libraryCallback =
        object : MediaLibrarySession.Callback {

            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = mediaButtonKeyEvent(intent) ?: return super.onMediaButtonEvent(
                    session,
                    controllerInfo,
                    intent
                )
                if (keyEvent.action != KeyEvent.ACTION_DOWN) {
                    return false
                }

                val sessionPlayer = session.player
                return when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                        if (sessionPlayer.isPlaying) {
                            sessionPlayer.pause()
                        } else {
                            sessionPlayer.play()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        sessionPlayer.play()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        sessionPlayer.pause()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        sessionPlayer.seekForward()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        sessionPlayer.seekForward()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        sessionPlayer.seekBack()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        sessionPlayer.seekBack()
                        true
                    }

                    else -> super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                if (!isAndroidAutoController(session, controller)) {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED))
                }
                when (customCommand.customAction) {
                    ACTION_PREVIOUS_CHAPTER -> seekToPreviousChapterOrMediaItem()
                    ACTION_NEXT_CHAPTER -> seekToNextChapterOrMediaItem()
                    else -> return super.onCustomCommand(session, controller, customCommand, args)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {

                // PHONE NOTIFICATION
                if (session.isMediaNotificationController(controller)) {
                    trackMediaButtonPreferenceController(controller, includeChapterControls = true)

                    val playerCommands =
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                            .buildUpon()
                            .remove(Player.COMMAND_SEEK_TO_NEXT)
                            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            .add(Player.COMMAND_SEEK_BACK)
                            .add(Player.COMMAND_SEEK_FORWARD)
                            .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(playerCommands)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                                .buildUpon()
                                .add(previousChapterCommand)
                                .add(nextChapterCommand)
                                .build(),
                        )
                        .setMediaButtonPreferences(
                            playbackButtonPreferences(
                                seekBackMs.secondsFromMillis(),
                                seekForwardMs.secondsFromMillis(),
                                includeChapterControls = true,
                            ),
                        )
                        .build()
                } else if (isAndroidAutoController(session, controller)) {
                    trackMediaButtonPreferenceController(controller, includeChapterControls = true)

                    val playerCommands =
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                            .buildUpon()
                            .remove(Player.COMMAND_SEEK_TO_NEXT)
                            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            .add(Player.COMMAND_SEEK_BACK)
                            .add(Player.COMMAND_SEEK_FORWARD)
                            .build()
                    val sessionCommands =
                        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                            .buildUpon()
                            .add(previousChapterCommand)
                            .add(nextChapterCommand)
                            .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(playerCommands)
                        .setAvailableSessionCommands(sessionCommands)
                        .setMediaButtonPreferences(
                            playbackButtonPreferences(
                                seekBackMs.secondsFromMillis(),
                                seekForwardMs.secondsFromMillis(),
                                includeChapterControls = true,
                            ),
                        )
                        .build()
                }

                // Default behavior for everything else
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }

            override fun onDisconnected(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ) {
                searchResults.remove(controller)
                synchronized(mediaButtonPreferenceControllers) {
                    mediaButtonPreferenceControllers.remove(controller)
                }
            }

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                isForPlayback: Boolean
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
                playbackExecutor.submit(
                    Callable {
                        runBlocking {

                            val lastPlayed = progressRepository.getLastPlayed()
                                ?: return@runBlocking MediaSession.MediaItemsWithStartPosition(
                                    ImmutableList.of(), 0, 0L
                                )

                            val entity = catalogDao.getById(lastPlayed.libraryItemId)
                                ?: return@runBlocking MediaSession.MediaItemsWithStartPosition(
                                    ImmutableList.of(), 0, 0L
                                )

                            val position = (lastPlayed.currentTimeSec * 1000).toLong()

                            val baseItem = MediaItem.Builder()
                                .setMediaId("item:${entity.libraryItemId}")
                                .setMediaMetadata(
                                    baseMetadata(entity.libraryItemId, entity.title, entity.author)
                                )
                                .build()

                            val allowRemoteFallback = serverReachabilityRepository.serverReachable.value
                            val resolved = resolveMediaItems(listOf(baseItem), allowRemoteFallback)
                            MediaSession.MediaItemsWithStartPosition(
                                ImmutableList.copyOf(resolved.map { it.mediaItem }),
                                0,
                                position
                            )
                        }
                    }
                )

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {

                val root =
                    MediaItem.Builder()
                        .setMediaId(ROOT_ID)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("NarraTome")
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                                .build()
                        )
                        .build()

                return Futures.immediateFuture(LibraryResult.ofItem(root, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
                browseExecutor.submit(
                    Callable {
                        runBlocking {
                            val browsePage = AndroidAutoBrowsePage.from(page, pageSize)
                            val serverReachable = serverReachabilityRepository.serverReachable.value
                            val downloadedOnly = !serverReachable
                            val items = when (parentId) {
                                ROOT_ID -> {
                                    listOf(
                                        browseCategory(RECENTLY_PLAYED_ID, "Continue"),
                                        browseCategory(RECENTLY_ADDED_ID, "Added"),
                                        browseCategory(DOWNLOADED_ID, "Downloaded"),
                                        browseCategory(FULL_LIBRARY_ID, "Library"),
                                    ).drop(browsePage.offset).take(browsePage.limit)
                                }

                                RECENTLY_PLAYED_ID -> {
                                    progressRepository.listContinueListening(
                                        limit = browsePage.limit,
                                        downloadedOnly = downloadedOnly,
                                        offset = browsePage.offset,
                                    ).map { row ->
                                        MediaItem.Builder()
                                            .setMediaId("item:${row.libraryItemId}")
                                            .setMediaMetadata(
                                                baseMetadata(row.libraryItemId, row.title, row.author)
                                            )
                                            .build()
                                    }
                                }

                                CONTINUE_SERIES_ID -> {
                                    progressRepository.listContinueSeries(
                                        limit = browsePage.limit,
                                        downloadedOnly = downloadedOnly,
                                        offset = browsePage.offset,
                                    ).map { row ->
                                        MediaItem.Builder()
                                            .setMediaId("item:${row.libraryItemId}")
                                            .setMediaMetadata(
                                                baseMetadata(row.libraryItemId, row.title, row.author)
                                            )
                                            .build()
                                    }
                                }

                                RECENTLY_ADDED_ID -> {
                                    val libraryId = if (serverReachable) {
                                        libraryRepository.bookLibraries()
                                            .getOrNull()
                                            ?.firstOrNull()
                                            ?.id
                                    } else {
                                        null
                                    }
                                        ?: catalogDao.firstLibraryIdOrNull()
                                        ?: return@runBlocking LibraryResult.ofItemList(ImmutableList.of(), params)

                                    catalogDao.listRecentByAdded(
                                        libraryId = libraryId,
                                        limit = browsePage.limit,
                                        downloadedOnly = downloadedOnly,
                                        offset = browsePage.offset,
                                    ).map { entity ->
                                        MediaItem.Builder()
                                            .setMediaId("item:${entity.libraryItemId}")
                                            .setMediaMetadata(
                                                baseMetadata(entity.libraryItemId, entity.title, entity.author)
                                            )
                                            .build()
                                    }
                                }

                                DOWNLOADED_ID -> {
                                    val libraryId = preferences.selectedLibraryId.first()
                                        ?: (if (serverReachable) {
                                            libraryRepository.bookLibraries()
                                                .getOrNull()
                                                ?.firstOrNull()
                                                ?.id
                                        } else {
                                            null
                                        })
                                        ?: catalogDao.firstLibraryIdOrNull()
                                        ?: return@runBlocking LibraryResult.ofItemList(ImmutableList.of(), params)

                                    catalogDao.listForLibrary(
                                        libraryId = libraryId,
                                        limit = browsePage.limit,
                                        downloadedOnly = true,
                                        offset = browsePage.offset,
                                    ).map { entity ->
                                        MediaItem.Builder()
                                            .setMediaId("item:${entity.libraryItemId}")
                                            .setMediaMetadata(
                                                baseMetadata(entity.libraryItemId, entity.title, entity.author)
                                            )
                                            .build()
                                    }
                                }

                                FULL_LIBRARY_ID -> {
                                    val libraryId = if (serverReachable) {
                                        libraryRepository.bookLibraries()
                                            .getOrNull()
                                            ?.firstOrNull()
                                            ?.id
                                    } else {
                                        null
                                    }
                                        ?: catalogDao.firstLibraryIdOrNull()
                                        ?: return@runBlocking LibraryResult.ofItemList(ImmutableList.of(), params)

                                    catalogDao.listForLibrary(
                                        libraryId = libraryId,
                                        limit = browsePage.limit,
                                        downloadedOnly = downloadedOnly,
                                        offset = browsePage.offset,
                                    ).map { entity ->
                                        MediaItem.Builder()
                                            .setMediaId("item:${entity.libraryItemId}")
                                            .setMediaMetadata(
                                                baseMetadata(entity.libraryItemId, entity.title, entity.author)
                                            )
                                            .build()
                                    }
                                }

                                else -> emptyList()
                            }

                            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                        }
                    }
                )

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<Void>> = browseExecutor.submit(Callable {
                runBlocking {
                    val results = searchMediaItems(query)
                    searchResults[browser] = query to results
                    mainHandler.post {
                        if (mediaLibrarySession === session) {
                            session.notifySearchResultChanged(browser, query, results.size, params)
                        }
                    }
                    LibraryResult.ofVoid(params)
                }
            })

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = browseExecutor.submit(Callable {
                runBlocking {
                    val results = searchResults[browser]?.takeIf { it.first == query }?.second
                        ?: searchMediaItems(query)
                    val window = AndroidAutoBrowsePage.from(page, pageSize)
                    LibraryResult.ofItemList(results.drop(window.offset).take(window.limit), params)
                }
            })

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
            ): ListenableFuture<List<MediaItem>> = Futures.transform(
                onSetMediaItems(mediaSession, controller, mediaItems, C.INDEX_UNSET, C.TIME_UNSET),
                { it.mediaItems }, MoreExecutors.directExecutor(),
            )

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                if (controller.uid != applicationInfo.uid && mediaItems.any { it.localConfiguration != null }) {
                    return Futures.immediateFailedFuture(UnsupportedOperationException("Choose an item from the library"))
                }
                // Read the player on its application thread before resolving on the executor.
                val currentItem = mediaSession.player.currentMediaItem
                val currentPosition = mediaSession.player.currentPosition
                val currentIndex = mediaSession.player.currentMediaItemIndex
                val currentQueue = (0 until mediaSession.player.mediaItemCount).map(mediaSession.player::getMediaItemAt)
                return playbackExecutor.submit(
                    Callable {
                        runBlocking {

                            networkPolicy.awaitReady()
                            val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
                            if (query != null && query.isBlank() && currentItem != null) {
                                return@runBlocking MediaSession.MediaItemsWithStartPosition(
                                    currentQueue, currentIndex, currentPosition,
                                )
                            }
                            val requested = if (query != null) {
                                val match = if (query.isBlank()) {
                                    currentItem ?: progressRepository.getLastPlayed()?.let {
                                        MediaItem.Builder().setMediaId("item:${it.libraryItemId}").build()
                                    }
                                } else searchMediaItems(query).firstOrNull()
                                if (match == null) {
                                    mediaSession.sendError(controller, androidx.media3.session.SessionError(
                                        androidx.media3.session.SessionError.ERROR_BAD_VALUE, "No matching audiobook available",
                                    ))
                                    throw UnsupportedOperationException("No matching audiobook available")
                                }
                                listOf(match)
                            } else mediaItems
                            requested.forEach {
                                networkPolicy.revoke("play:${it.mediaId.substringBefore('#')}")
                            }
                            val allowRemoteFallback = serverReachabilityRepository.serverReachable.value
                            val resolved = resolveMediaItems(requested, allowRemoteFallback)

                            if (resolved.isEmpty()) {
                                mediaSession.sendError(controller, androidx.media3.session.SessionError(
                                    androidx.media3.session.SessionError.ERROR_BAD_VALUE, "Audiobook is not available for playback",
                                ))
                                throw UnsupportedOperationException("Audiobook is not available for playback")
                            }

                            val requestedItem = requested.singleOrNull()
                            val requestedItemId = playbackLibraryItemIdFromMediaId(requestedItem?.mediaId)
                            val requestedItemHasUri = requestedItem?.localConfiguration?.uri != null
                            val shouldResumeFromItemProgress =
                                (startPositionMs == C.TIME_UNSET || startPositionMs == 0L) &&
                                    (startIndex == C.INDEX_UNSET || startIndex == 0) &&
                                    requestedItemId != null &&
                                    !requestedItemHasUri
                            val resumePositionMs = if (shouldResumeFromItemProgress) {
                                progressRepository.getMediaProgress(requestedItemId)?.currentTimeSec
                                    ?.let { (it * 1000).toLong() }
                            } else {
                                null
                            }
                            val requestedStartPositionMs =
                                if (query != null && query.isBlank() && currentItem != null) {
                                    currentPosition
                                } else if (startPositionMs == C.TIME_UNSET) {
                                    resumePositionMs ?: 0L
                                } else {
                                    startPositionMs
                                }
                            val mediaItemsOnly = resolved.map { it.mediaItem }

                            MediaSession.MediaItemsWithStartPosition(
                                ImmutableList.copyOf(mediaItemsOnly),
                                if (startIndex == C.INDEX_UNSET) 0 else startIndex,
                                requestedStartPositionMs,
                            )
                        }
                    }
                )
            }
        }

    private suspend fun searchMediaItems(query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        val online = serverReachabilityRepository.serverReachable.value
        val libraryId = preferences.selectedLibraryId.first()
            ?: (if (online) libraryRepository.bookLibraries().getOrNull()?.firstOrNull()?.id else null)
            ?: preferences.cachedLibraries().firstOrNull()?.id
            ?: catalogDao.firstLibraryIdOrNull()
            ?: return emptyList()
        // Match the existing local search ceiling; keep one bounded snapshot per browser.
        return searchForAuto(query, online,
            remote = { libraryRepository.searchBooks(libraryId, it).getOrNull() },
            downloaded = { itemRepository.searchLocalSubstring(libraryId, it, limit = 80, downloadedOnly = true) },
        ).map { row ->
            MediaItem.Builder().setMediaId("item:${row.id}")
                .setMediaMetadata(baseMetadata(row.id, row.title, row.author)).build()
        }
    }

    private var lastSavedPosition = 0L
    private var lastSavedId: String? = null

    private fun saveLastPlayedPosition() {
        val exo = player ?: return
        val id = exo.currentMediaItem?.mediaId?.removePrefix("item:")?.substringBefore("#") ?: return
        val position = cumulativePositionMsOrNull(exo) ?: return
        val duration = progressDurationMs(exo)

        // Throttle: only save if item changed or position moved > 5s
        if (id == lastSavedId && kotlin.math.abs(position - lastSavedPosition) < 5_000) {
            return
        }

        lastSavedId = id
        lastSavedPosition = position

        progressExecutor.execute {
            runBlocking {
                progressRepository.updateLocalProgress(
                    libraryItemId = id,
                    currentTimeSec = position / 1000.0,
                    durationSec = duration?.div(1000.0),
                    markDirty = true
                )
            }
        }
    }

    private fun cumulativePositionMsOrNull(exo: ExoPlayer): Long? {
        if (exo.mediaItemCount <= 1) return exo.currentPosition.coerceAtLeast(0L)
        val prefixDurations = itemDurationsMs(exo)
            .take(exo.currentMediaItemIndex)
            .filterKnownDurations()
            ?: return null
        return prefixDurations.sum() + exo.currentPosition.coerceAtLeast(0L)
    }

    private fun progressDurationMs(exo: ExoPlayer): Long? {
        val recipe = exo.currentMediaItem?.mediaId?.let(bookPlaybackRecipes::get)
        recipe?.canonicalDurationMs?.let { return it }
        if (recipe?.hasPlaceholderDurations == true) return null
        return if (exo.mediaItemCount <= 1) {
            exo.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        } else {
            itemDurationsMs(exo).filterKnownDurations()?.sum()
        }
    }

    private fun itemDurationsMs(exo: ExoPlayer): List<Long> {
        val timeline = exo.currentTimeline
        return (0 until exo.mediaItemCount).map { index ->
            if (!timeline.isEmpty && index < timeline.windowCount) {
                timeline.getWindow(index, timelineWindow)
                timelineWindow.durationMs
            } else {
                C.TIME_UNSET
            }
        }
    }

    private suspend fun resolveMediaItems(
        mediaItems: List<MediaItem>,
        allowRemoteFallback: Boolean,
    ): List<ResolvedMediaItem> {
        return mediaItems.flatMap { mi ->

            // Already resolved item → pass through unchanged
            val existingUri = mi.localConfiguration?.uri
            if (existingUri != null) {
                return@flatMap listOf(ResolvedMediaItem(mi))
            }

            val rawId = playbackLibraryItemIdFromMediaId(mi.mediaId)
                ?: mi.mediaId.substringAfter("item:").substringBefore("#")

            val tracks = itemRepository.resolvePlayableUrls(rawId, allowRemoteFallback).getOrNull()
                ?: return@flatMap emptyList()
            val catalogItem = catalogDao.getById(rawId)

            val title = mi.mediaMetadata.title?.toString().nonBlankOrNull()
                ?: catalogItem?.title
                ?: ""
            val author = mi.mediaMetadata.artist?.toString().nonBlankOrNull()
                ?: catalogItem?.author
            val bookMediaItem = MediaItem.Builder()
                .setMediaId("item:$rawId")
                .setMediaMetadata(baseMetadata(rawId, title, author))
                .build()
            val cachedBookDetail = itemRepository.getCachedBookDetailOrNull(rawId)
            val fallbackDurationMs = cachedBookDetail
                ?.durationSec
                ?.let { (it * 1000).toLong() }
                ?.takeIf { it > 0L }
                ?: 0L
            val trackDurationsMs = tracks.map { track ->
                track.durationSec?.let { (it * 1000).toLong() } ?: C.TIME_UNSET
            }
            val placeholderDurations = placeholderDurationsMs(trackDurationsMs, fallbackDurationMs)
            val parts = tracks.mapIndexed { index, track ->
                BookPlaybackPart(
                    mediaItem = MediaItem.Builder().setUri(track.url.toUri()).build(),
                    durationMs = placeholderDurations[index],
                )
            }
            bookPlaybackRecipes[bookMediaItem.mediaId] = BookPlaybackRecipe(
                mediaItem = bookMediaItem,
                parts = parts,
                canonicalDurationMs = fallbackDurationMs.takeIf { it > 0L },
                hasPlaceholderDurations = trackDurationsMs.any { it == C.TIME_UNSET },
                chapters = cachedBookDetail?.chapters.orEmpty(),
            )
            listOf(
                ResolvedMediaItem(
                    mediaItem = bookMediaItem,
                )
            )
        }
    }

    private data class ResolvedMediaItem(
        val mediaItem: MediaItem,
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        logPlaybackSnapshot(
            "notificationUpdate startInForegroundRequired=$startInForegroundRequired " +
                "state=${session.player.playbackState} playWhenReady=${session.player.playWhenReady} " +
                "items=${session.player.mediaItemCount}",
        )
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        logPlaybackSnapshot("taskRemoved")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        logPlaybackSnapshot("serviceDestroy")
        networkPolicy.revoke(playbackAction)
        getSystemService(NotificationManager::class.java).cancel(METERED_NOTICE_NOTIFICATION_ID)
        mainHandler.removeCallbacks(updateProgressRunnable)
        serviceScope.cancel()
        browseExecutor.shutdown()
        playbackExecutor.shutdown()
        progressExecutor.shutdown()
        mediaLibrarySession?.release()
        player?.release()
        player = null
        mediaLibrarySession = null
        super.onDestroy()
    }

    private fun logPlaybackDiagnostic(message: String) {
        if (!BuildConfig.DEBUG) return
        android.util.Log.d(PLAYBACK_DIAGNOSTICS_TAG, message)
    }

    private fun logPlaybackSnapshot(event: String) {
        if (!BuildConfig.DEBUG) return
        val notificationActive = runCatching {
            getSystemService(NotificationManager::class.java)
                .activeNotifications
                .any { it.id == PLAYBACK_NOTIFICATION_ID }
        }.getOrDefault(false)
        val foreground = runCatching { isPlaybackOngoing() }.getOrDefault(false)
        val current = player
        logPlaybackDiagnostic(
            "$event foreground=$foreground notificationActive=$notificationActive " +
                "playerState=${current?.playbackState} playWhenReady=${current?.playWhenReady} " +
                "hasItem=${current?.currentMediaItem != null} " +
                "sessionState=${mediaLibrarySession?.player?.playbackState}",
        )
    }

    private fun refreshPlayerArtworkMetadata() {
        val exo = player ?: return
        for (index in 0 until exo.mediaItemCount) {
            val item = exo.getMediaItemAt(index)
            val rawId = item.mediaId.removePrefix("item:").substringBefore("#")
            if (rawId.isBlank()) continue
            val targetArtworkUri = playbackArtworkUri(this, coverCacheRepository, rawId)
            if (item.mediaMetadata.artworkUri == targetArtworkUri) continue

            val updatedMetadata = item.mediaMetadata
                .buildUpon()
                .setArtworkUri(targetArtworkUri)
                .build()
            val updatedItem = item
                .buildUpon()
                .setMediaMetadata(updatedMetadata)
                .build()
            exo.replaceMediaItem(index, updatedItem)
        }
    }

    private fun baseMetadata(entityId: String, title: String, author: String?) =
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(author ?: "")
            .setArtworkUri(playbackArtworkUri(this, coverCacheRepository, entityId))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
            .build()

    private fun browseCategory(mediaId: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                    .build()
            )
            .build()

    private fun String?.nonBlankOrNull(): String? =
        this?.takeIf { it.isNotBlank() }

    private fun playbackButtonPreferences(
        backSeconds: Int,
        forwardSeconds: Int,
        includeChapterControls: Boolean = false,
    ): ImmutableList<CommandButton> = ImmutableList.builder<CommandButton>().apply {
        if (includeChapterControls) {
            add(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setSessionCommand(previousChapterCommand)
                    .setDisplayName("Previous chapter")
                    .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build(),
            )
        }
        add(
            CommandButton.Builder(skipBackIcon(backSeconds))
                .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setDisplayName("Back ${backSeconds}s")
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
        )
        add(
            CommandButton.Builder(skipForwardIcon(forwardSeconds))
                .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setDisplayName("Forward ${forwardSeconds}s")
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
        )
        if (includeChapterControls) {
            add(
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setSessionCommand(nextChapterCommand)
                    .setDisplayName("Next chapter")
                    .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                    .build(),
            )
        }
    }.build()

    private fun trackMediaButtonPreferenceController(
        controller: MediaSession.ControllerInfo,
        includeChapterControls: Boolean,
    ) {
        synchronized(mediaButtonPreferenceControllers) {
            mediaButtonPreferenceControllers[controller] = includeChapterControls
        }
    }

    @Suppress("DEPRECATION")
    private fun mediaButtonKeyEvent(intent: Intent): KeyEvent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

    private fun skipBackIcon(seconds: Int): Int =
        when (seconds) {
            5 -> CommandButton.ICON_SKIP_BACK_5
            10 -> CommandButton.ICON_SKIP_BACK_10
            15 -> CommandButton.ICON_SKIP_BACK_15
            30 -> CommandButton.ICON_SKIP_BACK_30
            else -> CommandButton.ICON_SKIP_BACK
        }

    private fun skipForwardIcon(seconds: Int): Int =
        when (seconds) {
            5 -> CommandButton.ICON_SKIP_FORWARD_5
            10 -> CommandButton.ICON_SKIP_FORWARD_10
            15 -> CommandButton.ICON_SKIP_FORWARD_15
            30 -> CommandButton.ICON_SKIP_FORWARD_30
            else -> CommandButton.ICON_SKIP_FORWARD
        }

    private fun Long.secondsFromMillis(): Int = (this / 1000L).toInt()

    companion object {
        private const val PLAYBACK_DIAGNOSTICS_TAG = "NarraTomePlayback"
        private const val PLAYBACK_NOTIFICATION_ID = 7102
        private const val METERED_NOTICE_NOTIFICATION_ID = 7103
        private const val ROOT_ID = "audiobook_root"
        private const val RECENTLY_PLAYED_ID = "recently_played"
        private const val CONTINUE_SERIES_ID = "continue_series"
        private const val RECENTLY_ADDED_ID = "recently_added"
        private const val DOWNLOADED_ID = "downloaded"
        private const val FULL_LIBRARY_ID = "full_library"
        private const val PLAYBACK_CHANNEL_ID = "playback"
        private const val DEFAULT_SEEK_SKIP_MS = 30_000L
        private const val ACTION_PREVIOUS_CHAPTER = "com.narratome.action.PREVIOUS_CHAPTER"
        private const val ACTION_NEXT_CHAPTER = "com.narratome.action.NEXT_CHAPTER"
    }
}
