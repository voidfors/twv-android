package com.twv.viewers

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.twv.viewers.data.DataStoreSettings
import com.twv.viewers.data.FavoriteNotifier
import com.twv.viewers.data.GqlClient
import com.twv.viewers.data.GqlStreamsApi
import com.twv.viewers.data.IrcClientImpl
import com.twv.viewers.data.MetaDb
import com.twv.viewers.data.RoomMetaCache
import com.twv.viewers.data.SettingsStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Ручной DI: достаточно для приложения из двух экранов. */
class AppContainer(context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val db = Room.databaseBuilder(context, MetaDb::class.java, "meta.db").build()

    val gql: TwitchGql = GqlClient(http)
    val irc: IrcClient = IrcClientImpl(http)
    val streams: StreamsApi = GqlStreamsApi(http)
    val metaCache: RoomMetaCache = RoomMetaCache(db.dao())
    val settings: SettingsStore = DataStoreSettings(context.applicationContext)
    val notifier: FavoriteNotifier = FavoriteNotifier(context.applicationContext)
}

class TwvApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
