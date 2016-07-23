package ca.uhn.fhir.rest.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.dstu3.model.OperationOutcome;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Validate;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.ValidationModeEnum;
import ca.uhn.fhir.rest.client.api.IRestfulClient;
import ca.uhn.fhir.rest.server.Constants;
import ca.uhn.fhir.util.TestUtil;

public class NonGenericClientDstu3Test {
	private static FhirContext ourCtx;
	private HttpClient myHttpClient;
	private HttpResponse myHttpResponse;

	@Before
	public void before() {
		myHttpClient = mock(HttpClient.class, new ReturnsDeepStubs());
		ourCtx.getRestfulClientFactory().setHttpClient(myHttpClient);
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		myHttpResponse = mock(HttpResponse.class, new ReturnsDeepStubs());
	
		System.setProperty(BaseClient.HAPI_CLIENT_KEEPRESPONSES, "true");

	}

	private String extractBodyAsString(ArgumentCaptor<HttpUriRequest> capt, int theIdx) throws IOException {
		String body = IOUtils.toString(((HttpEntityEnclosingRequestBase) capt.getAllValues().get(theIdx)).getEntity().getContent(), "UTF-8");
		return body;
	}

	@Test
	public void testValidateResourceOnly() throws Exception {
		IParser p = ourCtx.newXmlParser();

		OperationOutcome conf = new OperationOutcome();
		conf.getText().setDivAsString("OK!");

		final String respString = p.encodeResourceToString(conf);
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(respString), Charset.forName("UTF-8"));
			}
		});

		IClient client = ourCtx.newRestfulClient(IClient.class, "http://example.com/fhir");

		Patient patient = new Patient();
		patient.addName().addFamily("FAM");
		
		int idx = 0;
		MethodOutcome outcome = client.validate(patient, null, null);
		String resp = ourCtx.newXmlParser().encodeResourceToString(outcome.getOperationOutcome());
		assertEquals("<OperationOutcome xmlns=\"http://hl7.org/fhir\"><text><div xmlns=\"http://www.w3.org/1999/xhtml\">OK!</div></text></OperationOutcome>", resp);
		assertEquals("http://example.com/fhir/$validate", capt.getAllValues().get(idx).getURI().toString());
		String request = extractBodyAsString(capt,idx);
		assertEquals("<Parameters xmlns=\"http://hl7.org/fhir\"><parameter><name value=\"resource\"/><resource><Patient xmlns=\"http://hl7.org/fhir\"><name><family value=\"FAM\"/></name></Patient></resource></parameter></Parameters>", request);

		idx = 1;
		outcome = client.validate(patient, ValidationModeEnum.CREATE, "http://foo");
		resp = ourCtx.newXmlParser().encodeResourceToString(outcome.getOperationOutcome());
		assertEquals("<OperationOutcome xmlns=\"http://hl7.org/fhir\"><text><div xmlns=\"http://www.w3.org/1999/xhtml\">OK!</div></text></OperationOutcome>", resp);
		assertEquals("http://example.com/fhir/$validate", capt.getAllValues().get(idx).getURI().toString());
		request = extractBodyAsString(capt,idx);
		assertEquals("<Parameters xmlns=\"http://hl7.org/fhir\"><parameter><name value=\"resource\"/><resource><Patient xmlns=\"http://hl7.org/fhir\"><name><family value=\"FAM\"/></name></Patient></resource></parameter><parameter><name value=\"mode\"/><valueString value=\"create\"/></parameter><parameter><name value=\"profile\"/><valueString value=\"http://foo\"/></parameter></Parameters>", request);
	}


	private interface IClient extends IRestfulClient {
		
		@Validate
		MethodOutcome validate(@ResourceParam IBaseResource theResource, @Validate.Mode ValidationModeEnum theMode, @Validate.Profile String theProfile);
		
	}
	
	@AfterClass
	public static void afterClassClearContext() {
		TestUtil.clearAllStaticFieldsForUnitTest();
	}

	@BeforeClass
	public static void beforeClass() {
		ourCtx = FhirContext.forDstu3();
	}

}