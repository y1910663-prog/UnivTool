package app.univtool.ui.panel2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import app.univtool.model.Course;
import app.univtool.ui.panel2.SelectNewFileTypeDialog.NewType;

public class FileSaveOps {
	public static void createNewFileByType(Path target, NewType type) throws IOException {
	    Files.createDirectories(target.getParent());

	    switch (type) {
	        case WORD -> {
	            try (XWPFDocument doc = new XWPFDocument();
	                 OutputStream os = Files.newOutputStream(target)) {
	                XWPFParagraph p = doc.createParagraph();
	                XWPFRun run = p.createRun();
	                run.setText("");
	                doc.write(os);
	            }
	        }
	        case EXCEL -> {
	            try (XSSFWorkbook wb = new XSSFWorkbook();
	                 OutputStream os = Files.newOutputStream(target)) {
	                wb.write(os);
	            }
	        }
	        case POWERPOINT -> {
	            try (XMLSlideShow ppt = new XMLSlideShow();
	                 OutputStream os = Files.newOutputStream(target)) {
	                XSLFSlide slide = ppt.createSlide();
	                XSLFTextBox box = slide.createTextBox();
	                XSLFTextParagraph para = box.addNewTextParagraph();
	                XSLFTextRun tr = para.addNewTextRun();
	                tr.setText("");
	                ppt.write(os);
	            }
	        }
	        case TEXT -> {
	            Files.writeString(target, "", StandardCharsets.UTF_8);
	        }
	        case MARKDOWN -> {
	            String tpl = "# 新規ドキュメント\n\n" +
	                         "- 作成日時: " + java.time.LocalDateTime.now() + "\n";
	            Files.writeString(target, tpl, StandardCharsets.UTF_8);
	        }
	        case RTF -> {
	            String rtf = """
	                         {\\rtf1\\ansi\\deff0
	                         {\\fonttbl{\\f0 Arial;}}
	                         \\fs24
	                         \\par
	                         }
	                         """;
	            Files.writeString(target, rtf, StandardCharsets.UTF_8);
	        }
	        default -> throw new IOException("Unsupported NewType: " + type);
	    }
	}


    public static String resolveCourseFolderName(Course c) {
        if (c.folder != null && !c.folder.isBlank()) return c.folder;
        return sanitize(c.name);
    }

    public static String buildFolderNameFromId(Course c) {
        String idPart = String.format("[%04d]", c.id == null ? 0 : c.id);
        String title = sanitize(c.name);
        String year = (c.year == null ? "" : "_" + c.year);
        return idPart + title + year;
    }

    public static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }
    public static Path ensureUnique(Path dir, String filename) {
        Path p = dir.resolve(filename);
        if (!java.nio.file.Files.exists(p)) return p;

        String name = filename;
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            name = filename.substring(0, dot);
            ext  = filename.substring(dot);
        }
        int i = 1;
        while (true) {
            Path cand = dir.resolve(String.format("%s (%d)%s", name, i, ext));
            if (!java.nio.file.Files.exists(cand)) return cand;
            i++;
        }
    }
}