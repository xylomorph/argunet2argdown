# argunet2argdown

A simple rudimentary groovy script to convert [Argunet](http://www.argunet.org/) files to [Argdown](https://argdown.org) files.

*Remark*: Note that this script does (so far) not consider all relevant information and fields. Nodes, sketched relations and edges that rely on defined semantic relations are translated to argdown. However, all other information is ignored (e.g., groups, inference rules, colors, etc.).


## Usage

Git clone this repository and execute the script (presupposes an [installed Groovy](https://groovy.apache.org/download.html)):

```bash
groovy -cp ARGUNET2ARGDOWN_DIR/lib/db4o-5.2-java5.jar:ARGUNET2ARGDOWN_DIR/lib/org.argunet.model_1.0.0.jar ARGUNET2ARGDOWN_DIR/argunet2argdown.groovy PATH_TO_ARGUNET_FILE
```
