#@ File (label="Landmark file") landmarksPath
#@ File (label="Input points (csv)") inCsv
#@ File (label="Output points (csv)") outCsv
#@ String (label="Transformation type", choices={"Thin Plate Spline", "Affine", "Similarity", "Rotation", "Translation"}) transformType
#@ Double (label="Full-resolution microns per pixel", value=0.22009446422471257) fullresMicronsPerPixel
#@ Integer (label="Bin size", value=2) binSize

import java.nio.file.*
import java.io.*
import java.util.*
import bigwarp.landmarks.*
import bigwarp.transforms.*
import net.imglib2.realtransform.*

def buildTransform(File landmarksPath, String transformType, int nd) {
    def ltm = new LandmarkTableModel(nd)
    try {
        ltm.load(landmarksPath)
    } catch (IOException e) {
        e.printStackTrace()
        return null
    }

    def bwTransform = new BigWarpTransform(ltm, transformType)
    def xfm = bwTransform.getTransformation()

    if (xfm instanceof Wrapped2DTransformAs3D)
        xfm = ((Wrapped2DTransformAs3D) xfm).getTransform()

    return xfm.inverse()
}

// Validate bin_size
if (binSize < 2) {
    System.err.println("Error: bin_size must be 2 or greater.")
    return
}

// Read input CSV
List<String> lines
try {
    lines = Files.readAllLines(Paths.get(inCsv.getAbsolutePath()))
} catch (IOException e) {
    e.printStackTrace()
    return
}

// Parse header and find column indices
String header = lines[0]
List<String> columns = header.split(",")

int colIndex = columns.indexOf("array_col")
int rowIndex = columns.indexOf("array_row")
int pxlColIndex = columns.indexOf("pxl_col_in_fullres")
int pxlRowIndex = columns.indexOf("pxl_row_in_fullres")

if ([colIndex, rowIndex, pxlColIndex, pxlRowIndex].contains(-1)) {
    System.err.println("Error: Missing required columns in CSV.")
    return
}

// Get transformation
def transform = buildTransform(landmarksPath, transformType, 2)
if (transform == null) return

List<String> outputLines = [header] // Store new CSV content
double[] result = new double[2]

// Transform each row
for (int i = 1; i < lines.size(); i++) {
    List<String> values = lines[i].split(",") as List

    try {
        double x = Double.parseDouble(values[colIndex])
        double y = Double.parseDouble(values[rowIndex])

        // Apply binning logic
        if (binSize > 2) {
            x *= (binSize / 2.0)
            y *= (binSize / 2.0)
        }

        double[] point = [x, y] as double[]
        transform.apply(point, result)

        values[pxlColIndex] = (result[0] / fullresMicronsPerPixel).toString()
        values[pxlRowIndex] = (result[1] / fullresMicronsPerPixel).toString()
    } catch (Exception e) {
        System.err.println("Warning: Failed to transform " + values)
        values[pxlColIndex] = "NaN"
        values[pxlRowIndex] = "NaN"
    }

    outputLines.add(values.join(","))
}

// Write output CSV
try {
    Files.write(Paths.get(outCsv.getAbsolutePath()), outputLines)
} catch (IOException e) {
    e.printStackTrace()
}
