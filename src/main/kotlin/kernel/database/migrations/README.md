# Migraciones SQL

Este paquete permite declarar migraciones con Kotlin y generar SQL compatible
con los motores soportados por el kernel. La sintaxis principal esta inspirada
en Laravel migrations:
`create`, `table`, `dropIfExists`, `rename`, columnas fluidas, indices y
operaciones reversibles con `up` y `down`.

No es una copia exacta de Laravel porque Kotlin tiene sus propias reglas. Por
ejemplo, `enum` es palabra reservada, asi que para columnas enum usamos
`enumColumn`.

## Motores Soportados

Hoy el kernel trae soporte oficial para:

- `pgsql`
- `mariadb`

La DSL actual sigue siendo mas rica para PostgreSQL; MariaDB funciona sobre el
subconjunto portable de migraciones y traduce automaticamente helpers comunes
como `increments`, `bigIncrements`, `binary`, `UUID`, `dateTime` y
`timestampTz` al SQL concreto de cada motor.

Internamente, el kernel ahora separa esta capa en:

- `PortableMigrationDsl`: operaciones comunes como tablas, columnas, vistas e indices;
- `PostgresMigrationDsl`: schemas, enums, domains, extensiones, funciones, secuencias y triggers;
- `PortableTableDefinitionDsl` / `PortableTableAlterationDsl`: helpers comunes al DSL de tablas;
- `PostgresTableDefinitionDsl` / `PostgresTableAlterationDsl`: constraints e indices propios de PostgreSQL;
- `MigrationRepositoryDialect`: diferencias del repository de migraciones por motor.

## Ejemplo Rapido

```kotlin
package kernel.database.migrations

class M0001_01_01_000000_create_users_table : Migration() {
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
```

SQL generado en PostgreSQL:

```sql
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
```

## Generar SQL

```kotlin
val migration = M0001_01_01_000000_create_users_table()
val generator = MigrationSqlGenerator()

val upSql = generator.generateUp(migration)
val downSql = generator.generateDown(migration)
val statements = generator.generateUpStatements(migration)
```

Internamente el kernel ahora llama a esta pieza `SchemaSqlGenerator`, pero el
alias `MigrationSqlGenerator` se conserva para no romper compatibilidad.

Tambien puedes pedir SQL para un driver concreto:

```kotlin
import kernel.database.pdo.drivers.MariaDbDriver
import kernel.database.pdo.drivers.PostgreSqlDriver

val pgSql = generator.generateUp(migration, PostgreSqlDriver)
val mariaSql = generator.generateUp(migration, MariaDbDriver)
```

## Descubrir Migraciones Por Convencion

El kernel puede descubrir migraciones automaticamente desde un package,
siguiendo una convención tipo Laravel:

```kotlin
val registry = MigrationDiscovery.discover("playground.database.migrations")
```

Las clases detectadas deben vivir en ese package y seguir una convención como:

```text
M2026_04_28_121500_create_users_table
```

Si una app necesita control absoluto, también puede construir un
`MigrationRegistry` manualmente.

## Ejecutar Migraciones Desde Codigo

Luego puedes crear un `Migrator` usando un `ConnectionResolver` y un
`MigrationRepository`:

```kotlin
val database = kernel.database.DatabaseManager.from(app)

val migrator = Migrator(
    repository = JdbcMigrationRepository(database),
    resolver = database,
    registry = registry
)

migrator.run()
migrator.run(MigrationRunOptions(database = "logs"))
migrator.run(
    MigrationRunOptions(
        only = setOf("M0001_01_01_000000_create_users_table")
    )
)

migrator.rollback()
migrator.rollback(MigrationRollbackOptions(steps = 1))
migrator.status()
migrator.status(MigrationStatusOptions(database = "logs"))
```

## Ejecutar Los Comandos CLI Desde Codigo

Las clases de comando del kernel también pueden invocarse programáticamente:

```kotlin
import kernel.command.CommandInput
import kernel.command.commands.MigrateCommand
import kernel.command.commands.MigrateRollbackCommand
import kernel.command.commands.MigrateStatusCommand

val migrate = MigrateCommand(migrator::run)
val rollback = MigrateRollbackCommand(migrator::rollback)
val status = MigrateStatusCommand(migrator::status)
```

Ejemplos:

