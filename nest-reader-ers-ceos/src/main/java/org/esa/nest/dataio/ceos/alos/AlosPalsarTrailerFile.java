package org.esa.nest.dataio.ceos.alos;

import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CEOSLeaderFile;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;


class AlosPalsarTrailerFile extends CEOSLeaderFile {

    private final static String trailer_recordDefinitionFile = "trailer_file.xml";

    public AlosPalsarTrailerFile(final ImageInputStream stream) throws IOException, IllegalBinaryFormatException {
        super(stream, AlosPalsarConstants.MISSION, trailer_recordDefinitionFile);

    }
}