package net.postchain.base.data

import net.postchain.config.app.AppConfig
import net.postchain.crypto.Secp256K1CryptoSystem
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

// TODO copied from postchain-base:test-jar, remove when accessible from there

fun testDbConfig(dbSchema: String, readConcurrency: Int = 10): AppConfig {
    val dbUrl = System.getenv("POSTCHAIN_DB_URL") ?: "jdbc:postgresql://localhost:5432/postchain"

    return mock {
        on { databaseDriverclass } doReturn "org.postgresql.Driver"
        on { cryptoSystem } doReturn Secp256K1CryptoSystem()
        on { databaseUrl } doReturn dbUrl
        on { databaseUsername } doReturn "postchain"
        on { databasePassword } doReturn "postchain"
        on { databaseSchema } doReturn dbSchema
        on { databaseReadConcurrency } doReturn readConcurrency
    }
}
