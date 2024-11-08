# SE-Planet-Exporter-Color-Getter
Used to automate the process of creating the `VoxelInfo` tags for the Planet Exporter mod in Space Engineers.

You will need:
 1. Have Java 8 or above installed. Your system likely comes with Java 8.
 2. To know if you're system is ARM (if you don't know, it almost certainly isn't)
 3. A copy of Space Engineers
 4. A folder containing all your voxel definition files. They must all start with "VoxelMaterials_" and be of the SBC file extension (as the game already prescribes).
 5. The Texconv tool (https://github.com/microsoft/DirectXTex). You want `texconv.exe` (or its ARM equivalent). Anywhere is acceptable, but preferrably treat it like a usual program.
 6. The root folder of your mod's assets, which the texture files in the voxel definitions would point from if Space Engineers read the textures.

How to use:
Download the `VoxelTextureAnalyzer.java` class, as well as `lwjgl-stb.jar`, `lwjgl.jar` and whichever native jars for `lwjgl-stb` and `lwjgl` your system needs.

Type the command `java -classpath <ProgramDirectory>\* VoxelTextureAnalyzer.java <TexConvFile> <VoxelDefinitionsDir> <GameAssetsDir> <ModAssetsDir>` where each in `<>` is the corresponding directory. I recommend using copy as path to get these directories.
