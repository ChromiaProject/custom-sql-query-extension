package net.postchain.gtx.extensions.customsqlquery

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import net.postchain.StorageBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.testDbConfig
import net.postchain.base.runStorageCommand
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.config.app.AppConfig
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import org.apache.commons.dbutils.QueryRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigInteger

internal class CustomSQLQueryIT {
    private val appConfig: AppConfig = testDbConfig("database_it")

    @BeforeEach
    fun beforeEach() {
        StorageBuilder.wipeDatabase(appConfig)
    }

    @Test
    fun test() {
        val blockchainRid = BlockchainRid(ByteArray(32) { 1 })
        runStorageCommand(appConfig, 17) { ctx ->
            val db = DatabaseAccess.of(ctx)
            db.initializeBlockchain(ctx, blockchainRid)

            val queryRunner = QueryRunner()
            val tableName = db.tableName(ctx, "my_entity")
            queryRunner.execute(ctx.conn, """
                create table $tableName
                (
                    rowid        bigint   not null,
                    col1         text     not null,
                    col2         bigint   not null,
                    col3         numeric  not null,
                    col4         bytea    not null,
                    col5         text     not null
                );
            """.trimIndent())
            queryRunner.execute(ctx.conn, """INSERT INTO $tableName(rowid, col1, col2, col3, col4, col5) VALUES (1, 'foo', 17, 4711, '\xDEADBEEF11'::bytea, 'some');""")
            queryRunner.execute(ctx.conn, """INSERT INTO $tableName(rowid, col1, col2, col3, col4, col5) VALUES (2, 'bar', 18, 4712, '\xDEADBEEF22'::bytea, 'thing');""")
            queryRunner.execute(ctx.conn, """INSERT INTO $tableName(rowid, col1, col2, col3, col4, col5) VALUES (3, 'baz', 19, 4713, '\xDEADBEEF33'::bytea, 'this');""")
            queryRunner.execute(ctx.conn, """INSERT INTO $tableName(rowid, col1, col2, col3, col4, col5) VALUES (4, 'baz', 19, 4713, '\xDEADBEEF33'::bytea, 'that');""")

            val config = GtvMLParser.parseGtvML(javaClass.getResource("/blockchain_config.xml")!!.readText(Charsets.UTF_8))
            val moduleFactory = CustomSQLQueryGTXModuleFactory()
            val module = moduleFactory.makeModule(config, blockchainRid)

            assertThat(module.getQueries()).isEqualTo(setOf("query1", "query2"))

            assertFailure { module.query(ctx, "bogus", gtv(mapOf())) }
                    .isInstanceOf<UserMistake>().hasMessage("Query bogus not found")

            assertFailure { module.query(ctx, "query1", gtv("bogus")) }
                    .isInstanceOf<ProgrammerMistake>().hasMessage("args is not a GtvDictionary")

            assertFailure { module.query(ctx, "query1", gtv(mapOf("foo" to gtv("bar")))) }
                    .isInstanceOf<UserMistake>().hasMessage("Missing argument arg1")

            assertFailure { module.query(ctx, "query2", gtv(mapOf("arg1" to gtv("foo"), "arg2" to gtv("bar")))) }
                    .isInstanceOf<UserMistake>().hasMessage("Type error: integer expected, found STRING with value \"bar\"")

            assertThat(module.query(ctx, "query2", gtv(mapOf(
                    "arg1" to gtv("foo"),
                    "arg2" to gtv(17),
                    "arg3" to gtv(BigInteger.valueOf(4711)),
                    "arg4" to gtv(ByteArray(16) { 17 }))
            )).asArray()).isEmpty()

            assertThat(module.query(ctx, "query2", gtv(mapOf(
                    "arg1" to gtv("baz"),
                    "arg2" to gtv(19),
                    "arg3" to gtv(BigInteger.valueOf(4713)),
                    "arg4" to gtv("DEADBEEF33".hexStringToByteArray()))
            )).asArray().toList()).containsExactlyInAnyOrder(
                    gtv(mapOf("col1" to gtv("baz"), "col2" to gtv(19), "col3" to gtv(BigInteger.valueOf(4713)), "col4" to gtv("DEADBEEF33".hexStringToByteArray()), "col5" to gtv("this"))),
                    gtv(mapOf("col1" to gtv("baz"), "col2" to gtv(19), "col3" to gtv(BigInteger.valueOf(4713)), "col4" to gtv("DEADBEEF33".hexStringToByteArray()), "col5" to gtv("that"))),
            )

            assertFailure {
                module.query(ctx, "query1", gtv(mapOf(
                        "arg1" to gtv("foo"),
                        "arg2" to gtv(17),
                        "arg3" to gtv(BigInteger.valueOf(4711)),
                        "arg4" to gtv(ByteArray(16) { 17 }))
                ))
            }.isInstanceOf<UserMistake>().hasMessage("Query execution error")
        }
    }
}
