/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.work.WorkManager
import app.altio.service.data.db.AltioDatabase
import app.altio.service.data.db.ClientTokenDao
import app.altio.service.data.db.DatabaseMigrations
import app.altio.service.data.db.DurableStateDatabase
import app.altio.service.data.db.LegacyStateImporter
import app.altio.service.data.db.ModelDao
import app.altio.service.data.download.AppWorkerFactory
import app.altio.service.data.job.JobRepositoryImpl
import app.altio.service.data.model.ModelRepositoryImpl
import app.altio.service.data.runtime.IdleSessionCleaner
import app.altio.service.data.runtime.InferenceScheduler
import app.altio.service.data.runtime.RoutingRuntimeProvider
import app.altio.service.data.runtime.RuntimeEngineHolder
import app.altio.service.data.runtime.RuntimeSessionManager
import app.altio.service.data.runtime.demo.DemoRuntimeProvider
import app.altio.service.data.session.SessionCoordinator
import app.altio.service.data.session.SessionRepositoryImpl
import app.altio.service.data.settings.SettingsRepositoryImpl
import app.altio.service.data.token.TokenRepositoryImpl
import app.altio.service.domain.job.JobRepository
import app.altio.service.domain.model.ModelRepository
import app.altio.service.domain.runtime.RuntimeProvider
import app.altio.service.domain.session.SessionRepository
import app.altio.service.domain.settings.SettingsRepository
import app.altio.service.domain.token.TokenRepository
import app.altio.service.logging.PersistentLogging
import app.altio.service.runtime.litert.LiteRtRuntimeProvider
import app.altio.service.server.AiHttpServer
import app.altio.service.server.ServerDependencies
import app.altio.service.state.InMemoryServiceStartFailureStore
import app.altio.service.state.ModelStateStore
import app.altio.service.state.ModelStateStoreImpl
import app.altio.service.state.ServerStateStore
import app.altio.service.state.ServerStateStoreImpl
import app.altio.service.state.ServiceStartFailureStore
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

private val Application.settingsDataStore by preferencesDataStore(name = "settings")

@DependencyGraph(AppScope::class)
interface AppGraph : MetroAppComponentProviders {
  val runtimeDatabase: AltioDatabase
  val durableDatabase: DurableStateDatabase
  val server: AiHttpServer
  val workerFactory: AppWorkerFactory
  val settingsRepository: SettingsRepository
  val modelRepository: ModelRepository
  val sessionRepository: SessionRepository
  val sessionCoordinator: SessionCoordinator
  val jobRepository: JobRepository
  val tokenRepository: TokenRepository
  val runtimeProvider: RuntimeProvider
  val engineHolder: RuntimeEngineHolder
  val sessionManager: RuntimeSessionManager
  val inferenceScheduler: InferenceScheduler
  val idleSessionCleaner: IdleSessionCleaner
  val legacyStateImporter: LegacyStateImporter
  val logging: PersistentLogging
  val modelStateStore: ModelStateStore
  val serverStateStore: ServerStateStore
  val serviceStartFailureStore: ServiceStartFailureStore

  @Provides
  @SingleIn(AppScope::class)
  fun provideLogging(application: Application, scope: CoroutineScope): PersistentLogging =
      PersistentLogging.create(application, scope, "service_logs.db")

  @Provides
  @SingleIn(AppScope::class)
  fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Provides
  @SingleIn(AppScope::class)
  fun provideRuntimeDatabase(application: Application): AltioDatabase =
      Room.databaseBuilder(application, AltioDatabase::class.java, "altio_runtime.db")
          .addMigrations(*DatabaseMigrations.runtime)
          .build()

  @Provides
  @SingleIn(AppScope::class)
  fun provideDurableDatabase(application: Application): DurableStateDatabase =
      Room.databaseBuilder(application, DurableStateDatabase::class.java, "altio_durable.db")
          .addMigrations(*DatabaseMigrations.durable)
          .build()

  @Provides fun provideModelDao(db: DurableStateDatabase): ModelDao = db.modelDao()

  @Provides
  fun provideClientTokenDao(db: DurableStateDatabase): ClientTokenDao = db.clientTokenDao()

  @Provides
  @SingleIn(AppScope::class)
  fun provideOkHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .build()

  @Provides
  @SingleIn(AppScope::class)
  fun provideWorkManager(application: Application): WorkManager =
      WorkManager.getInstance(application)

  @Provides
  @SingleIn(AppScope::class)
  fun provideSettingsRepository(application: Application): SettingsRepository =
      SettingsRepositoryImpl(application.settingsDataStore)

  @Provides
  @SingleIn(AppScope::class)
  fun provideModelRepository(
      dao: ModelDao,
      workManager: WorkManager,
      application: Application,
  ): ModelRepository = ModelRepositoryImpl(dao, workManager, application)

  @Provides
  @SingleIn(AppScope::class)
  fun provideSessionRepository(db: AltioDatabase): SessionRepository =
      SessionRepositoryImpl(db.sessionDao())

  @Provides
  @SingleIn(AppScope::class)
  fun provideSessionCoordinator(
      sessionRepository: SessionRepository,
      sessionManager: RuntimeSessionManager,
  ): SessionCoordinator = SessionCoordinator(sessionRepository, sessionManager)

