About NEST Version 3C

The Next ESA SAR Toolbox (NEST) will be used for reading, post-processing, analyzing and visualizing the large archive of data from present and planned ESA SAR missions including Sentinel-1, as well as third party SAR-data from JERS SAR, ALOS PALSAR, TerraSAR-X and Radarsat-2. NEST will help the remote sensing community by handling ESA SAR products and complimenting existing commercial packages.

NEST will include the functionality of the Basic Envisat SAR Toolbox (BEST) and will reuse and strive
to be compatible with the Basic ERS & Envisat (A)ATSR and MERIS (BEAM) Toolbox.

NEST is extensible with an API that allows users to easily create their own plugin Readers, Processors and Writers.
Developer workshops and tutorials will be planned to actively encourage contributions by the SAR community.
If you are interested in developing a reader or writer for a product please contact us.



Installation
* Install J2SE 1.6 JRE 
* Install Java Advanced Imaging JRE 
* Download the latest NEST build (www.array.ca/nest/), unzip the binaries files and run the DAT application 


Building NEST from the source

1. Download and install the required build tools
* Install J2SE 1.6 JDK and set JAVA_HOME accordingly. 
* Install Maven and set MAVEN_HOME accordingly. 
* Install Java Advanced Imaging JDK & JRE 
* Install JAI Image I/O JDK & JRE 
2. Add $JAVA_HOME/bin, $MAVEN_HOME/bin to your PATH.

3. Download the NEST source code and unpack into $MY_PROJECTS/nest.
4. Copy 3rd-party folder found in nest-developer-tools\maven-dependencies into your Maven repository, which is by default ~/.m2/repository.
5. Cd into $MY_PROJECTS/nest
6. Build NEST from source: Type mvn compile or mvn package
7. Open project in the your IDE. IntelliJ IDEA users:

    * To build IDEA project files for NEST: Type mvn idea:idea
    * In IDEA, go to the IDEA Main Menu/File/Open Project and simply open the created project file $MY_PROJECTS/nest/nest.ipr

8. Open project in the your IDE. Eclipse users:

    * To build Eclipse project files for NEST: Type mvn eclipse:eclipse
    * Make sure that M2_REPO classpath variable is set:

        1. Open Window/Preferences..., then select Java/Build Path/Classpath Variables
        2. Select New... and add variable M2_REPO
        3. Select Folder... and choose the location of your Maven local repository, e.g ~/.m2/repository


    * Click Main Menu/File/Import
    * Select General/Existing Project into Workspace
    * Select Root Directory $MY_PROJECTS/nest
    * Click Finish


9. Use the following configuration to run DAT:

    * Main class: com.bc.ceres.launcher.Launcher
    * VM parameters: -Xmx1024M -Dceres.context=nest
    * Program parameters: none
    * Working directory: $MY_PROJECTS/nest/beam
    * Use classpath of module (project in Eclipse): nest-bootstrap


Enjoy!

