<p style="text-align:left">
  <img src="https://www.kanopus.cl/assets/kanopus_black.png" width="220" alt="Kanopus logo"/>
</p>

![Maven](https://img.shields.io/maven-central/v/cl.kanopus.util/klib-deploy-sql) ![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue) ![Java](https://img.shields.io/badge/java-17+-orange)

# klib-deploy-sql

**Klib Deploy SQL** is a lightweight Java library that simplifies connecting to multiple databases and executing SQL
scripts in a consistent and automated way.  
It is designed to support deployment, migration, and initialization workflows across different relational database
engines.

---

## ✨ Features

- 🔗 **Multi-database support**  
  Works with PostgreSQL, Oracle, SQL Server, DB2, and other JDBC-compatible databases.

- 📜 **Script execution**  
  Run raw SQL scripts or batches in a reliable and consistent way.

- 🔁 **One-time & repeatable scripts**  
  Mark scripts as `onetime="true"` to run them only once, or `onetime="false"` to run them on every deployment.

- 🗂️ **Automatic schema management**  
  Automatically creates internal tracking tables (`CATALOG_SCRIPT_SQL` and `CATALOG_SCRIPT_SQL_EXECUTION`) to
  record which scripts have been executed, when, and with what result.

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
	<version>4.05.1</version>
</dependency>
```

Add the corresponding JDBC driver for your database (example for PostgreSQL):

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
		</scripts>
	</database>

</catalog>

```

```java
import cl.kanopus.deploysql.DeploySQL;

public class DeployExample {

    static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/mydb";
        String user = "postgres";
        String password = "secret";

        DeploySQL deploy = new DeploySQL(user, password, url);
        deploy.execute("./catalog.xml");
    }
}
```

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
