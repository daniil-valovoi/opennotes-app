package dev.opennotes

import android.app.Application
import dev.opennotes.data.AppDatabase
import dev.opennotes.data.NotesRepository
import dev.opennotes.importer.SdocxImporter

class OpenNotesApp : Application() {
    lateinit var repository: NotesRepository
        private set
    lateinit var importer: SdocxImporter
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.get(this)
        repository = NotesRepository(database.notesDao(), filesDir)
        importer = SdocxImporter(this, repository)
    }
}
