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

package net.grinder.console.common;

import java.util.LinkedList;


/**
 * {@link ErrorHandler} that queues up its errors when a delegate
 * <code>ErrorHandler</code> is not available, and passes the errors
 * on when a delegate is available.
 *
 * @author Philip Aston
 * @version $Revision$
 */
public final class ErrorQueue implements ErrorHandler {

  private ErrorHandler m_delegate = null;
  private final LinkedList m_queue = new LinkedList();

  /**
   * Set the delegate error handler. Any queued up errors will be
   * reported to the delegate immediately.
   *
   * @param errorHandler Where to report errors.
   */
  public void setErrorHandler(ErrorHandler errorHandler) {
    synchronized (this) {
      m_delegate = errorHandler;

      if (m_delegate != null) {
        synchronized (m_queue) {
          while (m_queue.size() > 0) {
            final DelayedError delayedError =
              (DelayedError)m_queue.removeFirst();

            delayedError.apply(m_delegate);
          }
        }
      }
    }
  }

  private static interface DelayedError {
    void apply(ErrorHandler errorHandler);
  }

  private void queue(DelayedError delayedError) {
    synchronized (this) {
      if (m_delegate != null) {
        delayedError.apply(m_delegate);
      }
      else {
        synchronized (m_queue) {
          m_queue.add(delayedError);
        }
      }
    }
  }

  /**
   * Method that handles error messages.
   *
   * @param errorMessage The error message.
   */
  public void handleErrorMessage(final String errorMessage) {
    queue(new DelayedError() {
        public void apply(ErrorHandler errorHandler) {
          errorHandler.handleErrorMessage(errorMessage);
        }
      });
  }

  /**
   * Method that handles error messages.
   *
   * @param errorMessage The error message.
   * @param title A title to use.
   */
  public void handleErrorMessage(final String errorMessage,
                                 final String title) {
    queue(new DelayedError() {
        public void apply(ErrorHandler errorHandler) {
          errorHandler.handleErrorMessage(errorMessage, title);
        }
      });
  }

  /**
   * Method that handles error messages.
   *
   * @param resourceKey Resource key that specifies message.
   * @param defaultMessage Default message to use if
   * <code>resourceKey</code> not found.
   */
  public void handleResourceErrorMessage(final String resourceKey,
                                         final String defaultMessage) {
    queue(new DelayedError() {
        public void apply(ErrorHandler errorHandler) {
          errorHandler.handleResourceErrorMessage(resourceKey, defaultMessage);
        }
      });
  }

  /**
   * Method that handles error messages.
   *
   * @param resourceKey Resource key that specifies message.
   * @param defaultMessage Default message to use if
   * <code>resourceKey</code> not found.
   * @param title A title to use.
   */
  public void handleResourceErrorMessage(final String resourceKey,
                                         final String defaultMessage,
                                         final String title) {
    queue(new DelayedError() {
        public void apply(ErrorHandler errorHandler) {
          errorHandler.handleResourceErrorMessage(resourceKey,
                                                  defaultMessage,
                                                  title);
        }
      });
  }

  /**
   * Method that handles exceptions.
   *
   * @param exception The exception.
   */
  public void handleException(final Exception exception) {
    queue(new DelayedError() {
        public void apply(ErrorHandler errorHandler) {
          errorHandler.handleException(exception);
        }
      });
  }

  /**
   * Method that handles exceptions.
   *
   * @param exception The exception.
   * @param title A title to use.
   */
  public void handleException(final Exception exception, final String title) {
    queue(new DelayedError() {
        public void apply(ErrorHandler errorHandler) {
          errorHandler.handleException(exception, title);
        }
      });
  }
}
