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
    -server -Xmx4096M -XX:CompileThreshold=100 -Xverify:none \
    -XX:+AggressiveOpts -XX:+UseFastAccessorMethods -Xconcurrentio \
    -Dceres.context=nest \
    "-Dnest.mainClass=org.esa.beam.framework.gpf.main.Main" \
    "-Dnest.home=$NEST_HOME" \
	"-Dnest.debug=false" \
    "-Dncsa.hdf.hdflib.HDFLibrary.hdflib=$NEST_HOME/libjhdf.so" \
    "-Dncsa.hdf.hdf5lib.H5.hdf5lib=$NEST_HOME/libjhdf5.so" \
    -jar $NEST_HOME/bin/ceres-launcher.jar "$@"

exit 0
