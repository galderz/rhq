/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.jbossatx.jta.recovery;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.naming.InitialContext;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAResource;

import com.arjuna.ats.jta.recovery.XAResourceRecovery;

import org.jboss.logging.Logger;
import org.jboss.security.SecurityAssociation;
import org.jboss.security.SimplePrincipal;

/**
 * This is an enhanced version of JBossTM's AppServerJDBCXARecovery implementation.
 * The only thing this implementation does differently is it becomes tolerant of
 * the times when the data source is not yet deployed.
 *
 * This provides recovery for compliant JDBC drivers accessed via datasources deployed in JBossAS 4.2
 * It is not meant to be db driver specific.
 *
 * This code is based on JDBCXARecovery, which expects JNDI to contain an XADataSource implementation.
 * In JBossAS, the object created in JNDI when a -ds.xml file containing <xa-datasource> element is used,
 * does not implement the XADataSource interface. We therefore use this modified code to pull the
 * datasource configuration information from the app server's JMX and instantiate an XADataSource from
 * which it can then create an XAConnection.
 *
 * To use this class, add an XAResourceRecovery entry in the jta section of jbossjta-properties.xml
 * for each datasource for which you need recovery, ensuring the value ends with ;<datasource-name>
 * i.e. the same value as is in the -ds.xml jndi-name element.
 * You also need the XARecoveryModule enabled and appropriate values for nodeIdentifier and xaRecoveryNode set.
 * See the JBossTS recovery guide if you are unclear on how the recovery system works.
 *
 * Note: This implementation expects to run inside the app server JVM. It probably won't work
 * if you configure the recovery manager to run as a separate process. (JMX can be accessed remotely,
 * but it would need code changes to the lookup function and some classpath additions).
 *
 *  <properties depends="arjuna" name="jta">
 *  ...
 *    <property name="com.arjuna.ats.jta.recovery.XAResourceRecovery1" value= "com.arjuna.ats.internal.jbossatx.jta.AppServerJDBCXARecovery;MyExampleDbName"/>
 *    <!-- xaRecoveryNode should match value in nodeIdentifier or be * -->
 *    <property name="com.arjuna.ats.jta.xaRecoveryNode" value="1"/>
 *
 */
public class AppServerJDBCXARecovery implements XAResourceRecovery {

    // implementation based on com.arjuna.ats.internal.jdbc.recovery.JDBCXARecovery

    public AppServerJDBCXARecovery() throws SQLException {
        if (log.isDebugEnabled()) {
            log.debug("AppServerJDBCXARecovery<init>");
        }

        _hasMoreResources = false;
        _connectionEventListener = new LocalConnectionEventListener();
    }

    /**
     * The recovery module will have chopped off this class name already. The
     * parameter should specify a jndi name for the datasource.
     */

    public boolean initialise(String parameter) throws SQLException {
        if (log.isDebugEnabled()) {
            log.debug("AppServerJDBCXARecovery.initialise(" + parameter + ")");
        }

        if (parameter == null)
            return false;

        retrieveData(parameter, _DELIMITER);

        // don't create the datasource yet, we'll do it lazily. Just keep its id.
        _dataSourceId = parameter;

        return true;
    }

    public synchronized XAResource getXAResource() throws SQLException {
        createConnection();

        if (_connection == null) {
            throw new SQLException("The data source named [" + _dataSourceId + "] is not deployed.");
        }

        return _connection.getXAResource();
    }

    public boolean hasMoreResources() {
        if (_dataSource == null)
            try {
                createDataSource();
            } catch (SQLException sqlException) {
                return false;
            }

        if (_dataSource != null) {
            _hasMoreResources = !_hasMoreResources;

            return _hasMoreResources;
        } else
            return false;
    }

