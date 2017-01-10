package org.jenkinsci.plugins.sqlplusscriptrunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class FileUtil {

	private static final String SQLPLUS_EXIT = "exit;";

	public static boolean hasExitCode(File file) {
		boolean found = false;
		try {
			FileReader fr = new FileReader(file);
			BufferedReader br = new BufferedReader(fr);
			String lastLine = "";
			String line;
			while ((line = br.readLine()) != null) {
				if (line.length() >= 5)
					lastLine = line;
			}

			if (lastLine.equalsIgnoreCase(SQLPLUS_EXIT))
				found = true;

			br.close();
			fr.close();
		} catch (IOException exc) {
			System.out.println(exc);
			System.exit(1);
		}
		return found;
	}

}