```kotlin
val migrateResult = migrate.execute(
    CommandInput(
        name = "migrate",
        arguments = emptyList(),
        options = mapOf("database" to "logs"),
        workingDirectory = app.basePath
    )
)

val rollbackResult = rollback.execute(
    CommandInput(
        name = "migrate:rollback",
        arguments = emptyList(),
        options = mapOf("step" to "1"),
        workingDirectory = app.basePath
    )
)

val statusResult = status.execute(
    CommandInput(
        name = "migrate:status",
        arguments = emptyList(),
        options = mapOf("only" to "M0001_01_01_000000_create_users_table"),
        workingDirectory = app.basePath
    )
)
```

Precedencia de conexion:

- `migration.connectionName`
- `MigrationRunOptions.database`
- `database.default`

Eso significa que una migracion puede declarar explicitamente su propia
conexion y dejar que el `Migrator` resuelva todo automaticamente.

Ejemplo:

```kotlin
class M2026_04_29_150000_create_audit_logs_table : Migration() {
    override val connectionName: String = "logs"

    override fun up() {
        create("audit_logs") {
            id().primaryKey()
            string("message").notNull()
            timestampsTz()
        }
    }

    override fun down() {
        dropIfExists("audit_logs")
    }
}
```

Comportamiento esperado:

- `./kernel migrate` ejecuta esta migracion en `logs`;
- `./kernel migrate --database=main` tambien la ejecuta en `logs`;
- una migracion que no define `connectionName` usa primero `--database`;
- si no se pasa `--database`, usa `database.default`.

## MariaDB En La Practica

Si una conexion usa `driver = mariadb`, el `Migrator` cambia automaticamente a
la gramática MariaDB para esa ejecucion.

Ejemplo:

```kotlin
val migrator = Migrator(
    repository = JdbcMigrationRepository(database),
    resolver = database,
    registry = registry
)

migrator.run(MigrationRunOptions(database = "logs"))
```

Con una configuracion como:

```text
database.default = main
database.connections.main.driver = pgsql
database.connections.logs.driver = mariadb
```

Asi puedes tener una app con PostgreSQL como conexion principal y MariaDB como
conexion secundaria, igual que en Laravel.

## Generar Stubs

Tambien puedes generar el codigo base de una migracion antes de guardarlo en
disco:

```kotlin
val factory = MigrationStubFactory()

val stub = factory.create(
    MigrationStubRequest(
        template = MigrationStubTemplate.CREATE_TABLE,
        tableName = "users"
    )
)

println(stub.fileName)
println(stub.source)
```

Templates disponibles:

- `BLANK`: migracion vacia con `up` y `down`.
- `CREATE_TABLE`: crea una tabla y agrega `dropIfExists` en `down`.
- `UPDATE_TABLE`: deja un bloque `table("...")` listo para editar.
- `DROP_TABLE`: genera `dropIfExists` en `up` y una restauracion basica en `down`.

## Schemas Y Extensiones

```kotlin
override fun up() {
    createSchema("pos")
    createExtension("pgcrypto")
    createExtension("btree_gist", schema = "public")
}

override fun down() {
    dropExtension("btree_gist")
    dropExtension("pgcrypto")
    dropSchema("pos")
}
```

SQL:

```sql
CREATE SCHEMA IF NOT EXISTS pos;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
```

Tambien puedes renombrar schemas:

```kotlin
renameSchema("pos", "point_of_sale")
dropSchema("pos", ifExists = true, cascade = true)
dropExtension("pgcrypto", ifExists = true, cascade = true)
createExtension("postgis", version = "3.5.0")
```

## Crear Tablas

```kotlin
create("products") {
    id().primaryKey()
    string("sku", 64).notNull().unique()
    text("description")
    numeric("price", 12, 2).notNull().default(0)
    boolean("active").notNull().default(true)
    timestampsTz()
}
```

`create` usa `CREATE TABLE IF NOT EXISTS` por defecto. Puedes desactivarlo:

```kotlin
create("products", ifNotExists = false) {
    id().primaryKey()
}
```

`createTable` es el alias descriptivo equivalente:

```kotlin
createTable("products") {
    id().primaryKey()
}
```

Para llaves primarias compuestas usa `primaryKey` dentro de `create`:

```kotlin
create("order_items") {
    uuid("order_id")
    uuid("product_id")
    int("quantity")
    primaryKey("order_id", "product_id")
}
```

