// Copyright (C) 2000, 2001, 2002, 2003 Philip Aston
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

package net.grinder.statistics;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import net.grinder.util.Serialiser;


/**
 * Factory for {@link TestStatistics} objects.
 *
 * @author Philip Aston
 * @version $Revision$
 * @stereotype singleton
 **/
public final class TestStatisticsFactory {

  private static final TestStatisticsFactory s_instance =
    new TestStatisticsFactory();

  private final Serialiser m_serialiser = new Serialiser();

  /**
   * @link dependency 
   * @stereotype instantiate
   **/
  /*#TestStatisticsImplementation lnkTestStatistics;*/

  /**
   * Singleton accessor.
   *
   * @return The singleton.
   */
  public static final TestStatisticsFactory getInstance() {
    return s_instance;
  }

  /**
   * Factory menthod.
   *
   * @return A new <code>TestStatistics</code>.
   */
  public final TestStatistics create() {
    return createImplementation();
  }

  /**
   * Package scope factory method that returns instances of our
   * implementation type.
   * @see #create
   **/
  final TestStatisticsImplementation createImplementation() {
    return new TestStatisticsImplementation();
  }

  final void writeStatisticsExternal(ObjectOutput out,
				     TestStatisticsImplementation statistics)
    throws IOException {
    statistics.myWriteExternal(out, m_serialiser);
  }

  final TestStatistics readStatisticsExternal(ObjectInput in)
    throws IOException {
    return new TestStatisticsImplementation(in, m_serialiser);
  }
}
