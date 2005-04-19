// Copyright (C) 2001, 2002, 2003, 2004 Philip Aston
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

package net.grinder.engine.process;

import net.grinder.common.FilenameFactory;
import net.grinder.common.GrinderException;
import net.grinder.common.GrinderProperties;
import net.grinder.common.Logger;
import net.grinder.communication.QueuedSender;
import net.grinder.console.messages.RegisterStatisticsViewMessage;
import net.grinder.script.Grinder.ScriptContext;
import net.grinder.script.InvalidContextException;
import net.grinder.script.Statistics;
import net.grinder.script.SSLControl;
import net.grinder.statistics.CommonStatisticsViews;
import net.grinder.statistics.StatisticsView;
import net.grinder.util.Sleeper;


/**
 * Implementation of <code>ScriptContext</code>.
 *
 * @author Philip Aston
 * @version $Revision$
 */
final class ScriptContextImplementation implements ScriptContext {

  private final String m_workerID;
  private final ThreadContextLocator m_threadContextLocator;
  private final GrinderProperties m_properties;
  private final QueuedSender m_consoleSender;
  private final Logger m_logger;
  private final FilenameFactory m_filenameFactory;
  private final Sleeper m_sleeper;
  private final SSLControl m_sslControl;

  public ScriptContextImplementation(String workerID,
                                     ThreadContextLocator threadContextLocator,
                                     GrinderProperties properties,
                                     QueuedSender consoleSender,
                                     Logger logger,
                                     FilenameFactory filenameFactory,
                                     Sleeper sleeper,
                                     SSLControl sslControl) {
    m_workerID = workerID;
    m_threadContextLocator = threadContextLocator;
    m_properties = properties;
    m_consoleSender = consoleSender;
    m_logger = logger;
    m_filenameFactory = filenameFactory;
    m_sleeper = sleeper;
    m_sslControl = sslControl;
  }

  public String getProcessID() {
    return m_workerID;
  }

  public int getThreadID() {
    final ThreadContext threadContext = m_threadContextLocator.get();

    if (threadContext != null) {
      return threadContext.getPluginThreadContext().getThreadID();
    }

    return -1;
  }

  public int getRunNumber() {
    final ThreadContext threadContext = m_threadContextLocator.get();

    if (threadContext != null) {
      return threadContext.getPluginThreadContext().getRunNumber();
    }

    return -1;
  }

  public Logger getLogger() {
    return m_logger;
  }

  public void sleep(long meanTime) throws GrinderException {
    m_sleeper.sleepNormal(meanTime);
  }

  public void sleep(long meanTime, long sigma) throws GrinderException {
    m_sleeper.sleepNormal(meanTime, sigma);
  }

  public FilenameFactory getFilenameFactory() {
    return m_filenameFactory;
  }

  public GrinderProperties getProperties() {
    return m_properties;
  }

  public void registerSummaryStatisticsView(StatisticsView statisticsView)
    throws GrinderException {
    CommonStatisticsViews.getSummaryStatisticsView().add(statisticsView);

    // Queue up, will get flushed with next process status or
    // statistics report.
    m_consoleSender.queue(new RegisterStatisticsViewMessage(statisticsView));
  }

  public void registerDetailStatisticsView(StatisticsView statisticsView)
    throws GrinderException {

    if (m_threadContextLocator.get() != null) {
      throw new InvalidContextException(
        "registerDetailStatisticsView() is not supported from worker threads");
    }

    // DetailStatisticsViews are only for the data logs, so we don't
    // register the view with the console.
    CommonStatisticsViews.getDetailStatisticsView().add(statisticsView);
  }

  public Statistics getStatistics() throws InvalidContextException {
    final ThreadContext threadContext = m_threadContextLocator.get();

    if (threadContext == null) {
      throw new InvalidContextException(
        "getStatistics() is only supported for worker threads");
    }

    return threadContext.getScriptStatistics();
  }

  public SSLControl getSSLControl() {
    return m_sslControl;
  }
}
