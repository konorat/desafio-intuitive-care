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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class Main {
    public static void main(String[] args) throws IOException {

        String url = "https://www.gov.br/ans/pt-br/acesso-a-informacao/participacao-da-sociedade/atualizacao-do-rol-de-procedimentos";
        String dir = System.getProperty("user.dir") + "\\src\\files\\";

        findFilesInWebPage(url,dir);
        zipDirectory(dir, dir + String.valueOf(Instant.now().toEpochMilli())+ "_files.zip");

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

}
