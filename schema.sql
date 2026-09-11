-- Student Record Management System
-- Database schema (SQLite / MySQL compatible with minor type tweaks)

CREATE TABLE IF NOT EXISTS students (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    roll_no     TEXT NOT NULL UNIQUE,
    department  TEXT NOT NULL,
    year        INTEGER NOT NULL,
    email       TEXT NOT NULL
);

-- Example queries used by the application:
-- INSERT INTO students (name, roll_no, department, year, email) VALUES (?, ?, ?, ?, ?);
-- SELECT * FROM students ORDER BY id DESC;
-- DELETE FROM students WHERE id = ?;
