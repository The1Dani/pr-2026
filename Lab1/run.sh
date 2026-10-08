#!/usr/bin/env bash

# Use the same JDK as IntelliJ (project SDK: corretto-27); override with JAVA_HOME
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/corretto-27}"

"$JAVA_HOME/bin/java" -cp target/Lab1*.jar com.experiment.Main "$@"
