import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class VoxelTextureAnalyzer {

	private static File texconvFile;

	public static void main(String[] args) {
		if (args.length < 4) {
			System.err.println("Usage: java VoxelTextureAnalyzer <TexConvFile> <VoxelDefinitionsDir> <GameAssetsDir> <ModAssetsDir>");
			System.exit(1);
		}
		texconvFile = new File(args[0]);
		File voxelsDir = new File(args[1]);
		File gameAssets = new File(args[2]);
		File modAssets = new File(args[3]);

		if (!texconvFile.exists() || !voxelsDir.isDirectory() || !gameAssets.isDirectory() || !modAssets.isDirectory()) {
			System.err.println("One or more provided directories are invalid.");
			System.exit(1);
		}

		// Find and process each voxel definition file
		File[] voxelFiles = voxelsDir.listFiles((dir, name) -> name.matches("VoxelMaterials_\\w+\\.sbc"));
		if (voxelFiles == null)
			return;

		for (File voxelFile : voxelFiles) {
			processVoxelFile(voxelFile, gameAssets, modAssets);
		}
	}

	private static void processVoxelFile(File voxelFile, File gameAssets, File modAssets) {
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(voxelFile);
			doc.getDocumentElement().normalize();

			NodeList voxelMaterials = doc.getElementsByTagName("VoxelMaterial");

			for (int i = 0; i < voxelMaterials.getLength(); i++) {
				Node voxelMaterialNode = voxelMaterials.item(i);
				if (voxelMaterialNode.getNodeType() == Node.ELEMENT_NODE) {
					Element voxelMaterial = (Element) voxelMaterialNode;

					// Locate the Id tag and then the SubtypeId within it
					NodeList idList = voxelMaterial.getElementsByTagName("Id");
					String subtypeId = null;
					if (idList.getLength() > 0) {
						Element idElement = (Element) idList.item(0);
						NodeList subtypeList = idElement.getElementsByTagName("SubtypeId");
						if (subtypeList.getLength() > 0) {
							subtypeId = subtypeList.item(0).getTextContent();
						}
					}

					// If SubtypeId wasn't found, skip this VoxelMaterial
					if (subtypeId == null) {
						System.err.println("SubtypeId not found for a VoxelMaterial in file " + voxelFile.getName());
						continue;
					}

					// Locate textures with LOD preference
					String albedoPath = getPreferredTexturePath(voxelMaterial, "ColorMetalXZnY", modAssets, gameAssets);
					String normalPath = getPreferredTexturePath(voxelMaterial, "NormalGlossXZnY", modAssets, gameAssets);
					String additivePath = getPreferredTexturePath(voxelMaterial, "ExtXZnY", modAssets, gameAssets);

					// Calculate texture values or use default values
					float[] rgb = albedoPath != null ? computeAverageRGB(albedoPath) : new float[] { 1.0f, 0.0f, 1.0f };
					float normalAlpha = normalPath != null ? computeAverageAlpha(normalPath) : 0.0f;
					float additiveAlpha = additivePath != null ? computeAverageAlpha(additivePath) : 0.0f;

					// Output the XML structure
					System.out.printf("    <VoxelInfo>%n");
					System.out.printf("      <VoxelName>%s</VoxelName>%n", subtypeId);
					System.out.printf("      <VoxelColor X=\"%.4f\" Y=\"%.4f\" Z=\"%.4f\" />%n", rgb[0], rgb[1], rgb[2]);
					System.out.printf("      <VoxelGloss>%.4f</VoxelGloss>%n", normalAlpha);
					System.out.printf("      <VoxelAdditive>%.4f</VoxelAdditive>%n", additiveAlpha);
					System.out.printf("    </VoxelInfo>%n");
				}
			}
		} catch (Exception e) {
			System.err.println("Error processing file " + voxelFile.getName() + ": " + e.getMessage());
		}
	}

	// Finds the preferred texture path from LOD tags
	private static String getPreferredTexturePath(Element voxelMaterial, String tagName, File modAssets, File gameAssets) {
		String[] lodOptions = { "Far3", "Far2", "Far1", "" };

		for (String lod : lodOptions) {
			NodeList textures = voxelMaterial.getElementsByTagName(tagName + lod);
			if (textures.getLength() > 0) {
				String relativePath = textures.item(0).getTextContent();
				File textureFile = new File(modAssets, relativePath);
				if (!textureFile.exists()) {
					textureFile = new File(gameAssets, relativePath);
				}
				if (textureFile.exists()) {
					return textureFile.getAbsolutePath();
				}
			}
		}
		return null;
	}

	// Computes average RGB values for albedo texture
	private static float[] computeAverageRGB(String path) {
		if (path.endsWith(".dds")) {
			path = convertDdsToPng(path);
			if (path == null)
				return new float[] { 1.0f, 0.0f, 1.0f }; // Default if conversion fails
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer width = stack.mallocInt(1);
			IntBuffer height = stack.mallocInt(1);
			IntBuffer channels = stack.mallocInt(1);

			ByteBuffer image = STBImage.stbi_load(path, width, height, channels, 4);
			if (image == null) {
				System.err.println("Failed to load texture: " + path);
				return new float[] { 1.0f, 0.0f, 1.0f };
			}

			long sumR = 0, sumG = 0, sumB = 0;
			int pixelCount = width.get(0) * height.get(0);

			for (int i = 0; i < pixelCount; i++) {
				sumR += Byte.toUnsignedInt(image.get(i * 4));
				sumG += Byte.toUnsignedInt(image.get(i * 4 + 1));
				sumB += Byte.toUnsignedInt(image.get(i * 4 + 2));
			}
			STBImage.stbi_image_free(image);

			return new float[] { roundToTenThousandth((float) sumR / pixelCount / 255), roundToTenThousandth((float) sumG / pixelCount / 255),
				roundToTenThousandth((float) sumB / pixelCount / 255) };
		}
	}

	// Computes average alpha for normal/additive texture
	private static float computeAverageAlpha(String path) {
		if (path.endsWith(".dds")) {
			path = convertDdsToPng(path);
			if (path == null)
				return 0.0f; // Default if conversion fails
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer width = stack.mallocInt(1);
			IntBuffer height = stack.mallocInt(1);
			IntBuffer channels = stack.mallocInt(1);

			ByteBuffer image = STBImage.stbi_load(path, width, height, channels, 4);
			if (image == null) {
				System.err.println("Failed to load texture: " + path);
				return 0.0f;
			}

			long sumAlpha = 0;
			int pixelCount = width.get(0) * height.get(0);

			for (int i = 0; i < pixelCount; i++) {
				sumAlpha += Byte.toUnsignedInt(image.get(i * 4 + 3)); // Alpha channel
			}
			STBImage.stbi_image_free(image);

			return roundToTenThousandth((float) sumAlpha / pixelCount / 255);
		}
	}

	// Helper method to round to the nearest ten thousandth
	private static float roundToTenThousandth(float value) {
		return Math.round(value * 10000f) / 10000f;
	}

	public static String convertDdsToPng(String ddsFilePath) {
		// Define output directory as "TexturesOut" within the directory of texconv.exe
		File texconvDir = texconvFile.getParentFile();
		File outputDir = new File(texconvDir, "TexturesOut");

		// Create the directory if it does not exist
		if (!outputDir.exists() && !outputDir.mkdirs()) {
			System.err.println("Failed to create output directory: " + outputDir.getAbsolutePath());
			return null;
		}

		// Define the path for the output PNG file in the TexturesOut directory
		String pngFileName = new File(ddsFilePath).getName().replace(".dds", ".png");
		String pngFilePath = new File(outputDir, pngFileName).getAbsolutePath();
		
		ProcessBuilder processBuilder
			= new ProcessBuilder(texconvFile.toString(), "-ft", "png", "-o", outputDir.getAbsolutePath(), ddsFilePath);

		try {
			Process process = processBuilder.start();
			int exitCode = process.waitFor();

			if (exitCode == 0) {
				File pngFile = new File(pngFilePath);
				if (pngFile.exists()) {
					return pngFilePath;
				} else {
					System.err.println("Conversion succeeded but PNG file not found: " + pngFilePath);
					return null;
				}
			} else {
				System.err.println("texconv failed with exit code: " + exitCode);
				return null;
			}
		} catch (IOException | InterruptedException e) {
			System.err.println("Error during DDS to PNG conversion: " + e.getMessage());
			return null;
		}
	}
}