## Eliminar Tablas

```kotlin
dropIfExists("products")
```

SQL:

```sql
DROP TABLE IF EXISTS products;
```

Para eliminar sin `IF EXISTS`:

```kotlin
drop("products")
```

Tambien existe el alias descriptivo:

```kotlin
dropTable("products", ifExists = true)
dropTable("products", ifExists = false)
```

## Renombrar Tablas

```kotlin
rename("users", "customers")
```

SQL:

```sql
ALTER TABLE users RENAME TO customers;
```

Alias descriptivo:

```kotlin
renameTable("users", "customers")
```

## Modificar Tablas

Usa `table`, igual que `Schema::table` en Laravel:

```kotlin
table("users") {
    string("phone", 30)
    renameColumn("name", "full_name")
    dropColumn("legacy_code")
}
```

SQL:

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE users RENAME COLUMN name TO full_name;
ALTER TABLE users DROP COLUMN IF EXISTS legacy_code;
```

## Tipos De Columnas

Los helpers estan basados en la documentacion oficial de PostgreSQL, capitulo
8: Data Types.

### Identificadores Y Relaciones

`id(name = "id")`: `UUID NOT NULL`.

`id().primaryKey()`: `UUID NOT NULL` y `PRIMARY KEY (id)`.

`uuid("id")`: `UUID`.

`foreignUuid("company_id")`: `UUID`.

`foreignId("user_id")`: `BIGINT`.

`increments("id")`: `SERIAL NOT NULL`.

`smallIncrements("id")`: `SMALLSERIAL NOT NULL`.

`bigIncrements("id")`: `BIGSERIAL NOT NULL`.

### Numericos

`smallInt("quantity")` o `smallInteger("quantity")`: `SMALLINT`.

`int("quantity")` o `integer("quantity")`: `INTEGER`.

`bigInt("external_id")`: `BIGINT`.

`smallSerial("position")`: `SMALLSERIAL`.

`serial("position")`: `SERIAL`.

`bigSerial("position")`: `BIGSERIAL`.

`numeric("price", 12, 2)` o `decimal("price", 12, 2)`: `NUMERIC(12, 2)`.

`real("ratio")`: `REAL`.

`doublePrecision("score")`: `DOUBLE PRECISION`.

`money("amount")`: `MONEY`.

### Caracter Y Texto

`char("code", 36)` o `character("code", 36)`: `CHAR(36)`.

`string("name")`: `VARCHAR(255)`.

`string("name", 100)`, `varchar("name", 100)` o
`characterVarying("name", 100)`: `VARCHAR(100)`.

`text("description")`: `TEXT`.

### Booleanos, Binarios Y Bits

`boolean("active")`: `BOOLEAN`.

`bytea("payload")` o `binary("payload")`: `BYTEA`.

`bit("flag")`: `BIT`.

`bit("flag", 1)`: `BIT(1)`.

`bitVarying("flags", 8)` o `varbit("flags", 8)`: `BIT VARYING(8)`.

### Fecha Y Hora

`date("birth_date")`: `DATE`.

`time("starts_at")`: `TIME`.

`time("starts_at", precision = 3)`: `TIME(3)`.

`timeTz("starts_at")`: `TIMETZ`.

`timestamp("created_at")`: `TIMESTAMP`.

`timestamp("created_at", precision = 6)`: `TIMESTAMP(6)`.

`timestampTz("created_at")`: `TIMESTAMPTZ`.

`timestampTz("created_at", precision = 6)`: `TIMESTAMPTZ(6)`.

`interval("duration")`: `INTERVAL`.

`interval("duration", fields = "DAY TO SECOND", precision = 3)`:
`INTERVAL DAY TO SECOND(3)`.

### JSON, XML Y Arrays

`json("raw_metadata")`: `JSON`.

`jsonb("metadata")`: `JSONB`.

`xml("document")`: `XML`.

`array("tags", "TEXT")`: `TEXT[]`.

`array("prices", "NUMERIC(12, 2)")`: `NUMERIC(12, 2)[]`.

### Red

`inet("ip_address")`: `INET`.

`cidr("network")`: `CIDR`.

`macaddr("mac")`: `MACADDR`.

`macaddr8("mac8")`: `MACADDR8`.

### Geometricos

`point("location")`: `POINT`.

`line("line_shape")`: `LINE`.

`lseg("segment")`: `LSEG`.

`box("bounds")`: `BOX`.

`path("route")`: `PATH`.

`polygon("area")`: `POLYGON`.

`circle("radius_area")`: `CIRCLE`.

### Busqueda De Texto Y Tipos Internos Utiles

`tsvector("search_vector")`: `TSVECTOR`.

`tsquery("search_query")`: `TSQUERY`.

`pgLsn("log_sequence")`: `PG_LSN`.

`pgSnapshot("snapshot_id")`: `PG_SNAPSHOT`.

`txidSnapshot("snapshot_id")`: `TXID_SNAPSHOT`.

### Rangos Y Multirangos

`int4Range("range")`: `INT4RANGE`.

`int8Range("range")`: `INT8RANGE`.

`numRange("range")`: `NUMRANGE`.

`tsRange("range")`: `TSRANGE`.

`tstzRange("range")`: `TSTZRANGE`.

`dateRange("range")`: `DATERANGE`.

`int4MultiRange("ranges")`: `INT4MULTIRANGE`.

`int8MultiRange("ranges")`: `INT8MULTIRANGE`.

`numMultiRange("ranges")`: `NUMMULTIRANGE`.

`tsMultiRange("ranges")`: `TSMULTIRANGE`.

`tstzMultiRange("ranges")`: `TSTZMULTIRANGE`.

`dateMultiRange("ranges")`: `DATEMULTIRANGE`.

### ENUM Y Tipos Personalizados

`enumColumn("status", "order_status")`: columna basada en un tipo enum nativo.

`custom("status", "order_status")`: tipo PostgreSQL manual.

## Helpers Tipo Laravel

```kotlin
timestamps()
timestampsTz()
softDeletes()
softDeletesTz()
```

`timestamps()` agrega `created_at` y `updated_at` como `TIMESTAMP`.

`timestampsTz()` agrega `created_at` y `updated_at` como `TIMESTAMPTZ`.

`softDeletes()` agrega `deleted_at` como `TIMESTAMP`.

`softDeletesTz()` agrega `deleted_at` como `TIMESTAMPTZ`.

## Modificadores De Columnas

```kotlin
string("email").notNull().unique()
id().primaryKey()
boolean("active").notNull().default(true)
numeric("price", 12, 2).notNull().default(0)
string("role", 30).default("admin")
timestampTz("created_at").useCurrent()
uuid("id").generatedUuid()
jsonb("metadata").notNull().defaultRaw("'{}'::jsonb")
```

`default("text")` escapa comillas simples.

`defaultRaw("SQL")` no escapa el valor; usalo para expresiones PostgreSQL.

`primaryKey()` y `primary()` marcan la columna como llave primaria.

`generatedUuid()` usa `gen_random_uuid()`. Para eso PostgreSQL necesita la
extension `pgcrypto`.

Alias disponibles:

```kotlin
timestampTz("created_at").defaultCurrentTimestamp()
uuid("id").defaultGeneratedUuid()
id().primary()
```

## Identity Y Columnas Generadas

PostgreSQL soporta columnas identity y columnas generadas almacenadas.

```kotlin
create("orders") {
    bigInt("ticket_number").generatedByDefaultAsIdentity()
    numeric("subtotal", 12, 2).notNull()
    numeric("tax", 12, 2).notNull()
    numeric("total", 12, 2).storedAs("subtotal + tax")
}
```

SQL:

```sql
ticket_number BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL
total NUMERIC(12, 2) GENERATED ALWAYS AS (subtotal + tax) STORED
```

Tambien existe:

```kotlin
bigInt("ticket_number").generatedAlwaysAsIdentity()
```

## Agregar Columnas

Sintaxis recomendada:

```kotlin
table("users") {
    string("phone", 30)
}
```

SQL:

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
```

