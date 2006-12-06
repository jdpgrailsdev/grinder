// Copyright (C) 2004, 2005, 2006 Philip Aston
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

package net.grinder.engine.agent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import net.grinder.common.Logger;
import net.grinder.communication.CommunicationException;
import net.grinder.communication.HandlerChainSender.MessageHandler;
import net.grinder.communication.Message;
import net.grinder.engine.common.EngineException;
import net.grinder.engine.messages.ClearCacheMessage;
import net.grinder.engine.messages.DistributeFileMessage;
import net.grinder.util.Directory;
import net.grinder.util.FileContents;
import net.grinder.util.StreamCopier;
import net.grinder.util.thread.UncheckedInterruptedException;


/**
 * ProcessReport {@link ClearCacheMessage}s and {@link
 * DistributeFileMessage}s received from the console.
 *
 * @author Philip Aston
 * @version $Revision$
 */
final class FileStore {

  private final Logger m_logger;

  private final File m_readmeFile;
  private final Directory m_incomingDirectory;
  private final Directory m_currentDirectory;
  private final MessageHandler m_messageHandler;
  private boolean m_incremental;


  public FileStore(File directory, Logger logger) throws FileStoreException {

    final File rootDirectory = directory.getAbsoluteFile();
    m_logger = logger;

    if (rootDirectory.exists()) {
      if (!rootDirectory.isDirectory()) {
        throw new FileStoreException(
          "Could not write to directory '" + rootDirectory +
          "' as file with that name already exists");
      }

      if (!rootDirectory.canWrite()) {
        throw new FileStoreException(
          "Could not write to directory '" + rootDirectory + "'");
      }
    }

    m_readmeFile = new File(rootDirectory, "README.txt");

    try {
      m_incomingDirectory = new Directory(new File(rootDirectory, "incoming"));
      m_currentDirectory = new Directory(new File(rootDirectory, "current"));
    }
    catch (Directory.DirectoryException e) {
      throw new FileStoreException(e.getMessage(), e);
    }

    m_incremental = false;
    
    m_messageHandler = new MessageHandler() {
      public boolean process(Message message) throws CommunicationException {
        if (message instanceof ClearCacheMessage) {
          m_logger.output("Clearing file store");

          try {
            synchronized (m_incomingDirectory) {
              m_incomingDirectory.deleteContents();
            }
          }
          catch (Directory.DirectoryException e) {
            m_logger.error(e.getMessage());
            throw new CommunicationException(e.getMessage(), e);
          }

          m_incremental = false;
          return true;
        }
        else if (message instanceof DistributeFileMessage) {
          try {
            synchronized (m_incomingDirectory) {
              m_incomingDirectory.create();

              createReadmeFile();

              final FileContents fileContents =
                ((DistributeFileMessage)message).getFileContents();

              m_logger.output("Updating file store: " + fileContents);
              fileContents.create(m_incomingDirectory);
            }

            return true;
          }
          catch (FileContents.FileContentsException e) {
            m_logger.error(e.getMessage());
            throw new CommunicationException(e.getMessage(), e);
          }
          catch (Directory.DirectoryException e) {
            m_logger.error(e.getMessage());
            throw new CommunicationException(e.getMessage(), e);
          }
        }

        return false;
      }

      public void shutdown() {
      }
    };
  }

  public Directory getDirectory() throws FileStoreException {
    try {
      synchronized (m_incomingDirectory) {
        if (m_incomingDirectory.getFile().exists()) {
          m_incomingDirectory.copyTo(m_currentDirectory, m_incremental);
        }
      }

      m_incremental = true;

      return m_currentDirectory;
    }
    catch (IOException e) {
      UncheckedInterruptedException.ioException(e);
      throw new FileStoreException("Could not create file store directory", e);
    }
  }

  public MessageHandler getMessageHandler() {
    return m_messageHandler;
  }

  private void createReadmeFile() throws CommunicationException {
    if (!m_readmeFile.exists()) {
      try {
        new StreamCopier(4096, true).
          copy(
            getClass().getResourceAsStream(
              "resources/FileStoreReadme.txt"),
            new FileOutputStream(m_readmeFile));
      }
      catch (IOException e) {
        UncheckedInterruptedException.ioException(e);
        m_logger.error(e.getMessage());
        throw new CommunicationException(e.getMessage(), e);
      }
    }
  }

  /**
   * Exception that indicates a <code>FileStore</code> related
   * problem.
   */
  public static final class FileStoreException extends EngineException {
    FileStoreException(String message) {
      super(message);
    }

    FileStoreException(String message, Throwable e) {
      super(message, e);
    }
  }
}