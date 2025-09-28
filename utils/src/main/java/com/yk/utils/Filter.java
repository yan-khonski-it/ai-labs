package com.yk.utils;

import java.util.Set;

/**
 * Default hard-coded filter to exclude files and directories from the code analysis.
 */
public class Filter {

  private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
      ".idea",
      ".git",
      "test",
      "target",
      "TMP",
      ".DS_Store",
      "__pycache__",
      "virtual_environment",
      ".settings",
      ".github",
      "circleci",
      "documents",
      "blackbox-test",
      "component-test"
      ,"packages-info",
      "releases-info"
      // add more if needed
  );

  private static final Set<String> EXCLUDED_FILES = Set.of(
      ".gitignore",
      ".gitattributes",
      "README.md",
      "LICENSE",
      "CODEOWNERS",
      ".flattened-pom.xml",
      "Jenkinsfile",
      "CHANGES",
      "CHANGELOG",
      "codecov.yml",
      "install-jdk.sh",
      "KEYS.txt",
      "publish.sh",
      ".DS_Store"
  );

  private static final Set<String> EXCLUDED_FILENAME_EXTENSIONS = Set.of(
      "jpg",
      "png",
      "jar",
      "dat",
      "data",
      "bin",
      "keystore",
      "key",
      "crt",
      "p12",
      "gz",
      "jsonl"
  );

  public static boolean isFileExcluded(String fileName) {
    if(EXCLUDED_FILES.contains(fileName)) {
      return true;
    }

    String filenameExtension = getFilenameExtension(fileName);
    if (filenameExtension.isBlank()) {
      return false;
    }

    return EXCLUDED_FILENAME_EXTENSIONS.contains(filenameExtension);
  }

  public static boolean isDirectoryExcluded(String dir) {
    return EXCLUDED_DIRECTORIES.contains(dir);
  }

  private static String getFilenameExtension(String filename) {
    int index = filename.lastIndexOf('.');
    if (index == -1) {
      return "";
    }

    return filename.substring(index + 1);
  }
}
