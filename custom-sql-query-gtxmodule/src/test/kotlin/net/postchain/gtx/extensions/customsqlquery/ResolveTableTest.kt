package net.postchain.gtx.extensions.customsqlquery

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

internal class ResolveTableTest {
    @Test
    fun one() {
        assertThat(resolveTable("SELECT * FROM [table:foo]", 17))
                .isEqualTo("SELECT * FROM \"c17.foo\"")
    }

    @Test
    fun two() {
        assertThat(resolveTable("SELECT * FROM [table:foo] LEFT JOIN [table:bar]", 17))
                .isEqualTo("SELECT * FROM \"c17.foo\" LEFT JOIN \"c17.bar\"")
    }

    @Test
    fun `with dot`() {
        assertThat(resolveTable("SELECT * FROM [table:foo.bar]", 17))
                .isEqualTo("SELECT * FROM \"c17.foo.bar\"")
    }
}
