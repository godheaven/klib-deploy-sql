![Logo](https://www.kanopus.cl/admin/javax.faces.resource/images/logo-gray.png.xhtml?ln=paradise-layout)

[![Maven Central](https://img.shields.io/maven-central/v/cl.kanopus.util/klib-deploy-sql.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/cl.kanopus.util/klib-deploy-sql)

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
	<version>3.58.0</version>
</dependency>
```

---

## 🛠️ Usage Example

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

## Authors

- [@pabloandres.diazsaavedra](https://www.linkedin.com/in/pablo-diaz-saavedra-4b7b0522/)

## License

This software is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
I hope you enjoy it.

[![Apache License, Version 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/license/apache-2-0)

## Support

For support, email soporte@kanopus.cl