  @Provides
  @SingleIn(AppScope::class)
  fun provideJobRepository(db: AltioDatabase): JobRepository = JobRepositoryImpl(db.jobDao())

  @Provides
  @SingleIn(AppScope::class)
  fun provideTokenRepository(dao: ClientTokenDao): TokenRepository = TokenRepositoryImpl(dao)

  @Provides
  @SingleIn(AppScope::class)
  fun provideWorkerFactory(
      application: Application,
      modelDao: ModelDao,
      httpClient: OkHttpClient,
  ): AppWorkerFactory {
    val openAppIntent =
        PendingIntent.getActivity(
            application,
            0,
            Intent(application, MainActivity::class.java).apply {
              flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    return AppWorkerFactory(modelDao, httpClient, openAppIntent)
  }

  @Provides
  @SingleIn(AppScope::class)
  fun provideLegacyStateImporter(
      application: Application,
      durableDatabase: DurableStateDatabase,
  ): LegacyStateImporter = LegacyStateImporter(application, durableDatabase)

  @Provides
  @SingleIn(AppScope::class)
  fun provideDemoRuntimeProvider(): DemoRuntimeProvider = DemoRuntimeProvider()

  @Provides
  @SingleIn(AppScope::class)
  fun provideRuntimeProvider(application: Application): LiteRtRuntimeProvider =
      LiteRtRuntimeProvider(cacheDir = application.cacheDir.absolutePath)

  @Provides
  @SingleIn(AppScope::class)
  fun provideEngineHolder(runtimeProvider: RuntimeProvider): RuntimeEngineHolder =
      RuntimeEngineHolder(runtimeProvider)

  @Provides
  @SingleIn(AppScope::class)
  fun provideRoutingRuntimeProvider(
      demoProvider: DemoRuntimeProvider,
      liteRtProvider: LiteRtRuntimeProvider,
  ): RuntimeProvider = RoutingRuntimeProvider(listOf(demoProvider, liteRtProvider))

  @Provides
  @SingleIn(AppScope::class)
  fun provideSessionManager(
      engineHolder: RuntimeEngineHolder,
      sessionRepository: SessionRepository,
  ): RuntimeSessionManager = RuntimeSessionManager(engineHolder, sessionRepository)

  @Provides
  @SingleIn(AppScope::class)
  fun provideIdleSessionCleaner(
      sessionRepository: SessionRepository,
      sessionCoordinator: SessionCoordinator,
      settingsRepository: SettingsRepository,
      scope: CoroutineScope,
  ): IdleSessionCleaner =
      IdleSessionCleaner(sessionRepository, sessionCoordinator, settingsRepository, scope)

  @Provides
  @SingleIn(AppScope::class)
  fun provideInferenceScheduler(
      scope: CoroutineScope,
      sessionManager: RuntimeSessionManager,
      jobRepository: JobRepository,
  ): InferenceScheduler = InferenceScheduler(scope, sessionManager, jobRepository)

  @Provides
  @SingleIn(AppScope::class)
  fun provideServerDependencies(
      modelRepository: ModelRepository,
      sessionRepository: SessionRepository,
      jobRepository: JobRepository,
      sessionManager: RuntimeSessionManager,
      inferenceScheduler: InferenceScheduler,
      settingsRepository: SettingsRepository,
      tokenRepository: TokenRepository,
      engineHolder: RuntimeEngineHolder,
  ): ServerDependencies =
      ServerDependencies(
          modelRepository = modelRepository,
          sessionRepository = sessionRepository,
          jobRepository = jobRepository,
          sessionManager = sessionManager,
          inferenceScheduler = inferenceScheduler,
          settingsRepository = settingsRepository,
          tokenRepository = tokenRepository,
          engineHolder = engineHolder,
      )

  @Provides
  @SingleIn(AppScope::class)
  fun provideServer(deps: ServerDependencies): AiHttpServer = AiHttpServer(deps)

  @Provides
  @SingleIn(AppScope::class)
  fun provideServiceStartFailureStore(): ServiceStartFailureStore =
      InMemoryServiceStartFailureStore()

  @Provides
  @SingleIn(AppScope::class)
  fun provideModelStateStore(
      settingsRepository: SettingsRepository,
      modelRepository: ModelRepository,
      engineHolder: RuntimeEngineHolder,
      scope: CoroutineScope,
  ): ModelStateStore =
      ModelStateStoreImpl(
          settingsRepository = settingsRepository,
          modelRepository = modelRepository,
          engineHolder = engineHolder,
          scope = scope,
      )

  @Provides
  @SingleIn(AppScope::class)
  fun provideServerStateStore(
      server: AiHttpServer,
      sessionRepository: SessionRepository,
      jobRepository: JobRepository,
      engineHolder: RuntimeEngineHolder,
      serviceStartFailureStore: ServiceStartFailureStore,
      scope: CoroutineScope,
  ): ServerStateStore =
      ServerStateStoreImpl(
          server = server,
          sessionRepository = sessionRepository,
          jobRepository = jobRepository,
          engineHolder = engineHolder,
          serviceStartFailureStore = serviceStartFailureStore,
          scope = scope,
      )

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides application: Application): AppGraph
  }
}
