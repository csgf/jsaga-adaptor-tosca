/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package it.infn.ct.jsaga.adaptor.tosca;

import fr.in2p3.jsaga.adaptor.ClientAdaptor;
import fr.in2p3.jsaga.adaptor.base.defaults.Default;
import fr.in2p3.jsaga.adaptor.base.usage.UAnd;
import fr.in2p3.jsaga.adaptor.base.usage.Usage;
import fr.in2p3.jsaga.adaptor.security.SecurityCredential;


import org.ogf.saga.error.*;

import it.infn.ct.jsaga.adaptor.tosca.security.ToscaSecurityCredential;

import java.util.Map;
import org.apache.log4j.Logger;
       
/* *********************************************
 * *** Istituto Nazionale di Fisica Nucleare ***
 * ***      Sezione di Catania (Italy)       ***
 * ***        http://www.ct.infn.it/         ***
 * *********************************************
 * File:    rOCCIJobControlAdaptor.java
  * Authors: Giuseppe LA ROCCA, Riccardo BRUNO
 * Email:   <giuseppe.larocca, riccardo.bruno>@ct.infn.it
 * Ver.:    1.0.0
 * Date:    24 February 2016
 * *********************************************/

public class ToscaAdaptorCommon extends Object implements ClientAdaptor {

  protected ToscaSecurityCredential credential = null;             

  protected String sshHost = null;  
  protected static final String AUTH = "auth";
  protected static final String RESOURCE = "compute";
  protected static final String USER_NAME = "user.name";
  protected static final String TOKEN = "token";
  protected static final String TOSCA_TEMPLATE = "tosca_template";
  
  public static final String LS = System.getProperty("line.separator");
  private static final Logger log = 
          Logger.getLogger(ToscaAdaptorCommon.class);
  
  // TOSCA data
  protected String toscaHost = "unset";
  protected int    toscaPort = 80; // Default port is HTTP
  protected String notfyEndpointHost="unset";
  protected int    notfyEndpointPort=8888; // Default port is 8888 ApiServerDaemon dev. port
  protected String ssh_username="";
  protected String ssh_password="";
  
  @Override
  public Class[] getSupportedSecurityCredentialClasses() 
  {    
      return new Class[] {         
      	  ToscaSecurityCredential.class
      };      
  }

  @Override
  public void setSecurityCredential(SecurityCredential sc) 
  {
      credential = (ToscaSecurityCredential)sc;
      credential.setUsername(ssh_username);
      credential.setPassword(ssh_password);
      
      try {            
            log.debug("No security is necessary yet"  + LS 
                     +"User: '"+credential.getUserID()+"'"+LS
                     +"ssh_username: '"+ssh_username+"'"+LS
                    +"ssh_password: '"+ssh_password+"'"+LS
                    );
            log.debug("TOKEN:"+sc.getAttribute("token"));                        
      }  catch (NotImplementedException e) { 
          log.debug("NotImplementedException: "+ LS + e.toString()); 
      } catch (NoSuccessException e) { 
          log.debug("NoSuccessException: "+ LS + e.toString()); 
      } catch (Exception e) {
          log.debug("Exception: "+ LS +e.toString());
      }
  }
  
  @Override
  public String getType() { return "tosca"; }

  @Override
  public int getDefaultPort() { return toscaPort; }
  
  @Override
  public void connect (String userInfo, String host, int port, String basePath, Map attributes) 
         throws NotImplementedException, 
                AuthenticationFailedException, 
                AuthorizationFailedException, 
                IncorrectURLException, 
                BadParameterException, 
                TimeoutException, 
                NoSuccessException 
  {
      log.debug("ToscaAdaptorCommon: connect()");
      log.debug("userInfo: "+userInfo);
      log.debug("host: "+host);
      log.debug("port: "+port);
      log.debug("basePath: "+basePath);
      log.debug("attributes: "+attributes);
  }
  
  @Override
  public void disconnect() throws NoSuccessException {  } 

  @Override
  public Usage getUsage() 
  { 
    return new UAnd.Builder()
            //.and(new U(AUTH))
            .build();      
  }

  @Override
  public Default[] getDefaults(Map map) throws IncorrectStateException 
  {
    return new Default[] {
        //new Default (AUTH, "x509"),        
    };
  }
}

