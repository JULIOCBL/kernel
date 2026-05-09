package kernel.database.orm

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelConventionTest {
    @Test
    fun `query builder infers plural snake case table names from singular model names`() {
        assertEquals(
            "SELECT * FROM blog_posts",
            BlogPost.query().buildSelectSql()
        )

        assertEquals(
            "SELECT * FROM categories",
            Category.query().buildSelectSql()
        )
    }

    @Test
    fun `model definition can still override table name when convention is not desired`() {
        assertEquals(
            "SELECT * FROM legacy_people",
            LegacyPerson.query().buildSelectSql()
        )
    }
}

private data class BlogPost(
    val id: Int,
    val title: String
) : Model() {
    override fun primaryKeyValue(): Any? = id

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "title" to title
        )
    }

    companion object : ModelDefinition<BlogPost>(
        mapper = { BlogPost(it.getInt("id"), it.getString("title")) }
    )
}

private data class Category(
    val id: Int,
    val name: String
) : Model() {
    override fun primaryKeyValue(): Any? = id

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name
        )
    }

    companion object : ModelDefinition<Category>(
        mapper = { Category(it.getInt("id"), it.getString("name")) }
    )
}

private data class LegacyPerson(
    val id: Int,
    val displayName: String
) : Model() {
    override fun primaryKeyValue(): Any? = id

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "display_name" to displayName
        )
    }

    companion object : ModelDefinition<LegacyPerson>(
        tableName = "legacy_people",
        mapper = { LegacyPerson(it.getInt("id"), it.getString("display_name")) }
    )
}
