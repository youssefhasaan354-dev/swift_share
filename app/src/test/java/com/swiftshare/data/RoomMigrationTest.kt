package com.swiftshare.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.swiftshare.data.db.AppDatabase
import com.swiftshare.data.entities.SessionEntity
import com.swiftshare.data.entities.TransferItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class RoomMigrationTest {

    @Test(timeout = 60000)
    fun testRoomMigration1To2ExecutesAllSchemaStatements() {
        val executedStatements = mutableListOf<String>()

        // Create a recording SupportSQLiteDatabase proxy
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_1_2.migrate(dbProxy)

        // Assert 4 main SQL statements were executed:
        // 1. Create table sessions
        // 2. Create table transfer_items
        // 3. Create index index_transfer_items_sessionId
        // 4. Migrate transfer_records to sessions
        // 5. Migrate transfer_records to transfer_items
        assertEquals(5, executedStatements.size)

        val createSessionsSql = executedStatements[0]
        assertTrue("Must create sessions table", createSessionsSql.contains("CREATE TABLE IF NOT EXISTS `sessions`"))
        assertTrue("Must define sessionId primary key", createSessionsSql.contains("`sessionId` TEXT NOT NULL"))
        assertTrue("Must define peerName", createSessionsSql.contains("`peerName` TEXT NOT NULL"))
        assertTrue("Must define isIncoming", createSessionsSql.contains("`isIncoming` INTEGER NOT NULL"))
        assertTrue("Must define totalFiles", createSessionsSql.contains("`totalFiles` INTEGER NOT NULL"))
        assertTrue("Must define totalBytes", createSessionsSql.contains("`totalBytes` INTEGER NOT NULL"))
        assertTrue("Must define status", createSessionsSql.contains("`status` TEXT NOT NULL"))

        val createItemsSql = executedStatements[1]
        assertTrue("Must create transfer_items table", createItemsSql.contains("CREATE TABLE IF NOT EXISTS `transfer_items`"))
        assertTrue("Must define id auto-increment", createItemsSql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
        assertTrue("Must define sessionId", createItemsSql.contains("`sessionId` TEXT NOT NULL"))
        assertTrue("Must define fileId", createItemsSql.contains("`fileId` TEXT NOT NULL"))
        assertTrue("Must define name", createItemsSql.contains("`name` TEXT NOT NULL"))
        assertTrue("Must define size", createItemsSql.contains("`size` INTEGER NOT NULL"))
        assertTrue("Must define transferredBytes", createItemsSql.contains("`transferredBytes` INTEGER NOT NULL"))

        val createIndexSql = executedStatements[2]
        assertTrue("Must create index on transfer_items sessionId", createIndexSql.contains("CREATE INDEX IF NOT EXISTS `index_transfer_items_sessionId`"))

        val migrateSessionsSql = executedStatements[3]
        assertTrue("Must insert into sessions from transfer_records", migrateSessionsSql.contains("INSERT OR IGNORE INTO sessions"))
        assertTrue("Must group by batchId", migrateSessionsSql.contains("GROUP BY batchId"))

        val migrateItemsSql = executedStatements[4]
        assertTrue("Must insert into transfer_items from transfer_records", migrateItemsSql.contains("INSERT INTO transfer_items"))
    }

    @Test(timeout = 60000)
    fun testSessionEntityAndTransferItemEntityProperties() {
        val session = SessionEntity(
            sessionId = "sess_101",
            peerName = "Pixel_8",
            peerIp = "192.168.49.1",
            isIncoming = true,
            totalFiles = 3,
            totalBytes = 15_000_000L,
            transferredBytes = 10_000_000L,
            status = "IN_PROGRESS",
            startTime = 1000L,
            endTime = null
        )
        assertEquals("sess_101", session.sessionId)
        assertEquals("Pixel_8", session.peerName)
        assertEquals(true, session.isIncoming)
        assertEquals(3, session.totalFiles)
        assertEquals(15_000_000L, session.totalBytes)
        assertEquals(10_000_000L, session.transferredBytes)
        assertEquals("IN_PROGRESS", session.status)

        val item = TransferItemEntity(
            sessionId = "sess_101",
            fileId = "f_1",
            name = "presentation.pdf",
            size = 5_000_000L,
            mimeType = "application/pdf",
            transferredBytes = 5_000_000L,
            status = "COMPLETED",
            uriOrPath = "/storage/emulated/0/Download/presentation.pdf",
            sha256 = "abcdef123456"
        )
        assertEquals("sess_101", item.sessionId)
        assertEquals("f_1", item.fileId)
        assertEquals("presentation.pdf", item.name)
        assertEquals("COMPLETED", item.status)
        assertEquals("abcdef123456", item.sha256)
    }
}
