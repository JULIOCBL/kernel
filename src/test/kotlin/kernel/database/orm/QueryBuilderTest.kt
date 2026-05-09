package kernel.database.orm

import kotlin.test.Test
import kotlin.test.assertEquals

class QueryBuilderTest {
    @Test
    fun `build select sql includes joins where clauses order and pagination in canonical order`() {
        val sql = QueryBuilder(
            table = "tickets",
            rowMapper = { _ -> Unit }
        )
            .select("tickets.id", "users.name")
            .join("users", "users.id", "=", "tickets.user_id")
            .where("tickets.status", "=", "paid")
            .where("tickets.user_id", "=", 7)
            .orderBy("tickets.id", "desc")
            .limit(10)
            .offset(20)
            .buildSelectSql()

        assertEquals(
            "SELECT tickets.id, users.name FROM tickets JOIN users ON users.id = tickets.user_id WHERE tickets.status = ? AND tickets.user_id = ? ORDER BY tickets.id DESC LIMIT 10 OFFSET 20",
            sql
        )
    }

    @Test
    fun `query builder supports or where in null checks and reorder helpers`() {
        val sql = QueryBuilder(
            table = "tickets",
            rowMapper = { _ -> Unit }
        )
            .where("tickets.status", "=", "paid")
            .orWhere("tickets.status", "=", "pending")
            .whereIn("tickets.user_id", listOf(7, 8, 9))
            .whereNull("tickets.deleted_at")
            .latest("tickets.id")
            .reorder("tickets.created_at", "asc")
            .buildSelectSql()

        assertEquals(
            "SELECT * FROM tickets WHERE tickets.status = ? OR tickets.status = ? AND tickets.user_id IN (?, ?, ?) AND tickets.deleted_at IS NULL ORDER BY tickets.created_at ASC",
            sql
        )
    }

    @Test
    fun `query builder supports nested where groups`() {
        val sql = QueryBuilder(
            table = "tickets",
            rowMapper = { _ -> Unit }
        )
            .where {
                where("tickets.status", "=", "paid")
                orWhere("tickets.status", "=", "pending")
            }
            .where {
                whereIn("tickets.user_id", listOf(7, 8, 9))
                orWhereNull("tickets.deleted_at")
            }
            .orderByDesc("tickets.id")
            .buildSelectSql()

        assertEquals(
            "SELECT * FROM tickets WHERE (tickets.status = ? OR tickets.status = ?) AND (tickets.user_id IN (?, ?, ?) OR tickets.deleted_at IS NULL) ORDER BY tickets.id DESC",
            sql
        )
    }

    @Test
    fun `query builder supports leading where group followed by or where group`() {
        val sql = QueryBuilder(
            table = "tickets",
            rowMapper = { _ -> Unit }
        )
            .where {
                where("tickets.status", "=", "paid")
                whereNotNull("tickets.approved_at")
            }
            .orWhere {
                where("tickets.status", "=", "pending")
                whereNull("tickets.deleted_at")
            }
            .buildSelectSql()

        assertEquals(
            "SELECT * FROM tickets WHERE (tickets.status = ? AND tickets.approved_at IS NOT NULL) OR (tickets.status = ? AND tickets.deleted_at IS NULL)",
            sql
        )
    }

    @Test
    fun `query builder rejects unsafe identifiers`() {
        val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
            QueryBuilder(
                table = "tickets; DROP TABLE users",
                rowMapper = { _ -> Unit }
            ).buildSelectSql()
        }

        assertEquals(
            "Identificador SQL inválido: `tickets; DROP TABLE users`.",
            error.message
        )
    }

    @Test
    fun `query builder rejects unsafe operators`() {
        val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
            QueryBuilder(
                table = "tickets",
                rowMapper = { _ -> Unit }
            ).where("id", "OR 1=1", 1)
        }

        assertEquals(
            "Operador SQL no permitido: `OR 1=1`.",
            error.message
        )
    }
}
