# argunet2argdown

A simple rudimentary groovy script to convert [Argunet](http://www.argunet.org/) files to [Argdown](https://argdown.org) files.

*Remark*: Note that this script does (so far) not consider all relevant information and fields. Nodes, sketched relations and edges that rely on defined semantic relations are translated to argdown. However, all other information is ignored (e.g., groups, inference rules, colors, etc.).


## Usage

Git clone this repository and execute the script (presupposes an [installed Groovy](https://groovy.apache.org/download.html)):

```bash
groovy -cp ARGUNET2ARGDOWN_DIR/lib/db4o-5.2-java5.jar:ARGUNET2ARGDOWN_DIR/lib/org.argunet.model_1.0.0.jar ARGUNET2ARGDOWN_DIR/argunet2argdown.groovy PATH_TO_ARGUNET_FILE
```

## Java Compatibility Notes

### Java 9+ Module System (JPMS) Restriction

db4o 5.2 uses reflection to access private fields at runtime, which was freely allowed in Java 8 but is blocked by strong encapsulation in Java 9+. Running the script with Java 9 or newer will produce an error like:

```
java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.lang.String java.lang.Enum.name accessible: module java.base does not "opens java.lang" to unnamed module
```

**Fix:** Pass `--add-opens` flags to the JVM via `JAVA_OPTS`:

```bash
JAVA_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED" groovy -cp ...
```

### JAVA_HOME Not Set

Groovy's launcher script uses the presence of `javac` to locate `JAVA_HOME`. If only a JRE (not a full JDK) is in `PATH`, the script fails with:

```
groovy: JAVA_HOME not set and cannot find javac to deduce location, please set JAVA_HOME.
```

**Fix:** Set `JAVA_HOME` explicitly, e.g.:

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk groovy -cp ...
```

### Combined Fix (Java 9+ with JRE-only in PATH)

```bash
JAVA_HOME=/usr/lib/jvm/java-26-openjdk \
JAVA_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED" \
groovy -cp ARGUNET2ARGDOWN_DIR/lib/db4o-5.2-java5.jar:ARGUNET2ARGDOWN_DIR/lib/org.argunet.model_1.0.0.jar ARGUNET2ARGDOWN_DIR/argunet2argdown.groovy PATH_TO_ARGUNET_FILE
```

Adjust `JAVA_HOME` to match your JDK installation path (find it with `find /usr/lib/jvm -name "javac"`). You may also set these variables permanently in your shell config (e.g. `~/.bashrc`).
