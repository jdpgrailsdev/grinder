// Copyright (C) 2004 Philip Aston
// All rights reserved.
//
// This file is part of The Grinder software distribution. Refer to
// the file LICENSE which is part of The Grinder distribution for
// licensing details. The Grinder distribution is available on the
// Internet at http://grinder.sourceforge.net/
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
// FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
// REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
// HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
// OF THE POSSIBILITY OF SUCH DAMAGE.

package net.grinder.console.model.editor;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

import net.grinder.console.common.DisplayMessageConsoleException;
import net.grinder.console.common.Resources;
import net.grinder.testutility.AbstractFileTestCase;


/**
 * Unit test for {@link Buffer}.
 *
 * @author Philip Aston
 * @version $Revision$
 */
public class TestBuffer extends AbstractFileTestCase {

  private static final Resources s_resources =
      new Resources("net.grinder.console.swingui.resources.Console");

  public void testBufferWithNoFile() throws Exception {
    final String text = "Some text for testing with";

    final Buffer buffer = new Buffer(s_resources, new StringTextSource(text));

    try {
      buffer.load();
      fail("Expected EditorException");
    }
    catch (EditorException e) {
    }

    try {
      buffer.save();
      fail("Expected EditorException");
    }
    catch (EditorException e) {
    }

    // Buffers are born into the world dirty. This one has no file,
    // and so cannot be redeemed.
    assertTrue(buffer.isDirty());
    assertTrue(buffer.isUpToDate());
    assertTrue(!buffer.hasAssociatedFile());

    assertEquals(Buffer.UNKNOWN_BUFFER, buffer.getType());

    assertTrue(buffer.isDirty());
    assertTrue(buffer.isUpToDate());
    assertTrue(!buffer.hasAssociatedFile());
  }

  private static final class Expectation {
    private Buffer.Type m_type;
    private File m_file;

    public Expectation(Buffer.Type type, String filename) {
      m_type = type;
      m_file = new File(filename);
    }

    public Buffer.Type getType() {
      return m_type;
    }

    public File getFile() {
      return m_file;
    }
  }

  private void assertNotEquals(Object o1, Object o2) {
    if (o1 == null) {
      assertNotNull(o2);
    }
    else if (o2 == null) {
      assertNotNull(o1);
    }
    else {
      assertTrue(o1 + " is not equal to " + o2, !o1.equals(o2));
    }
  }

  public void testGetType() throws Exception {
    final TextSource textSource = new StringTextSource("");

    final Expectation[] wordsOfExpectation = {
      new Expectation(Buffer.HTML_BUFFER, "somefile/blah.htm"),
      new Expectation(Buffer.HTML_BUFFER, "foo.html"),
      new Expectation(Buffer.JAVA_BUFFER, "eieio.java"),
      new Expectation(Buffer.MSDOS_BATCH_BUFFER, "eat/my.shorts.bat"),
      new Expectation(Buffer.MSDOS_BATCH_BUFFER, "alpha.cmd"),
      new Expectation(Buffer.PROPERTIES_BUFFER, "essential.properties"),
      new Expectation(Buffer.PYTHON_BUFFER, "why/oh.py"),
      new Expectation(Buffer.SHELL_BUFFER, "bishbosh.bash"),
      new Expectation(Buffer.SHELL_BUFFER, "clishclosh.csh"),
      new Expectation(Buffer.SHELL_BUFFER, "kkkkrassh.ksh"),
      new Expectation(Buffer.SHELL_BUFFER, "be/quiet.sh"),
      new Expectation(Buffer.TEXT_BUFFER, "tick.txt"),
      new Expectation(Buffer.TEXT_BUFFER, "tech.text"),
      new Expectation(Buffer.XML_BUFFER, "xplicitly.xml"),
      new Expectation(Buffer.UNKNOWN_BUFFER, "blurb/blah"),
      new Expectation(Buffer.UNKNOWN_BUFFER, "fidledly.foo"),
      new Expectation(Buffer.UNKNOWN_BUFFER, "bah/bah"),
      new Expectation(Buffer.UNKNOWN_BUFFER, "...."),
    };

    for (int i=0; i<wordsOfExpectation.length; ++i) {
      final Expectation expectation = wordsOfExpectation[i];

      final Buffer buffer = 
        new Buffer(s_resources, textSource, expectation.getFile());

      assertEquals(expectation.getType(), buffer.getType());
    }

    assertEquals(Buffer.HTML_BUFFER, Buffer.HTML_BUFFER);
    assertNotEquals(Buffer.HTML_BUFFER, Buffer.TEXT_BUFFER);
    assertNotEquals(Buffer.TEXT_BUFFER, Buffer.HTML_BUFFER);
    assertNotEquals(Buffer.PROPERTIES_BUFFER, Buffer.UNKNOWN_BUFFER);
    assertEquals(Buffer.PYTHON_BUFFER, Buffer.PYTHON_BUFFER);
  }

  public void testBufferWithAssociatedFile() throws Exception {

    final String s0 =
      "A shield for your eyes\na beast in the well on your hand";

    final String s1 =
      "Catch the mean beast\nin the well in the hell on the back\n" +
      "Watch out! You've got no shield\n" +
      "Break up! He's got no peace";

    final TextSource textSource = new StringTextSource(s0);
    assertSame(s0, textSource.getText());

    final File file = new File(getDirectory(), "myfile.txt");

    final Buffer buffer = new Buffer(s_resources, textSource, file);

    assertEquals(Buffer.TEXT_BUFFER, buffer.getType());
    assertTrue(buffer.isDirty());
    assertTrue(!buffer.isUpToDate());
    assertTrue(buffer.hasAssociatedFile());

    buffer.save();

    assertTrue(!buffer.isDirty());
    assertTrue(buffer.isUpToDate());

    assertSame(s0, textSource.getText());

    textSource.setText(s1);

    assertTrue(buffer.isDirty());
    assertTrue(buffer.isUpToDate());
    assertSame(s1, textSource.getText());

    buffer.load();

    assertTrue(!buffer.isDirty());
    assertTrue(buffer.isUpToDate());
    assertEquals(s0, textSource.getText());
    assertNotSame(s0, textSource.getText());

    file.setLastModified(System.currentTimeMillis() + 1);

    assertTrue(!buffer.isUpToDate());

    buffer.load();

    assertTrue(buffer.isUpToDate());
  }

  public void testBufferWithBadAssociatedFile() throws Exception {

    final TextSource textSource = new StringTextSource("");

    final Buffer buffer = new Buffer(s_resources, textSource, getDirectory());

    try {
      buffer.load();
      fail("Expected DisplayMessageConsoleException");
    }
    catch (DisplayMessageConsoleException e) {
      assertTrue(e.getNestedThrowable() instanceof IOException);
    }

    try {
      buffer.save();
      fail("Expected DisplayMessageConsoleException");
    }
    catch (DisplayMessageConsoleException e) {
      assertTrue(e.getNestedThrowable() instanceof IOException);
    }
  }

  private static final class StringTextSource implements TextSource {

    private String m_text;
    private int m_revision = 0;

    public StringTextSource(String text) {
      m_text = text;
    }

    public String getText() {
      return m_text;
    }

    public void setText(String text) {
      m_text = text;
      ++m_revision;
    }

    public int getRevision() {
      return m_revision;
    }
  }
}
