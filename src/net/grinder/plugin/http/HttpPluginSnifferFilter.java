// Copyright (C) 2000 Paco Gomez
// Copyright (C) 2000, 2001, 2002 Philip Aston
// Copyright (C) 2000 Phil Dawes
// Copyright (C) 2001 Kalle Burbeck
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

package net.grinder.plugin.http;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.text.DateFormat;

import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;

import net.grinder.TCPSniffer;
import net.grinder.tools.tcpsniffer.ConnectionDetails;
import net.grinder.tools.tcpsniffer.EchoFilter;
import net.grinder.tools.tcpsniffer.SnifferFilter;


/**
 * {@link SnifferFilter} that outputs session in a form that can be
 * reused by the HTTP plugin.
 *
 * <p>Bugs:
 * <ul>
 * <li>Assumes Request-Line (GET ...) is first line of packet, and that
 * every packat that starts with such a line is the start of a request.
 * <li>Should filter chunked transfer coding from POST data.
 * <li>Doesn't handle line continuations.
 * <li>Tickles bug in regexp
 * </ul>
 *
 * @author Philip Aston
 * @version $Revision$
 */
public class HttpPluginSnifferFilter implements SnifferFilter
{
    private final static String FILENAME_PREFIX = "http-plugin-sniffer-post-";
    private final static String s_newLine =
	System.getProperty("line.separator");

    /**
     * Map of {@link ConnectionDetails} to handlers.
     */
    private final Map m_handlers = new HashMap();

    private boolean m_preludeWritten;

    private int m_currentRequestNumber;
    private long m_lastTime;

    private static final String[] s_mirroredHeaders = {
	"Content-Type",
	"Content-type",		// For the broken browsers of this world.
	"If-Modified-Since",
    };

    public HttpPluginSnifferFilter()
    {
	m_currentRequestNumber =
	    Integer.getInteger(TCPSniffer.INITIAL_TEST_PROPERTY, 0).intValue()
	    - 1;

	markTime();
    }

    /**
     * The main handler method called by the sniffer engine.
     *
     * <p>NOTE, this is called for message fragments, don't assume
     * that its passed a complete HTTP message at a time.</p>
     *
     * @param connectionDetails The TCP connection.
     * @param buffer The message fragment buffer.
     * @param bytesRead The number of bytes of buffer to process.
     */
    public void handle(ConnectionDetails connectionDetails, byte[] buffer,
		       int bytesRead)
	throws IOException, RESyntaxException
    {
	getHandler(connectionDetails).handle(buffer, bytesRead);
    }

    public void connectionOpened(ConnectionDetails connectionDetails) 
    {
    }

    public void connectionClosed(ConnectionDetails connectionDetails)
	throws IOException, RESyntaxException
    {
	getHandler(connectionDetails).endMessage();
    }

    private void writePrelude() 
    {
	outputnl("");
	outputnl("#");
	outputnl("# The Grinder version @version@");
	outputnl("#");
	outputnl("# Script generated by the TCPSniffer at " + 
	       DateFormat.getDateTimeInstance().format(
		   Calendar.getInstance().getTime()));
	outputnl("#");
	outputnl("");
	outputnl("grinder.processes=1");
	outputnl("grinder.threads=1");
	outputnl("grinder.cycles=0         # Until console sends stop.");
	outputnl("");
	outputnl("grinder.plugin=net.grinder.plugin.http.HttpPlugin");
	outputnl("");
    }

    private Handler getHandler(ConnectionDetails connectionDetails)
	throws RESyntaxException
    {
	synchronized (m_handlers) {
	    if (!m_preludeWritten) {
		writePrelude();
		m_preludeWritten = true;
	    }

	    final Handler oldHandler =
		(Handler)m_handlers.get(connectionDetails);

	    if (oldHandler != null) {
		return oldHandler;
	    }
	    else {
		outputnl(s_newLine +
			 "# New connection: " +
			 connectionDetails.getDescription());

		final Handler newHandler = new Handler(connectionDetails);
		m_handlers.put(connectionDetails, newHandler);
		return newHandler;
	    }
	}
    }

    private synchronized int getRequestNumber()
    {
	return m_currentRequestNumber;
    }

    private synchronized int incrementRequestNumber() 
    {
	return ++m_currentRequestNumber;
    }

    private synchronized long markTime()
    {
	final long currentTime = System.currentTimeMillis();
	final long result = currentTime - m_lastTime;
	m_lastTime = currentTime;
	return result;
    }

    private void outputnl(String s) 
    {
	System.out.println(s);
    }

    private void output(String s) 
    {
	System.out.print(s);
    }

