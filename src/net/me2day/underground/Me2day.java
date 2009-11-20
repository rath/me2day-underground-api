package net.me2day.underground;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

/**
 * 
 * @author rath
 * @version 1.0, since 2009/11/18
 */
public class Me2day {
	
	@Getter @Setter
	private String username;
	@Getter @Setter
	private String password;
	private HttpClient client = new DefaultHttpClient();
	
	public Me2day() { 
//		CookieStore store = new MyCookieStore();
	}
	
	private void setDefaultHeaders( HttpRequestBase base ) {
		base.addHeader("Accept-Encoding", "gzip,deflate");
		base.addHeader("Accept", "text/javascript, text/html, application/xml, text/xml, */*");
	}
	
	/**
	 * 로그인!
	 * 
	 * @throws IOException
	 */
	@SneakyThrows(org.apache.http.client.ClientProtocolException.class)
	public void login() throws IOException {
		HttpGet index = new HttpGet("http://me2day.net");
		client.execute(index).getEntity().consumeContent();
		
		HttpPost post = new HttpPost("http://me2day.net/account/login_select");
		setDefaultHeaders(post);
		
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("user_id", getUsername()));
		params.add(new BasicNameValuePair("password", ""));
		params.add(new BasicNameValuePair("redirect_url", ""));
		
		UrlEncodedFormEntity formData = new UrlEncodedFormEntity(params, "UTF-8");
		post.setEntity(formData);
		
		HttpResponse response = client.execute(post);
		HttpEntity entity = response.getEntity();
		String jsonResult = EntityUtils.toString(entity, "UTF-8");
		entity.consumeContent();
		
		Pattern p = Pattern.compile("url\":\"(.*?)\"");
		Matcher m = p.matcher(jsonResult);
		if( !m.find() ) {
			System.out.println(jsonResult); 
			throw new IOException("/account/login_select may be changed his layout! ouch!");
		}
	
		String url = m.group(1);
		url = url.replace("\\", "");
		
//		System.out.println("URL: " + url);
		
		HttpGet loginUrl = new HttpGet(url + "&user_id=" + getUsername() + "&password=" + 
				getPassword() + "&save_login=0&callback=IDontWantCallback");
		response = client.execute(loginUrl);
		String loginResult = EntityUtils.toString(response.getEntity(), "UTF-8");
		
		response.getEntity().consumeContent();
		
		p = Pattern.compile("result\":true");
		m = p.matcher(loginResult);
		if( !m.find() ) 
			throw new IOException("login failed");
	}
	
	/**
	 * 내가 가입한(만든) 밴드의 목록을 가져온다.
	 * 
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	@SneakyThrows(org.apache.http.client.ClientProtocolException.class)
	public List<Band> getMyBands() throws IOException {
		HttpGet get = new HttpGet(String.format("http://me2day.net/band/%s/setting", getUsername()));
		HttpResponse response = client.execute(get);
		String page = EntityUtils.toString(response.getEntity(), "UTF-8");
		response.getEntity().consumeContent();
		
		Pattern p = Pattern.compile("<div id=\"my_bands_list\">.*?<tbody>(.*?)</tbody>", Pattern.DOTALL);
		Matcher m = p.matcher(page);
		if( !m.find() )
			throw new IOException("Oops, /band/username/setting page may be modified!");
		
		List<Band> bands = new ArrayList<Band>();
		
		String items = m.group(1);
		p = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
		m = p.matcher(items);
		while( m.find() ) {
			String item = m.group(1);
			Band band = new Band();
			
			Pattern p0 = Pattern.compile("<td class=\"band_name\">.*?<a href=\"(.*?)\">", Pattern.DOTALL);
			Matcher m0 = p0.matcher(item);
			if( m0.find() ) {
				band.setUrl(new URL("http://me2day.net" + m0.group(1)));
			}
			
			Pattern p1 = Pattern.compile("<td class=\"band_name\">(.*?)</td>", Pattern.DOTALL);
			Matcher m1 = p1.matcher(item);
			if( m1.find() ) {
				String name = m1.group(1);
				name = name.replaceAll("<[^>]+>", "").trim();
				band.setName(name);
			}
			bands.add(band);
		}
		
		return bands;
	}
	
	@SneakyThrows(org.apache.http.client.ClientProtocolException.class) 
	public void createBandPost( Band band, String body, String tags ) throws IOException {
		HttpPost post = new HttpPost(String.format("http://me2day.net/%s/create", band.getId()));
		setDefaultHeaders(post);
		
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("post[body]", body));
		params.add(new BasicNameValuePair("post[tags]", tags));
		params.add(new BasicNameValuePair("post[icon]", "15"));
		params.add(new BasicNameValuePair("topic", ""));
		params.add(new BasicNameValuePair("container_type", "band"));
		
		UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(params, "UTF-8");
		post.setEntity(formEntity);
		
		HttpResponse response = client.execute(post);
		response.getEntity().consumeContent();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
		System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "false");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "INFO");
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "ERROR");

		Me2day api = new Me2day();
		api.setUsername("rath");
		api.setPassword(args[0]);
		api.login();
		System.out.println( api.getMyBands() );
		
		api.createBandPost(Band.create("rath", "me2adalt"), "테스트", "태그");
	}

}
