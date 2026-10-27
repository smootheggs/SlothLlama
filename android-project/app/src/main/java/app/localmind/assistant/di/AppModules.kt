package app.localmind.assistant.di

import android.content.Context
import androidx.room.Room
import app.localmind.assistant.data.inference.LiteRtLmInferenceEngine
import app.localmind.assistant.data.local.LocalMindDao
import app.localmind.assistant.data.local.LocalMindDatabase
import app.localmind.assistant.data.local.MIGRATION_1_2
import app.localmind.assistant.data.repository.ChatRepositoryImpl
import app.localmind.assistant.domain.inference.LocalInferenceEngine
import app.localmind.assistant.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLocalMindDatabase(
        @ApplicationContext context: Context
    ): LocalMindDatabase = Room.databaseBuilder(
        context,
        LocalMindDatabase::class.java,
        "localmind.db"
    )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideLocalMindDao(database: LocalMindDatabase): LocalMindDao = database.dao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(
        impl: LiteRtLmInferenceEngine
    ): LocalInferenceEngine

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository
}
