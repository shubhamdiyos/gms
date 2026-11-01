"""Derive a PostgreSQL baseline from the JPA entity source.

Stage 1 used this to turn ``ARCHITECTURE.md §5.1``'s design from a table of intentions into DDL
that could be executed against column names the *code* chose rather than names a reader
transcribed. Stage 2 supersedes it for the baseline itself -- V1 comes from a live server, which
is a higher authority than a parser over annotations -- and it keeps two jobs the live dump does
not serve: it is how a future migration finds the column a new entity actually creates without
booting anything, and it is the second opinion that catches a V1 transcription error.

    usage: python3 scripts/derive-entity-schema.py > /tmp/derived.sql

Read-only with respect to the repository.

Checked against the live server rather than trusted: run against the current entities it agrees
with the database V1..V5 describe on **38 of 38 tables and 598 of 598 columns**. Two defects were
found by making that comparison and both are fixed inline below, each with a comment naming the
column it was silently eating. What is still approximate:

* Types. ``text`` for every ``String``, ``boolean`` for boxed and primitive alike. The real
  schema says ``character varying(255)`` and PostgreSQL's own boolean, which is what made
  ``WHERE is_current`` fail with "argument of WHERE must be type boolean" until the mapping was
  fixed -- a partial unique index over an ``integer`` column is accepted and then never matches.
* Nullability, entirely ignored. ``nullable = false`` does not reach the output, so this file
  cannot be used to prove a NOT NULL is safe.
* An association with no ``@JoinColumn`` is named ``<field>_id`` by JPA convention. Correct for
  every entity today, not guaranteed for the next one.

So: use it for the *inventory* -- which table, which column -- and check any type or nullability
against ``\\d table`` on the container before writing a migration that depends on it.
"""
import glob
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENT = os.path.join(REPO, "gms_backend", "src", "main", "java", "com", "gms", "model", "entity")


def snake(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def sql_type(java_type):
    if re.search(r"\bString\b", java_type):
        return "text"
    if "Instant" in java_type:
        return "timestamptz"
    if "LocalDate" in java_type:
        return "date"
    if "BigDecimal" in java_type:
        return "numeric"
    if re.search(r"\b[Bb]oolean\b", java_type):
        return "boolean"
    return "integer"


out = []
join_tables = []
collections = []

for path in sorted(glob.glob(os.path.join(ENT, "*.java"))):
    src = re.sub(r"//[^\n]*", "", open(path).read())
    cls = os.path.basename(path)[:-5]
    if cls == "BaseAuditEntity":
        continue
    m = re.search(r"@Table\((.*?)\)", src, re.S)
    if not m:
        continue
    tn = re.search(r'name\s*=\s*"([^"]+)"', m.group(1))
    table = tn.group(1) if tn else snake(cls) + "s"

    cols = []
    # The type character class includes `.`. Without it the match silently fails on a qualified
    # type, and "silently" is the whole problem: `private StatusEnum.Department department;`
    # (Designation.java:53-54) and `private java.math.BigDecimal cost;` (SmsLog.java:56-57) were
    # both annotated, both real columns, and both missing from the derived schema until the dot
    # was added. A generator that drops a column it cannot parse is worse than one that errors.
    for fm in re.finditer(
        r"((?:@\w+(?:\([^;]*?\))?\s*)*)private\s+([\w<>,\s\[\].]+?)\s+(\w+)\s*(?:=[^;]*)?;", src
    ):
        anns, ftype, fname = fm.group(1), fm.group(2), fm.group(3)
        if "@Transient" in anns or "@JsonIgnoreProperties" in anns:
            continue
        if "@Id" in anns and re.search(r"\b(Integer|Long)\b", ftype):
            cols.append(("id", "integer PRIMARY KEY"))
            continue
        cn = re.search(r'@Column\([^)]*name\s*=\s*"([^"]+)"', anns, re.S)
        jc = re.search(r'@JoinColumn\([^)]*name\s*=\s*"([^"]+)"', anns, re.S)
        # Anchored on the opening paren, not `[^)]*` in front of `name`. The greedy form was the
        # Stage 1 bug this line is now a comment about: `[^)]*` happily crossed
        # `@JoinTable(name = "parent_student", joinColumns = @JoinColumn(` because no `)` appears
        # until the nested call closes, and leftmost-longest then reported the *nested* name, so
        # the generator emitted a table called `parent_id`.
        jt = re.search(r'@JoinTable\(\s*name\s*=\s*"([^"]+)"', anns, re.S)
        if "@ElementCollection" in anns:
            col = cn.group(1) if cn else snake(fname)
            ct = re.search(r'@CollectionTable\(\s*name\s*=\s*"([^"]+)"', anns)
            jc2 = re.search(r'@CollectionTable\(.*?@JoinColumn\(\s*name\s*=\s*"([^"]+)"', anns, re.S)
            collections.append((table, fname, col,
                                ct.group(1) if ct else table + "_" + fname,
                                jc2.group(1) if jc2 else table[:-1] + "_id"))
            continue
        if jt:
            join_tables.append((jt.group(1), anns))
            continue
        if jc:
            cols.append((jc.group(1), "integer"))
            continue
        if cn or re.search(r"@Column\b", anns):
            name = cn.group(1) if cn else snake(fname)
            cols.append((name, sql_type(ftype)))
            continue
        if "@OneToMany" in anns or "@ManyToMany" in anns or "mappedBy" in anns:
            continue
        if re.search(r"@ManyToOne|@OneToOne", anns):
            cols.append((snake(fname) + "_id", "integer"))

    if "extends BaseAuditEntity" in src:
        for n, t in (("created_by", "integer"), ("updated_by", "integer"),
                     ("version", "integer"), ("created_on", "timestamptz"),
                     ("updated_on", "timestamptz")):
            if not any(n == c[0] for c in cols):
                cols.append((n, t))

    body = ",\n  ".join(f"{n} {t}" for n, t in cols)
    out.append(f"CREATE TABLE {table} (\n  {body}\n);")

for jt_name, anns in join_tables:
    names = re.findall(r'@(?:Inverse)?JoinColumn\(\s*name\s*=\s*"([^"]+)"', anns)
    if len(names) >= 2:
        out.append(f"CREATE TABLE {jt_name} (\n  {names[0]} integer,\n  {names[1]} integer\n);")

for parent_table, fname, col, t, jcol in collections:
    out.append(f"CREATE TABLE {t} (\n  {jcol} integer,\n  {col} text\n);")

print("\n".join(out))
