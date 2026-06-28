package com.agon.app.di

import com.agon.app.domain.engine.ai.DominoAIEngine
import com.agon.app.domain.repository.GameRepository
import com.agon.app.domain.repository.NetworkRepository
import com.agon.app.domain.repository.SettingsRepository
import com.agon.app.domain.repository.StatsRepository
import com.agon.app.domain.usecase.ai.AIPlayUseCase
import com.agon.app.domain.usecase.ai.AISelectDifficultyUseCase
import com.agon.app.domain.usecase.game.*
import com.agon.app.domain.usecase.network.*
import com.agon.app.domain.usecase.settings.*
import com.agon.app.domain.usecase.stats.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    // ───────────── GAME ─────────────
    @Provides
    @ViewModelScoped
    fun provideNewGameUseCase(repo: GameRepository) =
        NewGameUseCase(repo)

    @Provides
    @ViewModelScoped
    fun providePlayTileUseCase(repo: GameRepository) =
        PlayTileUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideDrawOrPassUseCase(repo: GameRepository) =
        DrawOrPassUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetLegalMovesUseCase(repo: GameRepository) =
        GetLegalMovesUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideCheckGameOverUseCase(repo: GameRepository) =
        CheckGameOverUseCase(repo)

    // ───────────── AI (FIXED) ─────────────
    @Provides
    @ViewModelScoped
    fun provideAIEngine(repo: GameRepository): DominoAIEngine =
        DominoAIEngine(repo)

    @Provides
    @ViewModelScoped
    fun provideAIPlayUseCase(
        repo: GameRepository,
        engine: DominoAIEngine
    ) = AIPlayUseCase(repo, engine)

    @Provides
    @ViewModelScoped
    fun provideAISelectDifficultyUseCase() =
        AISelectDifficultyUseCase()

    // ───────────── NETWORK ─────────────
    @Provides
    @ViewModelScoped
    fun provideCreateRoomUseCase(repo: NetworkRepository) =
        CreateRoomUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideDiscoverRoomsUseCase(repo: NetworkRepository) =
        DiscoverRoomsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideJoinRoomUseCase(repo: NetworkRepository) =
        JoinRoomUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideLeaveRoomUseCase(repo: NetworkRepository) =
        LeaveRoomUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideSyncGameStateUseCase(repo: NetworkRepository) =
        SyncGameStateUseCase(repo)

    // ───────────── SETTINGS ─────────────
    @Provides
    @ViewModelScoped
    fun provideLoadSettingsUseCase(repo: SettingsRepository) =
        LoadSettingsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideSaveSettingsUseCase(repo: SettingsRepository) =
        SaveSettingsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideUpdateSettingUseCase(repo: SettingsRepository) =
        UpdateSettingUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideResetSettingsUseCase(repo: SettingsRepository) =
        ResetSettingsUseCase(repo)

    // ───────────── STATS ─────────────
    @Provides
    @ViewModelScoped
    fun provideLoadStatsUseCase(repo: StatsRepository) =
        LoadStatsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideRecordGameUseCase(repo: StatsRepository) =
        RecordGameUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideClearStatsUseCase(repo: StatsRepository) =
        ClearStatsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideExportStatsUseCase(repo: StatsRepository) =
        ExportStatsUseCase(repo)

    @Provides
    @ViewModelScoped
    fun provideGetAchievementsUseCase(repo: StatsRepository) =
        GetAchievementsUseCase(repo)
}