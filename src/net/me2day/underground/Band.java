package net.me2day.underground;

import java.net.URL;

import lombok.Data;
import lombok.SneakyThrows;

public @Data class Band {
	private String name;
	private URL url;
	
	public String getId() {
		String uri = url.toString();
		int i0 = uri.lastIndexOf("/");
		return uri.substring(i0+1);
	}
	
	@SneakyThrows(java.net.MalformedURLException.class)
	public static Band create( String username, String id ) {
		Band band = new Band();
		band.setUrl(new URL(String.format("http://me2day.net/%s/band/%s", username, id)));
		return band;
	}
}
