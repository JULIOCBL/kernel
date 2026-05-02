package kernel.database.migrations

import kotlin.test.Test

class MigrationColumnFeatureTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `generates column helpers defaults identity and generated columns`() {
        val migration = object : Migration() {
            override fun up() {
                create("orders") {
                    id().generatedUuid().primary()
                    string("code", 40).notNull().unique()
                    boolean("active").notNull().default(true)
                    numeric("subtotal", 12, 2).notNull().default(0)
                    numeric("tax", 12, 2).notNull().default(0)
                    numeric("total", 12, 2).storedAs("subtotal + tax")
                    bigInt("ticket_number").generatedAlwaysAsIdentity()
                    timestampTz("created_at").useCurrent()
                    jsonb("metadata").defaultRaw("'{}'::jsonb")
                }
            }

            override fun down() = Unit
        }

        assertGeneratedSql(
            """
            CREATE TABLE orders (
                id UUID NOT NULL DEFAULT gen_random_uuid(),
                code VARCHAR(40) NOT NULL UNIQUE,
                active BOOLEAN NOT NULL DEFAULT TRUE,
                subtotal NUMERIC(12, 2) NOT NULL DEFAULT 0,
                tax NUMERIC(12, 2) NOT NULL DEFAULT 0,
                total NUMERIC(12, 2) GENERATED ALWAYS AS (subtotal + tax) STORED,
                ticket_number BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
                created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                metadata JSONB DEFAULT '{}'::jsonb,
                PRIMARY KEY (id)
            );
            """,
            generator.generateUp(migration)
        )
    }

    @Test
    fun `generates broad postgres data type families`() {
        val migration = object : Migration() {
            override fun up() {
                create("postgres_types") {
                    smallInt("small_count")
                    int("count")
                    bigInt("big_count")
                    real("ratio")
                    doublePrecision("score")
                    money("amount")
                    bytea("payload")
                    bit("flag", 1)
                    varbit("flags", 8)
                    time("starts_at", precision = 3)
                    timeTz("starts_at_tz", precision = 3)
                    interval("duration", fields = "DAY TO SECOND", precision = 3)
                    json("raw_metadata")
                    xml("document")
                    inet("ip_address")
                    point("location")
                    tsvector("search_vector")
                    int4Range("integer_range")
                    dateMultiRange("date_multi_range")
                    array("tags", "TEXT")
                }
            }

            override fun down() = Unit
        }

        assertGeneratedSql(
            """
            CREATE TABLE postgres_types (
                small_count SMALLINT,
                count INTEGER,
                big_count BIGINT,
                ratio REAL,
                score DOUBLE PRECISION,
                amount MONEY,
                payload BYTEA,
                flag BIT(1),
                flags BIT VARYING(8),
                starts_at TIME(3),
                starts_at_tz TIMETZ(3),
                duration INTERVAL DAY TO SECOND(3),
                raw_metadata JSON,
                document XML,
                ip_address INET,
                location POINT,
                search_vector TSVECTOR,
                integer_range INT4RANGE,
                date_multi_range DATEMULTIRANGE,
                tags TEXT[]
            );
            """,
            generator.generateUp(migration)
        )
    }
}