Tambien existe la forma directa:

```kotlin
addColumn("users") {
    string("phone", 30)
}

addColumn("users", ifNotExists = false) {
    string("phone", 30)
}
```

## Eliminar Columnas

```kotlin
table("users") {
    dropColumn("phone")
}
```

Para eliminar varias:

```kotlin
table("users") {
    dropColumn("phone", "legacy_code")
}
```

Para usar `CASCADE`:

```kotlin
table("users") {
    dropColumn("phone", cascade = true)
}
```

Forma directa:

```kotlin
dropColumn("users", "phone")
dropColumn("users", "phone", ifExists = false, cascade = true)
```

## Renombrar Columnas

```kotlin
table("users") {
    renameColumn("name", "full_name")
}
```

SQL:

```sql
ALTER TABLE users RENAME COLUMN name TO full_name;
```

Forma directa:

```kotlin
renameColumn("users", "name", "full_name")
```

## Foreign Keys Y Constraints

Dentro de `create`:

```kotlin
create("orders") {
    id().primaryKey()
    foreignUuid("customer_id").notNull()
    numeric("total", 12, 2).notNull()
    unique("store_id", "number")
    check("orders_total_check", "total >= 0")
    foreign("customer_id").references("id").on("customers").cascadeOnDelete()
}
```

