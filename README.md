<p style="text-align:left">
  <img src="https://www.kanopus.cl/assets/kanopus_black.png" width="220" alt="Kanopus logo"/>
</p>

![Maven](https://img.shields.io/maven-central/v/cl.kanopus.util/klib-deploy-sql) ![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue) ![Java](https://img.shields.io/badge/java-21+-orange)

# klib-deploy-sql

**Klib Deploy SQL** is a lightweight Java library that simplifies connecting to multiple databases and executing SQL
scripts in a consistent and automated way.  
It is designed to support deployment, migration, and initialization workflows across different relational database
engines.

---

## ✨ Features

- 🔗 **Multi-database support**  
  Works with PostgreSQL, Oracle, SQL Server and DB2 (the engine is detected from the JDBC url).

- 📜 **Script execution**  
  Run raw SQL scripts or batches in a reliable and consistent way.

- 🔁 **One-time & repeatable scripts**  
  Mark scripts as `onetime="true"` to run them only once, or `onetime="false"` to run them on every deployment.

- 🗂️ **Automatic schema management**  
  Automatically creates internal tracking tables (`CATALOG_SCRIPT_SQL`, `CATALOG_SCRIPT_SQL_EXECUTION` and
  `CATALOG_SCRIPT_SQL_LOCK`) to record which scripts have been executed, when, and with what result.

- 🛑 **Fail fast, transactional**  
  Each script runs in its own transaction and the run stops at the first failure (both configurable).

- 🔒 **Deploy lock**  
  A lock row prevents two deployments from running against the same database at the same time.

- ✅ **Execution tracking**  
  Every script execution is recorded with its status (`SUCCESS` / `ERROR`), execution time, and error message if
  applicable.

- ⚙️ **Automation ready**  
  Useful for database initialization, deployment pipelines, and CI/CD integration.

- 🧩 **Framework-agnostic**  
  Lightweight design, minimal dependencies. Can be used standalone or within existing applications.

- 🔍 **XML validation**  
  The `catalog.xml` file is validated against an XSD schema before execution, ensuring correctness of the
  configuration.

---

## 🚀 Installation

Add the dependency to your `pom.xml`:

```xml

<dependency>
	<groupId>cl.kanopus.util</groupId>
	<artifactId>klib-deploy-sql</artifactId>
	<version>4.20.0</version>
</dependency>
```

Add the corresponding JDBC driver for your database (`org.postgresql:postgresql`, `com.oracle.database.jdbc:ojdbc11`,
`com.microsoft.sqlserver:mssql-jdbc` or `com.ibm.db2:jcc`; example for PostgreSQL):

```xml

<dependency>
	<groupId>org.postgresql</groupId>
	<artifactId>postgresql</artifactId>
</dependency>
```

---

## 🚀 Usage Guide

catalog.xml

```xml 
<?xml version="1.0" encoding="UTF-8"?>
<catalog>

	<database>
		<label>KANOPUS-LOCAL</label>
		<scripts>
			<script onetime="true" type="DATA" label="test1">your_scripts_folder/test1.sql</script>
			<script onetime="false" type="DATA" label="test2">your_scripts_folder/test2.sql</script>
			<script onetime="false" type="FUNCTION" label="fn">your_scripts_folder/my_function.sql</script>
		</scripts>
	</database>

</catalog>

```

- `onetime="true"`: executed until it succeeds once, then skipped. `onetime="false"`: executed on every run.
- `type`: free text stored in the catalog (`DATA`, `TABLE`, ...). `FUNCTION` enables a special mode for function
  bodies: every `'` is escaped as `''` and every `$BODY$` marker is replaced by `'`, so the body is sent as a quoted
  string.
- Script paths are resolved against the working directory. The catalog itself is looked up at the given path and,
  as fallbacks, `catalog.xml`, `./catalog.xml` and `../catalog.xml` (also on the classpath).
- Scripts and the catalog are read as UTF-8.

```java
import cl.kanopus.deploysql.DeployResult;
import cl.kanopus.deploysql.DeploySQL;

public class DeployExample {

    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/mydb";
        String user = "postgres";
        String password = "secret";

        DeploySQL deploy = new DeploySQL(user, password, url);
        DeployResult result = deploy.deploy("./catalog.xml"); // throws DeployException if a script fails
        System.out.printf("executed=%d skipped=%d failed=%d%n", result.getExecuted(), result.getSkipped(), result.getFailed());
    }
}
```

### Options

| Setter | Default | Description |
|---|---|---|
| `setContinueOnError(boolean)` | `false` | `false`: stop at the first failing script and throw `DeployException` (its `getResult()` holds the partial result). `true`: record the error, continue, and report failures in the returned `DeployResult`. |
| `setTransactional(boolean)` | `true` | Run every script (plus its `SUCCESS` record) in one transaction: auto-commit off, commit on success, rollback on failure. Disable it for statements that cannot run in a transaction (PostgreSQL `CREATE INDEX CONCURRENTLY`, `VACUUM`, ...) or scripts that manage transactions themselves. Engines that auto-commit DDL (Oracle, some DB2/SQL Server statements) cannot roll those statements back. |
| `setLockEnabled(boolean)` | `true` | Take the deploy lock (a row with `LOCK_ID = 1` in `CATALOG_SCRIPT_SQL_LOCK`) for the whole run. |
| `setLockWaitTimeout(Duration)` | 1 minute | How long to wait for a lock held by another deployment before failing with `DeployLockException`. |
| `setStaleLockTimeout(Duration)` | `null` (never) | Age after which a lock is considered abandoned (e.g. the process was killed) and is broken automatically. Ages are computed with the clocks of the deploying JVMs. |

If a deployment was killed and left the lock behind, remove it manually:

```sql
DELETE FROM CATALOG_SCRIPT_SQL_LOCK;
```

### Tracking tables

| Table | Content |
|---|---|
| `CATALOG_SCRIPT_SQL` | One row per script: `CATALOG_ID`, `OBJECT_TYPE`, `LABEL`, `FILENAME` (script key), `ONE_TIME` (`1`/`0`). |
| `CATALOG_SCRIPT_SQL_EXECUTION` | One row per execution: `CATALOG_ID`, `EXECUTION_DATE` (timestamp), `MILISECONDS`, `STATUS` (`SUCCESS`/`ERROR`), `EXIT_MESSAGE` (database error, truncated to 2500 characters). |
| `CATALOG_SCRIPT_SQL_LOCK` | Deploy lock: `LOCK_ID`, `LOCKED_AT`, `LOCKED_BY`. |

The DDL of each engine lives in the jar under `schema/<engine>/`. Missing tables are created on every run.

---

## 🔄 Changes in 4.20.0

Behavior changes:

- **Fail fast by default.** A failing script now stops the run and `execute(...)`/`deploy(...)` throw
  `DeployException`. Up to 4.19.x the error was only recorded and the run continued; call
  `setContinueOnError(true)` to keep that behavior.
- **One transaction per script** (see `setTransactional`). Previously every statement was auto-committed, so a
  script failing halfway left partial changes.
- **Deploy lock.** A new table `CATALOG_SCRIPT_SQL_LOCK` is created and a lock row is taken during the run.
- **Script key = path.** Scripts are tracked by their normalized path as declared in the catalog (absolute paths
  under the catalog directory are made relative to it), upper-cased, instead of only the upper-case base filename.
  Two scripts with the same name in different folders are no longer confused. Rows written by older versions (keyed
  by base name) are recognized and re-keyed automatically, so already executed one-time scripts are not run again.
- **Tracking errors are no longer swallowed.** If the tracking tables cannot be read or written the run aborts with
  the `SQLException`. A one-time script whose tracking insert failed in the past is no longer skipped forever: the
  decision is based on the catalog row plus a successful execution.
- **The JDBC login timeout** (30 s) is passed as a driver connection property instead of the JVM-global
  `DriverManager.setLoginTimeout`; a timeout set in the url wins. Drivers are discovered through JDBC 4 (no
  `Class.forName`).
- **The JDBC user is no longer logged**, and passwords/secrets in the url are masked in logs.

Fixes:

- DB2: `CATALOG_SCRIPT_SQL_EXECUTION` did not match the INSERT (it had `STATUS`/`ERROR_MESSAGE` and lacked
  `MILISECONDS`/`EXIT_MESSAGE`), so every execution record failed and scripts were skipped forever. Existing DB2
  tables are upgraded automatically (`MILISECONDS` and `EXIT_MESSAGE` columns are added).
- `ONE_TIME` was written inverted when an existing catalog row was updated.
- `NullPointerException` while recording a failure whose exception had no cause; error messages longer than the
  column are truncated instead of failing the insert.
- `EXECUTION_DATE` is created as `TIMESTAMP` (`DATETIME2` on SQL Server) instead of `DATE`, so new installations keep
  the time of the execution. Existing tables are not altered; to keep the time on PostgreSQL run e.g.
  `ALTER TABLE CATALOG_SCRIPT_SQL_EXECUTION ALTER COLUMN EXECUTION_DATE TYPE TIMESTAMP;`
- Scripts (including `FUNCTION` mode) are always read as UTF-8, independently of the platform charset.
- `catalog.xml` parsing is hardened against XXE (DTDs and external entities are rejected).

Dependencies: Java 21, Spring Boot 4.2.0 BOM, `org.glassfish.jaxb:jaxb-runtime` (replaces
`com.sun.xml.bind:jaxb-impl`), Oracle driver `ojdbc11` (replaces `ojdbc8`). JDBC drivers remain `provided`: add the
one for your database.

New API: `DeploySQL.deploy()`, `DeploySQL.deploy(String)`, `DeployResult`, `DeployException`,
`DeployLockException`, `CatalogUtils.getScriptKey(...)`, `CatalogUtils.loadCatalog(File)`, `JdbcUrlUtils.mask(...)`.

---

## 🧪 Building

```bash
mvn verify
```

The tests start PostgreSQL with [Testcontainers](https://testcontainers.com/), so a running Docker daemon is
required; no local database is needed.

---

## 📚 When to use

- Database initialization in new environments.
- Automated migrations as part of CI/CD pipelines.
- Running repeatable deployment scripts across multiple database types.

---

## 👤 Author

⭐**Pablo Andrés Díaz Saavedra** — Founder of **Kanopus – Software Guided by the Stars**⭐

Kanopus is building a constellation of developers creating tools, libraries and platforms that simplify software
engineering.

[GitHub](https://github.com/godheaven) | [LinkedIn](https://www.linkedin.com/in/pablo-diaz-saavedra-4b7b0522/) | [Website](https://kanopus.cl)

## 📄 License

This software is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

[![Apache License, Version 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/license/apache-2-0)

## 🛟 Support

For support or questions contact: 📧 [soporte@kanopus.cl](mailto:soporte@kanopus.cl)
