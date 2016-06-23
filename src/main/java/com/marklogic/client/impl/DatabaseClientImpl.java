/*
 * Copyright 2012-2016 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.client.impl;

import java.io.OutputStream;
import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.marklogic.client.admin.ExtensionMetadata;
import com.marklogic.client.document.BinaryDocumentManager;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.ForbiddenUserException;
import com.marklogic.client.document.GenericDocumentManager;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.semantics.GraphManager;
import com.marklogic.client.semantics.SPARQLQueryManager;
import com.marklogic.client.util.RequestLogger;
import com.marklogic.client.eval.ServerEvaluationCall;
import com.marklogic.client.extensions.ResourceManager;
import com.marklogic.client.DatabaseClientFactory.HandleFactoryRegistry;
import com.marklogic.client.admin.ServerConfigurationManager;
import com.marklogic.client.alerting.RuleManager;
import com.marklogic.client.document.TextDocumentManager;
import com.marklogic.client.Transaction;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.pojo.PojoRepository;
import com.marklogic.client.impl.PojoRepositoryImpl;
import com.marklogic.client.io.marker.TriplesReadHandle;
import com.marklogic.client.io.marker.TriplesWriteHandle;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.DatabaseClientFactory.SSLHostnameVerifier;
import com.marklogic.client.DatabaseClientFactory.SecurityContext;

import javax.net.ssl.SSLContext;

public class DatabaseClientImpl implements DatabaseClient {
	static final private Logger logger = LoggerFactory.getLogger(DatabaseClientImpl.class);

	private RESTServices          services;
	private String                host;
	private int                   port;
	private String                database;
	private String                user;
	private String                password;
	private Authentication        type;
	private String                forestName;
	private SSLContext            context;
	private SSLHostnameVerifier   verifier;
	private HandleFactoryRegistry handleRegistry;
	private SecurityContext       securityContext; // Would be used once we remove Authentication entirely

	public DatabaseClientImpl(RESTServices services, String host, int port, String database,
		String user, String password, Authentication type, String forestName, SSLContext context, SSLHostnameVerifier verifier)
	{
		this.services = services;
		this.host     = host;
		this.port     = port;
		this.database = database;
		this.user     = user;
		this.password = password;
		this.type     = type;
		this.forestName=forestName;
		this.context  = context;
		this.verifier = verifier;
		services.setDatabaseClient(this);
	}

	public HandleFactoryRegistry getHandleRegistry() {
		return handleRegistry;
	}
	public void setHandleRegistry(HandleFactoryRegistry handleRegistry) {
		this.handleRegistry = handleRegistry;
	}

	@Override
	public Transaction openTransaction() throws ForbiddenUserException, FailedRequestException {
		return services.openTransaction(null, TransactionImpl.DEFAULT_TIMELIMIT);
	}

	@Override
	public Transaction openTransaction(String name) throws ForbiddenUserException, FailedRequestException {
		return services.openTransaction(name, TransactionImpl.DEFAULT_TIMELIMIT);
	}

	@Override
	public Transaction openTransaction(String name, int timeLimit) throws ForbiddenUserException, FailedRequestException{
		return services.openTransaction(name, timeLimit);
	}

	@Override
	public GenericDocumentManager newDocumentManager() {
		GenericDocumentImpl docMgr = new GenericDocumentImpl(services);
		docMgr.setForestName(getForestName());
		docMgr.setHandleRegistry(getHandleRegistry());
		return docMgr;
	}
	@Override
	public BinaryDocumentManager newBinaryDocumentManager() {
		BinaryDocumentImpl docMgr = new BinaryDocumentImpl(services);
		docMgr.setForestName(getForestName());
		docMgr.setHandleRegistry(getHandleRegistry());
		return docMgr;
	}
	@Override
	public JSONDocumentManager newJSONDocumentManager() {
		JSONDocumentImpl docMgr = new JSONDocumentImpl(services);
		docMgr.setForestName(getForestName());
		docMgr.setHandleRegistry(getHandleRegistry());
		return docMgr;
	}
	@Override
	public TextDocumentManager newTextDocumentManager() {
		TextDocumentImpl docMgr = new TextDocumentImpl(services);
		docMgr.setForestName(getForestName());
		docMgr.setHandleRegistry(getHandleRegistry());
		return docMgr;
	}
	@Override
	public XMLDocumentManager newXMLDocumentManager() {
		XMLDocumentImpl docMgr = new XMLDocumentImpl(services);
		docMgr.setForestName(getForestName());
		docMgr.setHandleRegistry(getHandleRegistry());
		return docMgr;
	}

	@Override
	public RuleManager newRuleManager() {
		RuleManagerImpl ruleMgr = new RuleManagerImpl(services);
		ruleMgr.setHandleRegistry(getHandleRegistry());
		return ruleMgr;
	}
	@Override
	public QueryManager newQueryManager() {
		QueryManagerImpl queryMgr = new QueryManagerImpl(services);
		queryMgr.setHandleRegistry(getHandleRegistry());
		return queryMgr;
	}
	@Override
	public ServerConfigurationManager newServerConfigManager() {
		ServerConfigurationManagerImpl configMgr =
			new ServerConfigurationManagerImpl(services);
		configMgr.setHandleRegistry(getHandleRegistry());
		return configMgr;
	}
	@Override
	public <T, ID extends Serializable> PojoRepository<T, ID> newPojoRepository(Class<T> clazz, Class<ID> idClass) {
		return new PojoRepositoryImpl<T, ID>(this, clazz, idClass);

	}

	@Override
	public RequestLogger newLogger(OutputStream out) {
		return new RequestLoggerImpl(out);
	}

	@Override
    public <T extends ResourceManager> T init(String resourceName, T resourceManager) {
		if (resourceManager == null)
			throw new IllegalArgumentException("Cannot initialize null resource manager");
		if (resourceName == null)
			throw new IllegalArgumentException("Cannot initialize resource manager with null resource name");
		if (resourceName.length() == 0)
			throw new IllegalArgumentException("Cannot initialize resource manager with empty resource name");

		((ResourceManagerImplementation) resourceManager).init(
				new ResourceServicesImpl(services,resourceName)
				);

		return resourceManager;
	}

	@Override
	public void release() {
		if (logger.isInfoEnabled())
			logger.info("Releasing connection");

		if (services != null)
			services.release();
	}

	@Override
	protected void finalize() throws Throwable {
		release();
		super.finalize();
	}

	@Override
	public Object getClientImplementation() {
		if (services == null)
			return null;
		return services.getClientImplementation();
	}

	// undocumented backdoor access to JerseyServices
	public RESTServices getServices() {
		return services;
	}

	@Override
	public ServerEvaluationCall newServerEval() {
		return new ServerEvaluationCallImpl(services, getHandleRegistry());
	}

	@Override
	public GraphManager newGraphManager() {
		return new GraphManagerImpl<TriplesReadHandle, TriplesWriteHandle>(services, getHandleRegistry());
	}

	@Override
	public SPARQLQueryManager newSPARQLQueryManager() {
		// TODO Auto-generated method stub
		return new SPARQLQueryManagerImpl(services);
	}

	@Override
	public String getHost() {
		return host;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public String getDatabase() {
		return database;
	}

	@Override
	public String getUser() {
		return user;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public Authentication getAuthentication() {
		return type;
	}

	@Override
	public String getForestName() {
		return forestName;
	}

	@Override
	public SSLContext getSSLContext() {
		return context;
	}

	@Override
	public SSLHostnameVerifier getSSLHostnameVerifier() {
		return verifier;
	}

	@Override
	public SecurityContext getSecurityContext() {
		return securityContext;
	}
}