    /**
     * Lookup the XADataSource in JNDI.
     */
    private final void createDataSource() throws SQLException {
        try {
            if (_dataSource == null) {
                // This is where we do JBossAS specific magic. Use the JMX to fetch the name of the XADataSource class
                // and its config params so that we don't have to duplicate the information in the -ds.xml file.

                // TODO: can we make this flexible enough to handle remote app server processes, so that
                // we can run the recovery manager out of process? The server addr would need to be on a per
                // jndiname basis, as we may be doing recovery for a whole cluster. We would need AS
                // jmx classes (and the db drivers naturally) on the recovery manager classpath.

                InitialContext context = new InitialContext();
                MBeanServerConnection server = (MBeanServerConnection) context.lookup("jmx/invoker/RMIAdaptor");
                ObjectName objectName = new ObjectName("jboss.jca:name=" + _dataSourceId
                    + ",service=ManagedConnectionFactory");

                if(_username !=null && _password !=null)
                {
                	SecurityAssociation.setPrincipal(new SimplePrincipal(_username));
                	SecurityAssociation.setCredential(_password);
                }

                String className = (String) server.invoke(objectName, "getManagedConnectionFactoryAttribute",
                    new Object[] { "XADataSourceClass" }, new String[] { "java.lang.String" });
                log.debug("AppServerJDBCXARecovery datasource classname = " + className);
                String properties = (String) server.invoke(objectName, "getManagedConnectionFactoryAttribute",
                    new Object[] { "XADataSourceProperties" }, new String[] { "java.lang.String" });
                // debug disabled due to security paranoia - it may log datasource password in cleartext.
                // log.debug("AppServerJDBCXARecovery.result="+properties);

                ObjectName txCmObjectName = new ObjectName("jboss.jca:name=" +_dataSourceId + ",service=XATxCM");
                String securityDomainName = (String) server.getAttribute(txCmObjectName, "SecurityDomainJndiName");
                log.debug("Security domain name associated with JCA ConnectionManager jboss.jca:name=" +_dataSourceId + ",service=XATxCM"+" is:"+securityDomainName);

                if(securityDomainName != null && !securityDomainName.equals(""))
                {
                	ObjectName _objectName = new ObjectName("jboss.security:service=XMLLoginConfig");
                	String config = (String)server.invoke(_objectName, "displayAppConfig", new Object[] {securityDomainName}, new String[] {"java.lang.String"});
                	String loginModuleClass = getValueForLoginModuleClass(config);
            		_dbUsername = getValueForKey(config, _USERNAME);
            		String _encryptedPassword = getValueForKey(config, _PASSWORD);
            		if (loginModuleClass.trim().equals("org.jboss.resource.security.SecureIdentityLoginModule"))
            		{
            		    _dbPassword = new String (decode(_encryptedPassword));
            		}
            		else if (loginModuleClass.trim().equals("org.jboss.resource.security.JaasSecurityDomainIdentityLoginModule"))
            		{
            		    String jaasSecurityDomain = getValueForKey(config, "jaasSecurityDomain");
            		    _dbPassword = new String (decodePBE(server, _encryptedPassword, jaasSecurityDomain));
            		}
            		_encrypted = true;
                }

                try {
                    _dataSource = getXADataSource(className, properties);
                    _supportsIsValidMethod = true; // assume it does; we'll lazily check the first time we try to connect
                } catch (Exception e) {
                    _dataSource = null;
                    log.error("AppServerJDBCXARecovery.createDataSource got exception during getXADataSource call: "
                        + e.toString(), e);
                    throw new SQLException(e.toString());
                }
            }
        } catch (MBeanException mbe) {
            if (mbe.getTargetException() instanceof InstanceNotFoundException) {
                // this is an expected condition when the data source is not yet deployed
                // just ignore this for now, the next time around, we try again to see if its deployed yet
                return;
            }
            log.error("AppServerJDBCXARecovery.createDataSource got exception " + mbe.toString(), mbe);

            throw new SQLException(mbe.toString());
        } catch (SQLException ex) {
            log.error("AppServerJDBCXARecovery.createDataSource got exception " + ex.toString(), ex);

            throw ex;
        } catch (Exception e) {
            log.error("AppServerJDBCXARecovery.createDataSource got exception " + e.toString(), e);

            throw new SQLException(e.toString());
        }
    }

