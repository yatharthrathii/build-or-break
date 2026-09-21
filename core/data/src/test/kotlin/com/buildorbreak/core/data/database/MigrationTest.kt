package com.buildorbreak.core.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DB_NAME = "migration-test.db"
private const val SCHEMAS = "schemas/com.buildorbreak.core.data.database.BuildOrBreakDatabase"

/**
 * Every migration, run against the schema it claims to start from.
 *
 * The old database is built from the JSON Room exported for that version, so
 * it is the shape that actually shipped and not a reconstruction of it. Room
 * then opens the file with the real migrations and checks every table against
 * what it expects, which is the check that fails on a phone as a crash on
 * launch. Two hand written migrations have failed exactly that way, both
 * times over a column type that looked right.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var opened: BuildOrBreakDatabase? = null

    @After
    fun tearDown() {
        opened?.close()
        context.deleteDatabase(DB_NAME)
    }

    /** Builds a database exactly as [version] of the app would have left it. */
    private fun createAt(version: Int, fill: (SQLiteDatabase) -> Unit = {}) {
        val schema = JSONObject(File("$SCHEMAS/$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(DB_NAME).also { it.parentFile?.mkdirs() }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")

            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))

                val indices = entity.optJSONArray("indices") ?: continue
                for (each in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(each).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }

            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) db.execSQL(setup.getString(index))

            fill(db)
            db.version = version
        }
    }

    private fun open(): BuildOrBreakDatabase = Room
        .databaseBuilder(context, BuildOrBreakDatabase::class.java, DB_NAME)
        .addMigrations(Migrations.FROM_1_TO_2, Migrations.FROM_2_TO_3, Migrations.FROM_3_TO_4)
        .allowMainThreadQueries()
        .build()
        .also { opened = it }

    private fun SQLiteDatabase.insertPlanAndGoal() {
        execSQL(
            "INSERT INTO plan (id, name, is_active, zone, created_at) " +
                "VALUES (1, 'Weekdays', 1, 'Asia/Kolkata', 0)",
        )
        execSQL(
            "INSERT INTO goal (id, plan_id, kind, title, item_id, value_kind, start_value, target_value, " +
                "start_date, target_date, is_active) " +
                "VALUES (1, 1, 'COUNT', 'Gym', NULL, 'NONE', 0, 20, 20700, 20760, 1)",
        )
    }

    private fun SQLiteDatabase.insertProgress(date: LocalDate, counted: Boolean) {
        val flag = if (counted) 1 else 0

        execSQL(
            "INSERT INTO goal_progress (goal_id, date, raw_value, smoothed_value, cumulative, pace_target, " +
                "projected_final, counted) VALUES (1, ${date.toEpochDay()}, 1, NULL, 1, 0, 0, $flag)",
        )
    }

    @Test
    fun `every shipped version opens on the current schema`() {
        for (version in 1 until BuildOrBreakDatabase.VERSION) {
            createAt(version)

            // Room validates every table when the file is first used.
            open().openHelper.writableDatabase

            opened?.close()
            context.deleteDatabase(DB_NAME)
        }
    }

    @Test
    fun `a week already left out is still left out after the update`() = runTest {
        // Wednesday and Thursday of the week of Monday the 14th of September 2026.
        createAt(version = 3) { db ->
            db.insertPlanAndGoal()
            db.insertProgress(LocalDate.of(2026, 9, 9), counted = true)
            db.insertProgress(LocalDate.of(2026, 9, 16), counted = false)
            db.insertProgress(LocalDate.of(2026, 9, 17), counted = false)
        }

        val weeks = open().goalWeekDao().observeLeftOutWeeks(goalId = 1).first()

        assertThat(weeks).containsExactly(LocalDate.of(2026, 9, 14))
    }

    @Test
    fun `a sunday belongs to the week that began six days before it`() = runTest {
        createAt(version = 3) { db ->
            db.insertPlanAndGoal()
            db.insertProgress(LocalDate.of(2026, 9, 20), counted = false)
        }

        val weeks = open().goalWeekDao().observeLeftOutWeeks(goalId = 1).first()

        assertThat(weeks).containsExactly(LocalDate.of(2026, 9, 14))
    }

    @Test
    fun `nothing left out before means nothing left out after`() = runTest {
        createAt(version = 3) { db ->
            db.insertPlanAndGoal()
            db.insertProgress(LocalDate.of(2026, 9, 16), counted = true)
        }

        assertThat(open().goalWeekDao().observeLeftOutWeeks(goalId = 1).first()).isEmpty()
    }
}