    /**
     * Regexp is not synchronised, so for now compile new objects
     * every time. If it becomes a bottleneck, the "get*Expression
     * methods should be implemented with object pools.
     *
     * From RFC 2616:
     *
     * Request-Line = Method SP Request-URI SP HTTP-Version CRLF
     * HTTP-Version = "HTTP" "/" 1*DIGIT "." 1*DIGIT http_URL =
     * "http:" "//" host [ ":" port ] [ abs_path [ "?" query ]]
     *  
     * We're flexible about SP and CRLF, see RFC 2616, 19.3.
    */
    private final RE getMethodLineExpression() throws RESyntaxException
    {
	return
	    new RE("^([:upper:]+)[ \\t]+(.+)[ \\t]+HTTP/\\d.\\d[ \\t]*\\r?$", 
		   RE.MATCH_MULTILINE);
    }

   /**
    * Regexp is not synchronised, so for now compile new objects
    * every time. If it becomes a bottleneck, the "get*Expression
    * methods should be implemented with object pools.
   */
   private RE getContentTypeMultipartExpression() throws RESyntaxException
   {
      return new RE("^Content-Type: multipart/form-data; boundary=(.*)$",
		    RE.MATCH_MULTILINE | RE.MATCH_CASEINDEPENDENT);
   }

   /**
    *
    * Regexp is not synchronised, so for now compile new objects
    * every time. If it becomes a bottleneck, the "get*Expression
    * methods should be implemented with object pools.
   */
   private RE getLastURLPathElementExpression() throws RESyntaxException
   {
       return new RE("^[^\\?]*/([^\\?]*)", RE.MATCH_MULTILINE);
    }

    /**
     * Regexp is not synchronised, so for now compile new objects
     * every time. If it becomes a bottleneck, the "get*Expression
     * methods should be implemented with object pools.
    */
    protected final RE getHeaderExpression(String headerName)
	throws RESyntaxException
    {
	return new RE("^" + headerName + ": (.*)$", RE.MATCH_MULTILINE);
    }

    /**
     * Regexp is not synchronised, so for now compile new objects
     * every time. If it becomes a bottleneck, the "get*Expression
     * methods should be implemented with object pools.
    */
    protected final RE getContentLengthExpression() throws RESyntaxException
    {
	final RE contentLengthExpession =
	    getHeaderExpression("Content-Length");

	// Sigh.
	contentLengthExpession.setMatchFlags(
	    contentLengthExpession.getMatchFlags() | RE.MATCH_CASEINDEPENDENT);

	return contentLengthExpession;
    }

    /**
     * Regexp is not synchronised, so for now compile new objects
     * every time. If it becomes a bottleneck, the "get*Expression
     * methods should be implemented with object pools.
     *
     * @param boundary Indicates that this is a multipart body.
    */
    private RE getMessageBodyExpression(String boundary)
	throws RESyntaxException
    {
	if (boundary == null) {
	    // Unfortunately regexp breaks with stack overflow for
	    // certain input. in particularly (BLAH\r\n\r\n<longline>)
	    return new RE("\\r\\n\\r\\n(.*)", RE.MATCH_SINGLELINE);
	}
	else {
	    //If multipart contentType. We currently only match one
	    //part.
	    return new RE("(--" + boundary + "(.*)--" + boundary +
			  "--(\\r\\n)?)",
			  RE.MATCH_SINGLELINE);
	}
    }

    private final class Handler
    {
	private final ConnectionDetails m_connectionDetails;

	private final StringBuffer m_outputBuffer = new StringBuffer();

	// Parse state.
	private int m_requestNumber;
	private boolean m_parsingHeaders = false;
	private boolean m_handlingPost = false;
	private final StringBuffer m_entityBodyBuffer = new StringBuffer();
	private int m_contentLength = -1;

	/** REs holds state, so this has to be per Handler. **/
	private final RE m_mirroredHeaderExpressions[] =
	    new RE[s_mirroredHeaders.length];

	public Handler(ConnectionDetails connectionDetails)
	    throws RESyntaxException
	{
	    m_connectionDetails = connectionDetails;

	    for (int i=0; i<s_mirroredHeaders.length; i++) {
		m_mirroredHeaderExpressions[i] =
		    getHeaderExpression(s_mirroredHeaders[i]);
	    }
	}