SQL:

```sql
CONSTRAINT orders_store_id_number_unique UNIQUE (store_id, number)
CONSTRAINT orders_total_check CHECK (total >= 0)
CONSTRAINT orders_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
```

Sobre una tabla existente:

```kotlin
table("orders") {
    foreign("customer_id").constrained("customers").cascadeOnUpdate().restrictOnDelete()
    uniqueConstraint("store_id", "number", name = "orders_store_number_unique")
    check("orders_total_check", "total >= 0")
    renameConstraint("orders_total_check", "orders_total_positive_check")
    dropConstraint("orders_legacy_check")
}
```

Acciones referenciales soportadas:

```kotlin
cascadeOnDelete()
restrictOnDelete()
nullOnDelete()
defaultOnDelete()
noActionOnDelete()
cascadeOnUpdate()
restrictOnUpdate()
nullOnUpdate()
defaultOnUpdate()
noActionOnUpdate()
```

Tambien puedes usar acciones manuales si respetan PostgreSQL:

```kotlin
foreign("customer_id").references("id").on("customers").onDelete("SET NULL")
foreign("customer_id").references("id").on("customers").onUpdate("CASCADE")
```

Constraints `EXCLUDE`:

```kotlin
table("reservations") {
    exclude(
        name = "reservations_room_during_exclude",
        using = "gist",
        "room_id WITH =",
        "during WITH &&"
    )
}
```

`foreign`, `check`, `unique` y `exclude` tambien se pueden usar dentro de
`create(...)`; `dropConstraint` y `renameConstraint` aplican sobre tablas
existentes o como helpers directos:

```kotlin
dropConstraint("orders", "orders_total_check")
renameConstraint("orders", "orders_total_check", "orders_total_positive_check")
```

## Cambiar Tipo De Columna

```kotlin
table("users") {
    changeColumnType(
        column = "age",
        type = "BIGINT",
        usingExpression = "age::bigint"
    )
}
```

SQL:

```sql
ALTER TABLE users ALTER COLUMN age TYPE BIGINT USING age::bigint;
```

Si PostgreSQL puede convertir automaticamente:

```kotlin
table("users") {
    changeColumnType("age", "BIGINT")
}
```

Forma directa:

```kotlin
alterColumnType("users", "age", "BIGINT")
alterColumnType("users", "age", "BIGINT", usingExpression = "age::bigint")
```

## Defaults Y Nullability

```kotlin
table("users") {
    setDefault("active", "TRUE")
    dropDefault("active")
    setNotNull("email")
    dropNotNull("nickname")
}
```

SQL:

```sql
ALTER TABLE users ALTER COLUMN active SET DEFAULT TRUE;
ALTER TABLE users ALTER COLUMN active DROP DEFAULT;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ALTER COLUMN nickname DROP NOT NULL;
```

`setDefault` recibe SQL crudo. Para texto incluye comillas:

```kotlin
table("users") {
    setDefault("role", "'customer'")
}
```

Formas directas:

```kotlin
setColumnDefault("users", "active", "TRUE")
dropColumnDefault("users", "active")
setColumnNotNull("users", "email")
dropColumnNotNull("users", "nickname")
```

## Indices

Indice simple:

```kotlin
table("users") {
    index("email")
}
```

SQL:

```sql
CREATE INDEX IF NOT EXISTS users_email_index ON users (email);
```

Indice unico:

```kotlin
table("users") {
    unique("email")
}
```

