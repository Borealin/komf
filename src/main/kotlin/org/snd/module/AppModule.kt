package org.snd.module

import okhttp3.OkHttpClient
import org.snd.config.AppConfig
import java.time.Clock

class AppModule(
    appConfig: AppConfig,
    context: AppContext
) : AutoCloseable {
    private val okHttpClient = OkHttpClient.Builder().build()
    private val jsonModule = JsonModule()

    private val repositoryModule = RepositoryModule(appConfig.database)

    private val discordModule = DiscordModule(
        config = appConfig.discord,
        okHttpClient = okHttpClient,
        jsonModule = jsonModule,
    )

    private val metadataModule = MetadataModule(
        config = appConfig.metadataProviders,
        okHttpClient = okHttpClient,
        jsonModule = jsonModule
    )

    private val mediaServerModule = MediaServerModule(
        komgaConfig = appConfig.komga,
        kavitaConfig = appConfig.kavita,
        okHttpClient = okHttpClient,
        jsonModule = jsonModule,
        repositoryModule = repositoryModule,
        metadataModule = metadataModule,
        discordModule = discordModule,
        clock = Clock.systemUTC(),
        systemDefaultClock = Clock.systemDefaultZone()
    )

    private val serverModule = ServerModule(
        config = appConfig.server,
        mediaServerModule = mediaServerModule,
        jsonModule = jsonModule,
        appContext = context
    )

    override fun close() {
        serverModule.close()
        mediaServerModule.close()
    }
}
