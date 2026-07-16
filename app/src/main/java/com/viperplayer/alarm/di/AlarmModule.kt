package com.viperplayer.alarm.di

import android.content.Context
import androidx.room.Room
import com.viperplayer.alarm.data.AlarmDao
import com.viperplayer.alarm.data.AlarmDatabase
import com.viperplayer.alarm.data.AlarmRepository
import com.viperplayer.alarm.data.AlarmRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the self-contained alarm feature: its own Room database + DAO, and the repository
 * binding. Kept in the alarm package so nothing in the shared DI modules needs to change.
 */
@Module
@InstallIn(SingletonComponent::class)
object AlarmModule {

    @Provides
    @Singleton
    fun provideAlarmDatabase(@ApplicationContext context: Context): AlarmDatabase =
        Room.databaseBuilder(context, AlarmDatabase::class.java, "viperplayer_alarms")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideAlarmDao(database: AlarmDatabase): AlarmDao = database.alarmDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAlarmRepository(impl: AlarmRepositoryImpl): AlarmRepository
}
