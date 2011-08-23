#! /bin/sh

if [ -z "$NEST_HOME" ]; then
    echo
    echo Error: NEST_HOME not found in your environment.
    echo Please set the NEST_HOME variable in your environment to match the
    echo location of the NEST installation
    echo
    exit 2
fi

chmod 755 $NEST_HOME/jre/bin/*

$NEST_HOME/jre/bin/java \
	-server -Xms512M -Xmx3000M -XX:PermSize=512m -XX:MaxPermSize=512m -Xverify:none \
    -XX:+AggressiveOpts -XX:+UseFastAccessorMethods -Xconcurrentio -XX:CompileThreshold=10000 \
    -XX:+UseParallelGC -XX:+UseNUMA -XX:-UseLoopPredicate -XX:+UseStringCache -XX:+UseCompressedStrings \
    -Dceres.context=nest \
    -Dnest.debug=false \
    -Djava.library.path=$PATH:$NEST_HOME \
    -jar $NEST_HOME/bin/ceres-launcher.jar

exit 0


