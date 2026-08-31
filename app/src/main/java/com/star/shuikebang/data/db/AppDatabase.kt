package com.star.shuikebang.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.star.shuikebang.data.entity.Question
import com.star.shuikebang.data.entity.Session
import com.star.shuikebang.data.entity.TranscriptLine

@Database(
    entities = [Session::class, TranscriptLine::class, Question::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun transcriptLineDao(): TranscriptLineDao
    abstract fun questionDao(): QuestionDao
}
