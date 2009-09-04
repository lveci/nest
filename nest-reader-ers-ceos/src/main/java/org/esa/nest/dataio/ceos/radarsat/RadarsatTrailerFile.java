package org.esa.nest.dataio.ceos.radarsat;

import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CEOSLeaderFile;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;


class RadarsatTrailerFile extends CEOSLeaderFile {

    private final static String mission = "radarsat";
    private final static String trailer_recordDefinitionFile = "trailer_file.xml";

    public RadarsatTrailerFile(final ImageInputStream stream) throws IOException, IllegalBinaryFormatException {
        super(stream, mission, trailer_recordDefinitionFile);

    }
}