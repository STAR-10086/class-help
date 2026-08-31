package com.star.shuikebang.di

import android.content.Context
import androidx.room.Room
import com.star.shuikebang.data.db.AppDatabase
import com.star.shuikebang.data.db.QuestionDao
import com.star.shuikebang.data.db.SessionDao
import com.star.shuikebang.data.db.TranscriptLineDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "shuikebang.db"
        ).build()
    }

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTranscriptLineDao(db: AppDatabase): TranscriptLineDao = db.transcriptLineDao()

    @Provides
    fun provideQuestionDao(db: AppDatabase): QuestionDao = db.questionDao()
}
