package kernel.database.migrations

import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationSqlGeneratorTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `generates create table sql for example migration`() {
        val sql = generator.generateUp(M0001_01_01_000000_create_example_table())

        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS example_table (
                id UUID NOT NULL,
                name VARCHAR(100) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                PRIMARY KEY (id)
            );
            """.trimIndent(),
            sql
        )
    }

    @Test
    fun `generates primary key from fluent column modifier`() {
        val migration = object : Migration() {
            override fun up() {
                create("users") {
                    id().primaryKey()
                    string("email").notNull()
                }
            }

            override fun down() = Unit
        }

        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS users (
                id UUID NOT NULL,
                email VARCHAR(255) NOT NULL,
                PRIMARY KEY (id)
            );
            """.trimIndent(),
            generator.generateUp(migration)
        )
    }

    @Test
    fun `generates drop table sql for example rollback`() {
        val sql = generator.generateDown(M0001_01_01_000000_create_example_table())

        assertEquals("DROP TABLE IF EXISTS example_table;", sql)
    }

    @Test
    fun `generates postgres column types and defaults`() {
        val migration = object : Migration() {
            override fun up() {
                create("products") {
                    id().defaultGeneratedUuid().primaryKey()
                    varchar("sku", 64).notNull().unique()
                    text("description")
                    numeric("price", 12, 2).notNull().default(0)
                    boolean("active").notNull().default(true)
                    timestampTz("created_at").notNull().defaultCurrentTimestamp()
                    jsonb("metadata").notNull().defaultRaw("'{}'::jsonb")
                }
            }

            override fun down() {
                dropIfExists("products")
            }
        }

        val sql = generator.generateUp(migration)

        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS products (
                id UUID NOT NULL DEFAULT gen_random_uuid(),
                sku VARCHAR(64) NOT NULL UNIQUE,
                description TEXT,
                price NUMERIC(12, 2) NOT NULL DEFAULT 0,
                active BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                PRIMARY KEY (id)
            );
            """.trimIndent(),
            sql
        )
    }

    @Test
    fun `generates documented postgres data types`() {
        val migration = object : Migration() {
            override fun up() {
                create("postgres_types") {
                    smallInt("small_count")
                    integer("count")
                    bigInt("big_count")
                    smallSerial("small_sequence")
                    serial("sequence")
                    bigSerial("big_sequence")
                    real("ratio")
                    doublePrecision("score")
                    money("amount")
                    bit("flag", 1)
                    bitVarying("flags", 8)
                    bytea("payload")
                    date("happened_on")
                    time("starts_at", precision = 3)
                    timeTz("starts_at_tz", precision = 3)
                    timestamp("created_at", precision = 6)
                    timestampTz("created_at_tz", precision = 6)
                    interval("duration", fields = "DAY TO SECOND", precision = 3)
                    json("raw_metadata")
                    jsonb("metadata")
                    xml("document")
                    inet("ip_address")
                    cidr("network")
                    macaddr("mac")
                    macaddr8("mac8")
                    point("location")
                    line("line_shape")
                    lseg("segment")
                    box("bounds")
                    path("route")
                    polygon("area")
                    circle("radius_area")
                    tsvector("search_vector")
                    tsquery("search_query")
                    pgLsn("log_sequence")
                    pgSnapshot("snapshot_id")
                    txidSnapshot("txid_snapshot_id")
                    int4Range("integer_range")
                    int8Range("big_integer_range")
                    numRange("numeric_range")
                    tsRange("timestamp_range")
                    tstzRange("timestamp_tz_range")
                    dateRange("date_range")
                    int4MultiRange("integer_multi_range")
                    int8MultiRange("big_integer_multi_range")
                    numMultiRange("numeric_multi_range")
                    tsMultiRange("timestamp_multi_range")
                    tstzMultiRange("timestamp_tz_multi_range")
                    dateMultiRange("date_multi_range")
                    array("tags", "TEXT")
                }
            }

            override fun down() = Unit
        }

        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS postgres_types (
                small_count SMALLINT,
                count INTEGER,
                big_count BIGINT,
                small_sequence SMALLSERIAL,
                sequence SERIAL,
                big_sequence BIGSERIAL,
                ratio REAL,
                score DOUBLE PRECISION,
                amount MONEY,
                flag BIT(1),
                flags BIT VARYING(8),
                payload BYTEA,
                happened_on DATE,
                starts_at TIME(3),
                starts_at_tz TIMETZ(3),
                created_at TIMESTAMP(6),
                created_at_tz TIMESTAMPTZ(6),
                duration INTERVAL DAY TO SECOND(3),
                raw_metadata JSON,
                metadata JSONB,
                document XML,
                ip_address INET,
                network CIDR,
                mac MACADDR,
                mac8 MACADDR8,
                location POINT,
                line_shape LINE,
                segment LSEG,
                bounds BOX,
                route PATH,
                area POLYGON,
                radius_area CIRCLE,
                search_vector TSVECTOR,
                search_query TSQUERY,
                log_sequence PG_LSN,
                snapshot_id PG_SNAPSHOT,
                txid_snapshot_id TXID_SNAPSHOT,
                integer_range INT4RANGE,
                big_integer_range INT8RANGE,
                numeric_range NUMRANGE,
                timestamp_range TSRANGE,
                timestamp_tz_range TSTZRANGE,
                date_range DATERANGE,
                integer_multi_range INT4MULTIRANGE,
                big_integer_multi_range INT8MULTIRANGE,
                numeric_multi_range NUMMULTIRANGE,
                timestamp_multi_range TSMULTIRANGE,
                timestamp_tz_multi_range TSTZMULTIRANGE,
                date_multi_range DATEMULTIRANGE,
                tags TEXT[]
            );
            """.trimIndent(),
            generator.generateUp(migration)
        )
    }

    @Test
    fun `returns each statement separately`() {
        val migration = object : Migration() {
            override fun up() {
                statement("CREATE EXTENSION IF NOT EXISTS pgcrypto;")
                create("audit_logs") {
                    id().primaryKey()
                    timestampTz("created_at").notNull()
                }
            }

            override fun down() {
                dropTable("audit_logs")
            }
        }

        val statements = generator.generateUpStatements(migration)

        assertEquals(2, statements.size)
        assertEquals("CREATE EXTENSION IF NOT EXISTS pgcrypto;", statements.first())
        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS audit_logs (
                id UUID NOT NULL,
                created_at TIMESTAMPTZ NOT NULL,
                PRIMARY KEY (id)
            );
            """.trimIndent(),
            statements.last()
        )
    }

    @Test
    fun `generates alter table operations`() {
        val migration = object : Migration() {
            override fun up() {
                table("users") {
                    string("email").notNull().unique()
                    renameColumn("name", "full_name")
                    changeColumnType(
                        column = "age",
                        type = "BIGINT",
                        usingExpression = "age::bigint"
                    )
                    setDefault("active", "TRUE")
                    dropDefault("active")
                    setNotNull("email")
                    dropNotNull("nickname")
                    dropColumn("legacy_code")
                }
                rename("users", "customers")
            }

            override fun down() = Unit
        }

        val sql = generator.generateUp(migration)

        assertEquals(
            """
            ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255) NOT NULL UNIQUE;

            ALTER TABLE users RENAME COLUMN name TO full_name;

            ALTER TABLE users ALTER COLUMN age TYPE BIGINT USING age::bigint;

            ALTER TABLE users ALTER COLUMN active SET DEFAULT TRUE;

            ALTER TABLE users ALTER COLUMN active DROP DEFAULT;

            ALTER TABLE users ALTER COLUMN email SET NOT NULL;

            ALTER TABLE users ALTER COLUMN nickname DROP NOT NULL;

            ALTER TABLE users DROP COLUMN IF EXISTS legacy_code;

            ALTER TABLE users RENAME TO customers;
            """.trimIndent(),
            sql
        )
    }

    @Test
    fun `generates index operations`() {
        val migration = object : Migration() {
            override fun up() {
                table("users") {
                    unique("email")
                }
                table("orders") {
                    index("customer_id", "created_at", concurrently = true)
                }
            }

            override fun down() {
                table("orders") {
                    dropIndex("orders_customer_id_created_at_index", concurrently = true)
                }
                table("users") {
                    dropIndex("users_email_unique")
                }
            }
        }

        assertEquals(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users (email);

            CREATE INDEX CONCURRENTLY IF NOT EXISTS orders_customer_id_created_at_index ON orders (customer_id, created_at);
            """.trimIndent(),
            generator.generateUp(migration)
        )
        assertEquals(
            """
            DROP INDEX CONCURRENTLY IF EXISTS orders_customer_id_created_at_index /* table: orders */;

            DROP INDEX IF EXISTS users_email_unique /* table: users */;
            """.trimIndent(),
            generator.generateDown(migration)
        )
    }

    @Test
    fun `generates constraints and generated columns`() {
        val migration = object : Migration() {
            override fun up() {
                create("orders") {
                    id().primaryKey()
                    foreignUuid("customer_id").notNull()
                    numeric("subtotal", 12, 2).notNull()
                    numeric("tax", 12, 2).notNull()
                    numeric("total", 12, 2).storedAs("subtotal + tax")
                    bigInt("ticket_number").generatedByDefaultAsIdentity()
                    unique("ticket_number")
                    check("orders_total_positive_check", "total >= 0")
                    foreign("customer_id").references("id").on("customers").cascadeOnDelete()
                }
            }

            override fun down() = Unit
        }

        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS orders (
                id UUID NOT NULL,
                customer_id UUID NOT NULL,
                subtotal NUMERIC(12, 2) NOT NULL,
                tax NUMERIC(12, 2) NOT NULL,
                total NUMERIC(12, 2) GENERATED ALWAYS AS (subtotal + tax) STORED,
                ticket_number BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
                PRIMARY KEY (id),
                CONSTRAINT orders_ticket_number_unique UNIQUE (ticket_number),
                CONSTRAINT orders_total_positive_check CHECK (total >= 0),
                CONSTRAINT orders_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
            );
            """.trimIndent(),
            generator.generateUp(migration)
        )
    }

    @Test
    fun `generates advanced indexes and table constraints`() {
        val migration = object : Migration() {
            override fun up() {
                table("orders") {
                    gin("metadata", name = "orders_metadata_gin", where = "metadata IS NOT NULL")
                    indexExpression(
                        name = "orders_lower_code_index",
                        expression = "lower(code)",
                        include = listOf("id"),
                        where = "deleted_at IS NULL"
                    )
                    foreign("customer_id").constrained("customers").cascadeOnUpdate().restrictOnDelete()
                    check("orders_total_check", "total >= 0")
                    uniqueConstraint("store_id", "number", name = "orders_store_number_unique")
                    exclude(
                        name = "reservations_room_during_exclude",
                        using = "gist",
                        "room_id WITH =",
                        "during WITH &&"
                    )
                    renameConstraint("orders_total_check", "orders_total_positive_check")
                    dropConstraint("orders_legacy_check")
                }
            }

            override fun down() = Unit
        }

        assertEquals(
            """
            CREATE INDEX IF NOT EXISTS orders_metadata_gin ON orders USING gin (metadata) WHERE metadata IS NOT NULL;

            CREATE INDEX IF NOT EXISTS orders_lower_code_index ON orders (lower(code)) INCLUDE (id) WHERE deleted_at IS NULL;

            ALTER TABLE orders ADD CONSTRAINT orders_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT ON UPDATE CASCADE;

            ALTER TABLE orders ADD CONSTRAINT orders_total_check CHECK (total >= 0);

            ALTER TABLE orders ADD CONSTRAINT orders_store_number_unique UNIQUE (store_id, number);

            ALTER TABLE orders ADD CONSTRAINT reservations_room_during_exclude EXCLUDE USING gist (room_id WITH =, during WITH &&);

            ALTER TABLE orders RENAME CONSTRAINT orders_total_check TO orders_total_positive_check;

            ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_legacy_check;
            """.trimIndent(),
            generator.generateUp(migration)
        )
    }

    @Test
    fun `generates database object operations`() {
        val migration = object : Migration() {
            override fun up() {
                createSchema("pos")
                createExtension("pgcrypto", schema = "public")
                createSequence(
                    "pos.ticket_numbers",
                    startWith = 1000,
                    incrementBy = 1,
                    cache = 10,
                    ownedBy = "pos.orders.ticket_number"
                )
                createDomain(
                    name = "positive_money",
                    type = "NUMERIC(12, 2)",
                    notNull = true,
                    checkExpression = "VALUE >= 0"
                )
                createView(
                    "pos.open_orders",
                    "SELECT * FROM pos.orders WHERE paid_at IS NULL"
                )
                createMaterializedView(
                    "pos.daily_sales",
                    "SELECT current_date AS sold_on, 0::numeric AS total",
                    withData = false
                )
                refreshMaterializedView("pos.daily_sales", concurrently = true)
                commentOnTable("pos.orders", "POS orders")
                commentOnColumn("pos.orders", "total", "Generated order total")
                createFunction(
                    name = "pos.touch_updated_at",
                    returns = "trigger",
                    language = "plpgsql",
                    body = "BEGIN\n    NEW.updated_at = CURRENT_TIMESTAMP;\n    RETURN NEW;\nEND"
                )
                createTrigger(
                    name = "orders_touch_updated_at",
                    table = "pos.orders",
                    timing = "before",
                    "update",
                    function = "pos.touch_updated_at()"
                )
            }

            override fun down() {
                dropTrigger("orders_touch_updated_at", "pos.orders")
                dropFunction("pos.touch_updated_at()")
                commentOnColumn("pos.orders", "total", null)
                dropMaterializedView("pos.daily_sales")
                dropView("pos.open_orders")
                dropDomain("positive_money")
                dropSequence("pos.ticket_numbers")
                dropExtension("pgcrypto")
                dropSchema("pos", cascade = true)
            }
        }

        assertEquals(
            """
            CREATE SCHEMA IF NOT EXISTS pos;

            CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

            CREATE SEQUENCE IF NOT EXISTS pos.ticket_numbers INCREMENT BY 1 START WITH 1000 CACHE 10 OWNED BY pos.orders.ticket_number;

            CREATE DOMAIN positive_money AS NUMERIC(12, 2) NOT NULL CHECK (VALUE >= 0);

            CREATE OR REPLACE VIEW pos.open_orders AS
            SELECT * FROM pos.orders WHERE paid_at IS NULL;

            CREATE MATERIALIZED VIEW IF NOT EXISTS pos.daily_sales AS
            SELECT current_date AS sold_on, 0::numeric AS total WITH NO DATA;

            REFRESH MATERIALIZED VIEW CONCURRENTLY pos.daily_sales WITH DATA;

            COMMENT ON TABLE pos.orders IS 'POS orders';

            COMMENT ON COLUMN pos.orders.total IS 'Generated order total';

            CREATE OR REPLACE FUNCTION pos.touch_updated_at()
            RETURNS trigger
            LANGUAGE plpgsql
            AS ${'$'}${'$'}
            BEGIN
                NEW.updated_at = CURRENT_TIMESTAMP;
                RETURN NEW;
            END
            ${'$'}${'$'};

            CREATE TRIGGER orders_touch_updated_at
            BEFORE UPDATE ON pos.orders
            FOR EACH ROW
            EXECUTE FUNCTION pos.touch_updated_at();
            """.trimIndent(),
            generator.generateUp(migration)
        )
        assertEquals(
            """
            DROP TRIGGER IF EXISTS orders_touch_updated_at ON pos.orders;

            DROP FUNCTION IF EXISTS pos.touch_updated_at();

            COMMENT ON COLUMN pos.orders.total IS NULL;

            DROP MATERIALIZED VIEW IF EXISTS pos.daily_sales;

            DROP VIEW IF EXISTS pos.open_orders;

            DROP DOMAIN IF EXISTS positive_money;

            DROP SEQUENCE IF EXISTS pos.ticket_numbers;

            DROP EXTENSION IF EXISTS pgcrypto;

            DROP SCHEMA IF EXISTS pos CASCADE;
            """.trimIndent(),
            generator.generateDown(migration)
        )
    }

    @Test
    fun `generates laravel inspired helpers`() {
        val migration = object : Migration() {
            override fun up() {
                create("users") {
                    id().generatedUuid().primaryKey()
                    string("name")
                    string("email").unique()
                    foreignUuid("company_id")
                    timestampsTz()
                    softDeletesTz()
                }
            }

            override fun down() {
                dropIfExists("users")
            }
        }

        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS users (
                id UUID NOT NULL DEFAULT gen_random_uuid(),
                name VARCHAR(255),
                email VARCHAR(255) UNIQUE,
                company_id UUID,
                created_at TIMESTAMPTZ,
                updated_at TIMESTAMPTZ,
                deleted_at TIMESTAMPTZ,
                PRIMARY KEY (id)
            );
            """.trimIndent(),
            generator.generateUp(migration)
        )
    }

    @Test
    fun `generates postgres enum operations`() {
        val migration = object : Migration() {
            override fun up() {
                createEnum("order_status", "pending", "paid", "cancelled")
                create("orders") {
                    id().primaryKey()
                    enumColumn("status", "order_status").notNull().default("pending")
                }
                addEnumValue("order_status", "refunded", after = "paid")
                renameEnumValue("order_status", "cancelled", "voided")
            }

            override fun down() {
                dropIfExists("orders")
                dropEnum("order_status")
            }
        }

        assertEquals(
            """
            CREATE TYPE order_status AS ENUM ('pending', 'paid', 'cancelled');

            CREATE TABLE IF NOT EXISTS orders (
                id UUID NOT NULL,
                status order_status NOT NULL DEFAULT 'pending',
                PRIMARY KEY (id)
            );

            ALTER TYPE order_status ADD VALUE IF NOT EXISTS 'refunded' AFTER 'paid';

            ALTER TYPE order_status RENAME VALUE 'cancelled' TO 'voided';
            """.trimIndent(),
            generator.generateUp(migration)
        )
        assertEquals(
            """
            DROP TABLE IF EXISTS orders;

            DROP TYPE IF EXISTS order_status;
            """.trimIndent(),
            generator.generateDown(migration)
        )
    }
}
