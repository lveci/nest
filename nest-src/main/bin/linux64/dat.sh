#! /bin/sh

$NEST_HOME/jre/bin/java \
    -server -Xmx3000M -XX:CompileThreshold=100 -Xverify:none \
    -XX:+AggressiveOpts -XX:+UseFastAccessorMethods -Xconcurrentio \
    -Dceres.context=nest \
    -Dnest.debug=false \
    -Djava.library.path=$PATH:$NEST_HOME \
    -jar $NEST_HOME/bin/ceres-launcher.jar

exit 0


