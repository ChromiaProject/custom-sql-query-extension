package net.postchain.gtx.extensions.customsqlquery

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import net.postchain.common.exception.UserMistake
import org.junit.jupiter.api.Test

internal class ResolveArgsTest {
    @Test
    fun `no args`() {
        assertThat(resolveArgs("SELECT * FROM [table:foo]"))
                .isEqualTo(QueryDef(listOf(), "SELECT * FROM [table:foo]"))
    }

    @Test
    fun `one arg`() {
        assertThat(resolveArgs("SELECT * FROM [table:foo] WHERE col1 = :arg1:text:"))
                .isEqualTo(QueryDef(listOf(ArgDef("arg1", ArgType.text)), "SELECT * FROM [table:foo] WHERE col1 = ?"))
    }

    @Test
    fun `multiple args`() {
        assertThat(resolveArgs("SELECT * FROM [table:foo] WHERE col1 = :arg1:text: AND col2 = :arg2:integer: AND col3 = :arg3:big_integer: AND col4 = :arg4:byte_array:"))
                .isEqualTo(QueryDef(listOf(ArgDef("arg1", ArgType.text), ArgDef("arg2", ArgType.integer), ArgDef("arg3", ArgType.big_integer), ArgDef("arg4", ArgType.byte_array)),
                        "SELECT * FROM [table:foo] WHERE col1 = ? AND col2 = ? AND col3 = ? AND col4 = ?"))
    }

    @Test
    fun `wrong type`() {
        assertFailure { resolveArgs("SELECT * FROM [table:foo] WHERE col1 = :arg1:bogus:") }
                .isInstanceOf<UserMistake>().hasMessage("Unknown argument type: bogus")
    }
}
