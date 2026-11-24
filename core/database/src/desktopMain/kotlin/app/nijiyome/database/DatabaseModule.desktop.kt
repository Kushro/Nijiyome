package app.nijiyome.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Desktop: the database is a file in the app's data directory, opened with the bundled SQLite driver. */
fun NijiyomeDatabase.Companion.open(file: File): NijiyomeDatabase {
    file.parentFile?.mkdirs()
    return configure(
        Room.databaseBuilder<NijiyomeDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO),
    )
}
