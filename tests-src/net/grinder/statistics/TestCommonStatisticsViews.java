// Copyright (C) 2000 Paco Gomez
// Copyright (C) 2000, 2001, 2002, 2003, 2004, 2005 Philip Aston
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

import junit.framework.TestCase;



/**
 * Unit test case for <code>CommonStatisticsViews</code>.
 *
 * @author Philip Aston
 * @version $Revision$
 * @see RawStatistics
 **/
public class TestCommonStatisticsViews extends TestCase {

  public TestCommonStatisticsViews(String name) {
	super(name);
  }

  public void testGetViews() throws Exception {
	final StatisticsView detail =
	    CommonStatisticsViews.getDetailStatisticsView();

	final ExpressionView[] detailExpressionViews =
	    detail.getExpressionViews();

	assertTrue(detailExpressionViews.length > 0);

	final StatisticsView summary =
	    CommonStatisticsViews.getSummaryStatisticsView();

	final ExpressionView[] summaryExpressionViews =
	    summary.getExpressionViews();

	assertTrue(summaryExpressionViews.length > 0);
  }
}
