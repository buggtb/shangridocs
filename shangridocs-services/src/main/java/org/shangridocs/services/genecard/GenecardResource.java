/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.shangridocs.services.genecard;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.protocol.HttpContext;
import org.apache.oodt.cas.metadata.util.PathUtils;
import org.w3c.dom.Node;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpWebConnection;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableBody;
import com.gargoylesoftware.htmlunit.html.HtmlTableCell;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;


@Path("/genecard")
public class GenecardResource {

  private final static String BASE_URL = "http://www.genecards.org/";
  
  private final static String SEARCH_BASE_URL = BASE_URL + "Search/Keyword";
  
  private final static String LOGIN_LINK = "/Account/LogOn?moduleName=GC";
  
  private String email;
  
  private String password;

  private enum GenecardData {
    Index(0), Symbol(2), Description(3), Category(4), GIFtS(5), GC_ID(6), Score(
        7);

    private int value;

    private GenecardData(int value) {
      this.value = value;
    }

    public int getValue() {
      return this.value;
    }

    public void setValue(int value) {
      this.value = value;
    }
  };

  public GenecardResource(@Context ServletContext sc) {
    java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(
        java.util.logging.Level.OFF);
    email = sc.getInitParameter("org.shangridocs.genecard.login.email");
    password = sc.getInitParameter("org.shangridocs.genecard.login.pass");
    
    if (email != null){
      email = PathUtils.replaceEnvVariables(email);
    }
    
    if (password != null){
      password = PathUtils.replaceEnvVariables(password);
    }
    
  }

  @PUT
  @Path("/query")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes("text/plain")
  public Response query(InputStream is) throws IOException {
    try (final WebClient webClient = new WebClient(BrowserVersion.CHROME)) {
      webClient.setWebConnection(new MyWebConnection(webClient));
      webClient.getOptions().setThrowExceptionOnScriptError(false);
      webClient.getOptions().setRedirectEnabled(true);
      
      // log in first if creds provided
      if (validateCreds()){
        HtmlPage loginResultPage = login(webClient);
        System.out.println("login result: "+loginResultPage.asXml());        
      }

      String searchText = IOUtils.toString(is, "UTF-8");
      WebRequest request = new WebRequest(new URL(SEARCH_BASE_URL + "?queryString="
          + searchText));
      System.out.println(request.toString());
      final HtmlPage page = webClient.getPage(request);
      System.out.println("Result: "+page.asXml());
      StringBuilder response = new StringBuilder();
      HtmlTable searchResultsTable = page.getHtmlElementById("searchResults");
      if (searchResultsTable != null) {
        response.append("[");
        HtmlTableBody body = searchResultsTable.getBodies().get(0);
        List<HtmlTableRow> rows = body.getRows();
        if (rows != null && rows.size() > 0) {
          for (HtmlTableRow row : rows) {
            List<HtmlTableCell> cells = row.getCells();
            response.append("{");
            if (cells != null && cells.size() > 0) {
              response.append("\"Index\" : \""
                  + cells.get(GenecardData.Index.getValue()).getTextContent()
                  + "\",");
              response.append("\"Symbol\" : \""
                  + cells.get(GenecardData.Symbol.getValue()).getTextContent()
                  + "\",");
              response.append("\"Description\" : \""
                  + cells.get(GenecardData.Description.getValue())
                      .getTextContent() + "\",");
              response.append("\"Category\" : \""
                  + cells.get(GenecardData.Category.getValue()).getTextContent()
                  + "\",");
              response.append("\"GIFtS\" : \""
                  + cells.get(GenecardData.GIFtS.getValue()).getTextContent()
                  + "\",");
              response.append("\"GC_ID\" : \""
                  + cells.get(GenecardData.GC_ID.getValue()).getTextContent()
                  + "\",");
              response.append("\"Score\" : \""
                  + cells.get(GenecardData.Score.getValue()).getTextContent()
                  + "\"");

              response.append("},");
            }
          }

          response.deleteCharAt(response.length() - 1);
        }

        response.append("]");
      }
      System.out.println(page.asXml());
      return Response.ok(response.toString(), MediaType.APPLICATION_JSON).build();
    }

  }
  
  private boolean validateCreds(){
    return email != null && 
        !email.equals("null") && 
        !email.equals("") && 
        password != null && 
        !password.equals("") && 
        !password.equals("null");
  }
  
  private HtmlPage login(WebClient webClient) throws FailingHttpStatusCodeException, IOException{
    WebRequest req = new WebRequest(new URL(BASE_URL));
    System.out.println(req);
    final HtmlPage page = webClient.getPage(req);
    HtmlPage loginPage = page.getAnchorByHref(LOGIN_LINK).click();
    HtmlForm form = loginPage.getForms().get(0);
    DomNodeList<HtmlElement> inputs = form.getElementsByTagName("input");
    HtmlInput emailInput = (HtmlInput)inputs.get(0);
    HtmlInput pass = (HtmlInput)inputs.get(1);
    emailInput.setTextContent(email);
    pass.setTextContent(password);
    inputs = form.getElementsByTagName("button");
    return (HtmlPage)inputs.get(0).click();
  }

  class MyWebConnection extends HttpWebConnection {
    public MyWebConnection(WebClient webClient) {
      super(webClient);
    }

    @Override
    protected HttpClientBuilder getHttpClientBuilder() {
      HttpClientBuilder builder = super.getHttpClientBuilder();

      CookieStore cookieStore = new BasicCookieStore();
      CookieSpecProvider csf = new CookieSpecProvider() {
        @Override
        public CookieSpec create(HttpContext context) {
          return new BrowserCompatSpec() {
            @Override
            public void validate(Cookie cookie, CookieOrigin origin)
                throws MalformedCookieException {
              // accept all cookies
            }

          };

        }
      };

      RequestConfig requestConfig = RequestConfig.custom()
          .setCookieSpec("easy").build();

      builder.setDefaultRequestConfig(requestConfig);
      builder.setDefaultCookieStore(cookieStore);
      builder.setDefaultCookieSpecRegistry(RegistryBuilder
          .<CookieSpecProvider> create().register(CookieSpecs.BEST_MATCH, csf)
          .register(CookieSpecs.BROWSER_COMPATIBILITY, csf)
          .register("easy", csf).build());

      return builder;
    }
  }

}
