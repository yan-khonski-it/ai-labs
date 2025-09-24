package com.yk.aiagent;

import java.util.regex.Pattern;

/**
 * Removes licence comment at the beginning of the source code file.
 */
public class ContentCleaner {

  // Matches a block comment at the very start (allowing leading whitespace)
  private static final Pattern LEADING_BLOCK =
      Pattern.compile("\\A\\s*/\\*.*?\\*/\\s*", Pattern.DOTALL);

  // Matches one-or-more //-comment lines at the very start (allowing leading whitespace)
  private static final Pattern LEADING_SLASHSLASH =
      Pattern.compile("\\A\\s*(?://.*\\R)+\\s*");

  private ContentCleaner() {}

  /** Strip BOM, then any leading license/header comments and surrounding blank space. */
  public static String stripLeadingCommentHeader(String s) {
    if (s == null || s.isEmpty()) return s;

    // Remove UTF-8 BOM if present
    if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
      s = s.substring(1);
    }

    // Iteratively remove a leading block or // header (plus surrounding whitespace)
    String prev;
    do {
      prev = s;
      s = LEADING_BLOCK.matcher(s).replaceFirst("");
      s = LEADING_SLASHSLASH.matcher(s).replaceFirst("");
    } while (!s.equals(prev));

    // Trim extra blank lines at the very start
    int i = 0;
    while (i < s.length() && (s.charAt(i) == '\r' || s.charAt(i) == '\n' || Character.isWhitespace(s.charAt(i)))) {
      i++;
    }
    return s.substring(i);
  }
}
