package com.chalo.customer.data.local

import androidx.room.migration.Migration

/**
 * Central registry for Room DB migrations.
 *
 * Add each future migration here (e.g. MIGRATION_1_2, MIGRATION_2_3) and include it in ALL.
 */
object DatabaseMigrations {
    val ALL: Array<Migration> = emptyArray()
}
