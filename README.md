# Klib Deploy SQL

**Klib Deploy SQL** is a lightweight Java library that simplifies connecting to multiple databases and executing SQL
scripts in a consistent and automated way.  
It is designed to support deployment, migration, and initialization workflows across different relational database
engines.

---

## ✨ Features

- 🔗 **Multi-database support**  
  Works with PostgreSQL, Oracle, SQL Server, and other JDBC-compatible databases.

- 📜 **Script execution**  
  Run raw SQL scripts or batches in a reliable and consistent way.

- ⚙️ **Automation ready**  
  Useful for database initialization, deployment pipelines, and CI/CD integration.

- 🧩 **Framework-agnostic**  
  Lightweight design, no heavy dependencies. Can be used standalone or within existing applications.

---

## 🚀 Installation

Add the dependency to your `pom.xml`:

```xml

<dependency>
	<groupId>cl.kanopus.util</groupId>
	<artifactId>klib-deploy-sql</artifactId>
	<version>3.0.0</version>
</dependency>
```

---

## 🛠️ Usage Example

```java
import cl.kanopus.deploysql.DeploySQL;

public class DeployExample {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/mydb";
        String user = "postgres";
        String password = "secret";

        DeploySQL deploy = new DeploySQL(user, password, url);
        deploy.execute();
    }
}
```

---

## 📚 When to use

- Database initialization in new environments.
- Automated migrations as part of CI/CD pipelines.
- Running repeatable deployment scripts across multiple database types.

---

## 📄 License

Released under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).  
