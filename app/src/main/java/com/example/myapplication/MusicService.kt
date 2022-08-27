package com.example.myapplication

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val NOW_PLAYING_CHANNEL_ID = "com.example.android.uamp.media.NOW_PLAYING"
const val NOW_PLAYING_NOTIFICATION_ID = 0xb339 // Arbitrary number used to identify our notification

open class MusicService : MediaBrowserServiceCompat() {
    protected lateinit var mediaSession: MediaSessionCompat
    protected var deviceMusics: MutableList<
        MediaBrowserCompat.MediaItem
    > = mutableListOf()
    protected var isForegroundService = false

    override fun onCreate() {
        super.onCreate()
        Log.e("onCreate MusicService", "*")

        mediaSession = MediaSessionCompat(this, "MusicService")
            .apply {
                isActive = true
            }

        sessionToken = mediaSession.sessionToken

        deviceMusics = loadDeviceMusics()

        val player = ExoPlayer.Builder(this).build()

        val mediaController = MediaControllerCompat(this, mediaSession.sessionToken)

        val notificationManagerBuilder = PlayerNotificationManager.Builder(
            this,
            NOW_PLAYING_NOTIFICATION_ID,
            NOW_PLAYING_CHANNEL_ID,
        )
        with(notificationManagerBuilder) {
            setMediaDescriptionAdapter(DescriptionAdapter(mediaController))
            setNotificationListener(PlayerNotificationListener())
            setChannelNameResourceId(R.string.notification_channel)
            setChannelDescriptionResourceId(R.string.notification_channel)
        }

        val notificationManager = notificationManagerBuilder.build()
        notificationManager.setMediaSessionToken(
            mediaSession.sessionToken
        )
        notificationManager.setSmallIcon(R.drawable.ic_launcher_foreground)
        notificationManager.setUseRewindAction(false)
        notificationManager.setUseFastForwardAction(false)

        notificationManager.setPlayer(player)

        player.setMediaItems(deviceMusics.map { mi ->
            MediaItem.fromUri(mi.description.mediaUri!!)
        })
        player.prepare()
        player.play()
    }

    private inner class PlayerNotificationListener :
        PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            if (ongoing && !isForegroundService) {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, this@MusicService.javaClass)
                )

                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }
    }

    private inner class DescriptionAdapter(private val controller: MediaControllerCompat) :
        PlayerNotificationManager.MediaDescriptionAdapter {

        override fun createCurrentContentIntent(player: Player): PendingIntent? =
            controller.sessionActivity

        override fun getCurrentContentText(player: Player): String {
            Log.e("-----------", controller.toString())
            return "text"
        }


        override fun getCurrentContentTitle(player: Player) =
            "title"

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            return null
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot("root", Bundle())
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(deviceMusics)
    }

    override fun onDestroy() {
        mediaSession.run {
            isActive = false
            release()
        }
    }

    fun loadDeviceMusics(): MutableList<MediaBrowserCompat.MediaItem> {
        val musics = mutableListOf<MediaBrowserCompat.MediaItem>()

        val cursor = this.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.AudioColumns.DATA,
                MediaStore.Audio.ArtistColumns.ARTIST
            ),
            null,
            null,
            null,
        )
        Log.i("scan", "view create")
        if(cursor != null) {
            Log.i("scan", "run while")
            while (cursor.moveToNext()) {
                val path = cursor.getString(0)
                if(!path.endsWith(".mp3")) {
                    continue;
                }
                val author = cursor.getString(1)
                val name = path
                    .substring(path.lastIndexOf("/") + 1)
                    .removeSuffix(".mp3")

                val musicDesc = MediaDescriptionCompat.Builder()
                    .setMediaId(path)
                    .setMediaUri(Uri.parse(path))
                    .setTitle(name)
                    .setSubtitle(author)
                    .build()

                musics.add(
                    MediaBrowserCompat.MediaItem(
                        musicDesc,
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
                )
                Log.i("scan", cursor.getString(0))
            }

            cursor.close();
        }

        return musics;
    }
}