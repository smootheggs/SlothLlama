package app.localmind.assistant.data.library

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The library's own database, kept apart from the chats (and their backups):
 *  - documents: one row per book, document or Wikipedia (Kiwix) file;
 *  - chunks: each document's passages, in order, with the heading they fall under;
 *  - chunks_fts: a full-text index of the passages' search words (see [SearchTokens]),
 *    row for row with chunks (docid = chunks.id).
 * Kiwix files are read in place, so they only get a row in documents.
 */
class LibraryDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE documents (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, kind TEXT NOT NULL, " +
                "uri TEXT, detail TEXT NOT NULL DEFAULT '', chars INTEGER NOT NULL DEFAULT 0, " +
                "chunks INTEGER NOT NULL DEFAULT 0, added_at INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL(
            "CREATE TABLE chunks (id INTEGER PRIMARY KEY AUTOINCREMENT, doc_id INTEGER NOT NULL, ord INTEGER NOT NULL, " +
                "heading TEXT NOT NULL, text TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX chunks_doc ON chunks(doc_id, ord)")
        db.execSQL("CREATE VIRTUAL TABLE chunks_fts USING fts4(tokens, tokenize=simple)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        const val NAME = "library.db"
        private const val VERSION = 1
    }
}
