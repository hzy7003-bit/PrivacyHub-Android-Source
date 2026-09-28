package com.privacyhub.security.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.privacyhub.security.keystore.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object SqlCipherFactoryProvider {
    fun create(context: Context): SupportSQLiteOpenHelper.Factory {
        val passphrase = DatabaseKeyManager(context).getOrCreateDatabasePassphrase()
        return SupportOpenHelperFactory(passphrase)
    }
}
