package com.yk.aiagent;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentCleanerTest {

  @Test
  void removesLeadingBlockCommentHeader() {
    String in = """
        /*
         * License here
         */
        package com.example;
        class A {}""";

    String expected = """
        package com.example;
        class A {}""";

    String out = ContentCleaner.stripLeadingCommentHeader(in);

    assertEquals(expected, out);
    assertTrue(out.startsWith("package com.example;"));
    assertTrue(out.contains("class A {}"));
    assertFalse(out.contains("License here"));
  }

  @Test
  void removesLeadingDoubleSlashHeader() {
    String in = """
        // First line
        // Second line
        package com.example;
        class A {}""";

    String expected = """
        package com.example;
        class A {}""";

    String out = ContentCleaner.stripLeadingCommentHeader(in);

    assertTrue(out.startsWith("package com.example;"));
    assertTrue(out.contains("class A {}"));
    assertFalse(out.contains("First line"));
    assertFalse(out.contains("Second line"));

    assertEquals(expected, out);
  }

  @Test
  void removesStackedHeaders_blockThenSlashSlash() {
    String in = """
        /* Block header */
        // Another header line
        package com.example;
        class A {}""";

    String expected = """
        package com.example;
        class A {}""";
    String out = ContentCleaner.stripLeadingCommentHeader(in);

    assertTrue(out.startsWith("package com.example;"));
    assertFalse(out.contains("Block header"));
    assertFalse(out.contains("Another header line"));

    assertEquals(expected, out);
  }

  @Test
  void preservesNonLeadingComments() {
    String in = """
        package com.example;
        // This comment should stay (not at file start)
        class A {
            /* also should stay */
            void m() {}
        }""";

    String expected = """
        package com.example;
        // This comment should stay (not at file start)
        class A {
            /* also should stay */
            void m() {}
        }""";

    String out = ContentCleaner.stripLeadingCommentHeader(in);

    // Nothing to remove at start
    assertEquals(in, out);
    assertTrue(out.contains("should stay"));
    assertTrue(out.contains("/* also should stay */"));

    assertEquals(expected, out);
  }

  @Test
  void handlesLeadingWhitespaceBeforeHeader() {
    String in = """
        
        /* license */
        package com.example;
        class A {}""";

    String expected = """
        package com.example;
        class A {}""";

    String out = ContentCleaner.stripLeadingCommentHeader(in);

    assertTrue(out.startsWith("package com.example;"));
    assertFalse(out.contains("license"));

    assertEquals(expected, out);
  }

  @Test
  void handlesUtf8Bom_thenHeader() {
    String bom = "\uFEFF";
    String in = bom + """
        /* header */
        package com.example;
        class A {}""";

    String expected ="""
        package com.example;
        class A {}""";

    String out = ContentCleaner.stripLeadingCommentHeader(in);

    assertTrue(out.startsWith("package com.example;"));
    assertFalse(out.contains("header"));
    // BOM removed
    assertFalse(out.startsWith(bom));

    assertEquals(expected, out);
  }

  @Test
  void idempotentWhenCalledTwice() {
    String in = """
        /* header */
        // another
        package com.example;
        class A {}""";

    String once = ContentCleaner.stripLeadingCommentHeader(in);
    String twice = ContentCleaner.stripLeadingCommentHeader(once);

    assertEquals(once, twice);
  }

  @Test
  void noHeader_noChange() {
    String in = """
        package com.example;
        class A {}""";
    String out = ContentCleaner.stripLeadingCommentHeader(in);

    assertEquals(in, out);
  }

  @Test
  void onlyHeader_resultsInEmptyOrWhitespace() {
    String in = """
        /* header only */""";
    String out = ContentCleaner.stripLeadingCommentHeader(in);

    // After stripping header and leading whitespace, nothing meaningful should remain
    assertTrue(out.isEmpty() || out.trim().isEmpty());
  }
}