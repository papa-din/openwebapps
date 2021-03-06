package com.mozilla.labs.soup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class SyncService {
	MainActivity mact;
	
	public SyncService(MainActivity ma) {
		mact = ma;
	}
	
	protected String doGet(String url, String usr, String pwd) {
		StringBuilder result = new StringBuilder();
		SchemeRegistry regs = new SchemeRegistry();
		regs.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
		HttpParams params = new BasicHttpParams();
		SingleClientConnManager mgr = new SingleClientConnManager(params, regs);
		
		DefaultHttpClient clnt = new DefaultHttpClient(mgr, params);
		HttpGet get = new HttpGet(url);
		
		if (usr != null && pwd != null) {
			Credentials crd = new UsernamePasswordCredentials(usr, pwd);
			BasicScheme bs = new BasicScheme();
			try {
				get.addHeader(bs.authenticate(crd, get));
			} catch (AuthenticationException e) {
				e.printStackTrace();
			}
		}
		
		try {
			int tmp;
			HttpResponse resp = clnt.execute(get);
			int code = resp.getStatusLine().getStatusCode();
			
			// FIXME: Somehow getContent() doesn't give us the real payload
			// in case the return code is not 200
			System.out.println("Code is: " + code + " " + resp.getStatusLine().getReasonPhrase() + " for " + url);
			
			if (code == 200) {
				HttpEntity hen = resp.getEntity();
				BufferedReader is = new BufferedReader(new InputStreamReader(hen.getContent()));
				while ((tmp = is.read()) != -1) {
					result.append((char)tmp);
				}
			} else if (code == 401) {
				result.append("\"Authentication failed\"");
			} else if (code == 404) {
				result.append("\"record not found\"");
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		return result.toString();
	}
	
	protected String doGet(String url) {
		return doGet(url, null, null);
	}
	
	public AppsAdapter parseManifest(String manifest) {
		// TODO: perform merge with local app repository as specified in
		// https://github.com/mozilla/openwebapps/blob/master/docs/SYNC.md
		// for now, we simply display a list of "available" apps
		try {
			JSONObject top = (JSONObject) new JSONTokener(manifest).nextValue();
			String payload = top.getString("payload");
			JSONObject list = (JSONObject) new JSONTokener(payload).nextValue();
			JSONObject installed = list.getJSONObject("installed");
			System.out.println("Got " + installed.length() + " installed apps!");
			return new AppsAdapter(installed, mact);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new AppsAdapter(new JSONObject(), mact);
		}
	}
	
	public String getCluster(String user) {
		String chkNode = "https://auth.services.mozilla.com/user/1.0/" + user + "/node/weave";
		String nodeVal = doGet(chkNode);
		
		// actual payload value is "No location" see comment in doGet()
		if (nodeVal.equals("\"record not found\"")) {
			return "";
		}
		return nodeVal;
	}
	
	public String getManifest(String user, String password, String node) {
		String payload = doGet(node + "1.0/" + user + "/storage/openwebapps/apps", user, password);
		System.out.println("Got payload " + payload + "\nof length:" + payload.length());
		
		if (payload.equals("\"Authentication failed\"")) {
			return "";
		} else if (payload.equals("\"record not found\"")) {
			return "{}";
		} else {
			return payload;
		}
	}
}
