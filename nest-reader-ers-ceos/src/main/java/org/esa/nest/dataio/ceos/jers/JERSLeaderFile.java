package org.esa.nest.dataio.ceos.jers;

import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CEOSLeaderFile;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;


class JERSLeaderFile extends CEOSLeaderFile {

    private final static String mission = "jers";
    private final static String leader_recordDefinitionFile = "leader_file.xml";

    public JERSLeaderFile(final ImageInputStream stream) throws IOException, IllegalBinaryFormatException {
        super(stream, mission, leader_recordDefinitionFile);

    }

}