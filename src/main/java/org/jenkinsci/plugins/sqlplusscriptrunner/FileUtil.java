package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class FileUtil {

	private static final String DEFAULT_ENCODE = "UTF-8";
	private static final String LAST_CMD_BEFORE_EXIT = ";\n";
	private static final String SQLPLUS_EXIT = "exit;";
	private static final String SQL_TEMP_SCRIPT = "temp-script-";
	private static final String SQL_PREFIX = ".sql";

	public static boolean hasExitCode(File file) {

		boolean found = false;
		try {

			InputStream in = new FileInputStream(file);
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
			BufferedReader br = new BufferedReader(reader);

			String lastLine = "";
			String line;
			while ((line = br.readLine()) != null) {
				if (line.length() >= 5)
					lastLine = line;
			}

			if (lastLine.equalsIgnoreCase(SQLPLUS_EXIT))
				found = true;

			br.close();
			reader.close();
			in.close();
		} catch (IOException exc) {
			System.out.println(exc);
			System.exit(1);
		}
		return found;
	}

	public static void addExit(String content, File file) throws IOException {

		Writer w = null;
		try {

			w = new OutputStreamWriter(new FileOutputStream(file, true), DEFAULT_ENCODE);
			PrintWriter pw = new PrintWriter(w);
			if (content != null)
				pw.println(content);
			pw.println(LAST_CMD_BEFORE_EXIT);
			pw.println(SQLPLUS_EXIT);
			pw.close();

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (w != null)
					w.close();
			} catch (IOException e) {
			}

		}
	}

	public static String createTempScript(String content) {

		String tempFile = "";
		try {

			File file = File.createTempFile(SQL_TEMP_SCRIPT + System.currentTimeMillis(), SQL_PREFIX);

			if (!FileUtil.hasExitCode(file))
				addExit(content, file);

			tempFile = file.getPath();

		} catch (IOException e) {
			e.printStackTrace();
		}
		return tempFile;

	}

	public static boolean findFile(String name, File file) {

		boolean found = false;
		File[] list = file.listFiles();
		if (list != null)
			for (File fil : list) {
				if (fil.isDirectory()) {
					findFile(name, fil);
				} else if (name.equalsIgnoreCase(fil.getName())) {
					found = true;
				}
			}
		return found;
	}

}