SQL:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS users_email_unique ON users (email);
```

Indice compuesto:

```kotlin
table("orders") {
    index("customer_id", "created_at")
}
```

SQL:

```sql
CREATE INDEX IF NOT EXISTS orders_customer_id_created_at_index ON orders (customer_id, created_at);
```

Indice concurrente:

```kotlin
table("orders") {
    index("customer_id", "created_at", concurrently = true)
}
```

Indice parcial:

```kotlin
table("orders") {
    index("status", where = "deleted_at IS NULL")
}
```

Indice con metodo PostgreSQL:

```kotlin
table("orders") {
    gin("metadata", name = "orders_metadata_gin", where = "metadata IS NOT NULL")
    gist("during", name = "orders_during_gist")
    brin("created_at", name = "orders_created_at_brin")
}
```

Indice por expresion:

```kotlin
table("orders") {
    indexExpression(
        name = "orders_lower_code_index",
        expression = "lower(code)",
        include = listOf("id"),
        where = "deleted_at IS NULL"
    )
}
```

SQL:

```sql
CREATE INDEX IF NOT EXISTS orders_lower_code_index ON orders (lower(code)) INCLUDE (id) WHERE deleted_at IS NULL;
```

Eliminar indice:

```kotlin
table("users") {
    dropIndex("users_email_index")
}
```

Forma directa:

```kotlin
createIndex(
    "orders_status_index",
    "orders",
    "status",
    using = "btree",
    include = listOf("id"),
    where = "deleted_at IS NULL"
)

dropIndex("orders_status_index", concurrently = true)
```

Nota multi-db:

- dentro de `table("...")`, `dropIndex(...)` ya conoce la tabla y funciona bien
  en PostgreSQL y MariaDB;
- si llamas `dropIndex(...)` de forma directa y la migracion puede correr sobre
  MariaDB, indica tambien `table = "orders"` para que el kernel pueda renderizar
  `DROP INDEX ... ON orders`.

## ENUM Nativo De PostgreSQL

PostgreSQL si maneja enums nativos. El flujo recomendado es crear primero el
tipo y luego usarlo en una columna con `enumColumn`.

```kotlin
override fun up() {
    createEnum("order_status", "pending", "paid", "cancelled")

    create("orders") {
        id().primaryKey()
        enumColumn("status", "order_status").notNull().default("pending")
    }
}

override fun down() {
    dropIfExists("orders")
    dropEnum("order_status")
}
```

SQL:

```sql
CREATE TYPE order_status AS ENUM ('pending', 'paid', 'cancelled');

CREATE TABLE IF NOT EXISTS orders (
    id UUID NOT NULL,
    status order_status NOT NULL DEFAULT 'pending',
    PRIMARY KEY (id)
);
```

Agregar un valor:

```kotlin
addEnumValue("order_status", "refunded")
addEnumValue("order_status", "refunded", after = "paid")
addEnumValue("order_status", "draft", before = "pending")
```

Renombrar un valor:

```kotlin
renameEnumValue("order_status", "cancelled", "voided")
```

Renombrar el tipo:

```kotlin
renameEnum("order_status", "purchase_status")
```

Eliminar el tipo:

```kotlin
dropEnum("order_status")
```

Opciones:

```kotlin
dropEnum("order_status", ifExists = false)
addEnumValue("order_status", "refunded", ifNotExists = false)
```

Nota importante: PostgreSQL no permite eliminar valores individuales de un ENUM
de forma directa. Para quitar valores normalmente se crea un nuevo tipo, se
convierte la columna y se elimina el tipo anterior.

## Domains

PostgreSQL permite crear domains para encapsular reglas reutilizables.

```kotlin
override fun up() {
    createDomain(
        name = "positive_money",
        type = "NUMERIC(12, 2)",
        notNull = true,
        defaultExpression = "0",
        checkExpression = "VALUE >= 0"
    )
}

override fun down() {
    dropDomain("positive_money", ifExists = true, cascade = true)
}
```

SQL:

```sql
CREATE DOMAIN positive_money AS NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (VALUE >= 0);
```

## Views Y Materialized Views

```kotlin
createView(
    "pos.open_orders",
    "SELECT * FROM pos.orders WHERE paid_at IS NULL",
    orReplace = true
)

createMaterializedView(
    "pos.daily_sales",
    "SELECT current_date AS sold_on, 0::numeric AS total",
    ifNotExists = true,
    withData = false
)

