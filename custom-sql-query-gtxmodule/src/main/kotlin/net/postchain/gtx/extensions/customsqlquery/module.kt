package net.postchain.gtx.extensions.customsqlquery

import mu.KotlinLogging
import net.postchain.base.BaseBlockBuilderExtension
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import net.postchain.core.EContext
import net.postchain.core.Transactor
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvNull
import net.postchain.gtv.GtvString
import net.postchain.gtv.GtvType
import net.postchain.gtv.mapper.toObject
import net.postchain.gtx.ArgumentMetadata
import net.postchain.gtx.GTXModule
import net.postchain.gtx.GTXModuleFactory
import net.postchain.gtx.GTXModuleMetadata
import net.postchain.gtx.MetadataProvider
import net.postchain.gtx.QueryMetadata
import net.postchain.gtx.ReturnMetadata
import net.postchain.gtx.data.ExtOpData
import net.postchain.gtx.special.GTXSpecialTxExtension
import org.apache.commons.dbutils.QueryRunner
import org.apache.commons.dbutils.handlers.MapListHandler
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.SQLException

private val logger = KotlinLogging.logger {}

data class CustomSQLQueryConfig(
        val queries: Map<String, String>
)

data class QueryDef(
        val args: List<ArgDef>,
        val sql: String,
)

data class ArgDef(
        val name: String,
        val type: ArgType,
)

@Suppress("EnumEntryName")
enum class ArgType {
    integer, big_integer, text, byte_array
}

@Suppress("unused")
class CustomSQLQueryGTXModuleFactory : GTXModuleFactory {
    override fun makeModule(config: Gtv, blockchainRID: BlockchainRid): CustomSQLQueryGTXModule {
        val sqlQueryConfig = config.asDict()["customsqlquery"]!!.toObject<CustomSQLQueryConfig>()
        val queries = sqlQueryConfig.queries.mapValues { (_, query) -> resolveArgs(query) }
        return CustomSQLQueryGTXModule(queries)
    }
}

private val argRegex = Regex(""":(?<name>[A-Za-z_][A-Za-z_0-9]*):(?<type>[A-Za-z_][A-Za-z_0-9]*):""")

internal fun resolveArgs(sql: String): QueryDef {
    val argsInSql = argRegex.findAll(sql)
    val resolvedSql = argRegex.replace(sql, "?")
    return QueryDef(argsInSql.map {
        val name = it.groups["name"]!!.value
        val typeName = it.groups["type"]!!.value
        ArgDef(name, try {
            ArgType.valueOf(typeName)
        } catch (_: IllegalArgumentException) {
            throw UserMistake("Unknown argument type: $typeName")
        })
    }.toList(), resolvedSql)
}

class CustomSQLQueryGTXModule(private val queries: Map<String, QueryDef>) : GTXModule, MetadataProvider {
    private val queryRunner = QueryRunner()

    override fun getSpecialTxExtensions(): List<GTXSpecialTxExtension> = listOf()

    override fun makeBlockBuilderExtensions(): List<BaseBlockBuilderExtension> = listOf()

    override fun getOperations(): Set<String> = setOf()

    override fun getQueries(): Set<String> = queries.keys

    override fun initializeDB(ctx: EContext) {}

    override fun getMetadata() = GTXModuleMetadata(
            operations = mapOf(),
            queries = queries.mapValues {
                QueryMetadata(
                        args = it.value.args.map { arg ->
                            ArgumentMetadata(
                                    name = arg.name,
                                    gtvTypes = setOf(when (arg.type) {
                                        ArgType.integer -> GtvType.INTEGER
                                        ArgType.big_integer -> GtvType.BIGINTEGER
                                        ArgType.text -> GtvType.STRING
                                        ArgType.byte_array -> GtvType.BYTEARRAY
                                    })
                            )
                        },
                        returnType = ReturnMetadata(gtvTypes = setOf(GtvType.ARRAY)))
            }
    )

    override fun makeTransactor(opData: ExtOpData): Transactor {
        throw UserMistake("Operation not found")
    }

    override fun query(ctxt: EContext, name: String, args: Gtv): Gtv {
        val queryDef = queries[name] ?: throw UserMistake("Query $name not found")

        if (args !is GtvDictionary) {
            throw ProgrammerMistake("args is not a GtvDictionary")
        }

        val actualArgs = queryDef.args.map { arg ->
            val gtvValue = args.asDict()[arg.name] ?: throw UserMistake("Missing argument ${arg.name}")
            when (arg.type) {
                ArgType.integer -> gtvValue.asInteger()
                ArgType.big_integer -> gtvValue.asBigInteger()
                ArgType.text -> gtvValue.asString()
                ArgType.byte_array -> gtvValue.asByteArray()
            }
        }

        val sql = resolveTable(queryDef.sql, ctxt.chainID)

        val queryResult = try {
            queryRunner.query(ctxt.conn, sql, MapListHandler(), *actualArgs.toTypedArray())
        } catch (e: SQLException) {
            logger.info("Query execution error: ${e.message}")
            throw UserMistake("Query execution error")
        }

        return GtvArray(queryResult.map {
            val obj = buildMap {
                it.entries.forEach { entry ->
                    val gtv = when (val dbValue = entry.value) {
                        is Int, is Long -> GtvInteger((dbValue as Number).toLong())
                        is BigInteger -> GtvBigInteger(dbValue)
                        is BigDecimal -> GtvBigInteger(dbValue.toBigIntegerExact())
                        is String -> GtvString(dbValue)
                        is ByteArray -> GtvByteArray(dbValue)
                        null -> GtvNull
                        else -> throw ProgrammerMistake("Unsupported return type" +
                                " ${dbValue.javaClass.simpleName} of column ${entry.key} " +
                                "from query $name")
                    }
                    set(entry.key, gtv)
                }
            }
            GtvDictionary.build(obj)
        }.toTypedArray())
    }

    override fun shutdown() {}
}

private val tableRegex = Regex("""\[table:([A-Za-z_][A-Za-z_0-9.]*)]""")

internal fun resolveTable(sql: String, chainID: Long): String =
        tableRegex.replace(sql) { "\"c${chainID}.${it.groupValues[1]}\"" }
