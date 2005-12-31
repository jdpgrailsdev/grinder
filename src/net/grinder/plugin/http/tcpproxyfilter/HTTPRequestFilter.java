// Copyright (C) 2005 Philip Aston
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

package net.grinder.plugin.http.tcpproxyfilter;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xmlbeans.XmlObject;
import org.picocontainer.Disposable;

import HTTPClient.Codecs;
import HTTPClient.NVPair;
import HTTPClient.ParseException;

import net.grinder.common.Logger;
import net.grinder.plugin.http.xml.AuthorizationHeaderType;
import net.grinder.plugin.http.xml.BaseURLType;
import net.grinder.plugin.http.xml.BasicAuthorizationHeaderType;
import net.grinder.plugin.http.xml.BodyType;
import net.grinder.plugin.http.xml.CommonHeadersType;
import net.grinder.plugin.http.xml.FormBodyType;
import net.grinder.plugin.http.xml.FormFieldType;
import net.grinder.plugin.http.xml.HeaderType;
import net.grinder.plugin.http.xml.ParameterType;
import net.grinder.plugin.http.xml.ParsedQueryStringType;
import net.grinder.plugin.http.xml.QueryStringType;
import net.grinder.plugin.http.xml.RelativeURLType;
import net.grinder.plugin.http.xml.RequestHeadersType;
import net.grinder.plugin.http.xml.RequestType;
import net.grinder.tools.tcpproxy.ConnectionDetails;
import net.grinder.tools.tcpproxy.EndPoint;
import net.grinder.tools.tcpproxy.TCPProxyFilter;


/**
 * {@link TCPProxyFilter} that transforms an HTTP request stream into
 * an XML document.
 *
 * <p>Bugs:
 * <ul>
 * <li>Assumes Request-Line (GET ...) is first line of packet, and that
 * every packet that starts with such a line is the start of a request.
 * <li>Should filter chunked transfer coding from POST data.
 * <li>Doesn't handle line continuations.
 * <li>Doesn't parse correctly if lines are broken across message
 * fragments.
 * </ul>
 *
 * TODO Record and use HTML page information.
 * TODO Session key processing.
 * TODO Avoid Jython 64K limit (does it still affect 2.2?)
 * TODO Use setDefaultHeaders for user agent
 *
 * @author Philip Aston
 * @author Bertrand Ave
 * @version $Revision$
 */