refreshMaterializedView("pos.daily_sales", concurrently = true, withData = true)
```

Rollback:

```kotlin
dropMaterializedView("pos.daily_sales", ifExists = true, cascade = false)
dropView("pos.open_orders", ifExists = true, cascade = false)
```

## Sequences

```kotlin
createSequence(
    "pos.ticket_numbers",
    ifNotExists = true,
    startWith = 1000,
    incrementBy = 1,
    minValue = 1,
    maxValue = 999999,
    cache = 10,
    cycle = false,
    ownedBy = "pos.orders.ticket_number"
)
```

SQL:

```sql
CREATE SEQUENCE IF NOT EXISTS pos.ticket_numbers INCREMENT BY 1 START WITH 1000 CACHE 10 OWNED BY pos.orders.ticket_number;
```

Tambien puedes usar:

```kotlin
dropSequence("pos.ticket_numbers", ifExists = true, cascade = true)
renameSequence("old_ticket_numbers", "ticket_numbers")
```

## Comentarios

```kotlin
commentOnTable("pos.orders", "POS orders")
commentOnColumn("pos.orders", "total", "Generated order total")
```

Para eliminar un comentario:

```kotlin
commentOnColumn("pos.orders", "total", null)
```

## Functions Y Triggers

```kotlin
createFunction(
    name = "pos.touch_updated_at",
    arguments = "",
    returns = "trigger",
    language = "plpgsql",
    body = """
        BEGIN
            NEW.updated_at = CURRENT_TIMESTAMP;
            RETURN NEW;
        END
    """.trimIndent(),
    orReplace = true
)

createTrigger(
    name = "orders_touch_updated_at",
    table = "pos.orders",
    timing = "before",
    "update",
    function = "pos.touch_updated_at()",
    forEach = "ROW",
    whenExpression = null
)
```

Rollback:

```kotlin
dropTrigger("orders_touch_updated_at", "pos.orders", ifExists = true, cascade = false)
dropFunction("pos.touch_updated_at()", ifExists = true, cascade = false)
```

Eventos soportados: `insert`, `update`, `delete`, `truncate`.

Timings soportados: `before`, `after`, `instead of`.

`forEach` puede ser `ROW` o `STATEMENT`.

## SQL Manual

Usa `statement` para extensiones, constraints, llaves foraneas, triggers,
funciones o cualquier operacion PostgreSQL que todavia no tenga helper.

```kotlin
override fun up() {
    statement("CREATE EXTENSION IF NOT EXISTS pgcrypto;")
    statement("ALTER TABLE orders ADD CONSTRAINT orders_user_id_foreign FOREIGN KEY (user_id) REFERENCES users(id);")
}

