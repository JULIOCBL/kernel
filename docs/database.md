# Capa: Database

Esta capa gestiona la persistencia de datos, desde el Query Builder fluido hasta las migraciones y seeders.

## Punto de Entrada (`DB`)

El objeto `DB` es la fachada principal para interactuar con la base de datos.

### Consultas Básicas
```kotlin
// Acceso a una tabla específica
DB.table("users").get()

// Acceso a una conexión específica
DB.connection("logs").table("api_logs").insert(mapOf("event" to "login"))
```

### Transacciones
El framework soporta transacciones asíncronas con propagación automática.
```kotlin
DB.transaction {
    DB.table("accounts").where("id", "=", 1).decrement("balance", 100)
    DB.table("transfers").insert(mapOf("from" to 1, "to" to 2, "amount" to 100))
    // Si algo falla aquí, se hace ROLLBACK automático
}
```

---

## Query Builder

Permite construir consultas SQL de forma fluida y segura contra inyecciones SQL.

### Selección y Filtros
- `select("col1", "col2")`: Elige qué columnas traer.
- `where(col, op, val)`: Filtro básico (e.g., `where("age", ">", 18)`).
- `orWhere(col, op, val)`: Filtro con lógica OR.
- `where { ... }` / `orWhere { ... }`: Agrupación de filtros (paréntesis en SQL).
- `whereIn(col, list)` / `orWhereIn(col, list)`: Filtro de conjunto.
- `whereNotIn(col, list)` / `orWhereNotIn(col, list)`: Filtro de exclusión de conjunto.
- `whereNull(col)` / `orWhereNull(col)`: Filtros de nulidad.
- `whereNotNull(col)` / `orWhereNotNull(col)`: Filtros de no-nulidad.

### Joins
- `join(table, left, op, right)`: INNER JOIN.
- `leftJoin(table, left, op, right)`: LEFT JOIN.

### Orden y Límite
- `orderBy(col, dir)` / `orderByDesc(col)`.
- `latest(col)` / `oldest(col)`.
- `reorder()`: Elimina todos los `orderBy` previos.
- `limit(n)` / `offset(n)`.

### Soft Deletes (Si el modelo lo soporta)
- `withTrashed()`: Incluye registros borrados.
- `onlyTrashed()`: Trae solo registros borrados.
- `withoutTrashed()`: Filtra registros borrados (comportamiento por defecto).

### Operaciones Terminales (Suspend)
- `get()`: Ejecuta y devuelve una lista de resultados.
- `first()`: Devuelve el primer resultado o null.
- `insert(map)`: Inserta un registro.
- `update(map)`: Actualiza registros (requiere un `where`).
- `delete()`: Elimina registros (requiere un `where`). Si el modelo tiene soft delete, hace un `UPDATE`.
- `upsert(records, uniqueBy, updateColumns)`: Inserta o actualiza masivamente en conflicto.

---

## Migraciones

Permiten definir el esquema de la base de datos como código.

### Estructura de una Migración
```kotlin
class CreateUsersTable : Migration() {
    override fun up() {
        create("users") {
            id() // UUID por defecto
            string("email").unique()
            string("password")
            boolean("is_active").default(true)
            timestamps() // created_at y updated_at
        }
    }

    override fun down() {
        dropIfExists("users")
    }
}
```

### Tipos de Columna Comunes
- `id()`, `uuid()`, `increments()`.
- `string(name, length)`, `text(name)`.
- `int(name)`, `bigInt(name)`, `decimal(name, precision, scale)`.
- `boolean(name)`.
- `date(name)`, `timestamp(name)`, `dateTime(name)`.
- `json(name)`.

### Modificadores
- `.nullable()` / `.notNull()`.
- `.unique()`.
- `.default(value)`.
- `.primary()`.

---

## Seeders

Pueblan la base de datos con datos iniciales o de prueba.

```kotlin
class UserSeeder(app: Application) : Seeder(app) {
    override suspend fun run() {
        DB.table("users").insert(mapOf(
            "email" to "admin@example.com",
            "password" to "secret"
        ))
    }
}
```
