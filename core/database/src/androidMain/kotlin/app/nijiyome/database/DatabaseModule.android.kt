package app.nijiyome.database

import android.content.Context
import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

fun NijiyomeDatabase.Companion.build(context: Context): NijiyomeDatabase =
    configure(Room.databaseBuilder(context.applicationContext, NijiyomeDatabase::class.java, NAME))

val databaseModule: Module = databaseModule { NijiyomeDatabase.build(androidContext()) }