    /**
     * Create the XAConnection from the XADataSource.
     */

    private final void createConnection() throws SQLException {
        try {
            if (_dataSource == null) {
                createDataSource();
                // if we still don't have it, its because the data source isn't deployed yet
                if (_dataSource == null) {
                    return;
                }
            }

            Boolean isConnectionValid;
            try {
                if (_connection != null && _supportsIsValidMethod) {
                    Connection connection = _connection.getConnection();
                    Method method = connection.getClass().getMethod("isValid", Integer.class);
                    isConnectionValid = (Boolean) method.invoke(connection, Integer.valueOf(5));
                } else {
                    isConnectionValid = Boolean.FALSE;
                }
            } catch (NoSuchMethodException nsme) {
                isConnectionValid = Boolean.FALSE;
                _supportsIsValidMethod = false;
                log.debug("XA datasource does not support isValid method - connection will always be recreated");
            } catch (Throwable t) {
                isConnectionValid = Boolean.FALSE;
                log.debug("XA connection is invalid - will recreate a new one. Cause: " + t);
            }

            if (!isConnectionValid.booleanValue()) {
                if (_connection != null) {
                    try {
                        _connection.close(); // just attempt to clean up anything that we can
                    } catch (Throwable t) {
                    } finally {
                        _connection = null;
                    }
                }

                // Check if the password is encrypted, the criteria should be the existence of <security-domain>EncryptDBPassword</security-domain>
                // in the -ds.xml file.

                if(!_encrypted) {
                    _connection = _dataSource.getXAConnection();
                }
                else {
                    _connection = _dataSource.getXAConnection(_dbUsername, _dbPassword);
                }
                _connection.addConnectionEventListener(_connectionEventListener);
                log.debug("Created new XAConnection");
            }
        } catch (SQLException ex) {
            log.error("AppServerJDBCXARecovery.createConnection got exception " + ex.toString(), ex);

            throw ex;
        } catch (Exception e) {
            log.error("AppServerJDBCXARecovery.createConnection got exception " + e.toString(), e);

            throw new SQLException(e.toString());
        }
    }

    private class LocalConnectionEventListener implements ConnectionEventListener {
        public void connectionErrorOccurred(ConnectionEvent connectionEvent) {
            _connection.removeConnectionEventListener(_connectionEventListener);
            _connection = null;
        }

        public void connectionClosed(ConnectionEvent connectionEvent) {
            _connection.removeConnectionEventListener(_connectionEventListener);
            _connection = null;
        }
    }

    // borrowed from org.jboss.resource.adapter.jdbc.xa.XAManagedConnectionFactory
    // in which the equivalent functionality is protected not public :-(
    private XADataSource getXADataSource(String xaDataSourceClassname, String propertiesString) throws Exception {
        // Map any \ to \\
        propertiesString = propertiesString.replaceAll("\\\\", "\\\\\\\\");

        Properties properties = new Properties();
        InputStream is = new ByteArrayInputStream(propertiesString.getBytes());
        properties.load(is);

        Class clazz = Thread.currentThread().getContextClassLoader().loadClass(xaDataSourceClassname);
        XADataSource xads = (XADataSource) clazz.newInstance();
        Class[] NOCLASSES = new Class[] {};
        for (Iterator i = properties.keySet().iterator(); i.hasNext();) {
            String name = (String) i.next();
            String value = properties.getProperty(name);
            //This is a bad solution.  On the other hand the only known example
            // of a setter with no getter is for Oracle with password.
            //Anyway, each xadatasource implementation should get its
            //own subclass of this that explicitly sets the
            //properties individually.
            Class type = null;
            try {
                Method getter = clazz.getMethod("get" + name, NOCLASSES);
                type = getter.getReturnType();
            } catch (NoSuchMethodException e) {
                type = String.class;

                try {
                    //HACK for now until we can rethink the XADataSourceProperties variable and pass type information
                    Method getter = clazz.getMethod("is" + name, NOCLASSES);
                    type = getter.getReturnType();

                } catch (NoSuchMethodException nsme) {
                    type = String.class;

                }

            }

            Method setter = clazz.getMethod("set" + name, new Class[] { type });
            PropertyEditor editor = PropertyEditorManager.findEditor(type);
            editor.setAsText(value);
            setter.invoke(xads, new Object[] { editor.getValue() });
        }
        return xads;
    }

