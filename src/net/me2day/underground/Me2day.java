package net.me2day.underground;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.me2day.underground.util.BASE64;

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
	
	Pattern p = Pattern.compile("<div class=\"sec_post\">(.*?)(?=<div class=\"sec_post\">)", Pattern.DOTALL);
	Pattern pId = Pattern.compile("<a href=\"/(.+?)/post/(.+?)/metoos\"");
	Pattern pNickname = Pattern.compile("<span class=\"name_text\"><a .*?>(.*?)</a></span>");
	Pattern pProfileImage = Pattern.compile("<div class=\"image_box\" >.*?<img src=\"([^\"]*)\"", Pattern.DOTALL);
	Pattern pIcon = Pattern.compile("<div class=\"icons_slt\">.*?<img.*?src=\"([^\"]*)\"", Pattern.DOTALL);
	Pattern pMetooCnt = Pattern.compile("<div class=\"metoo_cnt\">.*?<a href=\".*?/metoos\".*?>([0-9]+)</a>", Pattern.DOTALL);
	Pattern pTimestamp = Pattern.compile("<span class=\"timestamp\" title=\"(.*?)\">");
	Pattern pContent = Pattern.compile("<div class=\"post_cont\">.*?<p>\\s*(.*?)\\s*<span", Pattern.DOTALL);
	Pattern pPhoto = Pattern.compile("new RichContentLink.*?'(http://.*?)'");
	Pattern pTags = Pattern.compile("<a href.*?rel=\"tag\">(.*?)</a>", Pattern.DOTALL);
	private String nextPageOffset;
	
	@Getter @Setter
	private boolean downloadImages;
	
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
	
	@SneakyThrows(org.apache.http.client.ClientProtocolException.class) 
	public void giftToken( String userIdToReceive, int amount ) throws IOException {
		HttpPost post = new HttpPost(String.format("http://me2day.net/%s/setting/token_gift", getUsername()));
		setDefaultHeaders(post);
		
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("token_gift_id", userIdToReceive));
		params.add(new BasicNameValuePair("token_to_gift", String.valueOf(amount)));
		
		UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(params, "UTF-8");
		post.setEntity(formEntity);
		
		HttpResponse response = client.execute(post);
		String result = EntityUtils.toString(response.getEntity(), "UTF-8");
		response.getEntity().consumeContent();
		
		Pattern p = Pattern.compile("result\":true");
		Matcher m = p.matcher(result);
		if( !m.find() ) {
			System.out.println(result);
			throw new IOException("giftToken failed!");
		}
	}
	
	public List<Map<String, String>> getMetoos() throws IOException {
		List<Map<String, String>> list = new ArrayList<Map<String, String>>();
		
		String queryString = "";
		if( nextPageOffset!=null ) 
			queryString = "?from=" + nextPageOffset;
		HttpGet get = new HttpGet(String.format("http://me2day.net/%s/metoo%s", getUsername(), queryString));
		get.addHeader("Accept", "text/html");
		
		HttpResponse response = client.execute(get);
		String result = EntityUtils.toString(response.getEntity(), "UTF-8");
		response.getEntity().consumeContent();
		
		int lastOffset = 0;
		Matcher matcher = p.matcher(result);
		while( matcher.find() ) {
			String group = matcher.group(1);

			Map<String, String> item = parseEntry(group);
			// 마지막 index를 검사해야 한다.
			list.add(item);
			lastOffset = matcher.end();
		}
		
		Map<String, String> lastItem = parseEntry(result.substring(lastOffset));
		list.add(lastItem);
		
		Pattern pNext = Pattern.compile("<a href=\".*?from=([0-9]+)\" id=\"get_mystream_link\"");
		matcher = pNext.matcher(result);
		if( matcher.find() ) {
			nextPageOffset = matcher.group(1);
		}
		
		return list;
	}
	
	public void resetPagination() {
		this.nextPageOffset = null;
	}

	
	protected Map<String, String> parseEntry( String group ) throws IOException {
		Map<String, String> item = new HashMap<String, String>();
		
		Matcher m;
		m = pId.matcher(group);
		if( m.find() ) {
			item.put("author.id", m.group(1));
			item.put("post.id", m.group(2));
		}
		m = pNickname.matcher(group);
		if( m.find() ) {
			item.put("author.nickname", m.group(1));
		}
		m = pProfileImage.matcher(group);
		if( m.find() ) {
			item.put("profile.url", m.group(1));
			if( downloadImages ) 
				item.put("profile.url.bytes", getContent(m.group(1)));
		}
		m = pIcon.matcher(group);
		if( m.find() ) { 
			item.put("icon.url", m.group(1));
			if( downloadImages ) 
				item.put("icon.url.bytes", getContent(m.group(1)));
		}
		m = pMetooCnt.matcher(group);
		if( m.find() ) {
			item.put("metoo.count", m.group(1));
		}
		m = pTimestamp.matcher(group);
		if( m.find() ) {
			item.put("timestamp", m.group(1));
		}
		m = pContent.matcher(group);
		if( m.find() ) {
			String content = m.group(1).trim();
			item.put("content.html", content);
			item.put("content.plain", content.replaceAll("<[^>]+>", ""));
		}
		m = pPhoto.matcher(group);
		if( m.find() ) {
			// 없을 수도 있어요.
			String photoPage = m.group(1).replaceAll("&amp;", "&");
			item.put("me2photo.page", photoPage);
			if( downloadImages ) {				
				String photoSource = getPage(photoPage);
				Pattern p = Pattern.compile("<img src=\"(.*?)\"");
				m = p.matcher(photoSource);
				if( m.find() ) {
					item.put("me2photo.page.bytes", getContent(m.group(1)));
				}
			}
		}
		m = pTags.matcher(group);
		StringBuilder tags = new StringBuilder();
		while( m.find() ) { 
			tags.append(m.group(1));
			tags.append(' ');
		}
		item.put("tags", tags.toString());
		
//		System.out.println(item.get("post.id") + ": " + item.get("content.plain"));
//		System.out.println(item.get("post.id") + ": " + item.get("tags"));
		return item;
	}
	
	private String getPage( String url ) throws IOException {
		HttpGet get = new HttpGet(url);
		HttpResponse res = client.execute(get);
		String page = EntityUtils.toString(res.getEntity(), "UTF-8");
		res.getEntity().consumeContent();
		return page;
	}
	
	private String getContent( String url ) throws IOException {
		HttpGet get = new HttpGet(url);
		HttpResponse res = client.execute(get);
		String ret = new BASE64(false).encode(EntityUtils.toByteArray(res.getEntity()));
		res.getEntity().consumeContent();
		return ret;
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
		api.setDownloadImages(true);
		api.setUsername("rath");
		api.setPassword(args[0]);
		api.login();
		List<Map<String, String>> metoos = new ArrayList<Map<String, String>>();
		while(true) {
			List<Map<String, String>> list = api.getMetoos();
			if( list.size()==0 ) 
				break;
			metoos.addAll(list);
			if( metoos.size() >= 100 ) 
				break;
		}
		
		ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("metoos.dat"));
		oos.writeObject(metoos);
		oos.flush();
		oos.close();
		
		System.exit(1);
		api.login();
		System.out.println( api.getMyBands() );
		
//		api.createBandPost(Band.create("rath", "me2adalt"), "테스트", "태그");
//		api.giftToken("xrath", 15);
	}

}
