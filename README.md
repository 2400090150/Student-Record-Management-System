# Student Record Management System

A small full-stack web application to add, view, and delete student records.
Built with plain HTML/CSS for the frontend, core Java for the backend
(no frameworks), and SQLite (via JDBC) for storage.

## Tech Stack
- **Frontend:** HTML, CSS
- **Backend:** Java (`com.sun.net.httpserver.HttpServer` — no external web framework)
- **Database:** SQLite, accessed through JDBC (`PreparedStatement`, `ResultSet`)

## Features
- View all student records in a table
- Add a new student via a form (name, roll number, department, year, email)
- Prevents duplicate roll numbers (shows an error banner instead of crashing)
- Delete a student record
- Styled, responsive-ish UI via a separate CSS file

## Project Structure
```
StudentManagementSystem/
├── src/Main.java       # HTTP server + all backend logic
├── web/index.html      # HTML template (form + table)
├── web/style.css       # Styling
├── schema.sql          # Database schema reference
├── sqlite-jdbc.jar     # SQLite JDBC driver
├── slf4j-api.jar       # Logging dependency required by the driver
└── slf4j-nop.jar       # No-op logging backend (silences logger warnings)
```

## How to Run
Requires a JDK (Java 11+) installed on your machine.

```bash
# 1. Compile
javac -d out -cp sqlite-jdbc.jar src/Main.java

# 2. Run
java -cp "out:sqlite-jdbc.jar:slf4j-api.jar:slf4j-nop.jar" Main
# On Windows, use ; instead of : in the classpath:
# java -cp "out;sqlite-jdbc.jar;slf4j-api.jar;slf4j-nop.jar" Main

# 3. Open in browser
http://localhost:8080
```

A file `students.db` will be created automatically in the folder you run it
from — that's your SQLite database.

## How It Works (for your own understanding)
1. **Startup:** `Main.main()` loads the SQLite JDBC driver and runs a
   `CREATE TABLE IF NOT EXISTS` statement so the database is ready on first run.
2. **Routing:** `HttpServer` maps URL paths to handler classes:
   - `GET /` → `HomeHandler` reads all rows with `SELECT * FROM students` and
     injects them into `index.html` as table rows.
   - `POST /add` → `AddHandler` reads the submitted form fields and runs an
     `INSERT` using a `PreparedStatement` (this avoids SQL injection — never
     build SQL by concatenating strings).
   - `GET /delete?id=X` → `DeleteHandler` runs a `DELETE ... WHERE id = ?`.
   - `GET /style.css` → serves the stylesheet.
3. **Duplicate handling:** the `roll_no` column has a `UNIQUE` constraint;
   if an insert violates it, SQLite throws an exception which the handler
   catches and redirects back with `?error=duplicate`.

## Suggested Resume Bullet Points
> **Student Record Management System** | Java, HTML/CSS, SQL (SQLite/JDBC)
> - Built a full-stack CRUD web application from scratch using core Java's
>   built-in HTTP server, without external frameworks, to manage student records.
> - Designed a relational schema and used JDBC `PreparedStatement`s for safe,
>   parameterized database queries, including uniqueness validation on roll numbers.
> - Implemented server-side HTML templating and a responsive CSS interface for
>   adding, viewing, and deleting records.

## Likely Interview Questions (and what to say)
- **"Why did you use PreparedStatement instead of Statement?"**
  → Prevents SQL injection by separating SQL code from user input; also lets
  the driver reuse the compiled query plan.
- **"How does your server handle different URLs?"**
  → `HttpServer.createContext()` registers a path with a handler class that
  implements `HttpHandler`; each handler's `handle()` method processes the
  request and writes the response.
- **"What happens if two people submit the form at the same time?"**
  → Good follow-up point to be honest about: this demo opens a new JDBC
  connection per request and doesn't use connection pooling or transactions,
  which would matter at real scale. Mentioning this shows you understand the
  limits of your own project — which interviewers respect.
- **"Why SQLite and not MySQL?"**
  → SQLite needs no separate server process, making the project simple to
  run anywhere. The included `schema.sql` and SQL syntax used are portable
  to MySQL/PostgreSQL with minor tweaks (e.g., `AUTO_INCREMENT` vs
  `AUTOINCREMENT`).

## Possible Extensions (mention these to show initiative)
- Add an "Edit" feature (`PUT`/`POST /edit`)
- Add search/filter by department or year
- Switch to MySQL and deploy with a connection pool (e.g., HikariCP)
- Add basic authentication for an admin-only delete