    public void retrieveData(String parameter,String delimiter)
     {
         StringTokenizer st = new StringTokenizer(parameter,delimiter);
         while (st.hasMoreTokens())
         {
             String data = st.nextToken();
             if(data.length()>9)
             {
                 if(_USERNAME.equalsIgnoreCase(data.substring(0,8)))
                 {
                     _username =data.substring(9);
                 }
                 if(_PASSWORD.equalsIgnoreCase(data.substring(0,8)))
                 {
                     _password =data.substring(9);
                 }
                 if(_JNDINAME.equalsIgnoreCase(data.substring(0,8)))
                 {
                     _dataSourceId=data.substring(9);
                 }
             }
         }

         if(_dataSourceId == null && parameter != null && parameter.indexOf('=') == -1) {
             // try to fallback to old parameter format where only the dataSourceId is given, without jndiname= prefix
             _dataSourceId = parameter;
         }
     }

     private String getValueForKey(String config, String key)
     {
         Pattern usernamePattern = Pattern.compile("(name=" + key + ", value=)(.*)(</li>)");
         Matcher m = usernamePattern.matcher(config);
         if(m.find())
         {
             return m.group(2);
         }
         return "";
     }

     private String getValueForLoginModuleClass(String config)
     {
         Pattern usernamePattern = Pattern.compile("(" + _MODULE + ":)(.*)");
         Matcher m = usernamePattern.matcher(config);
         if(m.find())
         {
             return m.group(2);
         }
         return "";
     }

     private static String decode(String secret) throws NoSuchPaddingException, NoSuchAlgorithmException,
             InvalidKeyException, BadPaddingException, IllegalBlockSizeException
     {
         byte[] kbytes = "jaas is the way".getBytes();
         SecretKeySpec key = new SecretKeySpec(kbytes, "Blowfish");

         BigInteger n = new BigInteger(secret, 16);
         byte[] encoding = n.toByteArray();

         Cipher cipher = Cipher.getInstance("Blowfish");
         cipher.init(Cipher.DECRYPT_MODE, key);
         byte[] decode = cipher.doFinal(encoding);
         return new String(decode);
      }

     private static String decodePBE(MBeanServerConnection server, String password, String jaasSecurityDomain) throws Exception
     {
          byte[] secret = (byte[]) server.invoke(new ObjectName(jaasSecurityDomain), "decode64", new Object[] {password}, new String[] {"java.lang.String"});
          return new String(secret, "UTF-8");
     }


    private boolean _supportsIsValidMethod;

    private XAConnection _connection;
    private XADataSource _dataSource;
    private LocalConnectionEventListener _connectionEventListener;
    private boolean _hasMoreResources;
    private boolean _encrypted;

    private String _dataSourceId;
    private String _username;
    private String _password;
    private String _dbUsername;
    private String _dbPassword;

    private final String _JNDINAME = "jndiname";
    private final String _USERNAME = "username";
    private final String _PASSWORD = "password";
    private final String _MODULE = "LoginModule Class";
    private final String _DELIMITER = ",";

    private Logger log = org.jboss.logging.Logger.getLogger(AppServerJDBCXARecovery.class);
}