override fun down() {
    statement("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_user_id_foreign;")
}
```

## Equivalencias Con Laravel

Laravel `Schema::create(...)`: usa `create(...)`.

Laravel `Schema::table(...)`: usa `table(...)`.

Laravel `Schema::dropIfExists(...)`: usa `dropIfExists(...)`.

Laravel `Schema::rename(...)`: usa `rename(...)`.

Laravel `$table->string(...)`: usa `string(...)`.

Laravel `$table->timestampsTz()`: usa `timestampsTz()`.

Laravel `$table->softDeletesTz()`: usa `softDeletesTz()`.

Laravel `$table->index(...)`: usa `index(...)`.

Laravel `$table->unique(...)`: usa `unique(...)`.

Laravel `$table->foreign(...)`: usa `foreign(...)`.

Laravel `$table->check(...)`: usa `check(...)`.

## API Directa Y Compatibilidad

La sintaxis recomendada es estilo Laravel (`create`, `table`,
`dropIfExists`). Tambien existen metodos descriptivos equivalentes para usar el
DSL desde codigo mas explicito o adaptadores internos.

Crear/eliminar/renombrar tablas:

```kotlin
createTable("users") { id().primaryKey() }
dropTable("users", ifExists = true)
renameTable("users", "customers")
```

Columnas:

```kotlin
addColumn("users") { string("phone") }
dropColumn("users", "phone", ifExists = true, cascade = false)
renameColumn("users", "name", "full_name")
alterColumnType("users", "age", "BIGINT", usingExpression = "age::bigint")
setColumnDefault("users", "active", "TRUE")
dropColumnDefault("users", "active")
setColumnNotNull("users", "email")
dropColumnNotNull("users", "nickname")
```

Constraints e indices:

```kotlin
dropConstraint("orders", "orders_total_check")
renameConstraint("orders", "orders_total_check", "orders_total_positive_check")
createIndex("orders_status_index", "orders", "status", where = "deleted_at IS NULL")
dropIndex("orders_status_index", concurrently = true)
```

## Validaciones

El DSL valida nombres `snake_case` de tablas, columnas, indices y tipos.

El DSL rechaza columnas duplicadas dentro de `create`.

El DSL rechaza primary keys que apunten a columnas inexistentes.

El DSL rechaza `NUMERIC(precision, scale)` cuando `scale` es mayor que
`precision`.

El DSL rechaza indices sin columnas.

El DSL rechaza enums sin valores o con valores repetidos.

El DSL rechaza `addEnumValue` con `before` y `after` al mismo tiempo.

El DSL rechaza foreign keys sin tabla referenciada.

El DSL rechaza columnas generadas con `DEFAULT`.

El DSL rechaza triggers sin eventos, eventos no soportados o `forEach` distinto
de `ROW`/`STATEMENT`.

El DSL permite nombres calificados para relaciones como `pos.orders`, validando
cada segmento como identificador PostgreSQL.

## Metodos Soportados

Schemas y extensiones: `createSchema`, `dropSchema`, `renameSchema`,
`createExtension`, `dropExtension`.

Tablas: `create`, `drop`, `dropIfExists`, `rename`, `table`.

Columnas: `id`, `uuid`, `foreignUuid`, `foreignId`, `increments`,
`smallIncrements`, `bigIncrements`, `smallInt`, `smallInteger`, `int`,
`integer`, `bigInt`, `smallSerial`, `serial`, `bigSerial`, `numeric`,
`decimal`, `real`, `doublePrecision`, `money`, `char`, `character`, `string`,
`varchar`, `characterVarying`, `text`, `boolean`, `bytea`, `binary`, `bit`,
`bitVarying`, `varbit`, `date`, `time`, `timeTz`, `timestamp`, `timestampTz`,
`interval`, `json`, `jsonb`, `xml`, `array`, `inet`, `cidr`, `macaddr`,
`macaddr8`, `point`, `line`, `lseg`, `box`, `path`, `polygon`, `circle`,
`tsvector`, `tsquery`, `pgLsn`, `pgSnapshot`, `txidSnapshot`, `int4Range`,
`int8Range`, `numRange`, `tsRange`, `tstzRange`, `dateRange`,
`int4MultiRange`, `int8MultiRange`, `numMultiRange`, `tsMultiRange`,
`tstzMultiRange`, `dateMultiRange`, `enumColumn`, `custom`.

Helpers: `timestamps`, `timestampsTz`, `softDeletes`, `softDeletesTz`.

Modificadores: `notNull`, `nullable`, `unique`, `default`, `defaultRaw`,
`useCurrent`, `generatedUuid`, `primaryKey`, `primary`, `storedAs`,
`generatedAlwaysAsIdentity`, `generatedByDefaultAsIdentity`.

Alter table: `dropColumn`, `renameColumn`, `changeColumnType`, `setDefault`,
`dropDefault`, `setNotNull`, `dropNotNull`, `foreign`, `check`,
`uniqueConstraint`, `exclude`, `dropConstraint`, `renameConstraint`.

Indices: `index`, `unique`, `gin`, `gist`, `brin`, `indexExpression`,
`dropIndex`.

Enums: `createEnum`, `dropEnum`, `addEnumValue`, `renameEnumValue`,
`renameEnum`.

Domains: `createDomain`, `dropDomain`.

Views: `createView`, `dropView`, `createMaterializedView`,
`dropMaterializedView`, `refreshMaterializedView`.

Sequences: `createSequence`, `dropSequence`, `renameSequence`.

Comentarios: `commentOnTable`, `commentOnColumn`.

Functions y triggers: `createFunction`, `dropFunction`, `createTrigger`,
`dropTrigger`.

SQL manual: `statement`.

## Pruebas

Pruebas unitarias:

```bash
./gradlew test --tests "kernel.database.migrations.MigrationSqlGeneratorTest"
./gradlew test --tests "kernel.database.migrations.MigrationValidationTest"
```

Prueba de integracion del flujo completo del DSL:

```bash
./gradlew test --tests "kernel.database.migrations.MigrationIntegrationTest"
```
