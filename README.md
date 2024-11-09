# SE-Planet-Exporter-Color-Getter
Used to automate the process of creating the `VoxelInfo` tags for the Planet Exporter mod in Space Engineers.

You will need:
 1. Have Java 8 or above installed. Your system likely comes with Java 8.
 2. A copy of Space Engineers
 3. A folder containing your planet mod's files. The voxel material definitions must be in `...\Data` and must all start with "VoxelMaterials_" and be of the SBC file extension (as the game already prescribes).
 4. The Texconv tool (https://github.com/microsoft/DirectXTex). You want `texconv.exe` (or its ARM equivalent). Anywhere is acceptable, but preferrably treat it like a usual program.

Running the program is the same as any other jar file:
`java -jar VoxelTextureAnalyzer.jar <TexConvFile> <ModDir> <GameDir>`
