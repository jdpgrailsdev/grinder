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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;


/**
 * Buffer state.
 *
 * @author Philip Aston
 * @version $Revision$
 */
public final class Buffer {

  /** Buffer type constant. */
  public static final Type HTML_BUFFER = new Type("HTML");

  /** Buffer type constant. */
  public static final Type JAVA_BUFFER = new Type("Java");

  /** Buffer type constant. */
  public static final Type MSDOS_BATCH_BUFFER = new Type("MSDOS batch");

  /** Buffer type constant. */
  public static final Type PROPERTIES_BUFFER = new Type("Properties");

  /** Buffer type constant. */
  public static final Type PYTHON_BUFFER = new Type("Python");

  /** Buffer type constant. */
  public static final Type SHELL_BUFFER = new Type("Shell");

  /** Buffer type constant. */
  public static final Type TEXT_BUFFER = new Type("Text");

  /** Buffer type constant. */
  public static final Type XML_BUFFER = new Type("XML");

  /** Buffer type constant. */
  public static final Type UNKNOWN_BUFFER = new Type("Unknown");

  // Common file types we're likely to work with. Definition must come
  // after type constants are initialised.
  private static final Map s_extensionMap = new HashMap() { {
      put("bash", SHELL_BUFFER);
      put("bat", MSDOS_BATCH_BUFFER);
      put("cmd", MSDOS_BATCH_BUFFER);
      put("csh", SHELL_BUFFER);
      put("htm", HTML_BUFFER);
      put("html", HTML_BUFFER);
      put("java", JAVA_BUFFER);
      put("ksh", SHELL_BUFFER);
      put("properties", PROPERTIES_BUFFER);
      put("py", PYTHON_BUFFER);
      put("sh", SHELL_BUFFER);
      put("text", TEXT_BUFFER);
      put("txt", TEXT_BUFFER);
      put("xml", XML_BUFFER);
    }
  };

  private final TextSource m_textSource;
  private final File m_file;
  private long m_lastModified;
  private int m_savedRevision;

  /**
   * Constructor for buffers with no associated file.
   *
   * @param textSource The text editor.
   */
  public Buffer(TextSource textSource) {
    this(textSource, null);
  }

  /**
   * Constructor for buffers with an associated file.
   *
   * @param textSource The text editor.
   * @param file The file.
   */
  public Buffer(TextSource textSource, File file) {
    m_textSource = textSource;
    m_file = file;

    // Assume dirty.
    m_savedRevision = -1;
    m_lastModified = -1;
  }

  /**
   * Update the text source from the file.
   *
   * @exception EditorException If the file could not be read.
   */
  public void load() throws EditorException {
    // The UI should never call save if there is no associated file,
    // but check anyway.
    if (!hasAssociatedFile()) {
      throw new EditorException(
        "Can't load a buffer that has no associated file");
    }

    final char[] buffer = new char[4096];
    final StringWriter stringWriter = new StringWriter();
    Reader reader = null;

    try {
      reader = new FileReader(m_file);
      int n;

      while ((n = reader.read(buffer)) > 0) {
        stringWriter.write(buffer, 0, n);
      }
    }
    catch (IOException e) {
      throw new EditorException("Could not write file", e);
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          // Oh well.
        }
      }
    }

    m_textSource.setText(stringWriter.toString());
    markNotDirty();
  }

  /**
   * Update the file from the text source.
   *
   * @exception EditorException If the file could not be written to.
   */
  public void save() throws EditorException {
    // The UI should never call save if there is no associated file,
    // but check anyway.
    if (!hasAssociatedFile()) {
      throw new EditorException(
        "Can't save a buffer that has no associated file");
    }

    Writer writer = null;

    try {
      writer = new FileWriter(m_file);
      writer.write(m_textSource.getText());
      markNotDirty();
    }
    catch (IOException e) {
      throw new EditorException("Could not write file", e);
    }
    finally {
      if (writer != null) {
        try {
          writer.close();
        }
        catch (IOException e) {
          // Oh well.
        }
      }
    }
  }

  private void markNotDirty() {
    m_savedRevision = m_textSource.getRevision();

    // We know we have an associated file.
    m_lastModified = m_file.lastModified();
  }

  /**
   * Return whether the buffer's text has been changed since the last
   * save.
   *
   * @return <code>true</code> => the text has changed.
   */
  public boolean isDirty() {
    return m_savedRevision != m_textSource.getRevision();
  }

  /**
   * Return whether the buffer has an associated file.
   *
   * @return <code>true</code> => the buffer has an associated file.
   */
  public boolean hasAssociatedFile() {
    return m_file != null;
  }

  /**
   * Return whether the file has been independently modified since the
   * last save.
   *
   * @return <code>true</code> => the file has changed independently
   * of the buffer.
   */
  public boolean isUpToDate() {
    return !hasAssociatedFile() || m_lastModified == m_file.lastModified();
  }

  /**
   * Get the type of the buffer.
   *
   * @return The buffer's type.
   */
  public Type getType() {

    if (hasAssociatedFile()) {
      final String name = m_file.getName();
      final int lastDot = name.lastIndexOf('.');

      if (lastDot >= 0) {
        final String extension = name.substring(lastDot + 1);
        final Type type = (Type)s_extensionMap.get(extension);

        if (type != null) {
          return type;
        }
      }
    }

    return UNKNOWN_BUFFER;
  }

  /**
   * Buffer type enumeration. Uses default (identity) equality semantics.
   */
  public static final class Type {
    private String m_name;

    private Type(String name) {
      m_name = name;
    }

    /**
     * Useful for debugging.
     *
     * @return Description of the type.
     */
    public String toString() {
      return m_name;
    }
  }
}