public final class HTTPRequestFilter
  implements TCPProxyFilter, Disposable {

  /**
   * A list of headers which we record.
   */
  private static final String[] MIRRORED_HEADERS = {
    "Accept",
    "Accept-Charset",
    "Accept-Encoding",
    "Accept-Language",
    "Cache-Control",
    "If-Modified-Since",
    "Referer", // Deliberate misspelling to match specification.
    "User-Agent",
  };

  private static final Set HTTP_METHODS_WITH_BODY = new HashSet(Arrays.asList(
    new String[] { "OPTIONS", "POST", "POST" }
  ));

  private static final Set COMMON_HEADERS = new HashSet(Arrays.asList(
    new String[] {
      "Accept",
      "Accept-Charset",
      "Accept-Encoding",
      "Accept-Language",
      "Cache-Control",
      "Referer",
      "User-Agent",
    }
  ));

  private final HTTPRecording m_httpRecording;
  private final Logger m_logger;

  private final Pattern m_basicAuthorizationHeaderPattern;
  private final Pattern m_contentTypePattern;
  private final Pattern m_contentLengthPattern;
  private final Pattern m_lastURLPathElementPattern;
  private final Pattern m_messageBodyPattern;
  private final Pattern m_requestLinePattern;

  /** Entries correspond to MIRRORED_HEADERS. */
  private final Pattern[] m_mirroredHeaderPatterns;

  private final IntGenerator m_requestIDGenerator = new IntGenerator();

  private final HandlerMap m_handlers = new HandlerMap();

  private final BaseURLMap m_baseURLMap = new BaseURLMap();
  private final CommonHeadersMap m_commonHeadersMap = new CommonHeadersMap();

  /**
   * Constructor.
   *
   * @param httpRecording
   *          Common HTTP recording state.
   * @param logger
   *          Logger to direct output to.
   */
  public HTTPRequestFilter(HTTPRecording httpRecording, Logger logger) {

    m_httpRecording = httpRecording;
    m_logger = logger;

    m_messageBodyPattern = Pattern.compile("\\r\\n\\r\\n(.*)", Pattern.DOTALL);

    // From RFC 2616:
    //
    // Request-Line = Method SP Request-URI SP HTTP-Version CRLF
    // HTTP-Version = "HTTP" "/" 1*DIGIT "." 1*DIGIT
    // http_URL = "http:" "//" host [ ":" port ] [ abs_path [ "?" query ]]
    //
    // We're flexible about SP and CRLF, see RFC 2616, 19.3.

    m_requestLinePattern =
      Pattern.compile(
        "^([A-Z]+)[ \\t]+" +          // Method.
        "(?:https?://[^/]+)?"  +      // Ignore scheme, host, port.
        "([^\\?]+)" +                 // Path.
        "(?:\\?(.*))?" +              // Optional query string.
        "[ \\t]+HTTP/\\d.\\d[ \\t]*\\r?\\n",
        Pattern.MULTILINE | Pattern.UNIX_LINES);

    m_contentLengthPattern = getHeaderPattern("Content-Length", true);

    m_contentTypePattern = getHeaderPattern("Content-Type", true);

    m_mirroredHeaderPatterns = new Pattern[MIRRORED_HEADERS.length];

    for (int i = 0; i < MIRRORED_HEADERS.length; i++) {
      m_mirroredHeaderPatterns[i] =
        getHeaderPattern(MIRRORED_HEADERS[i], false);
    }

    m_basicAuthorizationHeaderPattern =
      Pattern.compile(
        "^Authorization:[ \\t]*Basic[  \\t]*([a-zA-Z0-9+/]*=*).*\\r?\\n",
        Pattern.MULTILINE | Pattern.UNIX_LINES);

    // Ignore maximum amount of stuff that's not a '?' or ';' followed by
    // a '/', then grab the next until the first '?' or ';'.
    m_lastURLPathElementPattern = Pattern.compile("^[^\\?;]*/([^\\?;]*)");
  }

  /**
   * Factory for regular expression patterns that match HTTP headers.
   *
   * @param headerName
   *          The header name.
   * @param caseInsensitive
   *          Some headers are commonly used in the wrong case.
   * @return The expression.
   */
  private static Pattern getHeaderPattern(String headerName,
                                          boolean caseInsensitive) {
    return Pattern.compile(
      "^" + headerName + ":[ \\t]*(.*)\\r?\\n",
      Pattern.MULTILINE |
      Pattern.UNIX_LINES |
      (caseInsensitive ? Pattern.CASE_INSENSITIVE : 0));
  }

  /**
   * The main handler method called by the sniffer engine.
   *
   * <p>
   * This is called for message fragments; we don't assume that its passed a
   * complete HTTP message at a time.
   * </p>
   *
   * @param connectionDetails
   *          The TCP connection.
   * @param buffer
   *          The message fragment buffer.
   * @param bytesRead
   *          The number of bytes of buffer to process.
   * @return Filters can optionally return a <code>byte[]</code> which will be
   *         transmitted to the server instead of <code>buffer</code>.
   */
  public byte[] handle(ConnectionDetails connectionDetails, byte[] buffer,
                       int bytesRead) {

    m_handlers.getHandler(connectionDetails).handle(buffer, bytesRead);
    return null;
  }

  /**
   * A connection has been opened.
   *
   * @param connectionDetails a <code>ConnectionDetails</code> value
   */
  public void connectionOpened(ConnectionDetails connectionDetails) {
    m_handlers.getHandler(connectionDetails);
  }

  /**
   * A connection has been closed.
   *
   * @param connectionDetails a <code>ConnectionDetails</code> value
   */
  public void connectionClosed(ConnectionDetails connectionDetails) {
    m_handlers.closeHandler(connectionDetails);
  }

  /**
   * Called after the filter has been stopped.
   */
  public void dispose() {
    m_handlers.closeAllHandlers();
  }

  /**
   * Class that handles a particular connection.
   *
   * <p>Multi-threaded calls for a given connection are
   * serialised.</p>
   **/
  private final class Handler {
    private final BaseURLType m_baseURL;

    // Parse data.
    private Request m_request;

    public Handler(ConnectionDetails connectionDetails) {
      m_baseURL =
        m_baseURLMap.getBaseURL(
          connectionDetails.isSecure() ?
              BaseURLType.Scheme.HTTPS : BaseURLType.Scheme.HTTP,
          connectionDetails.getRemoteEndPoint());
    }

    public synchronized void handle(byte[] buffer, int length) {

      // String used to parse headers - header names are US-ASCII encoded and
      // anchored to start of line. The correct character set to use for URL's
      // is not well defined by RFC 2616, so we use ISO8859_1. This way we are
      // at least non-lossy (US-ASCII maps characters above 0xFF to '?').
      final String asciiString;
      try {
        asciiString = new String(buffer, 0, length, "ISO8859_1");
      }
      catch (UnsupportedEncodingException e) {
        throw new AssertionError(e);
      }

      final Matcher matcher = m_requestLinePattern.matcher(asciiString);

      if (matcher.find()) {
        // Packet is start of new request message.

        endMessage();

        final String method = matcher.group(1);
        final String path = matcher.group(2);
        final String queryString = matcher.group(3);

        m_request = new Request(method, path, queryString);
      }

      // Stuff we do whatever.

      if (m_request == null) {
        m_logger.error("UNEXPECTED - No current request");
      }
      else if (m_request.getBody() != null) {
        m_request.getBody().write(buffer, 0, length);
      }
      else {
        // Still parsing headers.
        // TODO add in order.
        for (int i = 0; i < MIRRORED_HEADERS.length; i++) {
          final Matcher headerMatcher =
            m_mirroredHeaderPatterns[i].matcher(asciiString);

          if (headerMatcher.find()) {
            m_request.addHeader(
              MIRRORED_HEADERS[i], headerMatcher.group(1).trim());
          }
        }

        final Matcher authorizationMatcher =
          m_basicAuthorizationHeaderPattern.matcher(asciiString);

        if (authorizationMatcher.find()) {
          m_request.addBasicAuthorization(
            authorizationMatcher.group(1).trim());
        }

        if (m_request.expectingBody()) {
          final Matcher contentLengthMatcher =
            m_contentLengthPattern.matcher(asciiString);

          // Look for the content length and type in the header.
          if (contentLengthMatcher.find()) {
            m_request.setContentLength(
              Integer.parseInt(contentLengthMatcher.group(1).trim()));
          }

          final Matcher contentTypeMatcher =
            m_contentTypePattern.matcher(asciiString);

          if (contentTypeMatcher.find()) {
            m_request.setContentType(contentTypeMatcher.group(1).trim());
          }

          final Matcher messageBodyMatcher =
            m_messageBodyPattern.matcher(asciiString);

          if (messageBodyMatcher.find()) {
            final int beginOffset = messageBodyMatcher.start(1);
            final int endOffset = messageBodyMatcher.end(1);
            m_request.new Body().write(
              buffer, beginOffset, endOffset - beginOffset);
          }
        }
      }
    }

    /**
     * Called when end of message is reached.
     */
    public synchronized void endMessage() {
      if (m_request != null) {
        m_request.record();
      }

      m_request = null;
    }

    private final class Request {
      private final RequestType m_requestXML =
        RequestType.Factory.newInstance();
      private final RequestHeadersType m_headers =
        RequestHeadersType.Factory.newInstance();

      private int m_contentLength = -1;
      private String m_contentType = null;
      private Body m_body;

      public Request(String method, String path, String queryString) {
        final Matcher lastURLPathElementMatcher =
          m_lastURLPathElementPattern.matcher(path);

        m_requestXML.setRequestId(m_requestIDGenerator.next());

        if (lastURLPathElementMatcher.find()) {
          m_requestXML.setShortDescription(
            method + " " + lastURLPathElementMatcher.group(1));
        }
        else {
          m_requestXML.setShortDescription(
            method + " " + m_requestXML.getRequestId());
        }

        m_requestXML.setMethod(RequestType.Method.Enum.forString(method));
        m_requestXML.setTime(Calendar.getInstance());

        final RelativeURLType url = m_requestXML.addNewUrl();
        url.setExtends(m_baseURL.getUrlId());
        url.setPath(path);

        if (queryString != null) {
          final QueryStringType urlQueryString = url.addNewQueryString();

          try {
            final NameValue[] queryStringAsNameValuePairs =
              parseNameValueString(queryString);

            final ParsedQueryStringType parsedQuery =
              ParsedQueryStringType.Factory.newInstance();

            for (int i = 0; i < queryStringAsNameValuePairs.length; ++i) {
              final ParameterType parameter = parsedQuery.addNewParameter();
              parameter.setName(queryStringAsNameValuePairs[i].getName());
              parameter.setValue(queryStringAsNameValuePairs[i].getValue());
            }

           urlQueryString.setParsed(parsedQuery);
          }
          catch (ParseException e) {
            urlQueryString.setUnparsed(queryString);
          }
        }

        final long lastResponseTime = m_httpRecording.getLastResponseTime();

        if (lastResponseTime > 0) {
          final long time = System.currentTimeMillis() - lastResponseTime;

          if (time > 10) {
            m_requestXML.setSleepTime(time);
          }
        }
      }

      public void addBasicAuthorization(String base64) {
        final String decoded = Codecs.base64Decode(base64);

        final int colon = decoded.indexOf(":");

        if (colon < 0) {
          m_logger.error("Could not decode Authorization header");
        }
        else {
          final BasicAuthorizationHeaderType basicAuthorization =
            m_headers.addNewAuthorization().addNewBasic();

          basicAuthorization.setUserid(decoded.substring(0, colon));
          basicAuthorization.setPassword(decoded.substring(colon + 1));
        }
      }

      public Body getBody() {
        return m_body;
      }

      public boolean expectingBody() {
        return HTTP_METHODS_WITH_BODY.contains(
          m_requestXML.getMethod().toString());
      }

      public void setContentType(String contentType) {
        m_contentType = contentType;
        addHeader("Content-Type", m_contentType);
      }

      public void setContentLength(int contentLength) {
        m_contentLength = contentLength;
      }

      public void addHeader(String name, String value) {
        final HeaderType header = m_headers.addNewHeader();
        header.setName(name);
        header.setValue(value);
      }

      public void record() {
        m_requestXML.setHeaders(
          m_commonHeadersMap.extractCommonHeaders(m_headers));

        if (getBody() != null) {
          getBody().record();
        }

        m_httpRecording.addRequest(m_requestXML);
      }

      private NameValue[] parseNameValueString(String input)
        throws ParseException {

        final NVPair[] pairs = Codecs.query2nv(input);

        final NameValue[] result = new NameValue[pairs.length];

        for (int i = 0; i < pairs.length; ++i) {
          result[i] = new NameValue(pairs[i].getName(), pairs[i].getValue());
        }

        return result;
      }

      private class Body {
        private final ByteArrayOutputStream m_entityBodyByteStream =
          new ByteArrayOutputStream();

        public Body() {
          assert m_body == null;
          m_body = this;
        }

        public void write(byte[] bytes, int start, int length) {
          final int lengthToWrite;

          if (m_contentLength != -1 &&
              length > m_contentLength - m_entityBodyByteStream.size()) {

            m_logger.error("Expected content length exceeded, truncating");
            lengthToWrite = m_contentLength - m_entityBodyByteStream.size();
          }
          else {
            lengthToWrite = length;
          }

          m_entityBodyByteStream.write(bytes, start, lengthToWrite);

          // We flush our entity data output now if we've reached the
          // specified Content-Length. If no contentLength was specified
          // we rely on next message or connection close event to flush
          // the data.
          if (m_contentLength != -1 &&
              m_entityBodyByteStream.size() >= m_contentLength) {

            endMessage();
          }
        }

        public void record() {
          final BodyType body = m_requestXML.addNewBody();
          body.setContentType(m_contentType);

          final byte[] bytes = m_entityBodyByteStream.toByteArray();

          if (bytes.length > 0x4000) {
            // Large amount of data, use a file.
            final String fileName =
              "http-data-" + m_requestXML.getRequestId() + ".dat";

            try {
              final FileOutputStream dataStream =
                new FileOutputStream(fileName);
              dataStream.write(bytes, 0, bytes.length);
              dataStream.close();
            }
            catch (IOException e) {
              m_logger.error("Failed to write body data to '" + fileName + "'");
              e.printStackTrace(m_logger.getErrorLogWriter());
            }

            body.setFile(fileName);
          }
          else {
            final String iso88591String;

            try {
              iso88591String = new String(bytes, "ISO8859_1");
            }
            catch (UnsupportedEncodingException e) {
              throw new AssertionError(e);
            }

            if ("application/x-www-form-urlencoded".equals(m_contentType)) {
              try {
                final NameValue[] formNameValuePairs =
                  parseNameValueString(iso88591String);

                final FormBodyType formData =
                  FormBodyType.Factory.newInstance();

                for (int i = 0; i < formNameValuePairs.length; ++i) {
                  final FormFieldType formField = formData.addNewFormField();
                  formField.setName(formNameValuePairs[i].getName());
                  formField.setValue(formNameValuePairs[i].getValue());
                }

                body.setForm(formData);
              }
              catch (ParseException e) {
                // Failed to parse form data as name-value pairs, we'll
                // treat it as raw data instead.
              }
            }

            if (body.getForm() == null) {
              // Basic handling of strings; should use content type headers.
              boolean looksLikeAnExtendedASCIIString = true;

              for (int i = 0; i < bytes.length; ++i) {
                final char c = iso88591String.charAt(i);

                if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                  looksLikeAnExtendedASCIIString = false;
                  break;
                }
              }

              if (looksLikeAnExtendedASCIIString) {
                body.setString(iso88591String);
              }
              else {
                body.setBinary(bytes);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Map of {@link ConnectionDetails} to handlers.
   */
  private class HandlerMap {
    private final Map m_handlers = new HashMap();

    public Handler getHandler(ConnectionDetails connectionDetails) {
      synchronized (m_handlers) {
        final Handler oldHandler =
          (Handler)m_handlers.get(connectionDetails);

        if (oldHandler != null) {
          return oldHandler;
        }
        else {
          final Handler newHandler = new Handler(connectionDetails);
          m_handlers.put(connectionDetails, newHandler);
          return newHandler;
        }
      }
    }

    public void closeHandler(ConnectionDetails connectionDetails) {

      final Handler handler;

      synchronized (m_handlers) {
        handler = (Handler)m_handlers.remove(connectionDetails);
      }

      if (handler == null) {
        throw new IllegalArgumentException(
          "Unknown connection " + connectionDetails);
      }

      handler.endMessage();
    }

    public void closeAllHandlers() {
      synchronized (m_handlers) {
        final Iterator iterator = m_handlers.values().iterator();

        while (iterator.hasNext()) {
          final Handler handler = (Handler)iterator.next();
          handler.endMessage();
        }
      }
    }
  }

  private final class BaseURLMap {
    private Map m_map = new HashMap();
    private IntGenerator m_idGenerator = new IntGenerator();

    public BaseURLType getBaseURL(
      BaseURLType.Scheme.Enum scheme, EndPoint endPoint) {

      final Object key = scheme.toString() + "://" + endPoint;

      synchronized (m_map) {
        final BaseURLType existing = (BaseURLType)m_map.get(key);

        if (existing != null) {
          return existing;
        }

        final BaseURLType result = BaseURLType.Factory.newInstance();
        result.setUrlId("url" + m_idGenerator.next());
        result.setScheme(scheme);
        result.setHost(endPoint.getHost());
        result.setPort(endPoint.getPort());

        m_httpRecording.addBaseURL(result);
        m_map.put(key, result);

        return result;
      }
    }
  }

  private final class CommonHeadersMap {
    private Map m_map = new HashMap();
    private IntGenerator m_idGenerator = new IntGenerator();

    public RequestHeadersType extractCommonHeaders(
      RequestHeadersType requestHeaders) {

      final CommonHeadersType commonHeaders =
        CommonHeadersType.Factory.newInstance();
      final RequestHeadersType newRequestHeaders =
        RequestHeadersType.Factory.newInstance();

      final XmlObject[] children = requestHeaders.selectPath("./*");

      for (int i = 0; i < children.length; ++i) {
        if (children[i] instanceof HeaderType) {
          final HeaderType header = (HeaderType)children[i];

          if (COMMON_HEADERS.contains(header.getName())) {
            commonHeaders.addNewHeader().set(header);
          }
          else {
            newRequestHeaders.addNewHeader().set(header);
          }
        }
        else if (children[i] instanceof AuthorizationHeaderType) {
          newRequestHeaders.addNewAuthorization().set(children[i]);
        }
        else {
          assert false;
        }
      }

      // Key that ignores ID.
      final Object key =
        Arrays.asList(commonHeaders.getHeaderArray()).toString();

      synchronized (m_map) {
        final CommonHeadersType existing = (CommonHeadersType)m_map.get(key);

        if (existing != null) {
          newRequestHeaders.setExtends(existing.getHeadersId());
        }
        else {
          commonHeaders.setHeadersId("headers" + m_idGenerator.next());
          m_httpRecording.addCommonHeaders(commonHeaders);
          m_map.put(key, commonHeaders);

          newRequestHeaders.setExtends(commonHeaders.getHeadersId());
        }
      }

      return newRequestHeaders;
    }
  }

  private static final class IntGenerator {
    private int m_value = -1;

    public synchronized int next() {
      return ++m_value;
    }
  }

  private static final class NameValue {
    private final String m_name;
    private final String m_value;

    public NameValue(String s1, String s2) {
      m_name = s1;
      m_value = s2;
    }

    public String getName() {
      return m_name;
    }

    public String getValue() {
      return m_value;
    }

    public String toString() {
      return getName() + "='" + getValue() + "'";
    }

    public int hashCode() {
      return m_name.hashCode() ^ m_value.hashCode();
    }

    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }

      if (!(o instanceof NameValue)) {
        return false;
      }

      final NameValue other = (NameValue)o;

      return
        getName().equals(other.getName()) &&
        getValue().equals(other.getValue());
    }
  }
}
