package com.marklogic.client.iml;

import java.util.Set;

import com.marklogic.client.AbstractDocument.Metadata;
import com.marklogic.client.DatabaseClientFactory.Authentication;

public interface RESTServices {
	public void connect(String host, int port, String user, String password, Authentication type);
	public void release();

	public void delete(String uri);
	public <T> T get(Class<T> as, String uri, String mimetype, Set<Metadata> categories);
	public void head(String uri);
	public void put(String uri, String mimetype, Object value, Set<Metadata> categories);
}