	public synchronized void handle(byte[] buffer, int bufferBytes)
	    throws IOException, RESyntaxException
	{
	    // String used to parse headers - header names are
	    // US-ASCII encoded and anchored to start of line.
	    final String asciiString =
		new String(buffer, 0, bufferBytes, "US-ASCII");

	    final RE methodLineExpresion = getMethodLineExpression();

	    if (methodLineExpresion.match(asciiString)) {
		// Packet is start of new request message.

		endMessage();
		m_parsingHeaders = true;

		final String method = methodLineExpresion.getParen(1);

		if (method.equals("GET")) {
		    m_handlingPost = false;
		}
		else if (method.equals("POST")) {
		    m_handlingPost = true;
		    m_entityBodyBuffer.setLength(0);
		    m_contentLength = -1;
		}
		else {
		    warn("Ignoring '" + method + "' from " +
			 m_connectionDetails.getDescription());
		    return;
		}

		final String url;

		if (methodLineExpresion.getParen(2).startsWith("http")) {
		    // Absolute URL given.
		    url = methodLineExpresion.getParen(2);
		} else {
		    // Calculate absolute URL.
		    url =
			m_connectionDetails.getURLBase("http") +
			methodLineExpresion.getParen(2);
		}

		// Stuff we do at start of request only.
		m_requestNumber = incrementRequestNumber();
		outputProperty("parameter.url", url);
		outputProperty("sleepTime", Long.toString(markTime()));

		// Base default description on test URL.
		final RE descriptionExpresion =
		    getLastURLPathElementExpression();

		final String description;

		if (descriptionExpresion.match(url)) {
		    description = descriptionExpresion.getParen(1);
		}
		else {
		    description = "";
		}

		outputProperty("description", description);
	    }

	    // Stuff we do whatever.
	    if (m_parsingHeaders) {
		for (int i=0; i<s_mirroredHeaders.length; i++) {
		    if (m_mirroredHeaderExpressions[i].match(asciiString)) {
			outputProperty(
			    "parameter.header." + s_mirroredHeaders[i],
			    m_mirroredHeaderExpressions[i].getParen(1).trim());
		    }
		}

		if (m_handlingPost) {
		    // Look for the content length in the header.
		    final RE contentLengthExpession =
			getContentLengthExpression();

		    if (contentLengthExpession.match(asciiString)) {
			m_contentLength =
			    Integer.parseInt(
				contentLengthExpession.getParen(1).trim());
		    }

		    // Multipart boundary specified?
		    final RE contentTypeMultipartExpression =
			getContentTypeMultipartExpression();

		    final String multipartBoundary;

		    if (contentTypeMultipartExpression.match(asciiString)) {
			multipartBoundary =
			    contentTypeMultipartExpression.getParen(1).trim();
		    }
		    else {
			multipartBoundary = null;
		    }

		    final RE messageBodyExpression =
			getMessageBodyExpression(multipartBoundary);

		    if (messageBodyExpression.match(asciiString)) {
			m_parsingHeaders = false;
			addToEntityBody(messageBodyExpression.getParen(1));

			System.out.println();
			System.out.println("MATCHED");
			System.out.println(asciiString);
			System.out.println("TO");
			System.out.println(messageBodyExpression.getParen(1));
			System.out.println();
		    }
		}
	    }
	    else {
		if (m_handlingPost) {
		    addToEntityBody(asciiString);
		}
		else {
		    warn("UNEXPECTED - Not parsing headers or handling POST");
		}
	    }
	}

	private void addToEntityBody(String request)
	    throws IOException, RESyntaxException
	{
	    m_entityBodyBuffer.append(request);
	
	    // We flush our entity data output now if we've reached or
	    // exceeded the specified Content-Length. If no
	    // contentLength was specified we rely on next message or
	    // connection close event to flush the data.
	    if (m_contentLength != -1 ) {
		final int bytesRead = m_entityBodyBuffer.length();

		if (bytesRead == m_contentLength) {
		    endMessage();
		}
		else if (bytesRead > m_contentLength) {
		    warn("Expected content length exceeded");
		    endMessage();
		}
	    }
	}

	private void outputProperty(String name, String value)
	{
	    m_outputBuffer.append(
		"grinder.test" + m_requestNumber + "." + name + "=" + value +
		s_newLine);
	}

	private void warn(String message)
	{
	    m_outputBuffer.append(
		"# WARNING request " + m_requestNumber + ": " + message +
		s_newLine);
	}

	protected final void endMessage() throws IOException
	{
	    if (!m_parsingHeaders && m_handlingPost) {
		final String filename = FILENAME_PREFIX + m_requestNumber;
		final Writer writer =
		    new BufferedWriter(new FileWriter(filename));

		writer.write(m_entityBodyBuffer.toString());
		writer.close();

		outputProperty("parameter.post", filename);
		m_handlingPost = false;
	    }

	    output(m_outputBuffer.toString());
	    m_outputBuffer.setLength(0);
	}
    }
}
