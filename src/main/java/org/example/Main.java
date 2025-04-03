package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.File;
import java.io.FileWriter;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {

        String url = "https://www.gov.br/ans/pt-br/acesso-a-informacao/participacao-da-sociedade/atualizacao-do-rol-de-procedimentos";
        String dirPdf = System.getProperty("user.dir") + "/src/files/pdf/";
        String dirCsv = System.getProperty("user.dir") + "/src/files/csv/";
        String zipTarget = System.getProperty("user.dir") + "/src/files/";

        findFilesInWebPage(url,dirPdf);
        zipDirectory(dirPdf, zipTarget + String.valueOf(Instant.now().toEpochMilli())+ "_pdf.zip");

        String pdf = findLatestAnexoIFile(dirPdf);

        if (pdf == null) {
            System.out.println("Nenhum arquivo Anexo_I encontrado.");
            return;
        }

        String csvOutput = System.getProperty("user.dir") + "/src/files/csv";
        TableExtractorFromPdf(pdf, csvOutput);
        zipDirectory(dirCsv, zipTarget + String.valueOf(Instant.now().toEpochMilli())+ "_csv.zip");

    }

    private static void findFilesInWebPage(String url, String dir){
        try {
            Document doc = Jsoup.connect(url).get();

            Elements links = doc.select("a[href$=.pdf]");

            for (Element link : links) {
                String pdfUrl = link.absUrl("href");
                String text = link.text().toLowerCase();
                String file = String.valueOf(Instant.now().toEpochMilli())+ "_" + pdfUrl.substring(pdfUrl.lastIndexOf("/") + 1);
                String saveDir = dir + file;

                if (text.contains("anexo i.") || text.contains("anexo ii.")) {
                    downloadFiles(pdfUrl, saveDir);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void downloadFiles(String url, String dir) throws IOException {
        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, Paths.get(dir));
            System.out.println("File in: " + dir);
        }
    }

    private static void zipDirectory(String sourceDir, String zipFile) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path zipFilePath = Paths.get(zipFile);

        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Stream<Path> paths = Files.walk(sourcePath);{
                paths.filter(path -> Files.isRegularFile(path) && !path.equals(zipFilePath))
                        .forEach(path -> addFileToZip(zipOut, sourcePath, path));
            }
        }
        System.out.println("File in: " + zipFile);
    }

    private static void addFileToZip(ZipOutputStream zipOut, Path sourcePath, Path file) {
        try (InputStream fis = Files.newInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(file).toString());
            zipOut.putNextEntry(zipEntry);
            fis.transferTo(zipOut);
            zipOut.closeEntry();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void TableExtractorFromPdf(String sourceDir, String csvOutput) {
        try {
            PDDocument document = PDDocument.load(new File(sourceDir));
            int totalPages = document.getNumberOfPages();

            FileWriter csvWriter = new FileWriter(new File(csvOutput, String.valueOf(Instant.now().toEpochMilli()) + "_output.csv"));

            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();

            csvWriter.write("Procedimento,RN,Vigência,Seg. Odontológica,Seg. Ambulatorial,HCO,HSO,REF,PAC,DUT,Subgrupo,Grupo,Capítulo");
            for (int i = 2; i < totalPages; i++) {
                Page page = new ObjectExtractor(document).extract(i);
                List<Table> tables = sea.extract(page);

                for (Table table : tables) {
                    for (List<RectangularTextContainer> row : table.getRows()) {
                        if (row.isEmpty() || row.get(0).getText().trim().equalsIgnoreCase("PROCEDIMENTO")) {
                            continue;
                        }

                        StringBuilder line = new StringBuilder();
                        for (RectangularTextContainer cell : row) {
                            String cellText = cell.getText().trim()
                                    .replace("\n", " ")
                                    .replace("\r", " ")
                                    .replaceAll("\\s+", " ")
                                    .replace("OD", "Seg. Odontológica")
                                    .replace("AMB", "Seg. Ambulatorial");
                            line.append(cellText).append(",");
                        }
                        csvWriter.write(line.substring(0, line.length() - 1) + "\n");
                    }
                }
            }

            csvWriter.close();
            document.close();
            System.out.println("File in: " + csvOutput);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String findLatestAnexoIFile(String directory) {
        try (Stream<Path> files = Files.list(Paths.get(directory))) {
            Optional<Path> latestFile = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().contains("Anexo_I_"))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()));
            return latestFile.map(Path::toString).orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}